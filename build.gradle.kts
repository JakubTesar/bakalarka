plugins {
    kotlin("jvm") version "2.2.20"
    application
}

group = "cz.tesar.bp"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("cz.tesar.bp.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val (k, v) = line.split("=", limit = 2)
                environment(k.trim(), v.trim())
            }
    }
}
