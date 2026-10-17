// android/core/src/main/kotlin/dev/sevamarg/core/text/Strings.kt
//
// Wording enters the app through this interface and nowhere else. :core holds the rules for turning
// facts into sentences; packages/i18n holds the sentences themselves. The split is enforced by
// construction: AnswerComposer emits i18n KEYS, and only a StringSource can turn a key into words.
// That is why a missing translation is a parity.mjs error at build time instead of an English label
// sitting inside a Hindi answer at demo time.
package dev.sevamarg.core.text

import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.catalogue.CatalogueFormatException
import java.io.InputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface StringSource {
    /** Null when the key does not exist - callers that must not show a raw key use this. */
    fun lookup(key: String): String?

    /** Substitute {placeholders} from [args]; keys without args are returned as-is. */
    fun format(key: String, args: Map<String, String> = emptyMap()): String
}

/** Exactly what packages/i18n/parity.mjs writes into android/core/src/main/assets/i18n/<lang>.json. */
@Serializable
data class StringAsset(
    val locale: String = "",
    val generator: String = "",
    @SerialName("generated_on") val generatedOn: String = "",
    @SerialName("key_count") val keyCount: Int = 0,
    val strings: Map<String, String> = emptyMap(),
)

/**
 * A loaded locale. [missing] is not cosmetic: if a key is absent at runtime the asset and the code
 * have drifted apart, and the About screen shows the count so a judge-visible failure is a loud one
 * rather than a silent fallback to "answer.meta.copies" on a citizen's screen.
 */
class StringTable(
    val locale: Lang,
    val generatedOn: String,
    val asset: StringAsset,
    private val fallback: StringSource? = null,
) : StringSource {

    val missing: MutableSet<String> = linkedSetOf()

    override fun lookup(key: String): String? = asset.strings[key] ?: fallback?.lookup(key)

    override fun format(key: String, args: Map<String, String>): String {
        val template = asset.strings[key] ?: fallback?.lookup(key) ?: run {
            missing.add(key)
            return key
        }
        if (args.isEmpty()) return template
        return PLACEHOLDER.replace(template) { match ->
            args[match.groupValues[1]] ?: match.value // an unknown placeholder stays visible
        }
    }

    val keyCount: Int get() = asset.strings.size

    companion object {
        private val PLACEHOLDER = Regex("\\{([A-Za-z_][\\w]*)}")
    }
}

object StringBundles {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(text: String, locale: Lang, fallback: StringSource? = null): StringTable {
        val asset = try {
            json.decodeFromString(StringAsset.serializer(), text)
        } catch (e: Exception) {
            throw CatalogueFormatException("i18n/${locale.code}.json could not be parsed: ${e.message}", e)
        }
        if (asset.strings.isEmpty()) {
            throw CatalogueFormatException("i18n/${locale.code}.json has no strings - every screen would be blank")
        }
        return StringTable(locale = locale, generatedOn = asset.generatedOn, asset = asset, fallback = fallback)
    }

    fun parse(stream: InputStream, locale: Lang, fallback: StringSource? = null): StringTable =
        parse(stream.use { it.readBytes().toString(Charsets.UTF_8) }, locale, fallback)

    /** The shipped bytes: :core publishes src/main/assets as a resource dir, so this is the APK's file. */
    fun fromResource(lang: Lang, fallbackLang: Lang? = null): StringTable {
        val path = "/i18n/${lang.code}.json"
        val stream = resource(path) ?: throw CatalogueFormatException(
            "Missing $path on the classpath. From the repo root: npm ci && npm run i18n:parity",
        )
        val fallback = fallbackLang?.let { resource("/i18n/${it.code}.json")?.let { s -> parse(s, fallbackLang) } }
        return parse(stream, lang, fallback)
    }

    private fun resource(path: String): InputStream? =
        StringBundles::class.java.getResourceAsStream(path)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(path.removePrefix("/"))
}
