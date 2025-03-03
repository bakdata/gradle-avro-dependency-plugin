plugins {
    java
    `java-gradle-plugin`
    // release
    id("com.bakdata.release") version "1.8.1"
    id("com.bakdata.sonar") version "1.8.1"
    id("com.bakdata.sonatype") version "1.8.2-SNAPSHOT"
    id("org.gradle.kotlin.kotlin-dsl") version "5.1.2"
    id("com.gradle.plugin-publish") version "1.3.0"
    id("org.jetbrains.dokka") version "1.9.10"
}

description = "A Gradle plugin that lets you compile Apache Avro schemas to Java classes and supports dependencies"

repositories {
    maven(url = "https://plugins.gradle.org/m2/")
}

group = "com.bakdata.gradle"

tasks.withType<Test> {
    maxParallelForks = 4
}

sonatype {
    developers {
        developer {
            name.set("Philipp Schirmer")
            id.set("philipp94831")
        }
    }
    createPublication = false
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

// config for gradle plugin portal doesn't support snapshot, so we add config only if release version
if (!version.toString().endsWith("-SNAPSHOT")) {
    apply(plugin = "com.gradle.plugin-publish")
}

gradlePlugin {
    website.set("https://github.com/bakdata/gradle-avro-dependency-plugin")
    vcsUrl.set("https://github.com/bakdata/gradle-avro-dependency-plugin")
    plugins {
        create("AvroPlugin") {
            id = "com.bakdata.avro"
            implementationClass = "com.bakdata.gradle.AvroPlugin"
            description = project.description
            displayName = "Gradle Avro dependency plugin"
            tags = listOf("bakdata", "avro")
        }
    }
}

dependencies {
    val junitVersion = "5.11.4"
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = junitVersion)
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = junitVersion)
    testImplementation(group = "org.assertj", name = "assertj-core", version = "3.27.2")
    implementation(group = "com.github.davidmc24.gradle.plugin", name = "gradle-avro-plugin", version = "1.9.1")
}
