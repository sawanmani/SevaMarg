// android/core/src/main/kotlin/dev/sevamarg/core/refusal/RefusalRouter.kt
//
// The refusal path is the feature, not the error page (J4). Three separate things can stop an answer
// and they must not be conflated, because the citizen's next action differs: we never heard a service
// (ask and suggest), we heard one but confidence is below the floor (say so, offer the office), the
// data is stale or unverified (say which date it was true till), or the topic is out of scope by
// design (caste certificate, another state, "fill the form for me", "is the agent right").
//
// The topic list below is vocabulary for what we refuse. It contains no facts about any service, so
// the placement rule still holds: nothing a citizen would act on is written in Kotlin.
package dev.sevamarg.core.refusal

import dev.sevamarg.core.catalogue.Catalogue
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.catalogue.Service
import dev.sevamarg.core.compose.Answer
import dev.sevamarg.core.compose.UiKeys
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.intent.Route
import dev.sevamarg.core.text.Normaliser

enum class RefusalReason {
    NONE,
    NO_SERVICE_MATCH,
    BELOW_CONFIDENCE,
    AMBIGUOUS,
    STALE_DATA,
    UNVERIFIED_DATA,
    OUT_OF_SCOPE_TOPIC,
    DISTRICT_NOT_COVERED,
    GARBLED_INPUT,
}

data class Suggestion(val serviceId: String, val name: String)

data class Refusal(
    val reason: RefusalReason,
    val messageKey: String,
    val officeTypeKeys: List<String>,
    val helpline: String?,
    val suggestions: List<Suggestion>,
    /** For STALE_DATA / UNVERIFIED_DATA: the date the answer was last true, when we have one. */
    val verifiedUpto: String?,
) {
    val isRefusal: Boolean get() = reason != RefusalReason.NONE
}

class RefusalRouter(private val catalogue: Catalogue) {

    /**
     * Checked BEFORE routing. "jaati praman patra" shares three tokens with our income-certificate
     * aliases; without a scope guard the router would confidently answer a question about a document
     * we have never verified - the exact failure this project promises not to have.
     */
    private val blockedTopics: List<Pair<RefusalReason, List<String>>> = listOf(
        // services outside the verified v1 catalogue
        RefusalReason.OUT_OF_SCOPE_TOPIC to listOf(
            "jaati praman", "jati praman", "caste certificate", "death certificate", "marriage certificate",
            "vakalatnama", "ration card", "pension", "ayushman", "license", "driving licence", "polla",
            "मृत्यु प्रमाण", "जाति प्रमाण", "विवाह प्रमाण", "राशन कार्ड", "पेंशन",
        ),
        // another state's rules: we verified UP pages, nothing else
        RefusalReason.OUT_OF_SCOPE_TOPIC to listOf(
            "bihar", "madhya pradesh", "maharashtra", "delhi", "punjab", "rajasthan", "jharkhand",
            "uttarakhand", "chhattisgarh", "bihar me", "mp me",
        ),
        // things an app must not do for a citizen
        RefusalReason.OUT_OF_SCOPE_TOPIC to listOf(
            "bhar do mere liye", "fill the form", "apply for me", "payment kar do", "paisa jama",
            "agent", "dalal", "bda kar do", "guarantee", "pakka bata do", "kya mujhe milega",
        ),
        // we hold no personal records, so we cannot know where "my" application is
        RefusalReason.OUT_OF_SCOPE_TOPIC to listOf(
            "mera application", "meri application", "mere naam ka", "my application status",
            "mera number", "meri file",
        ),
    )

    private val normalisedBlocklist: List<Pair<RefusalReason, List<String>>> =
        blockedTopics.map { (reason, keys) -> reason to keys.map { Normaliser.phrase(it) } }

    fun scopeViolation(query: String): RefusalReason? {
        val phrase = Normaliser.phrase(query)
        for ((reason, keys) in normalisedBlocklist) {
            if (keys.any { phrase.contains(it) }) return reason
        }
        return null
    }

    /**
     * ASR on a noisy street returns fragments. A fragment must land here, never on an invented answer,
     * so the check is deliberately about shape: almost no content words at all.
     */
    private fun looksGarbled(query: String): Boolean {
        val tokens = Normaliser.tokens(query)
        return tokens.size <= 1 && query.length < 12
    }

