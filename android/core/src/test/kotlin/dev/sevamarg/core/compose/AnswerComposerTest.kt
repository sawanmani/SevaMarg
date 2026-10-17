// android/core/src/test/kotlin/dev/sevamarg/core/compose/AnswerComposerTest.kt
//
// The composer is where a wrong answer would be born, so the tests here are all about two
// invariants: a line exists only because a cited catalogue row exists, and switching language
// changes the words and never the numbers.
//
// Note how content assertions work here: expected text is taken from the loaded fixture object, never
// re-typed as a literal in this file. A hand-copied Devanagari string in a test is a Unicode
// composition lottery - and a red test that fails for encoding reasons teaches the team to ignore it.
package dev.sevamarg.core.compose

import dev.sevamarg.core.Fixtures
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.intent.Route
import dev.sevamarg.core.text.Normaliser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnswerComposerTest {

    private val composer = Fixtures.composer
    private val income = checkNotNull(Fixtures.catalogue.service(Fixtures.income))
    private val sample = checkNotNull(Fixtures.catalogue.service(Fixtures.sampleUnverified))

    private fun route(fields: List<String>, serviceId: String = Fixtures.income) =
        Route(serviceId, 1.0, fields, MatchedVia.ALIAS_PHRASE)

    @Test
    fun `every line carries its own citation and the answer is quotable`() {
        val answer = checkNotNull(composer.composeOverview(Fixtures.income, Lang.HI, Fixtures.today))
        assertTrue(answer.nonEmptySections.isNotEmpty())
        for (section in answer.nonEmptySections) {
            assertTrue(section.lines.isNotEmpty(), "${section.field} produced an empty section")
            for (line in section.lines) {
                assertTrue(line.citation.sourceUrl.startsWith("https://"), line.citation.sourceUrl)
                assertTrue(line.citation.present, "citation not usable: ${line.citation}")
            }
        }
        assertTrue(answer.allCitationsPresent)
        assertEquals(36, answer.staleness.ageDays)
        assertFalse(answer.isStale)
        assertFalse(answer.provisional)
    }

    @Test
    fun `section titles are keys, never sentences written in kotlin`() {
        // This is the rule that keeps a Hindi answer from containing an English label: the composer
        // may only name a key, and the key must exist in both string bundles.
        val answer = checkNotNull(composer.composeOverview(Fixtures.income, Lang.HI, Fixtures.today))
        assertTrue(answer.nonEmptySections.isNotEmpty())
        for (section in answer.nonEmptySections) {
            assertEquals("answer.section.${section.field}", section.titleKey)
            assertNotNull(Fixtures.hi.lookup(section.titleKey), "no Hindi label for ${section.titleKey}")
            assertNotNull(Fixtures.en.lookup(section.titleKey), "no English label for ${section.titleKey}")
        }
    }

    @Test
    fun `catalogue values travel as key value meta and are never spelled out here`() {
        val answer = checkNotNull(composer.compose(route(listOf("documents", "fees")), Lang.HI, Fixtures.today))
        val documents = answer.sections.first { it.field == "documents" }
        assertEquals(listOf("copies=1", "format=self-attested copy", "who=applicant", "attested=yes"), documents.lines[0].meta)
        assertEquals(listOf("copies=2", "who=either", "attested=no"), documents.lines[1].meta)

        val fees = answer.sections.first { it.field == "fees" }
        assertEquals(listOf("amount_inr=25", "mode=either"), fees.lines[0].meta)
        assertEquals(listOf("amount_inr=10", "mode=cash", "free_below_income_inr=50000"), fees.lines[1].meta)

        // the words are the catalogue's own Hindi twin, in the language the citizen asked in
        assertEquals(income.documents[0].item.hi, documents.lines[0].text)
        assertEquals(income.fees[1].item.hi, fees.lines[1].text)
        val english = checkNotNull(composer.compose(route(listOf("documents")), Lang.EN, Fixtures.today))
        assertEquals(income.documents[0].item.en, english.sections.first().lines[0].text)
    }

    @Test
    fun `a field the catalogue does not hold produces no section at all`() {
        // The composer's job is to refuse by producing nothing. If it ever invents a placeholder row,
        // this test is the one that catches it.
        val answer = composer.compose(
            route(listOf("fees", "documents"), Fixtures.sampleUnverified),
            Lang.HI,
            Fixtures.today,
        )
        assertNull(answer, "the sample service holds neither fees nor documents")
    }

    @Test
    fun `an unknown service id composes nothing`() {
        assertNull(composer.compose(route(listOf("fees"), "no-such-service"), Lang.HI, Fixtures.today))
        assertNull(composer.composeOverview("no-such-service", Lang.HI, Fixtures.today))
        assertNull(composer.compose(Route(null, 1.0, listOf("fees"), MatchedVia.NONE), Lang.HI, Fixtures.today))
    }

    @Test
    fun `sentinel content makes the whole answer provisional`() {
        val answer = checkNotNull(
            composer.compose(route(listOf("eligibility"), Fixtures.sampleUnverified), Lang.HI, Fixtures.today),
        )
        assertTrue(answer.provisional, "VERIFY_OFFICIAL text must flag the card, not be read out as fact")
        assertFalse(answer.allCitationsPresent, "0000-00-00 on https://VERIFY_OFFICIAL is not a citation")
        assertEquals(sample.eligibility[0].sourceUrl, answer.citations.single().sourceUrl)
    }

    @Test
    fun `a stale service is labelled with the oldest date it was true for`() {
        val answer = checkNotNull(composer.composeOverview(Fixtures.residence, Lang.HI, Fixtures.today))
        assertTrue(answer.isStale)
        assertEquals(649, answer.staleness.ageDays)
        assertEquals("2025-01-05", IsoDate.fromEpochDay(answer.staleness.oldest ?: -1))
    }

    @Test
    fun `channel steps keep their visit flag - the step reduction denominator`() {
        val answer = checkNotNull(composer.compose(route(listOf("channel")), Lang.EN, Fixtures.today))
        val steps = answer.sections.first { it.field == "channel" }.lines
        assertEquals(6, steps.size, "3 online + 3 CSC steps")
        assertEquals(1, steps.count { it.meta.contains("visit=yes") }, "one step in this flow needs an office visit")
        assertEquals(5, steps.count { it.meta.contains("visit=no") })
        assertTrue(steps.all { it.meta.any { m -> m.startsWith("channel=") } })
        assertEquals(listOf("channel=online", "step=1", "visit=no"), steps.first().meta)
        // the baseline the claim is measured against has to agree with the steps we just rendered
        assertEquals(income.channelsBaseline.map { it.visits }, listOf(0, 1))
    }

    @Test
    fun `escalation only offers a contact that is verified`() {
        val answer = checkNotNull(composer.compose(route(listOf("escalation")), Lang.HI, Fixtures.today))
        val line = answer.sections.first { it.field == "escalation" }.lines.first()
        assertTrue(line.meta.contains("contact=1800-000-0000"), line.meta.toString())
        // an unverified number is dropped from meta - it never reaches a screen or a dialer
        assertTrue(line.meta.none { it.contains("VERIFY_OFFICIAL") })
        assertEquals(income.escalation[0].hasVerifiedContact, true)
        assertEquals(sample.eligibility[0].sourceUrl.contains("VERIFY_OFFICIAL"), true)
    }

    @Test
    fun `switching language changes words, not facts`() {
        // Phase 2.9's rule in one test: the fee a Hindi speaker hears is the fee an English speaker
        // hears. Numbers and citations come from the same rows, so they must compare equal.
        val hi = checkNotNull(composer.composeOverview(Fixtures.income, Lang.HI, Fixtures.today))
        val en = checkNotNull(composer.composeOverview(Fixtures.income, Lang.EN, Fixtures.today))
        assertEquals(hi.fieldNames, en.fieldNames)
        assertEquals(hi.citations, en.citations)
        assertEquals(
            hi.sections.flatMap { it.lines }.map { it.meta },
            en.sections.flatMap { it.lines }.map { it.meta },
        )
        // ...while the wording itself differs, and no Hindi leaks into the English card
        assertNotEquals(hi.serviceName, en.serviceName)
        assertEquals(income.lang.en.name, en.serviceName)
        assertFalse(Normaliser.hasDevanagari(en.serviceName), en.serviceName)
        assertTrue(Normaliser.hasDevanagari(hi.serviceName), hi.serviceName)
    }

    @Test
    fun `faqs are a section like any other and cite their page`() {
        val answer = checkNotNull(composer.compose(route(listOf("faqs")), Lang.EN, Fixtures.today))
        val faqs = answer.sections.first { it.field == "faqs" }
        assertEquals(1, faqs.lines.size)
        assertTrue(faqs.lines.first().text.contains(" - "), "question and answer are read as one line")
        assertEquals("https://fixture.invalid/edistrict/income#faq", faqs.lines.first().citation.sourceUrl)
    }

    @Test
    fun `next steps carry the status code so the label can be chosen by language`() {
        val answer = checkNotNull(composer.compose(route(listOf("next_steps")), Lang.HI, Fixtures.today))
        val lines = answer.sections.first { it.field == "next_steps" }.lines
        assertEquals(3, lines.size)
        assertEquals(listOf("status=applied"), lines[0].meta, "no eta in the catalogue -> no eta in the meta")
        assertEquals(listOf("status=verified", "eta_days=15"), lines[1].meta)
        assertEquals(listOf("status=delivered", "eta_days=30"), lines[2].meta)
    }
}
