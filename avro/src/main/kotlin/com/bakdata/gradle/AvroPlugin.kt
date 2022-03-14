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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType

class AvroPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply("com.github.davidmc24.gradle.plugin.avro")
        val findPlugin: Plugin<Any>? = project.plugins.findPlugin("java-library")
        findPlugin.also { println("Found ${it}") }
        val sourceSets: SourceSetContainer = project.getSourceSets()
        val configurationsWithAvroConfiguration: Map<Configuration, Configuration> = sourceSets
            .flatMap { sourceSet: SourceSet ->
                SourceSetConfigurator(project, sourceSet).configure()
            }.toMap()
        applyInheritance(configurationsWithAvroConfiguration)
    }

    private fun applyInheritance(configurationsWithAvroConfiguration: Map<Configuration, Configuration>) {
        configurationsWithAvroConfiguration.forEach { (originalConfiguration: Configuration, avroConfiguration: Configuration) ->
            originalConfiguration.extendsFrom.forEach { extendsFrom: Configuration ->
                // check if there is an avro configuration for the configuration it extends from
                val extendsFromAvroConfiguration = configurationsWithAvroConfiguration[extendsFrom]
                extendsFromAvroConfiguration?.also {
                    // if yes, avroConfiguration should extend from the respective avro configuration
                    avroConfiguration.extendsFrom(it)
                }
            }
        }
    }

    private fun Project.getSourceSets() = project.extensions.getByType(SourceSetContainer::class)

}