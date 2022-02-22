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
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class AvroPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply("com.github.davidmc24.gradle.plugin.avro")

        getSourceSets(project).each { SourceSet sourceSet ->
            setupSourceSet(project, sourceSet)
        }
    }

    private static void setupSourceSet(Project project, SourceSet sourceSet) {
        GenerateAvroJavaTask generateAvroJava = project.tasks.named(sourceSet.getTaskName("generate", "avroJava"), GenerateAvroJavaTask).get()
        Task configureDeleteExternalJava = project.task(sourceSet.getTaskName("configure", "deleteExternalJava")) { Task task ->
            task.dependsOn(generateAvroJava)
            task.setGroup(generateAvroJava.group)
        }
        Delete deleteExternalJava = project.tasks.create(sourceSet.getTaskName("delete", "externalJava"), Delete) { Delete delete ->
            delete.dependsOn(configureDeleteExternalJava)
            delete.setGroup(generateAvroJava.group)
        }

        JavaCompile compileJava = project.tasks.named(sourceSet.compileJavaTaskName, JavaCompile).get()
        compileJava.dependsOn(deleteExternalJava)

        Provider<Directory> externalAvroDir = project.layout.buildDirectory.dir("external-" + sourceSet.name + "-avro")
        Task configureCopyAvro = project.task(sourceSet.getTaskName("configure", "copyExternalAvroResources")) { Task task ->
            task.setGroup(generateAvroJava.group)
        }
        Copy copyAvro = project.tasks.create(sourceSet.getTaskName("copy", "externalAvroResources"), Copy) { Copy copy ->
            copy.dependsOn(configureCopyAvro)
            copy.setGroup(generateAvroJava.group)
        }
        FileCollection avroOutputs = getAvroOutputs(generateAvroJava)

        project.configurations { ConfigurationContainer configs ->
            registerResources(configs, sourceSet)
            List<String> configurations = getRelevantConfigurations(sourceSet)
            configurations.each { String configurationName ->
                setupConfiguration(configs, configurationName, sourceSet, configureDeleteExternalJava,
                        deleteExternalJava, generateAvroJava, externalAvroDir, copyAvro, configureCopyAvro, avroOutputs)
            }
        }
    }

    private static void setupConfiguration(ConfigurationContainer configs, String configurationName, SourceSet sourceSet,
                                           Task configureDeleteExternalJava, Delete deleteExternalJava,
                                           GenerateAvroJavaTask generateAvroJava,
                                           Provider<Directory> externalAvroDir, Copy copyAvro, Task configureCopyAvro,
                                           FileCollection avroOutputs) {
        Configuration configuration = configs.findByName(configurationName)
        if (configuration) {
            String name = getConfigurationName(sourceSet, "avro", configurationName)
            Configuration avroConfiguration = configs.create(name)
            setupConfiguration(configuration, avroConfiguration, configureDeleteExternalJava,
                    deleteExternalJava, generateAvroJava, externalAvroDir, copyAvro, configureCopyAvro, avroOutputs)
        }
    }

    private static List<String> getRelevantConfigurations(SourceSet sourceSet) {
        Arrays.asList(sourceSet.implementationConfigurationName, sourceSet.apiConfigurationName)
    }

    private static String getConfigurationName(SourceSet sourceSet, String prefix, String configurationName) {
        return configurationName.startsWith(sourceSet.name) ?
                // e.g. testImplementation becomes testAvroImplementation
                sourceSet.name + prefix.capitalize() + configurationName.split(sourceSet.name, 2)[1]
                // e.g. implementation becomes avroImplementation
                : prefix + configurationName.capitalize()
    }

    private static void registerResources(ConfigurationContainer configurations, SourceSet sourceSet) {
        if (configurations.findByName(sourceSet.apiConfigurationName)) {
            // add .avsc files to jar allowing us to use them in other projects as a schema dependency
            sourceSet.resources.srcDirs("src/" + sourceSet.name + "/avro")
        }
    }

    private static SourceSetContainer getSourceSets(Project project) {
        project.extensions.getByType(SourceSetContainer)
    }

    private static void setupConfiguration(Configuration configuration, Configuration avroConfiguration,
                                           Task configureDeleteExternalJava, Delete delete,
                                           GenerateAvroJavaTask generateAvroJava,
                                           Provider<Directory> dir, Copy copy, Task configureCopy,
                                           FileCollection avroOutputs) {
        configuration.extendsFrom avroConfiguration
        addSources(generateAvroJava, avroConfiguration, dir, copy, configureCopy)
        configureCompilation(configureDeleteExternalJava, avroConfiguration, delete, avroOutputs)
    }

    private static FileCollection getAvroOutputs(GenerateAvroJavaTask generateAvroJava) {
        return generateAvroJava.getOutputs().getFiles()
    }

    private static void configureCompilation(Task configureDeleteExternalJava, Configuration avroConfiguration,
                                             Delete delete, FileCollection avroOutputs) {
        configureDeleteExternalJava.dependsOn(avroConfiguration)
        configureDeleteExternalJava.doLast {
            List<String> exclusions = findExclusions(avroConfiguration)
            // empty exclusions would delete whole folder
            if (exclusions) {
                avroOutputs.files.each { File file ->
                    delete.delete delete.project.fileTree(file) { ConfigurableFileTree tree ->
                        tree.include exclusions
                    }
                }
            }
        }
    }

    private static List<String> findExclusions(Configuration avroConfiguration) {
        avroConfiguration
                .findAll { File file ->
                    file.name.endsWith("jar")
                }
                .collect { File file ->
                    new ZipFile(file).entries()
                            .findAll { ZipEntry entry ->
                                entry.name.endsWith(".class")
                            }
                            .collect { ZipEntry entry ->
                                return entry.name.replaceAll(".class\$", ".java")
                            }
                }
                .flatten() as List<String>
    }

    private static void addSources(GenerateAvroJavaTask generateAvroJava, Configuration avroConfiguration,
                                   Provider<Directory> dir, Copy copyTask, Task configureCopy) {
        configureCopy.dependsOn(avroConfiguration)
        // copy external avro files to separate build directory.
        // Directly adding zipTree as source breaks caching: https://github.com/gradle/gradle/issues/18382
        configureCopy.doLast {
            copyTask.from(
                    avroConfiguration.collect { File file ->
                        copyTask.project.zipTree(file)
                    }) {
                it.include "**/*.avsc"
            }
            copyTask.into(dir)
            copyTask.includeEmptyDirs = false
        }
        generateAvroJava.dependsOn(copyTask)
        generateAvroJava.source {
            dir
        }
    }
}
