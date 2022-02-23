[![Build Status](https://dev.azure.com/bakdata/public/_apis/build/status/bakdata.gradle-avro-dependency-plugin?repoName=bakdata%2Fgradle-avro-dependency-plugin&branchName=main)](https://dev.azure.com/bakdata/public/_build/latest?definitionId=29&repoName=bakdata%2Fgradle-avro-dependency-plugin&branchName=main)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.bakdata.gradle%3Agradle-avro-dependency-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=com.bakdata.gradle%3Agradle-avro-dependency-plugin)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.bakdata.gradle%3Agradle-avro-dependency-plugin&metric=coverage)](https://sonarcloud.io/summary/new_code?id=com.bakdata.gradle%3Agradle-avro-dependency-plugin)

# gradle-avro-dependency-plugin
A Gradle plugin that lets you compile Apache Avro schemas to Java classes and supports dependencies.

This plugin applies the [gradle-avro-plugin](https://github.com/davidmc24/gradle-avro-plugin)
and adds the capability to reference external Avro schemas in your schema files using dependencies.
This plugin adds the configurations `avroImplementation` and `testAvroImplementation` to your Gradle project.
Thereby, you can include external .avsc files without needing to copy them to the project.
The .avsc file need to be present in the referenced artifact.
If the java-library plugin is also present, .avsc files will be added to the jar publication,
thus making it usable as a dependency for the aforementioned configurations.
The java-library plugin also adds the `avroApi` configuration.

## Example
With the following `build.gradle.kts` file
```
plugins {
    java
    id("com.bakdata.avro")
}
repositories {
    mavenCentral()
}
dependencies {
    avroImplementation(group = "com.bakdata.kafka", name = "error-handling", version = "1.2.2")
}
```
you are able to compile the following Avro schema
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