// android/core/src/main/kotlin/dev/sevamarg/core/compose/Answer.kt
//
// The only object the UI is allowed to render. Note what is NOT here: there is no place for a
// sentence assembled from a model, and no section without a citation. Section titles are i18n KEYS
// ("answer.section.fees"), never text baked into Kotlin, so a missing translation shows up as a
// missing key in parity.mjs rather than as English leaking into a Hindi answer.
//
// These types are @Serializable so :bench can write a golden Answer JSON per eval item and a reviewer
// can diff the shipped answers by eye.
package dev.sevamarg.core.compose

import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.catalogue.Staleness
import dev.sevamarg.core.intent.MatchedVia
import kotlinx.serialization.Serializable

/** Naming rule, enforced by the composer: every section title key is "answer.section.<field>". */
object UiKeys {
    fun section(field: String): String = "answer.section.$field"
    const val refusalNotSure = "refusal.not_sure"
    const val refusalNotVerified = "refusal.not_verified"
    const val refusalOutOfScope = "refusal.out_of_scope"
    const val refusalStale = "refusal.stale"
    const val refusalDistrictNotCovered = "refusal.district_not_covered"
    const val refusalWhereToAsk = "refusal.where_to_ask"
    const val refusalHelpline = "refusal.helpline"
    const val refusalSuggestions = "refusal.suggested_services"
    const val staleWarning = "answer.stale_warning"
    const val verifiedUpto = "answer.verified_upto"
    const val provisionalBanner = "answer.provisional_banner"
}

@Serializable
data class Citation(val sourceUrl: String, val verifiedOn: String) {
    /** A citation is only usable if a human can open it and it carries a real date. */
    val present: Boolean get() = sourceUrl.startsWith("http") && verifiedOn.length == 10 && !verifiedOn.startsWith("0000")
}

/**
 * [text] is always catalogue content. [meta] carries raw catalogue values (a number of copies, a fee,
 * an enum like "self-attested") that the UI and the TTS templates label from i18n - the module that
 * knows the facts does not decide how to say "days" in two languages.
 */
@Serializable
data class AnswerLine(val text: String, val meta: List<String> = emptyList(), val citation: Citation)

@Serializable
data class AnswerSection(val field: String, val titleKey: String, val lines: List<AnswerLine>) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

@Serializable
data class Answer(
    val serviceId: String,
    val serviceName: String,
    val lang: Lang,
    val sections: List<AnswerSection>,
    val via: MatchedVia,
    val confidence: Double,
    val provisional: Boolean,
    val staleness: Staleness,
) {
    val nonEmptySections: List<AnswerSection> get() = sections.filterNot { it.isEmpty }

    /** Every distinct official page this answer rests on, in first-mentioned order. */
    val citations: List<Citation> get() = sections.flatMap { it.lines }.map { it.citation }.distinctBy { it.sourceUrl }

    /** False means the composer produced an unanswerable card - the UI must not show it. */
    val allCitationsPresent: Boolean get() = citations.isNotEmpty() && citations.all { it.present }

    val fieldNames: List<String> get() = nonEmptySections.map { it.field }

    val isStale: Boolean get() = staleness.isStale

    val isEmpty: Boolean get() = nonEmptySections.isEmpty()
}
