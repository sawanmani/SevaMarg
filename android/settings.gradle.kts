// android/settings.gradle.kts
// One repo, four modules. The dependency rule is enforced by the module graph, not by review
// comments: :core has no Android dependency at all, so the brain stays testable on a plain JVM and
// :bench can run the accuracy numbers without an emulator.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "seva-marg"

include(":app")     // Compose UI, single Activity - no logic that cannot be rendered
include(":core")    // pure Kotlin JVM: catalogue models, router, composer, refusal. No android.*
include(":voice")   // the only module allowed to touch the microphone and the speaker
include(":bench")   // JVM eval runner: reads evals/dist/eval-set.json, writes results JSON
