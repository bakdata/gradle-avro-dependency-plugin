plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka") version "1.9.10"
}
apply(plugin = "org.gradle.kotlin.kotlin-dsl")

description = "A Gradle plugin that lets you compile Apache Avro schemas to Java classes and supports dependencies"

dependencies {
    implementation(group = "com.github.davidmc24.gradle.plugin", name = "gradle-avro-plugin", version = "1.9.1")
}
