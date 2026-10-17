// android/core/src/test/kotlin/dev/sevamarg/core/refusal/RefusalRouterTest.kt
//
// J4 is the demo moment, so these are behaviour tests rather than unit tests of a helper: for each way
// an answer can be unsafe, the citizen must get a specific, actionable refusal - the reason in their
// language, an office type they can walk to, and a helpline that has actually been verified.
package dev.sevamarg.core.refusal

import dev.sevamarg.core.Fixtures
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.compose.UiKeys
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.intent.Route
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefusalRouterTest {

    private val router = Fixtures.router
    private val refusals = Fixtures.refusal
    private val composer = Fixtures.composer

    /** The real pipeline for one utterance: route -> compose -> refuse-or-answer. */
    private fun ask(query: String, lang: Lang = Lang.HI, context: String? = null): Refusal {
        val route = router.route(query, lang, context)
        return refusals.evaluate(route, composer.compose(route, lang, Fixtures.today), query, lang, Fixtures.today)
    }

    private fun evaluate(route: Route, lang: Lang = Lang.HI, query: String = ""): Refusal =
        refusals.evaluate(route, composer.compose(route, lang, Fixtures.today), query, lang, Fixtures.today)

    @Test
    fun `a confident route to a topic outside the catalogue is still stopped`() {
        // The scope guard runs before the router's arithmetic, because "jaati praman" shares three
        // tokens with our income-certificate aliases. Confidence is not evidence of scope.
        val confident = Route(Fixtures.income, 1.0, listOf("documents"), MatchedVia.ALIAS_PHRASE)
        val refusal = evaluate(confident, query = "jaati praman patra ke liye kya karna hoga")
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, refusal.reason)
        assertEquals(UiKeys.refusalOutOfScope, refusal.messageKey)
        assertTrue(refusal.isRefusal)
    }

    @Test
    fun `another state, another year, same refusal`() {
        // We verified UP pages for 2026. Nothing else is knowable from this asset.
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, ask("bihar me income certificate ka rule kya hai").reason)
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, ask("death certificate ka process bata do").reason)
    }

    @Test
    fun `the app will not act as a middleman or a guarantor`() {
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, ask("fee online bhar do mere liye").reason)
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, ask("agent 500 rupaye maang raha hai sahi hai").reason)
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, ask("meri income 1.9 lakh hai kya mujhe milega").reason)
    }

    @Test
    fun `a personal record question is refused because we hold no records`() {
        // This is the one an assistant with a model would answer plausibly. We keep no PII at all, so
        // "where is my application" can only be answered by the office - saying so is the honest card.
        val refusal = ask("mera application kahan atka hai")
        assertEquals(RefusalReason.OUT_OF_SCOPE_TOPIC, refusal.reason)
        assertNotNull(refusal.helpline, "a refusal has to leave somewhere to go")
    }

    @Test
    fun `below the service's own floor the app admits it is not sure`() {
        val route = router.route("income certifi kete han du ma", Lang.HI)
        val refusal = evaluate(route, query = "income certifi kete han du ma")
        assertEquals(RefusalReason.BELOW_CONFIDENCE, refusal.reason)
        assertEquals(UiKeys.refusalNotSure, refusal.messageKey)
        assertTrue(route.confidence < 0.85, "the floor is 0.85, got ${route.confidence}")
    }

    @Test
    fun `noise that matches nothing is refused rather than answered with the top service`() {
        val refusal = ask("qqq www eee", Lang.EN)
        assertEquals(RefusalReason.NO_SERVICE_MATCH, refusal.reason)
        assertEquals(3, refusal.suggestions.size, "the suggestions are the services we actually hold")
    }

    @Test
    fun `a fragment from a noisy street is labelled as garbled, not as a wrong answer`() {
        assertEquals(RefusalReason.GARBLED_INPUT, ask("hmm").reason)
        assertEquals(RefusalReason.GARBLED_INPUT, ask("ha").reason)
    }

    @Test
    fun `ambiguity asks instead of guessing and offers both services`() {
        val route = Route(Fixtures.income, 0.95, listOf("documents"), MatchedVia.ALIAS_TOKENS)
            .copy(ambiguousWith = listOf(Fixtures.residence))
        val refusal = evaluate(route, query = "praman patra ke liye kagaz")
        assertEquals(RefusalReason.AMBIGUOUS, refusal.reason)
        assertEquals(
            listOf(Fixtures.income, Fixtures.residence),
            refusal.suggestions.map { it.serviceId },
            "a clarification must list the two things the citizen could have meant",
        )
    }

    @Test
    fun `a district we have not verified is refused even at full confidence`() {
        val query = "income certificate ki fee gautam buddha nagar me kitni hai"
        val route = router.route(query, Lang.HI)
        assertEquals(1.0, route.confidence, 1e-9)
        val refusal = evaluate(route, query = query)
        assertEquals(RefusalReason.DISTRICT_NOT_COVERED, refusal.reason)
        assertEquals(UiKeys.refusalDistrictNotCovered, refusal.messageKey)
        assertEquals(listOf("office.type.tehsil", "office.type.csc"), refusal.officeTypeKeys)
    }

    @Test
    fun `unverified data is called unverified, not stale`() {
        val route = Route(Fixtures.sampleUnverified, 1.0, listOf("eligibility"), MatchedVia.ALIAS_PHRASE)
        val refusal = evaluate(route, query = "sample service")
        assertEquals(RefusalReason.UNVERIFIED_DATA, refusal.reason)
        assertEquals(UiKeys.refusalNotVerified, refusal.messageKey)
        assertNull(refusal.verifiedUpto, "there is no date this was ever true for")
    }

    @Test
    fun `stale data names the last date it was true`() {
        val route = Route(Fixtures.residence, 1.0, listOf("fees"), MatchedVia.ALIAS_PHRASE)
        val refusal = evaluate(route, query = "residence certificate fee")
        assertEquals(RefusalReason.STALE_DATA, refusal.reason)
        assertEquals("2025-01-05", refusal.verifiedUpto)
        // its own key, not the banner template: a refusal has no {days} to substitute
        assertEquals(UiKeys.refusalStale, refusal.messageKey)
        assertFalse(Fixtures.hi.lookup(refusal.messageKey)!!.contains("{"))
    }

    @Test
    fun `a session carry over at 0 6 is not refused for being below the floor`() {
        val route = Route(Fixtures.income, 0.60, listOf("next_steps"), MatchedVia.SESSION_CONTEXT)
        assertEquals(RefusalReason.NONE, evaluate(route, query = "aage kya hoga").reason)
    }

    @Test
    fun `every refusal leaves somewhere to ask, in both languages`() {
        // J4's promise in one loop: a refusal is never a dead end. And no refusal may print a key -
        // a raw "office.type.dm_office" on a screen is exactly the bug this test exists to catch.
        val queries = listOf(
            "jaati praman patra",
            "qqq www eee",
            "hmm",
            "income certifi kete han du ma",
            "income certificate ki fee gautam buddha nagar me kitni hai",
            "mera application kahan atka hai",
        )
        for (query in queries) {
            val refusal = ask(query)
            assertTrue(refusal.isRefusal, "expected a refusal for: $query")
            assertTrue(refusal.officeTypeKeys.isNotEmpty(), "no office offered for: $query")
            assertNotNull(refusal.helpline, "no helpline offered for: $query")
            for (key in listOf(refusal.messageKey) + refusal.officeTypeKeys) {
                assertNotNull(Fixtures.hi.lookup(key), "missing Hindi label for $key ($query)")
                assertNotNull(Fixtures.en.lookup(key), "missing English label for $key ($query)")
            }
            for (spoken in Fixtures.speechHi.refusalSentences(refusal) + Fixtures.speechEn.refusalSentences(refusal)) {
                assertFalse(spoken.contains("{") || spoken.contains("office.type.") || spoken.contains("refusal."), spoken)
            }
        }
    }

    @Test
    fun `office keys come from the catalogue and resolve to a label in both languages`() {
        // build.mjs copies the schema enum verbatim, so the key form has to be derived, not typed -
        // and every derived key must have a word attached to it before a citizen can be told to go
        // somewhere. A raw "office.type.dm_office" on a screen is the bug this catches.
        val route = Route(Fixtures.residence, 1.0, listOf("office"), MatchedVia.ALIAS_PHRASE)
        val refusal = evaluate(route, query = "residence certificate kahan jaana padega")
        assertEquals(RefusalReason.STALE_DATA, refusal.reason)
        assertEquals(listOf("office.type.tehsil"), refusal.officeTypeKeys)
        assertNotNull(Fixtures.hi.lookup("office.type.tehsil"))
        assertNotNull(Fixtures.en.lookup("office.type.tehsil"))
        assertTrue(Fixtures.speechHi.refusalSentences(refusal).any { it.isNotBlank() })
    }
}
