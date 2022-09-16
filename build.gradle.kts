import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "fr.corpauration"
version = "1.0-SNAPSHOT"

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
    implementation("org.slf4j:slf4j-simple:2.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_16.toString()
}

