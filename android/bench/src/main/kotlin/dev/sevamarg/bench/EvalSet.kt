// android/bench/src/main/kotlin/dev/sevamarg/bench/EvalSet.kt
//
// The eval set is generated data (evals/build.mjs), so it is modelled here rather than hand-written:
// one definition, in the file that the Node side writes and the JVM side reads. Unknown keys are
// ignored because the generator may add report columns tomorrow and the runner must still work
// yesterday's binary.
package dev.sevamarg.bench

import dev.sevamarg.core.catalogue.CatalogueFormatException
import java.io.InputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EvalSet(
    val generator: String = "",
    @SerialName("generated_on") val generatedOn: String = "",
    /** True while the questions come from *.sample.yaml. Reported, never hidden. */
    val provisional: Boolean = false,
    /** The generator's strictness flag: sample data is allowed to be short, the real set is not. */
    val strict: Boolean = false,
    val targets: Map<String, Int> = emptyMap(),
    val counts: Map<String, Int> = emptyMap(),
    val total: Int = 0,
    @SerialName("refusal_cases") val refusalCases: Int = 0,
    @SerialName("twins_paired") val twinsPaired: Int = 0,
    /**
     * Items the router cannot reach from a cold start because no alias contains them - follow-ups like
     * "form bhar diya ab aage kya hoga". The runner uses this list to decide which items get session
     * context, so "unreachable" is a documented property of the data, not an unexplained accuracy loss.
     */
    @SerialName("unreachable_by_aliases") val unreachableByAliases: List<Unreachable> = emptyList(),
    val items: List<EvalItem> = emptyList(),
) {
    val unreachableIds: Set<String> get() = unreachableByAliases.map { it.id }.toSet()

    companion object {
        const val ASSET_PATH = "/eval-set.json"

        val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun parse(text: String): EvalSet = try {
            json.decodeFromString(EvalSet.serializer(), text)
        } catch (e: Exception) {
            throw CatalogueFormatException("eval-set.json could not be parsed: ${e.message}", e)
        }

        fun parse(stream: InputStream): EvalSet = parse(stream.use { it.readBytes().toString(Charsets.UTF_8) })

        /** From evals/dist/, published onto the classpath by bench/build.gradle.kts. */
        fun fromResource(path: String = ASSET_PATH): EvalSet {
            val stream = EvalSet::class.java.getResourceAsStream(path)
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(path.removePrefix("/"))
                ?: throw CatalogueFormatException(
                    "Missing $path on the classpath. From the repo root: npm ci && npm run evals:build",
                )
            return parse(stream).also {
                if (it.items.isEmpty()) throw CatalogueFormatException("eval-set.json has no items")
            }
        }
    }
}

@Serializable
data class Unreachable(val id: String, val ask: String = "", val service: String = "")

/**
 * A single question. [service] null means the correct behaviour is a refusal - an out-of-scope ask.
 * [intent] marks a follow-up (next_steps / escalation / status_lookup), which is only answerable when
 * the previous service is carried over, exactly like a real conversation.
 */
@Serializable
data class EvalItem(
    val id: String,
    val ask: String,
    val service: String? = null,
    /** Why this one is out of scope - printed with the failure so a reader can check the judgement. */
    val why: String? = null,
    val intent: String? = null,
    @SerialName("expect_fields") val expectFields: List<String> = emptyList(),
    @SerialName("must_not_say") val mustNotSay: List<String> = emptyList(),
    @SerialName("twin_of") val twinOf: String? = null,
    val lang: String = "hi",
    val source: String = "",
) {
    val expectsRefusal: Boolean get() = service == null
}
