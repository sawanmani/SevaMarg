// android/app/build.gradle.kts
// The shell: one Activity, Compose Material 3, no logic that a :core test could not run better.
// Everything a citizen hears as a fact comes out of the catalogue asset through :core; this module only
// decides where it goes on the screen.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.sevamarg"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.sevamarg"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"        // same 0.x as package.json until 1.0 is the submitted build
    }

    // Debug only, and only ever debug-signed: judges sideload the APK from a QR code (memory
    // "Android Runtime and Offline Distribution Requirements"). No store signing config exists yet,
    // and when it does the keystore stays out of git (.gitignore already excludes *.jks).
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaJvmTarget.get())
    }

    buildFeatures {
        // The switch that turns the compose compiler plugin on for this module.
        compose = true
        // buildConfig stays off (it has been off by default since AGP 8). Turning it on generates one
        // Java file per variant, and that single file is enough to put this module on the javac path -
        // which needs an Android system-modules image built by jlink, and jlink from JDK 26 rejects the
        // java.base inside it ("cannot find the build signature"). Nothing needs BuildConfig: the version
        // line reads from the package manager instead (data/AppVersion.kt), which is the same number the
        // installer has, so no claim on screen depends on a compile-time copy.
    }

    // This one line is the reason the phone shows the same data the tests assert on. :core is a plain
    // JVM module, so its src/main/assets reaches the device only as a *resource inside a jar* - and
    // Android does not read classpath resources at runtime. Publishing the same directory as an APK
    // assets dir here means catalogue.json and i18n/*.json are the exact bytes npm run catalogue:build
    // wrote, with no copy step that could go stale.
    sourceSets {
        getByName("main") {
            assets.srcDir("../core/src/main/assets")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaJvmTarget.get())) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":voice"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Phase 2.9/2.5: the chosen language and the recent-service list persist through DataStore. It is
    // the only place app state outlives the process, and it holds no PII - a locale code and service ids.
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // No test dependencies yet, on purpose. Compose UI tests need the Android artifact variants
    // (androidx.test / espresso / ui-test-junit4), and the first thing worth testing on-device is the
    // accessibility pass in Phase 2.6. Adding the coordinates now would be a build-time cost with no
    // test behind it.
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
