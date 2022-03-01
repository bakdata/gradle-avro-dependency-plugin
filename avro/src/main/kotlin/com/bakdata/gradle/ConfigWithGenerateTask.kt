package com.bakdata.gradle

import com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask
import org.gradle.api.artifacts.Configuration
import java.io.File

class ConfigWithGenerateTask(val avroConfiguration: Configuration, val generateAvroJava: GenerateAvroJavaTask) {
    fun addSources() {
        avroConfiguration.map { file: File ->
            generateAvroJava.project.zipTree(file).files
        }.forEach { files: Set<File> ->
            generateAvroJava.source(files)
        }
    }
}
