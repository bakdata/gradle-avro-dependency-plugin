plugins {
    kotlin("jvm") version "1.5.21"
    id("org.jetbrains.dokka") version "1.9.10"
}

description = "A Gradle plugin that lets you compile Apache Avro schemas to Java classes and supports dependencies"

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(group = "com.github.davidmc24.gradle.plugin", name = "gradle-avro-plugin", version = "1.9.1")
}
