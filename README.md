[![Build Status](https://dev.azure.com/bakdata/public/_apis/build/status/bakdata.gradle-avro-dependency-plugin?repoName=bakdata%2Fgradle-avro-dependency-plugin&branchName=main)](https://dev.azure.com/bakdata/public/_build/latest?definitionId=29&repoName=bakdata%2Fgradle-avro-dependency-plugin&branchName=main)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.bakdata.gradle%3Agradle-avro-dependency-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=com.bakdata.gradle%3Agradle-avro-dependency-plugin)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.bakdata.gradle%3Agradle-avro-dependency-plugin&metric=coverage)](https://sonarcloud.io/summary/new_code?id=com.bakdata.gradle%3Agradle-avro-dependency-plugin)

# gradle-avro-dependency-plugin
A Gradle plugin that lets you compile Apache Avro schemas to Java classes and supports dependencies.

It is available on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.bakdata.avro).

This plugin applies the [gradle-avro-plugin](https://github.com/davidmc24/gradle-avro-plugin)
and adds the capability to reference external Avro schemas in your schema files using dependencies.
This plugin adds the configurations `avroImplementation` and `testAvroImplementation` to your Gradle project.
Thereby, you can include external .avsc files without copying them to the project.
The .avsc files need to be present in the referenced artifact.
If the java-library plugin is also present, .avsc files will be added to the jar publication,
thus making it usable as a dependency for the aforementioned configurations.
The java-library plugin also adds the `avroApi` configuration.

## Example
With the following `build.gradle.kts` file
```
plugins {
    java
    id("com.bakdata.avro") version "1.0.0"
}
repositories {
    mavenCentral()
}
dependencies {
    avroImplementation(group = "com.bakdata.kafka", name = "error-handling", version = "1.2.2")
}
```
you are able to compile the following Avro schema when placing it in `src/main/avro`
```
{
  "type": "record",
  "name": "Record",
  "namespace": "com.bakdata",
  "fields": [
    {
      "name": "dead_letter",
      "type": "com.bakdata.kafka.DeadLetter"
    }
  ]
}
```

By just using the gradle-avro-plugin, you would not be able to compile the schema
because the schema for `com.bakdata.kafka.DeadLetter` is not present in the project.
It is located in the `com.bakdata.kafka:error-handling` dependency
and our plugin adds it to the classpath of the Avro compiler.

## Development

Snapshot versions of these plugins are published to Sonatype.
You can use them in your project by adding the following snippet to your `settings.gradle.kts`

```
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
}
```
