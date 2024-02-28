plugins {
    // release
    id("net.researchgate.release") version "3.0.2"
    id("com.bakdata.sonar") version "1.1.17"
    id("com.bakdata.sonatype") version "1.2.1"
    id("org.hildan.github.changelog") version "1.13.1"
    id("org.gradle.kotlin.kotlin-dsl") version "2.1.6" apply false
    id("com.gradle.plugin-publish") version "1.2.1" apply false
}

allprojects {
    repositories {
        maven(url = "https://plugins.gradle.org/m2/")
    }

    group = "com.bakdata.gradle"

    tasks.withType<Test> {
        maxParallelForks = 4
    }
}

configure<com.bakdata.gradle.SonatypeSettings> {
    developers {
        developer {
            name.set("Philipp Schirmer")
            id.set("philipp94831")
        }
    }
}

configure<org.hildan.github.changelog.plugin.GitHubChangelogExtension> {
    githubUser = "bakdata"
    futureVersionTag = findProperty("changelog.releaseVersion")?.toString()
    sinceTag = findProperty("changelog.sinceTag")?.toString()
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    apply(plugin = "java-gradle-plugin")

    // config for gradle plugin portal doesn't support snapshot, so we add config only if release version
    if (!version.toString().endsWith("-SNAPSHOT")) {
        apply(plugin = "com.gradle.plugin-publish")
    }

    // description is only ready after evaluation
    afterEvaluate {
        configure<GradlePluginDevelopmentExtension> {
            plugins {
                create("${project.name.capitalize()}Plugin") {
                    id = "com.bakdata.${project.name}"
                    implementationClass = "com.bakdata.gradle.${project.name.capitalize()}Plugin"
                    description = project.description
                    displayName = "Bakdata $name plugin"
                }
            }
        }

        extensions.findByType(com.gradle.publish.PluginBundleExtension::class)?.apply {
            // actual block of plugin portal config, need to be done on each subproject as the plugin does not support multi-module projects yet...
            website = "https://github.com/bakdata/gradle-avro-dependency-plugin"
            vcsUrl = "https://github.com/bakdata/gradle-avro-dependency-plugin"
            tags = listOf("bakdata", name)
        }
    }

    dependencies {
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.7.2")
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.7.2")
        "testImplementation"("org.assertj", "assertj-core", "3.20.2")
    }
}

val sonarqube by tasks
sonarqube.enabled = false //FIXME requires Java 17
