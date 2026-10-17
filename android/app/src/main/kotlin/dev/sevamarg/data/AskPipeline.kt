// android/app/src/main/kotlin/dev/sevamarg/data/AskPipeline.kt
//
// One question in, one card out. The order is not arbitrary and it is the whole product:
//   route (:core, deterministic)  ->  compose (catalogue facts only)  ->  decide (answer or refusal)
// There is no branch that asks a model, and no branch that fills a gap with a plausible sentence. If
// :core cannot answer from cited data, the citizen gets a refusal card with an office and a phone
// number - which is Phase 1's promise in one function.
package dev.sevamarg.data

import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.compose.Answer
import dev.sevamarg.core.compose.AnswerComposer
import dev.sevamarg.core.intent.IntentRouter
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.intent.Route
import dev.sevamarg.core.refusal.Refusal
import dev.sevamarg.core.refusal.RefusalRouter

sealed interface AskOutcome {

    /** The route that produced it, because the session carry-over needs the service for the next turn. */
    val route: Route

    data class Answered(val answer: Answer, override val route: Route) : AskOutcome
    data class Refused(val refusal: Refusal, override val route: Route) : AskOutcome

    /** Nothing was asked - the UI must not turn a blank into a refusal lecture. */
    data class Blank(override val route: Route) : AskOutcome
}

class AskPipeline(data: ShippedData) {

    private val catalogue = data.catalogue
    private val router = IntentRouter(catalogue)
    private val composer = AnswerComposer(catalogue)
    private val refusals = RefusalRouter(catalogue)

    /** [contextServiceId] is the last service the person was shown, for "what happens now" follow-ups. */
    fun ask(query: String, lang: Lang, contextServiceId: String? = null): AskOutcome {
        if (query.isBlank()) return AskOutcome.Blank(router.route("", lang))

        val route = router.route(query, lang, contextServiceId)
        // A route that matched nothing still gets composed into null and evaluated: the refusal router is
        // what turns "no match" into the office + helpline card, so it is never the UI's job to guess.
        val answer = route.serviceId?.let { composer.compose(route, lang) }
        val refusal = refusals.evaluate(route, answer, query, lang)
        return if (refusal.isRefusal || answer == null) {
            AskOutcome.Refused(refusal, route)
        } else {
            AskOutcome.Answered(answer, route)
        }
    }

    /**
     * The Journey / Checklist / Office tabs are the *same* cited-answer path as Ask, narrowed to the
     * fields each tab is about. Routing the tab through the composer (rather than reading the Service
     * straight into a Text) is what keeps the provisional banner, the per-line meta labels and the
     * source chip on those screens too - a projection that skipped the composer could show a sentinel
     * value as a fact, which is the one thing every other screen is built to prevent.
     */
    fun composeFields(serviceId: String, lang: Lang, fields: List<String>): Answer? {
        val service = catalogue.service(serviceId) ?: return null
        val available = fields.filter { it in service.availableFields() }
        if (available.isEmpty()) return null
        return composer.compose(Route(serviceId, 1.0, available, MatchedVia.ALIAS_PHRASE), lang)
    }
}
