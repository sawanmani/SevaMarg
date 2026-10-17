// android/voice/build.gradle.kts
// The only module allowed to touch the microphone and the speaker (docs/PROJECT_STRUCTURE.md).
// :app talks to the interfaces in src/main/kotlin, :core never sees audio bytes at all - that is what
// keeps the brain testable without a device.
//
// Deliberately no ASR/TTS dependency yet. ADR-0003 is still "proposed": Vosk-Android Hindi accuracy and
// offline Hindi TTS are Phase 1.1 spike questions, and picking an engine by importing it first is how a
// project ends up shipping the wrong one. The engine artifacts land in the spike's commit, with the
// model id, size and SHA recorded in NOTICES.md.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.sevamarg.voice"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaJvmTarget.get())) }
}

dependencies {
    // Only the language enum and the answer type: :voice converts speech to text and text to speech,
    // it does not get an opinion about what the answer should be.
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
}
