plugins {
    kotlin("jvm") version "2.2.20"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("org.example.MainKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.1"
val coroutinesVersion = "1.10.1"

dependencies {
    // HTTP client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}
