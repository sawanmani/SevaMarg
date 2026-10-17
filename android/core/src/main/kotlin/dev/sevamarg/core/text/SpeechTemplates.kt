// android/core/src/main/kotlin/dev/sevamarg/core/text/SpeechTemplates.kt
//
// Deterministic sentence assembly for TTS (and for the "copy answer" text). The reason this is not
// left to the model layer: a language model would have to be told the fee, and the only place the fee
// is allowed to come from is the cited catalogue. So the *shape* of a sentence lives here, the
// *words* live in packages/i18n, and the *values* arrive from the Answer.
//
// Nothing in this file contains a citizen-facing fact. If a key is missing we drop the fragment
// rather than print a raw key at a person on the phone.
package dev.sevamarg.core.text

import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.compose.Answer
import dev.sevamarg.core.refusal.Refusal

class SpeechTemplates(private val strings: StringSource) {

    /** One sentence per line of the answer, in reading order, ready for TalkBack and for TTS. */
    fun sentences(answer: Answer): List<String> = buildList {
        add(strings.format(KEY_INTRO, mapOf("service" to answer.serviceName)))
        for (section in answer.nonEmptySections) {
            val title = strings.format(section.titleKey)
            for (line in section.lines) {
                val core = "${title}: ${line.text}"
                val details = metaLabels(line.meta)
                add(if (details.isEmpty()) core else "$core (${details.joinToString(", ")})")
            }
        }
        // The two honesty states are their own sentences, never folded into a fact sentence: a
        // listener must be able to tell "this is the fee" apart from "this has not been verified".
        if (answer.provisional) strings.lookup(KEY_PROVISIONAL)?.let { add(it) }
        if (answer.isStale) {
            val days = answer.staleness.ageDays
            if (days != null) add(strings.format(KEY_STALE, mapOf("days" to days.toString())))
        }
        answer.staleness.oldest?.let { oldest ->
            add(strings.format(KEY_SOURCE_FOOTER, mapOf("date" to IsoDate.fromEpochDay(oldest))))
        }
    }

    fun speakable(answer: Answer): String = sentences(answer).joinToString(separator = strings.format(KEY_SEPARATOR) + " ")

    /**
     * The refusal, spoken. This is the answer a citizen gets when we do not know, and it is written
     * to be as actionable as the ones we do know: what we could not confirm, where to ask, which
     * verified number to call, and which services we *can* answer about.
     */
    fun refusalSentences(refusal: Refusal): List<String> = buildList {
        if (refusal.messageKey.isNotBlank()) strings.lookup(refusal.messageKey)?.let { add(it) }
        refusal.verifiedUpto?.let { add(strings.format(KEY_VERIFIED_UPTO, mapOf("date" to it))) }
        if (refusal.officeTypeKeys.isNotEmpty()) {
            val where = strings.format(KEY_WHERE_TO_ASK)
            val offices = refusal.officeTypeKeys.mapNotNull { strings.lookup(it) }.joinToString(", ")
            if (offices.isNotBlank()) add("$where $offices")
        }
        refusal.helpline?.let { add(strings.format(KEY_HELPLINE, mapOf("contact" to it))) }
        if (refusal.suggestions.isNotEmpty()) {
            val heading = strings.format(KEY_SUGGESTIONS)
            add("$heading ${refusal.suggestions.joinToString(", ") { it.name }}")
        }
    }

    fun speakableRefusal(refusal: Refusal): String =
        refusalSentences(refusal).joinToString(separator = strings.format(KEY_SEPARATOR) + " ")

    /**
     * `meta` entries are `key=value` pairs taken straight from the catalogue (copies=1, days=30,
     * who=applicant). They become words only through string keys; an unknown key or an unlabelled
     * enum value is dropped, so a raw code can never reach a citizen's screen or speaker.
     */
    fun metaLabels(meta: List<String>): List<String> = meta.mapNotNull { entry ->
        val split = entry.indexOf('=')
        if (split <= 0) return@mapNotNull null
        val key = entry.substring(0, split)
        val value = entry.substring(split + 1)
        val labelKey = "answer.meta.$key"

        // yes/no values pick a whole phrase instead of filling {n}. The key form is the one the string
        // files use (answer.meta.visit_yes): a dot here would look up a key that does not exist and
        // every "is an office visit needed" detail would vanish from the spoken answer in silence.
        if (key in BOOLEAN_KEYS) {
            val suffix = if (value == "yes") "yes" else "no"
            return@mapNotNull strings.lookup("answer.meta.${key}_$suffix")
        }

        // no template -> no fragment. This is what keeps "copies=1" off a citizen's screen when a
        // label was forgotten: parity.mjs should have failed first, and if it did not, we stay silent.
        if (strings.lookup(labelKey) == null) return@mapNotNull null

        val rendered = if (key in ENUM_KEYS) {
            // an enum value with no label in this language is dropped, never printed as a code
            val label = strings.lookup(ENUM_KEYS.getValue(key) + value) ?: return@mapNotNull null
            strings.format(labelKey, mapOf("n" to label))
        } else {
            strings.format(labelKey, mapOf("n" to value))
        }

        // an unresolved {placeholder} is worse than a missing detail: it is the bug the string files
        // are supposed to make impossible, so it must never reach a speaker.
        if (rendered.contains("{")) null else rendered
    }

    companion object {
        private const val KEY_INTRO = "speech.answer_intro"
        private const val KEY_SOURCE_FOOTER = "speech.source_footer"
        private const val KEY_SEPARATOR = "speech.separator"
        private const val KEY_PROVISIONAL = "answer.provisional_banner"
        private const val KEY_STALE = "answer.stale_warning"
        private const val KEY_VERIFIED_UPTO = "answer.verified_upto"
        private const val KEY_WHERE_TO_ASK = "refusal.where_to_ask"
        private const val KEY_HELPLINE = "refusal.helpline"
        private const val KEY_SUGGESTIONS = "refusal.suggested_services"

        /** Meta keys whose value selects the key suffix instead of filling {n}. */
        private val BOOLEAN_KEYS = setOf("visit", "attested")

        /** Closed vocabularies from service.schema.json - the labels live in i18n (see i18n tests). */
        private val ENUM_KEYS = mapOf(
            "who" to "answer.enum.who.",
            "mode" to "answer.enum.mode.",
            "channel" to "answer.enum.channel.",
            "status" to "answer.enum.status.",
            "office_type" to "office.type.",
        )
    }
}
