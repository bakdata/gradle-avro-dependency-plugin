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

import org.assertj.core.api.Condition
import org.assertj.core.api.SoftAssertions
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class AvroPluginIntegrationTest {
    private fun taskWithPathAndOutcome(path: String, outcome: TaskOutcome):
            Condition<BuildTask> = Condition({ it.path == path && it.outcome == outcome }, "Task $path=$outcome")

    private fun GradleRunner.withProjectPluginClassPath(): GradleRunner {
        val classpath = System.getProperty("java.class.path")
        return withPluginClasspath(classpath.split(":").map { File(it) })
    }

    @Test
    fun shouldCompileAndHaveNoExternalClassFiles(@TempDir testProjectDir: Path) {
        Files.writeString(
            testProjectDir.resolve("build.gradle.kts"), """
            plugins {
                java
                id("com.bakdata.avro")
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation(group ="org.apache.avro", name = "avro", version = "1.11.0")
                avroImplementation(group = "com.bakdata.kafka", name = "error-handling", version = "1.2.2")
            }
        """.trimIndent()
        )
        Files.createDirectories(testProjectDir.resolve("src/main/avro/"))
        Files.copy(
            AvroPluginIntegrationTest::class.java.getResourceAsStream("/Record.avsc"),
            testProjectDir.resolve("src/main/avro/Record.avsc")
        )
        Files.createDirectories(testProjectDir.resolve("src/test/avro/"))
        Files.copy(
            AvroPluginIntegrationTest::class.java.getResourceAsStream("/TestRecord.avsc"),
            testProjectDir.resolve("src/test/avro/TestRecord.avsc")
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("build")
            .withProjectPluginClassPath()
            .build()
        println(result.output)

        SoftAssertions.assertSoftly { softly ->
            softly.assertThat(result.tasks)
                .haveExactly(1, taskWithPathAndOutcome(":configureCopyExternalAvroResources", TaskOutcome.SUCCESS))
                .haveExactly(1, taskWithPathAndOutcome(":copyExternalAvroResources", TaskOutcome.SUCCESS))
                .haveExactly(1, taskWithPathAndOutcome(":configureCopyTestExternalAvroResources", TaskOutcome.SUCCESS))
                .haveExactly(1, taskWithPathAndOutcome(":copyTestExternalAvroResources", TaskOutcome.SUCCESS))
            val javaClasses = testProjectDir.resolve("build/classes/java")
            softly.assertThat(javaClasses.resolve("main/com/bakdata/kafka/DeadLetter.class").toFile())
                .doesNotExist()
            softly.assertThat(javaClasses.resolve("main/com/bakdata/Record.class").toFile())
                .exists()
            softly.assertThat(javaClasses.resolve("test/com/bakdata/kafka/DeadLetter.class").toFile())
                .doesNotExist()
            softly.assertThat(javaClasses.resolve("test/com/bakdata/TestRecord.class").toFile())
                .exists()
        }
    }
}
