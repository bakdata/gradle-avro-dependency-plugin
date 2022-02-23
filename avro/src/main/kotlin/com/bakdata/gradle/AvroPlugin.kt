/*
 * The MIT License
 *
 * Copyright (c) 2022 bakdata GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bakdata.gradle

import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class AvroPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply("com.github.davidmc24.gradle.plugin.avro")
        val sourceSets: SourceSetContainer = project.getSourceSets()
        val configurationsWithAvroConfiguration: Map<Configuration, Configuration> = sourceSets
            .flatMap { sourceSet: SourceSet ->
                project.setupSourceSet(sourceSet)
            }.associateBy({ it.first }, { it.second })
        applyInheritance(configurationsWithAvroConfiguration)
    }

    private fun applyInheritance(configurationsWithAvroConfiguration: Map<Configuration, Configuration>) {
        configurationsWithAvroConfiguration.forEach { (originalConfiguration: Configuration, avroConfiguration: Configuration) ->
            originalConfiguration.extendsFrom.forEach { extendsFrom: Configuration ->
                configurationsWithAvroConfiguration[extendsFrom]?.also { extendsFromAvroConfiguration: Configuration ->
                    println("Letting ${avroConfiguration.name} extend from ${extendsFromAvroConfiguration.name}")
                    avroConfiguration.extendsFrom(extendsFromAvroConfiguration)
                }
            }
        }
    }

    private fun Project.setupSourceSet(sourceSet: SourceSet): List<Pair<Configuration, Configuration>> {
        val generateAvroJava: GenerateAvroJavaTask =
            tasks.named(sourceSet.getTaskName("generate", "avroJava"), GenerateAvroJavaTask::class.java).get()
        val configureDeleteExternalJava: Task = task(sourceSet.getTaskName("configureDelete", "externalJava")) {
            dependsOn(generateAvroJava)
            group = generateAvroJava.group
        }
        val deleteExternalJava: Delete =
            tasks.create(sourceSet.getTaskName("delete", "externalJava"), Delete::class.java) {
                dependsOn(configureDeleteExternalJava)
                group = generateAvroJava.group
            }

        val compileJava: JavaCompile = tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java).get()
        compileJava.dependsOn(deleteExternalJava)

        val externalAvroDir: Provider<Directory> = layout.buildDirectory.dir("external-${sourceSet.name}-avro")
        val configureCopyAvro: Task = task(sourceSet.getTaskName("configureCopy", "externalAvroResources")) {
            group = generateAvroJava.group
        }
        val copyAvro: Copy =
            tasks.create(sourceSet.getTaskName("copy", "externalAvroResources"), Copy::class.java) {
                dependsOn(configureCopyAvro)
                group = generateAvroJava.group
            }
        val avroOutputs: FileCollection = generateAvroJava.getAvroOutputs()

        with(configurations) {
            registerResources(sourceSet)
            val configurations: List<String> = sourceSet.getRelevantConfigurations()
            return configurations.mapNotNull { configurationName ->
                setupConfiguration(
                    configurationName,
                    sourceSet,
                    configureDeleteExternalJava,
                    deleteExternalJava,
                    generateAvroJava,
                    externalAvroDir,
                    copyAvro,
                    configureCopyAvro,
                    avroOutputs
                )
            }
        }
    }

    private fun ConfigurationContainer.setupConfiguration(
        configurationName: String,
        sourceSet: SourceSet,
        configureDeleteExternalJava: Task,
        deleteExternalJava: Delete,
        generateAvroJava: GenerateAvroJavaTask,
        externalAvroDir: Provider<Directory>,
        copyAvro: Copy,
        configureCopyAvro: Task,
        avroOutputs: FileCollection
    ): Pair<Configuration, Configuration>? {
        return findByName(configurationName)?.let { configuration: Configuration ->
            val name: String = sourceSet.getConfigurationName("avro", configurationName)
            val avroConfiguration: Configuration = create(name)
            configuration.setupConfiguration(
                avroConfiguration,
                configureDeleteExternalJava,
                deleteExternalJava,
                generateAvroJava,
                externalAvroDir,
                copyAvro,
                configureCopyAvro,
                avroOutputs
            )
            Pair(configuration, avroConfiguration)
        }
    }

    private fun Configuration.setupConfiguration(
        avroConfiguration: Configuration,
        configureDeleteExternalJava: Task,
        delete: Delete,
        generateAvroJava: GenerateAvroJavaTask,
        dir: Provider<Directory>,
        copy: Copy,
        configureCopy: Task,
        avroOutputs: FileCollection
    ) {
        extendsFrom(avroConfiguration)
        generateAvroJava.addSources(avroConfiguration, dir, copy, configureCopy)
        configureDeleteExternalJava.configureCompilation(avroConfiguration, delete, avroOutputs)
    }

    private fun Task.configureCompilation(
        avroConfiguration: Configuration,
        delete: Delete,
        avroOutputs: FileCollection
    ) {
        dependsOn(avroConfiguration)
        doLast {
            val exclusions: List<String> = avroConfiguration.findExclusions()
            // empty exclusions would delete whole folder
            if (exclusions.isNotEmpty()) {
                avroOutputs.files.forEach { file: File ->
                    delete.delete(delete.project.fileTree(file) {
                        include(exclusions)
                    })
                }
            }
        }
    }

    private fun GenerateAvroJavaTask.addSources(
        avroConfiguration: Configuration,
        dir: Provider<Directory>,
        copyTask: Copy,
        configureCopy: Task
    ) {
        configureCopy.dependsOn(avroConfiguration)
        // copy external avro files to separate build directory.
        // Directly adding zipTree as source breaks caching: https://github.com/gradle/gradle/issues/18382
        configureCopy.doLast {
            copyTask.from(
                avroConfiguration.map { file: File ->
                    copyTask.project.zipTree(file)
                }) {
                include("**/*.avsc")
            }
            copyTask.into(dir)
            copyTask.includeEmptyDirs = false
        }
        dependsOn(copyTask)
        source(dir)
    }

    private fun SourceSet.getConfigurationName(
        prefix: String,
        configurationName: String
    ) =
        if (configurationName.startsWith(name)) {
            // e.g. testImplementation becomes testAvroImplementation
            "$name${prefix.capitalize()}" + configurationName.split(name.toPattern(), 2)[1]
        } else {
            // e.g. implementation becomes avroImplementation
            "$prefix${configurationName.capitalize()}"
        }

    private fun SourceSet.getRelevantConfigurations() =
        listOf(implementationConfigurationName, apiConfigurationName)

    private fun ConfigurationContainer.registerResources(sourceSet: SourceSet) {
        findByName(sourceSet.apiConfigurationName)?.also {
            // add .avsc files to jar allowing us to use them in other projects as a schema dependency
            sourceSet.resources.srcDirs("src/${sourceSet.name}/avro")
        }
    }

    private fun GenerateAvroJavaTask.getAvroOutputs() = outputs.files

    private fun Project.getSourceSets() = project.extensions.getByType(SourceSetContainer::class)

    private fun Configuration.findExclusions() =
        filter { file: File -> file.name.endsWith("jar") }
            .flatMap { file: File ->
                findExclusions(file)
            }

    private fun findExclusions(file: File) = ZipFile(file).entries().asSequence()
        .filter { entry: ZipEntry -> entry.name.endsWith(".class") }
        .map { entry: ZipEntry -> entry.name.replace(Regex(".class$"), ".java") }
        .asIterable()
}