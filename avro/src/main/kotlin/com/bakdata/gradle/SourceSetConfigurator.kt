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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val EXTERNAL_JAVA = "externalJava"

class SourceSetConfigurator(project: Project, sourceSet: SourceSet) {
    private val project: Project
    private val sourceSet: SourceSet
    private val generateAvroJava: GenerateAvroJavaTask
    private val configureDeleteExternalJava: Task
    private val deleteExternalJava: Delete
    private val avroOutputs: FileCollection

    init {
        this.project = project
        this.sourceSet = sourceSet
        this.generateAvroJava =
            project.tasks.named(sourceSet.getTaskName("generate", "avroJava"), GenerateAvroJavaTask::class.java).get()
        this.configureDeleteExternalJava = project.task(sourceSet.getTaskName("configureDelete", EXTERNAL_JAVA)) {
            dependsOn(generateAvroJava)
            group = generateAvroJava.group
        }
        this.deleteExternalJava =
            project.tasks.create(sourceSet.getTaskName("delete", EXTERNAL_JAVA), Delete::class.java) {
                dependsOn(configureDeleteExternalJava)
                group = generateAvroJava.group
            }
        this.avroOutputs = generateAvroJava.outputs.files
    }

    fun configure(): List<Pair<Configuration, ConfigWithGenerateTask>> {
        val compileJava: JavaCompile = project.tasks.named(sourceSet.compileJavaTaskName, JavaCompile::class.java).get()
        compileJava.dependsOn(deleteExternalJava)

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
    ): Pair<Configuration, ConfigWithGenerateTask>? {
        return findByName(configurationName)?.let { configuration: Configuration ->
            val name: String = sourceSet.getConfigurationName("avro", configurationName)
            val avroConfiguration: Configuration = create(name)
            configuration.setupConfiguration(
                avroConfiguration
            )
            configuration to ConfigWithGenerateTask(avroConfiguration, generateAvroJava)
        }
    }

    private fun Configuration.setupConfiguration(
        avroConfiguration: Configuration
    ) {
        extendsFrom(avroConfiguration)
        configureDeleteExternalJava.configureCompilation(avroConfiguration)
    }

    private fun Task.configureCompilation(
        avroConfiguration: Configuration
    ) {
        dependsOn(avroConfiguration)
        doLast {
            val exclusions: List<String> = avroConfiguration.findExclusions()
            // empty exclusions would delete whole folder
            if (exclusions.isNotEmpty()) {
                avroOutputs.files.forEach { file: File ->
                    deleteExternalJava.delete(deleteExternalJava.project.fileTree(file) {
                        include(exclusions)
                    })
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