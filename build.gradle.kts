import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "fr.corpauration"
version = "1.2.2"

repositories {
    mavenCentral()
    maven { url = uri("https://dl.bintray.com/kotlinx/kotlinx") }
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
}

dependencies {
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.quartz-scheduler:quartz:2.3.2")
    implementation(project(":cy-celcat"))
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("org.latencyutils:LatencyUtils:2.0.3")
    implementation("io.ktor:ktor-server-core:2.1.1")
    implementation("io.ktor:ktor-server-netty:2.1.1")
    implementation("io.ktor:ktor-server-metrics-micrometer:2.1.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.9.2")
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_16.toString()
}

abstract class DockeriseTask : DefaultTask() {

    @Input
    var ver: String = ""

    init {
        dependsOn("jar")
    }

    @TaskAction
    fun dockerise() {
        val process = ProcessBuilder("docker", "image", "build", "-t", "cyrel-sync:${ver}", ".").start()
        process.inputStream.reader(Charsets.UTF_8).use {
            println(it.readText())
        }
        process.errorStream.reader(Charsets.UTF_8).use {
            println(it.readText())
        }
        while (process.isAlive) {

        }
    }
}

abstract class DeleteJarTask : DefaultTask() {

    @TaskAction
    fun deleteJar() {
    }
}

tasks.register<DockeriseTask>("dockerise") {
    ver = version as String
}

tasks.register<DeleteJarTask>("deleteJar") {
    delete(fileTree("build/libs") {
        include("**/*.jar")
    })
}

tasks.build {
    dependsOn("deleteJar")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        println(file.name)
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(zipTree(file.absoluteFile))
    }
}
