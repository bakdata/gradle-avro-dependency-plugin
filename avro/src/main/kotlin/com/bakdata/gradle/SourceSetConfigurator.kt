package com.bakdata.gradle

import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask
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
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val EXTERNAL_AVRO_RESOURCES = "externalAvroResources"

private const val EXTERNAL_JAVA = "externalJava"

class SourceSetConfigurator(project: Project, sourceSet: SourceSet) {
    private val project: Project
    private val sourceSet: SourceSet
    private val generateAvroJava: GenerateAvroJavaTask
    private val configureDeleteExternalJava: Task
    private val deleteExternalJava: Delete
    private val externalAvroDir: Provider<Directory>
    private val configureCopyAvro: Task
    private val copyAvro: Copy
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
        this.externalAvroDir = project.layout.buildDirectory.dir("external-${sourceSet.name}-avro")
        this.configureCopyAvro = project.task(sourceSet.getTaskName("configureCopy", EXTERNAL_AVRO_RESOURCES)) {
            group = generateAvroJava.group
        }
        this.copyAvro =
            project.tasks.create(sourceSet.getTaskName("copy", EXTERNAL_AVRO_RESOURCES), Copy::class.java) {
                dependsOn(configureCopyAvro)
                group = generateAvroJava.group
            }
        this.avroOutputs = generateAvroJava.outputs.files
    }

    fun configure(): List<Pair<Configuration, Configuration>> {
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
        configureDeleteExternalJava.configureCompilation(avroConfiguration)
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
            copyAvro.into(externalAvroDir)
            copyAvro.includeEmptyDirs = false
        }
        dependsOn(copyAvro)
        source(externalAvroDir)
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