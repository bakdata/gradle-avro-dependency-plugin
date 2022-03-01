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

import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.assertj.core.api.SoftAssertions
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.io.File

internal class AvroPluginTest {
    private fun taskWithName(name: String): Condition<Task> = Condition({ it.name == name }, "Task with name $name")
    private fun configurationWithName(name: String): Condition<Configuration> =
        Condition({ it.name == name }, "Configuration with name $name")

    private fun folderWithName(name: String): Condition<File> =
        Condition({ it.path.endsWith(name.replace("/", File.separator)) }, "File with name $name")

    private fun Project.evaluate() {
        (this as DefaultProject).evaluate()
    }

    @Test
    fun shouldAddTasksAndConfigurations() {
        val project = ProjectBuilder.builder().build()

        Assertions.assertThatCode {
            project.pluginManager.apply("java")
            project.pluginManager.apply("com.bakdata.avro")
            project.evaluate()
        }.doesNotThrowAnyException()

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(project.plugins.hasPlugin("com.github.davidmc24.gradle.plugin.avro"))
                .isTrue
            softly.assertThat(project.tasks)
                .haveExactly(1, taskWithName("configureDeleteExternalJava"))
                .haveExactly(1, taskWithName("deleteExternalJava"))
                .haveExactly(1, taskWithName("configureDeleteTestExternalJava"))
                .haveExactly(1, taskWithName("deleteTestExternalJava"))
            softly.assertThat(project.configurations)
                .haveExactly(1, configurationWithName("avroImplementation"))
                .haveExactly(0, configurationWithName("avroApi"))
                .haveExactly(1, configurationWithName("testAvroImplementation"))
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("testAvroImplementation")
                    softly.assertThat(it.extendsFrom).anySatisfy { extendsFrom ->
                        softly.assertThat(extendsFrom.name).isEqualTo("avroImplementation")
                    }
                }
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("avroImplementation")
                    softly.assertThat(it.extendsFrom).isEmpty()
                }
            softly.assertThat(project.extensions.getByType(SourceSetContainer::class))
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("main")
                    softly.assertThat(it.resources.srcDirs)
                        .haveExactly(0, folderWithName("src/main/avro"))
                }
        }
    }

    @Test
    fun shouldAddApiConfigurations() {
        val project = ProjectBuilder.builder().build()

        Assertions.assertThatCode {
            project.pluginManager.apply("java-library")
            project.pluginManager.apply("com.bakdata.avro")
            project.evaluate()
        }.doesNotThrowAnyException()

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(project.plugins.hasPlugin("com.github.davidmc24.gradle.plugin.avro"))
                .isTrue
            softly.assertThat(project.tasks)
                .haveExactly(1, taskWithName("configureDeleteExternalJava"))
                .haveExactly(1, taskWithName("deleteExternalJava"))
                .haveExactly(1, taskWithName("configureDeleteTestExternalJava"))
                .haveExactly(1, taskWithName("deleteTestExternalJava"))
            softly.assertThat(project.configurations)
                .haveExactly(1, configurationWithName("avroImplementation"))
                .haveExactly(1, configurationWithName("avroApi"))
                .haveExactly(1, configurationWithName("testAvroImplementation"))
                .haveExactly(0, configurationWithName("testAvroApi"))
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("testAvroImplementation")
                    softly.assertThat(it.extendsFrom).anySatisfy { extendsFrom ->
                        softly.assertThat(extendsFrom.name).isEqualTo("avroImplementation")
                    }
                }
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("avroImplementation")
                    softly.assertThat(it.extendsFrom).anySatisfy { extendsFrom ->
                        softly.assertThat(extendsFrom.name).isEqualTo("avroApi")
                    }
                }
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("avroApi")
                    softly.assertThat(it.extendsFrom).isEmpty()
                }
            softly.assertThat(project.extensions.getByType(SourceSetContainer::class))
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("main")
                    softly.assertThat(it.resources.srcDirs)
                        .haveExactly(1, folderWithName("src/main/avro"))
                }
                .anySatisfy {
                    softly.assertThat(it.name).isEqualTo("test")
                    softly.assertThat(it.resources.srcDirs)
                        .haveExactly(0, folderWithName("src/test/avro"))
                }
        }
    }
}