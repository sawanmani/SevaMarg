// android/core/src/main/kotlin/dev/sevamarg/core/intent/FieldKeywords.kt
//
// Which part of the answer did the citizen ask for? These tables are QUESTION vocabulary, not
// answers: "kitne din" is how a person asks about the SLA; the SLA itself only exists in the cited
// catalogue. That distinction is what keeps docs/PROJECT_STRUCTURE.md's placement rule intact - no
// fee, office or day-count is written in Kotlin anywhere in this module.
package dev.sevamarg.core.intent

import dev.sevamarg.core.text.Normaliser

object FieldKeywords {

    /** The catalogue fields that a composed answer can contain. */
    val ANSWERABLE_FIELDS: List<String> = listOf(
        "eligibility", "documents", "fees", "sla", "channel", "office", "next_steps", "escalation", "faqs",
    )

    /** What an overview answer contains: the four things a citizen acts on, in decision order. */
    val OVERVIEW_FIELDS: List<String> = listOf("eligibility", "documents", "fees", "sla", "office")

    /**
     * "status_lookup" and "overview" are routing outcomes, not catalogue fields. Status questions are
     * answered from next_steps - which is exactly what the problem statement says citizens cannot find.
     */
    const val STATUS_LOOKUP = "status_lookup"
    const val OVERVIEW = "overview"

    private val RAW: Map<String, List<String>> = mapOf(
        "documents" to listOf(
            "document", "documents", "kagaz", "kaagaz", "काग़ज़", "प्रति", "कागज", "दस्तावेज", "दस्तावेज़",
            "photo copy", "self attested", "kitna copy", "प्रमाण पत्र की प्रति",
        ),
        "fees" to listOf(
            "fee", "fees", "paisa", "paise", "kharch", "kharcha", "फीस", "लागत", "रुपये", "चार्ज",
            "kitna paisa", "kitna kharcha", "charge", "charges", "free", "muft", "शुल्क", "पैसा", "कीमत",
        ),
        "sla" to listOf(
            "din", "days", "kitne din", "samay", "time", "kab milega", "kab tak", "sla", "मिल जाता",
            "der", "दिन", "समय", "कितने दिन", "कब", "मिलेगा", "देरी",
        ),
        "office" to listOf(
            "kahan", "office", "office se", "tehsil", "talati", "csc", "kendra", "center", "centre",
            "dm office", "jagah", "कहाँ", "कार्यालय", "केंद्र", "तहसील", "पता", "address", "nearest",
            "jana padega", "ana padega", "area", "near", "nearby", "apna ilaka", "ilaka", "अपना इलाका", "इलाका",
        ),
        "eligibility" to listOf(
            "kaun", "kon", "eligible", "eligibility", "adhikar", "le sakta", "अधिकार", "पात्रता",
            "de sakta", "कौन", "पात्र", "अधिकारी", "lagu", "yogya", "yogata",
        ),
        "channel" to listOf(
            "online", "apply", "application", "form", "bhar", "bharna", "jama", "process", "vidhi",
            "प्रक्रिया", "ऑनलाइन", "आवेदन", "फॉर्म", "kaise lena", "kaise banega", "kaise banta",
        ),
        "next_steps" to listOf(
            "aage", "age kya", "kya hoga", "next", "after", "status", "sthiti", "क्या होगा", "आगे", "स्थिति",
            "kahan pahuncha", "milega ya nahi", "tracking", "track", "reference number",
        ),
        "escalation" to listOf(
            "shikayat", "complaint", "complain", "grievance", "nahi mila", "nahi mil raha", "atka", "pending",
            "नहीं मिल रहा", "help", "madad", "शिकायत", "फरिय़ाद", "नहीं मिला", "रुका", "रुका हुआ",
            "kya karu", "koi upay",
        ),
        "faqs" to listOf(
            "faq", "faqs", "sawal", "savals", "prashn", "सवाल", "प्रश्न", "common question",
        ),
    )

    /** Pre-normalised so the router does the folding once at load, not per question. */
    private val TABLE: Map<String, List<String>> = RAW.mapValues { (_, keys) -> keys.map { Normaliser.phrase(it) } }

    /**
     * Long keywords match as substrings (a phrase like "kitne din" carries its own signal); short
     * ones must be a whole token, or "din" would fire inside "dinga" and every answer would claim an
     * SLA. Returns the fields the query asked for, strongest first, restricted to what the service
     * can actually answer.
     */
    fun detect(queryPhrase: String, queryTokens: Set<String>, available: Set<String>): List<String> {
        val hits = mutableListOf<Pair<String, Int>>()
        for ((field, keys) in TABLE) {
            if (field !in available) continue
            var count = 0
            for (k in keys) {
                val matched = if (k.length >= 5 || k.contains(' ')) queryPhrase.contains(k) else queryTokens.contains(k)
                if (matched) count++
            }
            if (count > 0) hits.add(field to count)
        }
        return hits.sortedByDescending { it.second }.map { it.first }
    }

    /** "status / kahan pahuncha" is a next_steps question with a different word. */
    private val STATUS_KEYS: List<String> =
        listOf("status", "track", "kahan pahuncha", "sthiti", "स्थिति", "reference number")
            .map { Normaliser.phrase(it) }

    fun isStatusLookup(queryPhrase: String): Boolean = STATUS_KEYS.any { queryPhrase.contains(it) }
}
