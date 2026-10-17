// android/app/src/main/kotlin/dev/sevamarg/data/ShippedData.kt
//
// The seam between "bytes inside the APK" and "objects :core can reason about". It is four lines of
// real work, and it is the only place in :app that knows an AssetManager exists - everything downstream
// is plain Kotlin that a JVM test can drive.
//
// Why this is not CatalogueLoader.fromResource(): :core is a pure JVM module, so its generated assets
// arrive on the classpath as jar entries, and Android does not read jar entries at runtime. app's
// build file republishes the same directory as an APK assets dir, and this file opens it from there.
// Same path, same bytes, one loader contract.
package dev.sevamarg.data

import android.content.res.AssetManager
import dev.sevamarg.core.catalogue.Catalogue
import dev.sevamarg.core.catalogue.CatalogueFormatException
import dev.sevamarg.core.catalogue.CatalogueLoader
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.StringBundles
import dev.sevamarg.core.text.StringTable
import java.io.IOException

class ShippedData(
    val catalogue: Catalogue,
    val tables: Map<Lang, StringTable>,
) {

    fun strings(lang: Lang): StringTable =
        tables[lang] ?: throw CatalogueFormatException("no string table loaded for ${lang.code}")

    /** Keys the code asked for that the asset did not have. Non-zero means the asset and the build drifted. */
    val missingKeyCount: Int get() = tables.values.sumOf { it.missing.size }

    companion object {
        fun i18nPath(lang: Lang) = "i18n/${lang.code}.json"

        /**
         * Throws CatalogueFormatException with a message that names the command to run. There is no
         * empty-catalogue fallback: an app that answers "no documents required" to everything because a
         * file was missing is worse than an app that says it cannot help.
         */
        fun load(assets: AssetManager): ShippedData {
            val catalogue = CatalogueLoader.parse(read(assets, CatalogueLoader.ASSET_PATH))
            val tables = Lang.entries.associateWith { lang ->
                StringBundles.parse(read(assets, i18nPath(lang)), lang)
            }
            return ShippedData(catalogue = catalogue, tables = tables)
        }

        private fun read(assets: AssetManager, path: String): String = try {
            assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            throw CatalogueFormatException(
                "The APK is missing the asset $path. From the repo root: " +
                    "npm ci && npm run catalogue:build && npm run i18n:parity, then rebuild.",
                e,
            )
        }
    }
}
