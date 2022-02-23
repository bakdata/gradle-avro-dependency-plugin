plugins {
    java
    id("com.bakdata.avro")
}
repositories {
    mavenCentral()
}
dependencies {
    avroImplementation(group = "com.bakdata.kafka", name = "error-handling", version = "1.2.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.0")
    testCompile("org.junit.jupiter:junit-jupiter-api:5.3.0")
}
tasks.withType<Test> {
    useJUnitPlatform()
}
