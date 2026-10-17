// android/core/build.gradle.kts
// The testable brain: a plain Kotlin/JVM module. It has NO Android dependency, which is what makes
// `./gradlew :core:test` finish in seconds on a laptop and run in CI without an emulator.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Bytecode target, not a toolchain request: see the note in libs.versions.toml. The module produces
// the same class files whether the machine holds JDK 17 or JDK 26.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaJvmTarget.get())) }
}
java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
}

// src/main/assets is where packages/catalogue/build.mjs writes catalogue.json and parity.mjs writes
// i18n/*.json. Exposing that directory as JVM resources means :core tests and the :bench runner read
// exactly the bytes that go into the APK - no fixture copy that can drift from the shipped data.
sourceSets {
    main {
        resources.srcDir("src/main/assets")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    // Gradle 9 stopped providing the launcher itself; without this the test task cannot start.
    testRuntimeOnly(libs.junit.platform.launcher)
    // assertions come from org.junit.jupiter.api.Assertions - no kotlin-test flavour to keep aligned
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        // quiet on green, explicit on red: a failing routing test must be readable in a CI log
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
