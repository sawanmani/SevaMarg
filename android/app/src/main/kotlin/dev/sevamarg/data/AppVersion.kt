// android/app/src/main/kotlin/dev/sevamarg/data/AppVersion.kt
//
// The version line in the diagnostics block. Read from the installed package instead of the generated
// BuildConfig, for two reasons that are both load-bearing:
//
// 1. It is the truth at runtime. BuildConfig.VERSION_NAME is a copy of versionName baked in at compile
//    time; the package manager is where that copy came from. One source, no second literal that can
//    disagree with the build file.
// 2. It keeps `buildFeatures.buildConfig` off, and that switch is what decides whether this module
//    needs javac at all. Turning it on generates a BuildConfig.java, and the JDK on the machine we
//    build on (26) cannot produce the Android system-modules image AGP asks jlink for - see the note
//    in gradle/libs.versions.toml. A version label is not worth a build that cannot run.
package dev.sevamarg.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppVersion {

    /** versionName from the manifest/build file, or "?" - a diagnostics label must never crash a screen. */
    fun name(context: Context): String = try {
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        info.versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        // Our own package cannot be missing while it is running. Kept because the API declares it, and
        // answered with "?" rather than a rethrow: nothing on this screen is worth a crash.
        "?"
    }
}
