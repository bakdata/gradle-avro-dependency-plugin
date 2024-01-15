/*
 * The MIT License
 *
 * Copyright (c) 2024 bakdata
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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val EXTERNAL_AVRO_RESOURCES = "externalAvroResources"

class SourceSetConfigurator(project: Project, sourceSet: SourceSet) {
    private val project: Project
    private val sourceSet: SourceSet
    private val generateAvroJava: GenerateAvroJavaTask
    private val externalAvroDir: Provider<Directory>
    private val configureCopyAvro: Task
    private val copyAvro: Copy

    init {
        this.project = project
        this.sourceSet = sourceSet
        this.generateAvroJava =
            project.tasks.named(sourceSet.getTaskName("generate", "avroJava"), GenerateAvroJavaTask::class.java).get()
        this.externalAvroDir = project.layout.buildDirectory.dir("external-${sourceSet.name}-avro")
        this.configureCopyAvro = project.task(sourceSet.getTaskName("configureCopy", EXTERNAL_AVRO_RESOURCES)) {
            group = generateAvroJava.group
        }
        this.copyAvro =
            project.tasks.create(sourceSet.getTaskName("copy", EXTERNAL_AVRO_RESOURCES), Copy::class.java) {
                dependsOn(configureCopyAvro)
                group = generateAvroJava.group
            }
    }

    fun configure(): List<Pair<Configuration, Configuration>> {
        with(project.configurations) {
            registerResources(sourceSet)
            val configurations: List<String> = sourceSet.getRelevantConfigurations()
            return configurations.mapNotNull { configurationName ->
                setupConfiguration(
                    configurationName
                )
            }
        }
    }

    private fun ConfigurationContainer.setupConfiguration(
        configurationName: String
    ): Pair<Configuration, Configuration>? {
        return findByName(configurationName)?.let { configuration: Configuration ->
            val name: String = sourceSet.getConfigurationName("avro", configurationName)
            val avroConfiguration: Configuration = create(name)
            configuration.setupConfiguration(
                avroConfiguration
            )
            configuration to avroConfiguration
        }
    }

    private fun Configuration.setupConfiguration(
        avroConfiguration: Configuration
    ) {
        extendsFrom(avroConfiguration)
        generateAvroJava.addSources(avroConfiguration)
        generateAvroJava.configureDeletion(avroConfiguration)
    }

    private fun GenerateAvroJavaTask.addSources(
        avroConfiguration: Configuration
    ) {
        configureCopyAvro.dependsOn(avroConfiguration)
        // copy external avro files to separate build directory.
        // Directly adding zipTree as source breaks caching: https://github.com/gradle/gradle/issues/18382
        configureCopyAvro.doLast {
            copyAvro.from(
                avroConfiguration.map { file: File ->
                    copyAvro.project.zipTree(file)
                }) {
                include("**/*.avsc")
            }
            copyAvro.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            copyAvro.into(externalAvroDir)
            copyAvro.includeEmptyDirs = false
        }
        dependsOn(copyAvro)
        source(externalAvroDir)
    }

    private fun Task.configureDeletion(
        avroConfiguration: Configuration
    ) {
        doLast {
            val exclusions: List<String> = avroConfiguration.findExclusions()
            // empty exclusions would delete whole folder
            if (exclusions.isNotEmpty()) {
                outputs.files.forEach { outputFile: File ->
                    val filesToDelete: FileTree = project.fileTree(outputFile) {
                        include(exclusions)
                    }
                    filesToDelete.files.forEach { fileToDelete: File ->
                        fileToDelete.delete()
                    }
                }
            }
        }
    }

    private fun Configuration.findExclusions() =
        filter { file: File -> file.name.endsWith("jar") }
            .flatMap { file: File ->
                findExclusions(file)
            }

    private fun findExclusions(file: File) = ZipFile(file).entries().asSequence()
        .filter { entry: ZipEntry -> entry.name.endsWith(".class") }
        .map { entry: ZipEntry -> entry.name.replace(Regex(".class$"), ".java") }
        .asIterable()

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

    private fun ConfigurationContainer.registerResources(sourceSet: SourceSet) {
        findByName(sourceSet.apiConfigurationName)?.also {
            // add .avsc files to jar allowing us to use them in other projects as a schema dependency
            sourceSet.resources.srcDirs("src/${sourceSet.name}/avro")
        }
    }

    private fun SourceSet.getRelevantConfigurations() =
        listOf(implementationConfigurationName, apiConfigurationName)

}
