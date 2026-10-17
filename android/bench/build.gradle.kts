// android/bench/build.gradle.kts
// The measurement module: a plain JVM runner, no Android, no emulator. It exists because every number
// in the pitch (intent accuracy, refusal coverage) has to be reproducible by someone else on a laptop
// in two minutes. `./gradlew :bench:run` prints it, `./gradlew :bench:test` fails the build on it.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jvm.application)
}

// Same rule as :core - pin the bytecode target, do not demand an installed JDK (libs.versions.toml).
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaJvmTarget.get())) }
}
java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
}

// evals/build.mjs writes eval-set.json into evals/dist/. Putting that directory on the resource path
// means the runner reads the very file the Node side generated - no copy that can drift, and no
// second definition of the eval set in Kotlin.
sourceSets {
    main {
        resources.srcDir("../../evals/dist")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("dev.sevamarg.bench.RunEvalsKt")
}

tasks.named<JavaExec>("run") {
    // `./gradlew :bench:run --args="--out ../../results/bench/latest.json"`, quiet otherwise
    standardInput = System.`in`
}

// scripts/bench-all.sh and CI use this one. Same main(), same report - the only difference is that the
// program's exit code does not become a build failure, because Gradle can express "the run went wrong" but
// not "1 = a promise broke, 2 = the catalogue is not verified yet". A caller that has to tell those apart
// reads the JSON instead, which is written either way. The relative path is from this project's directory:
// android/bench/../../results is the repo's results/, the folder the deck quotes numbers from.
tasks.register<JavaExec>("evalReport") {
    group = "verification"
    description = "Run the eval set, write results/bench/latest.json, leave the exit code to the reader"
    mainClass.set(application.mainClass)
    classpath = sourceSets["main"].runtimeClasspath
    args("--out", "../../results/bench/latest.json")
    setIgnoreExitValue(true)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
