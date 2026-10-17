// android/core/src/main/kotlin/dev/sevamarg/core/intent/IntentRouter.kt
//
// The whole product rests on this file being boring. It maps one utterance to (service, fields) with
// arithmetic a reviewer can re-do by hand - no model, no weights, no "it usually works". Where the
// arithmetic is not confident it says so, and RefusalRouter turns that into an honest answer. That
// trade (fluency for auditability) is ADR-0001; the accuracy target it has to hit is >= 85 % intent
// accuracy on 20 real utterances, measured on the budget phone, not claimed.
package dev.sevamarg.core.intent

import dev.sevamarg.core.catalogue.Catalogue
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.catalogue.Service
import dev.sevamarg.core.text.Normaliser

/** How the router got here. Reported in the answer footer and in :bench output, never hidden. */
enum class MatchedVia {
    /** An alias or name appeared verbatim in the question - the strongest evidence we have. */
    ALIAS_PHRASE,

    /** Word-set overlap with an alias: "income ki praman patra fees" style. */
    ALIAS_TOKENS,

    /** No service named, but a follow-up field question in a session that already showed one. */
    SESSION_CONTEXT,

    /** Nothing. Goes to the refusal path. */
    NONE,
}

data class Route(
    val serviceId: String?,
    val confidence: Double,
    val fields: List<String>,
    val via: MatchedVia,
    val ambiguousWith: List<String> = emptyList(),
    val queryPhrase: String = "",
    val statusLookup: Boolean = false,
) {
    val answered: Boolean get() = serviceId != null && fields.isNotEmpty()
}

class IntentRouter(catalogue: Catalogue) {

    private class Phrase(val text: String, val tokens: Set<String>, val lang: Lang)

    private class ServiceIndex(val service: Service, val phrases: List<Phrase>)

    private fun phrasesFor(name: String, aliases: List<String>, lang: Lang): List<Phrase> =
        (listOf(name) + aliases)
            .map { Normaliser.phrase(it) to Normaliser.tokens(it).toSet() }
            .filter { it.first.isNotEmpty() }
            .distinctBy { it.first }
            .map { Phrase(it.first, it.second, lang) }

    private val indexes: List<ServiceIndex> = catalogue.services.map { service ->
        ServiceIndex(
            service,
            phrasesFor(service.lang.hi.name, service.lang.hi.aliases, Lang.HI) +
                phrasesFor(service.lang.en.name, service.lang.en.aliases, Lang.EN) +
                service.searchTerms
                    .map { Normaliser.phrase(it) to Normaliser.tokens(it).toSet() }
                    .filter { it.first.isNotEmpty() }
                    .distinctBy { it.first }
                    .map { Phrase(it.first, it.second, Lang.EN) },
        )
    }

    /** Below this we do not name a service at all: a wrong certificate costs a trip to a tehsil. */
    private val noMatchFloor = 0.34

    fun route(query: String, lang: Lang, contextServiceId: String? = null): Route {
        val qPhrase = Normaliser.phrase(query)
        val qTokens = Normaliser.tokens(query).toSet()
        if (qTokens.isEmpty()) return Route(null, 0.0, emptyList(), MatchedVia.NONE, emptyList(), qPhrase)

        val scored = indexes.map { idx ->
            var best = 0.0
            var verbatim = false
            for (p in idx.phrases) {
                // Asking in English nudges the English alias list; asking in Hindi nudges Devanagari.
                // Small on purpose: it breaks ties, it never overrides evidence.
                val langBonus = if (p.lang == lang) 0.03 else 0.0
                val s = if (qPhrase.contains(p.text)) {
                    // A full alias inside the question is near-proof; length adds a small margin so a
                    // two-word hit beats a one-word hit rather than tying with it.
                    verbatimCandidate(p, qTokens) + langBonus
                } else {
                    0.85 * Normaliser.overlap(p.tokens, qTokens) + langBonus
                }
                if (s > best) {
                    best = s
                    verbatim = qPhrase.contains(p.text)
                }
            }
            idx.service to RouteScore(best.coerceAtMost(1.0), verbatim)
        }.sortedByDescending { it.second.score }

        val top = scored.firstOrNull()
        val topScore = top?.second?.score ?: 0.0

        if (top == null || topScore < noMatchFloor) {
            return followUpOrRefusal(qPhrase, qTokens, contextServiceId, topScore)
        }

        // Two services the same distance apart is a real state of the world: ask, do not guess. A
        // verbatim alias hit is strong enough that a distant second place is not ambiguity.
        val rivals = scored.drop(1).filter { topScore - it.second.score < 0.05 && it.second.score >= noMatchFloor }
        val ambiguous = if (rivals.isEmpty() || topScore >= 0.80) emptyList() else rivals.map { it.first.serviceId }

        val service = top.first
        val via = if (top.second.verbatim) MatchedVia.ALIAS_PHRASE else MatchedVia.ALIAS_TOKENS
        return Route(
            serviceId = service.serviceId,
            confidence = topScore,
            fields = fieldsFor(service, qPhrase, qTokens),
            via = via,
            ambiguousWith = ambiguous,
            queryPhrase = qPhrase,
            statusLookup = FieldKeywords.isStatusLookup(qPhrase),
        )
    }

