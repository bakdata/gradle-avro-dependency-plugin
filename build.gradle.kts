plugins {
    java
    `java-gradle-plugin`
    // release
    alias(libs.plugins.release)
    alias(libs.plugins.sonar)
    alias(libs.plugins.sonatype)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.dokka)
}

description = "A Gradle plugin that lets you compile Apache Avro schemas to Java classes and supports dependencies"

repositories {
    maven(url = "https://plugins.gradle.org/m2/")
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
}

group = "com.bakdata.gradle.avro"

tasks.withType<Test> {
    maxParallelForks = 4
}

publication {
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
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// config for Gradle plugin portal doesn't support snapshot, so we add config only if release version
if (!version.toString().endsWith("-SNAPSHOT")) {
    apply(plugin = "com.gradle.plugin-publish")
}

gradlePlugin {
    website.set("https://github.com/bakdata/gradle-avro-dependency-plugin")
    vcsUrl.set("https://github.com/bakdata/gradle-avro-dependency-plugin")
    plugins {
        create("AvroPlugin") {
            id = "com.bakdata.avro"
            implementationClass = "com.bakdata.gradle.avro.AvroDependencyPlugin"
            description = project.description
            displayName = "Gradle Avro dependency plugin"
            tags = listOf("bakdata", "avro")
        }
    }
}

dependencies {
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    implementation(libs.avro.plugin)
}