    /** One entry point: the UI asks this after composing, and shows either the answer or this. */
    fun evaluate(
        route: Route,
        answer: Answer?,
        query: String,
        lang: Lang,
        todayEpochDay: Long = IsoDate.todayEpochDay(),
    ): Refusal {
        val violation = scopeViolation(query)
        if (violation != null) return build(RefusalReason.OUT_OF_SCOPE_TOPIC, lang, null, route)
        if (route.serviceId == null && looksGarbled(query)) return build(RefusalReason.GARBLED_INPUT, lang, null, route)
        if (route.serviceId == null) return build(RefusalReason.NO_SERVICE_MATCH, lang, null, route)

        val service = catalogue.service(route.serviceId) ?: return build(RefusalReason.NO_SERVICE_MATCH, lang, null, route)

        if (route.via != MatchedVia.SESSION_CONTEXT && route.confidence < service.confidenceFloor) {
            return build(RefusalReason.BELOW_CONFIDENCE, lang, service, route)
        }
        if (route.ambiguousWith.isNotEmpty()) {
            return build(RefusalReason.AMBIGUOUS, lang, service, route)
        }
        // A district-scoped question against a state-scope catalogue is a question about data we have
        // not read. Saying the state fee here is the exact kind of confident wrong answer the whole
        // project promises to avoid, so the mention has to stop the answer even at confidence 1.0.
        val district = Districts.mentioned(query)
        if (district != null && !Districts.isCovered(district, coveredDistricts())) {
            return build(RefusalReason.DISTRICT_NOT_COVERED, lang, service, route)
        }
        if (answer == null || answer.isEmpty) return build(RefusalReason.NO_SERVICE_MATCH, lang, service, route)

        // The two data-honesty states. Stale and unverified are reported separately because one is
        // "confirm with the office", the other is "we have not read this yet".
        if (answer.provisional) return build(RefusalReason.UNVERIFIED_DATA, lang, service, route)
        if (answer.isStale) {
            return build(RefusalReason.STALE_DATA, lang, service, route, answer.staleness.oldest?.let { IsoDate.fromEpochDay(it) })
        }
        if (!answer.allCitationsPresent) return build(RefusalReason.UNVERIFIED_DATA, lang, service, route)

        return Refusal(RefusalReason.NONE, "", emptyList(), null, emptyList(), null)
    }

    private fun build(
        reason: RefusalReason,
        lang: Lang,
        service: Service?,
        route: Route,
        verifiedUpto: String? = null,
    ): Refusal = Refusal(
        reason = reason,
        messageKey = messageKeyFor(reason),
        officeTypeKeys = (service?.office?.type ?: defaultOfficeTypes()).map { officeTypeKey(it) },
        helpline = service?.escalation?.firstOrNull { it.hasVerifiedContact }?.contact
            ?: catalogue.verifiedContacts().firstOrNull(),
        suggestions = suggestions(lang, route),
        verifiedUpto = verifiedUpto,
    )

    private fun messageKeyFor(reason: RefusalReason): String = when (reason) {
        RefusalReason.BELOW_CONFIDENCE, RefusalReason.NO_SERVICE_MATCH,
        RefusalReason.AMBIGUOUS, RefusalReason.GARBLED_INPUT -> UiKeys.refusalNotSure
        RefusalReason.UNVERIFIED_DATA -> UiKeys.refusalNotVerified
        RefusalReason.DISTRICT_NOT_COVERED -> UiKeys.refusalDistrictNotCovered
        // Its own key, deliberately: answer.stale_warning is a template that needs a {days} argument,
        // and a refusal card does not carry one. Reusing it would read "{days} दिन पुरानी" to a person.
        RefusalReason.STALE_DATA -> UiKeys.refusalStale
        RefusalReason.OUT_OF_SCOPE_TOPIC -> UiKeys.refusalOutOfScope
        RefusalReason.NONE -> ""
    }

    private fun defaultOfficeTypes(): List<String> = listOf("tehsil", "csc")

    /**
     * Districts whose fees/offices we hold in the asset. Today every service is state-scope, so this
     * is empty; as soon as packages/catalogue/overrides ships, district_scope values flow through here
     * and Districts becomes a lookup instead of a refusal.
     */
    private fun coveredDistricts(): List<String> =
        catalogue.services.map { it.districtScope }.filterNot { it.equals("state", ignoreCase = true) }.distinct()

    /**
     * Catalogue office codes are schema enums (tehsil, csc, dm_office, village_pradh); the key form
     * is the same with any separator folded to "_", so a value typed "dm office" in YAML still finds
     * its label instead of showing a raw key.
     */
    private fun officeTypeKey(code: String): String =
        "office.type." + code.lowercase().replace(NON_KEY_CHAR, "_").trim('_')

    /**
     * "Did you mean one of these?" is how a refusal becomes useful. Limited to the services we
     * actually hold, ordered by name length match - never a guess about a service we have not read.
     */
    private fun suggestions(lang: Lang, route: Route): List<Suggestion> {
        val named = listOfNotNull(route.serviceId) + route.ambiguousWith
        val pool = if (named.isNotEmpty()) {
            named.mapNotNull { catalogue.service(it) }
        } else {
            catalogue.services
        }
        return pool.take(4).map { Suggestion(it.serviceId, it.lang.pick(lang)) }
    }

    companion object {
        private val NON_KEY_CHAR = Regex("[^a-z0-9]+")
    }
}
