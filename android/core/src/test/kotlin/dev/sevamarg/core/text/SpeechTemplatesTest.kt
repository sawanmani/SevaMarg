// android/core/src/test/kotlin/dev/sevamarg/core/text/SpeechTemplatesTest.kt
//
// Voice is the product. What these tests protect is the difference between a spoken answer and a
// spoken data structure: a citizen must hear "कितनी प्रति: 1", never "copies=1", and never a key, never
// an unresolved {placeholder}. Anything the string files cannot label is dropped instead of spoken.
package dev.sevamarg.core.text

import dev.sevamarg.core.Fixtures
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.compose.AnswerComposer
import dev.sevamarg.core.compose.UiKeys
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.intent.Route
import dev.sevamarg.core.refusal.RefusalReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeechTemplatesTest {

    /** A minimal StringSource with exactly the keys each case needs - the drop rules are local to it. */
    private class Stub(private val table: Map<String, String>) : StringSource {
        override fun lookup(key: String): String? = table[key]
        override fun format(key: String, args: Map<String, String>): String =
            args.entries.fold(table[key] ?: key) { acc, (name, value) -> acc.replace("{$name}", value) }
    }

    private val incomeAnswer = Fixtures.composer.composeOverview(Fixtures.income, Lang.HI, Fixtures.today)!!

    @Test
    fun `one sentence per answer line, in reading order, wrapped in an intro and a source line`() {
        val sentences = Fixtures.speechHi.sentences(incomeAnswer)
        val lineCount = incomeAnswer.nonEmptySections.sumOf { it.lines.size }
        assertEquals(lineCount + 2, sentences.size, "intro + one per line + the source footer")
        assertTrue(sentences.first().contains(incomeAnswer.serviceName), sentences.first())
        assertTrue(sentences.last().contains("2026-09-10"), "the footer names the date: ${sentences.last()}")
    }

    @Test
    fun `nothing that is spoken can be a raw code, a key or an open placeholder`() {
        val spoken = Fixtures.speechHi.speakable(incomeAnswer)
        for (forbidden in listOf("=", "{", "}", "answer.", "office.type.", "VERIFY_OFFICIAL", "fixture.invalid")) {
            assertFalse(spoken.contains(forbidden), "leaked \"$forbidden\" into: $spoken")
        }
    }

    @Test
    fun `catalogue values become labelled words`() {
        val copies = Fixtures.speechHi.metaLabels(listOf("copies=1"))
        assertEquals(listOf(checkNotNull(Fixtures.hi.lookup("answer.meta.copies")).replace("{n}", "1")), copies)

        val fee = Fixtures.speechHi.metaLabels(listOf("amount_inr=25", "mode=either"))
        assertEquals(2, fee.size, fee.toString())
        assertTrue(fee[0].contains("25"), fee[0])
        // an enum value is labelled through its own key, so Hindi never hears the word "either"
        assertTrue(fee[1].contains(checkNotNull(Fixtures.hi.lookup("answer.enum.mode.either"))), fee[1])
        assertFalse(fee[1].contains("either"), fee[1])
    }

    @Test
    fun `yes and no values pick a whole phrase`() {
        // The key form in the string files is answer.meta.visit_yes, not answer.meta.visit.yes. Get it
        // wrong and the office-visit warning - the single most useful detail in a journey - disappears
        // from the spoken answer without any error anywhere.
        val yes = Fixtures.speechHi.metaLabels(listOf("visit=yes"))
        assertEquals(listOf(checkNotNull(Fixtures.hi.lookup("answer.meta.visit_yes"))), yes)
        assertEquals(1, Fixtures.speechHi.metaLabels(listOf("attested=no")).size)
    }

    @Test
    fun `an unknown key, an unlabelled enum value and an unresolved placeholder are all dropped`() {
        val speech = SpeechTemplates(
            Stub(
                mapOf(
                    "answer.meta.copies" to "Copies: {n}",
                    "answer.meta.who" to "Filed by: {n}",
                    "answer.meta.total" to "Total {rupees} please",
                ),
            ),
        )
        assertEquals(listOf("Copies: 1"), speech.metaLabels(listOf("copies=1")))
        assertTrue(speech.metaLabels(listOf("unknown_key=1")).isEmpty(), "no template -> no fragment")
        assertTrue(speech.metaLabels(listOf("who=neighbour")).isEmpty(), "an unlabelled enum value is never read out")
        assertTrue(speech.metaLabels(listOf("total=5")).isEmpty(), "an unresolved placeholder must not ship")
        assertTrue(speech.metaLabels(listOf("notakeyvalue")).isEmpty(), "a meta entry without = is not a fact")
    }

    @Test
    fun `staleness is its own sentence, not folded into a fact`() {
        val stale = Fixtures.composer.composeOverview(Fixtures.residence, Lang.HI, Fixtures.today)!!
        val sentences = Fixtures.speechHi.sentences(stale)
        assertTrue(sentences.any { it.contains("649") }, sentences.toString())
        assertTrue(sentences.last().contains("2025-01-05"), sentences.last())
    }

    @Test
    fun `an unverified answer says so out loud`() {
        val route = Route(Fixtures.sampleUnverified, 1.0, listOf("eligibility"), MatchedVia.ALIAS_PHRASE)
        val answer = Fixtures.composer.compose(route, Lang.HI, Fixtures.today)!!
        assertTrue(answer.provisional)
        assertTrue(
            Fixtures.speechHi.sentences(answer).contains(checkNotNull(Fixtures.hi.lookup(UiKeys.provisionalBanner))),
            "the banner is a sentence of its own",
        )
    }

    @Test
    fun `a refusal is spoken with the office and the verified number`() {
        val query = "income certificate ki fee gautam buddha nagar me kitni hai"
        val route = Fixtures.router.route(query, Lang.HI)
        val refusal = Fixtures.refusal.evaluate(
            route, Fixtures.composer.compose(route, Lang.HI, Fixtures.today), query, Lang.HI, Fixtures.today,
        )
        assertEquals(RefusalReason.DISTRICT_NOT_COVERED, refusal.reason)
        val spoken = Fixtures.speechHi.speakableRefusal(refusal)
        assertTrue(spoken.contains("1800-000-0000"), spoken)
        assertTrue(spoken.contains(checkNotNull(Fixtures.hi.lookup("office.type.tehsil"))), spoken)
        assertFalse(spoken.contains("office.type."), spoken)
        assertFalse(spoken.contains("{"), spoken)
    }

    @Test
    fun `the same fee is spoken in both languages and the english sentence has no devanagari`() {
        // Phase 2.9: "no screen may exist in only one language" and "every number must be identical".
        val en = Fixtures.composer.composeOverview(Fixtures.income, Lang.EN, Fixtures.today)!!
        val spokenHi = Fixtures.speechHi.speakable(incomeAnswer)
        val spokenEn = Fixtures.speechEn.speakable(en)
        assertTrue(spokenHi.contains("25"), spokenHi)
        assertTrue(spokenEn.contains("25"), spokenEn)
        assertFalse(spokenEn.any { it in '\u0900'..'\u097F' }, "Hindi leaked into the English answer")
        assertTrue(spokenHi.any { it in '\u0900'..'\u097F' }, "a Hindi answer with no Devanagari is not Hindi")
    }

    @Test
    fun `no detail the catalogue carries is dropped from the spoken answer`() {
        // metaLabels drop what they cannot label, on purpose - and that is exactly the danger: a lost
        // label makes one fact vanish from a citizen's answer with no error anywhere in any system.
        // So the rule is asserted as a count, over every field of every service in both catalogues:
        // the fixture for shape coverage, the shipped asset for what is actually going on the phone.
        val hiFresh = StringBundles.fromResource(Lang.HI, Lang.EN)
        val enFresh = StringBundles.fromResource(Lang.EN)
        val tables = mapOf(Lang.HI to hiFresh, Lang.EN to enFresh)
        for (catalogue in listOf(Fixtures.catalogue, Fixtures.shipped)) {
            val composer = AnswerComposer(catalogue)
            for (service in catalogue.services) {
                val route = Route(service.serviceId, 1.0, service.availableFields().sorted(), MatchedVia.ALIAS_PHRASE)
                for ((lang, table) in tables) {
                    val answer = composer.compose(route, lang, Fixtures.today) ?: continue
                    val speech = SpeechTemplates(table)
                    for (section in answer.nonEmptySections) {
                        assertNotEquals(section.titleKey, table.format(section.titleKey), "no title wording for ${section.titleKey}")
                        for (line in section.lines) {
                            assertEquals(
                                line.meta.size, speech.metaLabels(line.meta).size,
                                "a detail vanished in ${service.serviceId}/${section.field}: ${line.meta}",
                            )
                        }
                    }
                }
            }
        }
        assertTrue(hiFresh.missing.isEmpty(), "unresolved lookups: ${hiFresh.missing}")
        assertTrue(enFresh.missing.isEmpty(), "unresolved lookups: ${enFresh.missing}")
    }

    @Test
    fun `every string key the templates ask for exists in both bundles`() {
        // The templates name keys by hand. This is the test that turns "a fragment silently vanished"
        // into a red build, in the module that knows the shapes, before any UI exists to notice.
        val keys = listOf(
            "speech.answer_intro", "speech.source_footer", "speech.separator",
            UiKeys.provisionalBanner, UiKeys.staleWarning, UiKeys.verifiedUpto,
            UiKeys.refusalWhereToAsk, UiKeys.refusalHelpline, UiKeys.refusalSuggestions,
            "answer.meta.copies", "answer.meta.format", "answer.meta.who", "answer.meta.amount_inr",
            "answer.meta.mode", "answer.meta.free_below_income_inr", "answer.meta.days",
            "answer.meta.eta_days", "answer.meta.channel", "answer.meta.step", "answer.meta.office_type",
            "answer.meta.contact", "answer.meta.status",
            "answer.meta.visit_yes", "answer.meta.visit_no", "answer.meta.attested_yes", "answer.meta.attested_no",
            "answer.enum.who.applicant", "answer.enum.mode.either", "answer.enum.channel.online",
            "answer.enum.status.applied", "office.type.tehsil", "office.type.csc",
        )
        // A fresh pair of bundles: StringTable records the keys it could not resolve, and that record
        // must belong to this test alone - otherwise a shared table would make the assertion pass or
        // fail depending on which test ran first.
        val hiFresh = StringBundles.fromResource(Lang.HI, Lang.EN)
        val enFresh = StringBundles.fromResource(Lang.EN)
        for (key in keys) {
            assertNotEquals(key, hiFresh.format(key), "no Hindi wording for $key")
            assertNotEquals(key, enFresh.format(key), "no English wording for $key")
        }
        assertTrue(hiFresh.missing.isEmpty(), "unresolved lookups: ${hiFresh.missing}")
        assertTrue(enFresh.missing.isEmpty(), "unresolved lookups: ${enFresh.missing}")
    }
}