    private fun verbatimCandidate(p: Phrase, qTokens: Set<String>): Double =
        0.90 + (p.tokens.size.toDouble() / (qTokens.size + 1)).coerceAtMost(0.1)

    /**
     * "form bhar diya ab aage kya hoga" names no service. In a live session that is a follow-up about
     * whatever is on screen, so we answer from that service's cited next_steps - and mark the route
     * as SESSION_CONTEXT so the benchmark can report carry-over accuracy separately.
     *
     * We answer the follow-up half of such a question and drop the rest: the citizen mentioned the
     * form, but they are asking what happens next. Refusing because one of the detected fields is not
     * a follow-up field would fail the exact utterances the brief complains about most.
     */
    private fun followUpOrRefusal(
        qPhrase: String,
        qTokens: Set<String>,
        contextServiceId: String?,
        topScore: Double,
    ): Route {
        if (contextServiceId != null) {
            val contextService = indexes.firstOrNull { it.service.serviceId == contextServiceId }?.service
            if (contextService != null) {
                // detectedFields, not fieldsFor: the overview fallback means "nothing specific was
                // asked", and overview fields include sla and office - so routing the fallback through
                // the follow-up filter would let any noise carry the previous card over.
                val fields = detectedFields(contextService, qPhrase, qTokens).filter { it in FOLLOW_UP_FIELDS }
                if (fields.isNotEmpty()) {
                    return Route(
                        serviceId = contextService.serviceId,
                        confidence = 0.60,
                        fields = fields,
                        via = MatchedVia.SESSION_CONTEXT,
                        queryPhrase = qPhrase,
                        statusLookup = FieldKeywords.isStatusLookup(qPhrase),
                    )
                }
            }
        }
        return Route(null, topScore, emptyList(), MatchedVia.NONE, emptyList(), qPhrase)
    }

    private fun fieldsFor(service: Service, qPhrase: String, qTokens: Set<String>): List<String> {
        val detected = detectedFields(service, qPhrase, qTokens)
        if (detected.isNotEmpty()) return detected
        // Nothing specific asked for: the general "what do I need to know" answer, capped at reality.
        val available = service.availableFields()
        val overview = FieldKeywords.OVERVIEW_FIELDS.filter { it in available }
        return if (overview.isNotEmpty()) overview else available.toList().sorted()
    }

    /**
     * Only the fields the citizen actually asked for, with no fallback. Empty means the question named
     * no part of the answer - which is a different fact from "it asked for something unanswerable".
     */
    private fun detectedFields(service: Service, qPhrase: String, qTokens: Set<String>): List<String> {
        val available = service.availableFields()
        val raw = FieldKeywords.detect(qPhrase, qTokens, available)
        // A question that asks for a real section is not also an FAQ question; keeping both would
        // read the same fact twice out loud.
        val detected = if (raw.size > 1) raw.filterNot { it == "faqs" } else raw
        return if (FieldKeywords.isStatusLookup(qPhrase) && "next_steps" in available) {
            (listOf("next_steps") + detected).distinct()
        } else {
            detected
        }
    }

    private data class RouteScore(val score: Double, val verbatim: Boolean)

    companion object {
        /** Fields that make sense as a follow-up without re-naming the service. */
        val FOLLOW_UP_FIELDS: Set<String> = setOf("next_steps", "escalation", "sla", "office")
    }
}
