// android/core/src/main/kotlin/dev/sevamarg/core/catalogue/CatalogueLoader.kt
//
// The single place where the shipped JSON asset becomes a Catalogue object. Two decisions matter:
//
// 1. ignoreUnknownKeys, but a HARD schema_version check. A new field in the asset must not silently
//    change behaviour on an older app build; a version we do not understand must fail here, at
//    launch, rather than render a wrong fee.
// 2. No partial success. If the asset does not parse, the app has nothing truthful to say, so this
//    throws and the caller shows the "catalogue unreadable" state - it does not fall back to an
//    empty catalogue that would then answer "no documents required" for everything.
package dev.sevamarg.core.catalogue

import java.io.InputStream
import kotlinx.serialization.json.Json

/** Raised for an asset this build cannot honestly serve. Message always names the fix. */
class CatalogueFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object CatalogueLoader {

    /** The schema version this code understands. packages/catalogue/build.mjs writes the same number. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** Resource path inside the APK (and on the JVM test/bench classpath - same bytes). */
    const val ASSET_PATH = "catalogue.json"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = false // a JSON null where a non-null fact is required must be an error
        prettyPrint = false
    }

    fun parse(text: String): Catalogue {
        val catalogue = try {
            json.decodeFromString(Catalogue.serializer(), text)
        } catch (e: Exception) {
            // Never let a serialisation message reach the citizen; it goes to the log and the About
            // screen's diagnostics line only.
            throw CatalogueFormatException("catalogue.json could not be parsed: ${e.message}", e)
        }
        check(catalogue)
        return catalogue
    }

    fun parse(stream: InputStream): Catalogue =
        stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }

    /**
     * JVM path (:core tests, :bench). Reads the very file build.mjs wrote into
     * android/core/src/main/assets, because core/build.gradle.kts publishes that directory as a
     * resource directory - so a green test run is a statement about the shipped bytes.
     */
    fun fromResource(path: String = "/$ASSET_PATH"): Catalogue {
        val stream = CatalogueLoader::class.java.getResourceAsStream(path)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(path.removePrefix("/"))
            ?: throw CatalogueFormatException(
                "Missing $path on the classpath. From the repo root: npm ci && npm run catalogue:build",
            )
        return parse(stream)
    }

    /** Structural sanity, run on every load. Field-level truth is validate.mjs's job at build time. */
    fun check(catalogue: Catalogue): Catalogue {
        if (catalogue.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw CatalogueFormatException(
                "catalogue.json has schema_version ${catalogue.schemaVersion}, this build understands " +
                    "$SUPPORTED_SCHEMA_VERSION. Rebuild the asset: npm run catalogue:build",
            )
        }
        if (catalogue.services.isEmpty()) {
            throw CatalogueFormatException("catalogue.json contains no services - nothing can be answered")
        }
        val duplicates = catalogue.services.groupBy { it.serviceId }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw CatalogueFormatException("duplicate service_id in the asset: ${duplicates.joinToString()}")
        }
        if (!catalogue.langs.contains("hi")) {
            throw CatalogueFormatException("catalogue.json is missing Hindi - the product's default language")
        }
        return catalogue
    }
}
