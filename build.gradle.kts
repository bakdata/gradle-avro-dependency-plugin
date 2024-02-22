buildscript {
    dependencies {
        classpath("org.gradle.kotlin:gradle-kotlin-dsl-plugins:2.1.6")
        classpath("com.gradle.publish:plugin-publish-plugin:0.11.0")
    }
}

plugins {
    // release
    id("net.researchgate.release") version "3.0.2"
    id("com.bakdata.sonar") version "1.1.17"
    id("com.bakdata.sonatype") version "1.1.14"
    id("org.hildan.github.changelog") version "1.13.1"
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

    dependencies {
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.7.2")
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.7.2")
        "testImplementation"("org.assertj", "assertj-core", "3.20.2")
    }
}

// config for gradle plugin portal
// doesn't support snapshot, so we add config only if release version
if (!version.toString().endsWith("-SNAPSHOT")) {
    subprojects.forEach { project ->
        with(project) {
            // com.gradle.plugin-publish depends on java-gradle-plugin, but it screws a bit this project
            apply(plugin = "java-gradle-plugin")
            apply(plugin = "com.gradle.plugin-publish")
            project.afterEvaluate {
                // java-gradle-plugin requires this block, but we already added the definitions in META-INF for unit testing...
                configure<GradlePluginDevelopmentExtension> {
                    plugins {
                        create("${project.name.capitalize()}Plugin") {
                            id = "com.bakdata.${project.name}"
                            implementationClass = "com.bakdata.gradle.${project.name.capitalize()}Plugin"
                            description = project.description
                        }
                    }
                }
                // actual block of plugin portal config, need to be done on each subproject as the plugin does not support multi-module projects yet...
                configure<com.gradle.publish.PluginBundleExtension> {
                    website = "https://github.com/bakdata/gradle-avro-dependency-plugin"
                    vcsUrl = "https://github.com/bakdata/gradle-avro-dependency-plugin"
                    (plugins) {
                        "${name.capitalize()}Plugin" {
                            displayName = "Bakdata $name plugin"
                            tags = listOf("bakdata", name)
                        }
                    }
                }
            }
        }
    }
}

val sonarqube by tasks
sonarqube.enabled = false //FIXME requires Java 17
