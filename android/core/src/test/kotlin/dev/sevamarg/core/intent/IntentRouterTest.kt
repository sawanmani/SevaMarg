// android/core/src/test/kotlin/dev/sevamarg/core/intent/IntentRouterTest.kt
//
// These tests are the audit trail for ADR-0001. Every expected number can be re-computed by hand from
// the scoring rule in IntentRouter: a verbatim alias is 0.90 + min(aliasTokens/(queryTokens+1), 0.1)
// (+0.03 when the alias is in the language being asked), anything else is 0.85 x overlap. That is the
// point - a reviewer holding a printout of the catalogue can check what the router would do with any
// sentence a citizen says.
//
// Hindi cases are built from the loaded fixture's own names, never re-typed in this file, so a case
// that fails means the matching rule broke and not that someone's keyboard composed a vowel sign
// differently.
package dev.sevamarg.core.intent

import dev.sevamarg.core.Fixtures
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.catalogue.Service
import dev.sevamarg.core.text.Normaliser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntentRouterTest {

    private val router = Fixtures.router
    private val income: Service = checkNotNull(Fixtures.catalogue.service(Fixtures.income))
    private val residence: Service = checkNotNull(Fixtures.catalogue.service(Fixtures.residence))

    @Test
    fun `a verbatim alias hit is a confident answer with the field the citizen asked for`() {
        val route = router.route("${income.lang.en.name} fees kitni hai", Lang.EN)
        assertEquals(Fixtures.income, route.serviceId)
        assertEquals(1.0, route.confidence, 1e-9) // 0.90 + the capped length margin + the language bonus
        assertEquals(MatchedVia.ALIAS_PHRASE, route.via)
        assertEquals(listOf("fees"), route.fields, route.fields.toString())
        assertTrue(route.ambiguousWith.isEmpty())
        assertTrue(route.answered)
    }

    @Test
    fun `a question in devanagari that matches a catalogue name exactly is a verbatim hit`() {
        val name = income.lang.hi.name // the fixture's own spelling, whatever script it lands in
        val route = router.route(name, Lang.HI)
        assertEquals(Fixtures.income, route.serviceId)
        assertEquals(MatchedVia.ALIAS_PHRASE, route.via)
        assertTrue(route.confidence >= 0.90, "a name typed whole must not score below the verbatim floor")
        assertTrue(route.ambiguousWith.isEmpty(), route.ambiguousWith.toString())
    }

    @Test
    fun `two services at the same distance are reported as ambiguity instead of a guess`() {
        // The two Hindi names share their last words, so a question that uses only the shared part is
        // equidistant. Overlap is a fraction of the smaller set, so both score 0.85 * 2/3 + 0.03.
        val shared = residence.lang.hi.name.split(" ").filter { income.lang.hi.name.contains(it) }
        assertTrue(shared.isNotEmpty(), "the fixture names must share a word for this case to exist")
        val route = router.route("${shared.joinToString(" ")} ke liye kaagaz kya chahiye", Lang.HI)
        assertEquals(0.597, route.confidence, 0.001)
        assertEquals(MatchedVia.ALIAS_TOKENS, route.via)
        assertEquals(listOf(Fixtures.residence), route.ambiguousWith, route.ambiguousWith.toString())
        assertEquals(listOf("documents"), route.fields, route.fields.toString())
    }

    @Test
    fun `hinglish reaches the same service as devanagari`() {
        // "aay praman patra" is one of the fixture's search terms; the fold in Normaliser is what makes
        // it also cover "आय प्रमाण पत्र" typed in the other script and "pramaan" typed with long vowels.
        val roman = income.searchTerms.first { it.contains("praman") || it.contains("pramaan") }
        val latin = router.route("$roman kitne din me milta hai", Lang.HI)
        assertEquals(Fixtures.income, latin.serviceId)
        assertEquals(1.0, latin.confidence, 1e-9)
        assertEquals(listOf("sla"), latin.fields, latin.fields.toString())
        assertEquals(Normaliser.tokens("pramaan patra"), Normaliser.tokens("praman patra"))
    }

    @Test
    fun `asking in english nudges the english aliases and asking in hindi the devanagari ones`() {
        // The bonus is 0.03: enough to break a tie between two equally plausible readings, never
        // enough to override evidence from the words themselves.
        val en = router.route("income certificate", Lang.EN)
        val hi = router.route("income certificate", Lang.HI)
        assertEquals(en.serviceId, hi.serviceId)
        assertTrue(en.confidence >= hi.confidence)
        assertEquals(1.0, en.confidence, 1e-9)
    }

    @Test
    fun `a follow-up with no service named is answered from the session, not refused`() {
        // J1's most common real utterance: the card is already on screen and the person asks "what
        // now". Confidence is fixed at 0.60 - a carry-over is never as strong as a named service - and
        // the route is marked SESSION_CONTEXT so :bench can report carry-over separately.
        val route = router.route("form bhar diya ab aage kya hoga", Lang.HI, contextServiceId = Fixtures.income)
        assertEquals(Fixtures.income, route.serviceId)
        assertEquals(0.60, route.confidence, 1e-9)
        assertEquals(MatchedVia.SESSION_CONTEXT, route.via)
        assertEquals(listOf("next_steps"), route.fields, "the form is context, the question is about what happens next")
    }

    @Test
    fun `a follow-up question that is not about a follow-up field stays a refusal`() {
        // Carrying the previous service over a question that asks for something else would fabricate
        // context the citizen never gave.
        val route = router.route("haan ji kuch nahi samajh aaya", Lang.HI, contextServiceId = Fixtures.income)
        assertNull(route.serviceId, "no follow-up field was asked for: ${route.fields}")
        assertEquals(MatchedVia.NONE, route.via)
    }

    @Test
    fun `a broken transcription scores evidence but stays below the service floor`() {
        val route = router.route("income certifi kete han du ma", Lang.HI)
        // one shared token out of a two-token alias is real evidence, but 0.85 * 1/2 is nowhere near
        // enough to send someone to a tehsil
        assertEquals(Fixtures.income, route.serviceId)
        assertTrue(
            // Double has no `until`; spelled out because "above the no-match floor, below the service
            // floor" is the whole meaning of a BELOW_CONFIDENCE refusal.
            route.confidence >= 0.34 && route.confidence < income.confidenceFloor,
            "score was ${route.confidence}",
        )
        assertEquals(MatchedVia.ALIAS_TOKENS, route.via)
    }

    @Test
    fun `noise that matches nothing routes nowhere`() {
        val route = router.route("qqq www eee", Lang.EN)
        assertNull(route.serviceId)
        assertEquals(MatchedVia.NONE, route.via)
        assertTrue(route.confidence < 0.34, "below the no-match floor: ${route.confidence}")
    }

    @Test
    fun `the router never promises a section the service does not have`() {
        // The sample service holds only eligibility. Asking it about fees must not produce a fees
        // field - that gap is what the refusal router is for.
        val route = router.route("sample service fees kya hai", Lang.EN)
        assertEquals(Fixtures.sampleUnverified, route.serviceId)
        assertEquals(listOf("eligibility"), route.fields, route.fields.toString())
    }

    @Test
    fun `status wording is flagged so the answer can lead with what happens next`() {
        val route = router.route("income certificate ka status kaise dekhein", Lang.HI)
        assertEquals(Fixtures.income, route.serviceId)
        assertTrue(route.statusLookup)
        assertEquals("next_steps", route.fields.first(), route.fields.toString())
    }

    @Test
    fun `a question that names a service but no field gets the overview, capped at reality`() {
        val route = router.route("mujhe income certificate chahiye", Lang.HI)
        assertEquals(Fixtures.income, route.serviceId)
        assertEquals(listOf("eligibility", "documents", "fees", "sla", "office"), route.fields, route.fields.toString())
        for (field in route.fields) {
            assertTrue(field in income.availableFields(), "$field is not in the catalogue")
        }
    }

    @Test
    fun `an empty question cannot be routed`() {
        for (blank in listOf("", "   ", "???")) {
            val route = router.route(blank, Lang.HI)
            assertNull(route.serviceId, "blank input \"$blank\" was routed anyway")
            assertEquals(0.0, route.confidence, 1e-9)
            assertFalse(route.answered)
        }
    }

    @Test
    fun `every service in the catalogue is reachable by at least one of its own names`() {
        // The failure this guards is invisible in a demo until it isn't: a service whose alias list is
        // written in a shape the normaliser cannot produce can never be asked for by voice.
        for (service in Fixtures.catalogue.services) {
            val candidates = (service.names + service.lang.hi.aliases + service.lang.en.aliases + service.searchTerms)
                .map { Normaliser.phrase(it) }
                .filter { it.isNotBlank() }
                .distinct()
            val hit = candidates.any { router.route(it, Lang.HI).serviceId == service.serviceId }
            assertTrue(hit, "unreachable service ${service.serviceId}: aliases ${service.aliases}")
        }
    }
}
