// android/core/src/test/kotlin/dev/sevamarg/core/catalogue/CatalogueLoaderTest.kt
//
// The loader is the boundary between "a file the generator wrote" and "facts we read out to a
// person". Everything asserted here is a way that boundary could leak: a schema we do not
// understand, an empty asset that would answer "no documents needed" for everything, a duplicate id
// that would make two services fight over one answer, a date that silently means something else.
package dev.sevamarg.core.catalogue

import dev.sevamarg.core.Fixtures
import dev.sevamarg.core.intent.FieldKeywords
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogueLoaderTest {

    @Test
    fun `the shipped asset parses and speaks both languages`() {
        val shipped = Fixtures.shipped
        assertEquals(CatalogueLoader.SUPPORTED_SCHEMA_VERSION, shipped.schemaVersion)
        assertTrue(shipped.serviceCount >= 1, "the asset must hold at least one service")
        assertTrue(shipped.langs.containsAll(listOf("hi", "en")), "langs: ${shipped.langs}")
        for (s in shipped.services) {
            assertTrue(s.lang.hi.name.isNotBlank(), "${s.serviceId} has no Hindi name")
            assertTrue(s.lang.en.name.isNotBlank(), "${s.serviceId} has no English name")
            // the alias list is the router's whole vocabulary; an empty one means the service is
            // unreachable by voice and only a test would have noticed
            assertTrue(s.aliases.isNotEmpty(), "${s.serviceId} has no aliases or search terms")
        }
    }

    @Test
    fun `a schema version this build does not understand is refused, not half-read`() {
        val text = """{"schema_version":99,"langs":["hi","en"],"services":[]}"""
        val e = assertThrows(CatalogueFormatException::class.java) { CatalogueLoader.parse(text) }
        assertTrue(e.message!!.contains("schema_version"), "message should name the fix: ${e.message}")
    }

    @Test
    fun `an asset with no services cannot be loaded at all`() {
        // The failure this guards: an empty catalogue parses happily and then answers every question
        // with "no documents required". There is no honest version of that answer.
        val text = """{"schema_version":1,"langs":["hi","en"],"services":[]}"""
        val e = assertThrows(CatalogueFormatException::class.java) { CatalogueLoader.parse(text) }
        assertTrue(e.message!!.contains("no services"), e.message)
    }

    @Test
    fun `duplicate service ids are refused`() {
        val one = Fixtures.catalogue.services.first()
        val text = CatalogueLoader.json.encodeToString(
            Catalogue.serializer(),
            Fixtures.catalogue.copy(services = listOf(one, one)),
        )
        val e = assertThrows(CatalogueFormatException::class.java) { CatalogueLoader.parse(text) }
        assertTrue(e.message!!.contains("duplicate service_id"), e.message)
    }

    @Test
    fun `a catalogue without hindi is refused`() {
        val text = CatalogueLoader.json.encodeToString(Catalogue.serializer(), Fixtures.catalogue.copy(langs = listOf("en")))
        val e = assertThrows(CatalogueFormatException::class.java) { CatalogueLoader.parse(text) }
        assertTrue(e.message!!.contains("Hindi"), e.message)
    }

    @Test
    fun `iso dates round trip, including january`() {
        // Hinnant's algorithm counts from March, so a January or February date has to be shifted back
        // a year first. Get that wrong and every Jan/Feb citation is exactly 365 days younger than it
        // is - which reads as "fresh" on stage.
        assertEquals(0L, IsoDate.epochDayOrNull("1970-01-01"))
        assertEquals(19723L, IsoDate.epochDayOrNull("2024-01-01"))
        assertEquals(19782L, IsoDate.epochDayOrNull("2024-02-29"))
        for (iso in listOf("1970-01-01", "2000-02-29", "2024-12-31", "2025-01-05", "2026-10-16")) {
            val day = checkNotNull(IsoDate.epochDayOrNull(iso)) { "fixture date stopped parsing: $iso" }
            assertEquals(iso, IsoDate.fromEpochDay(day), "round trip broke on $iso")
        }
    }

    @Test
    fun `unparseable and sentinel dates are null rather than a wrong number`() {
        assertNull(IsoDate.epochDayOrNull("0000-00-00")) // the "not verified yet" placeholder
        assertNull(IsoDate.epochDayOrNull("2026-13-01"))
        assertNull(IsoDate.epochDayOrNull("2026/01/02"))
        assertNull(IsoDate.epochDayOrNull("kab"))
    }

    @Test
    fun `available fields are exactly what the catalogue holds`() {
        val income = checkNotNull(Fixtures.catalogue.service(Fixtures.income))
        assertEquals(
            setOf("eligibility", "documents", "fees", "sla", "channel", "office", "next_steps", "escalation", "faqs"),
            income.availableFields(),
        )
        val sample = checkNotNull(Fixtures.catalogue.service(Fixtures.sampleUnverified))
        assertEquals(setOf("eligibility"), sample.availableFields(), "the sample service holds nothing else")
    }

    @Test
    fun `helplines are only offered when they are verified`() {
        assertEquals(listOf("1800-000-0000"), Fixtures.catalogue.verifiedContacts())
        // the real asset is still full of VERIFY_OFFICIAL; none of it may reach a caller
        assertTrue(Fixtures.shipped.verifiedContacts().none { it.contains("VERIFY_OFFICIAL") })
    }

    @Test
    fun `staleness is judged on the oldest citation, not the newest`() {
        val income = checkNotNull(Fixtures.catalogue.service(Fixtures.income))
        val fresh = income.staleness(Fixtures.today)
        assertFalse(fresh.isStale)
        assertEquals(36, fresh.ageDays, "2026-09-10 -> 2026-10-16 is 36 days")

        val residence = checkNotNull(Fixtures.catalogue.service(Fixtures.residence))
        val stale = residence.staleness(Fixtures.today)
        assertTrue(stale.isStale)
        assertEquals(649, stale.ageDays)
        assertEquals("2025-01-05", IsoDate.fromEpochDay(checkNotNull(stale.oldest)))
    }

    @Test
    fun `a visit count is derived from the steps, never typed alongside them`() {
        val income = checkNotNull(Fixtures.catalogue.service(Fixtures.income))
        val online = income.channel.first { it.kind == "online" }
        val csc = income.channel.first { it.kind == "csc" }
        assertEquals(0, online.visitCount)
        assertEquals(1, csc.visitCount)
        assertEquals(listOf(0, 1), income.channelsBaseline.map { it.visits })
    }

    @Test
    fun `language tags from settings and asr land on one of two languages`() {
        assertEquals(Lang.HI, Lang.parse("hi"))
        assertEquals(Lang.HI, Lang.parse("hin"))
        assertEquals(Lang.HI, Lang.parse(" Hindi ")) // a locale tag from settings, padded
        assertEquals(Lang.HI, Lang.parse("हिन्दी"))
        assertEquals(Lang.EN, Lang.parse("en"))
        assertEquals(Lang.EN, Lang.parse("  ")) // default is English, never a crash
        assertEquals("hi", Lang.HI.code)
        assertEquals("en", Lang.EN.code)
    }

    @Test
    fun `a bilingual pair that is the same string twice is flagged`() {
        assertFalse(Fixtures.catalogue.services.first().lang.hi.name.let { Bilingual(it, "Income Certificate").untranslated })
        assertTrue(Bilingual("आय प्रमाण पत्र", "आय प्रमाण पत्र").untranslated, "paste-a-second-time is not a translation")
    }

    @Test
    fun `the shipped asset round-trips through the model with no field lost`() {
        // Phase 2.4: catch schema drift here instead of on stage. If a @Serializable field were dropped,
        // renamed or made @Transient, encode -> decode would not return the same object and this fails on
        // the real bytes the APK carries, not a hand-written fixture.
        val shipped = Fixtures.shipped
        val again = CatalogueLoader.parse(CatalogueLoader.json.encodeToString(Catalogue.serializer(), shipped))
        assertEquals(shipped, again, "the model does not round-trip the asset it just read - schema drift")

        for (s in shipped.services) {
            // build.mjs writes these derived arrays; they must survive the round trip and stay consistent
            // with the data they describe.
            val channelKinds = s.channel.map { it.kind }.toSet()
            val baselineKinds = s.channelsBaseline.map { it.kind }.toSet()
            assertTrue(
                baselineKinds.all { it in channelKinds },
                "${s.serviceId}: a hand-counted baseline names ${baselineKinds - channelKinds}, which has no steps",
            )
            assertTrue(
                s.districtOverrideFields.all { it in FieldKeywords.ANSWERABLE_FIELDS },
                "${s.serviceId}: district_override_fields lists ${s.districtOverrideFields - FieldKeywords.ANSWERABLE_FIELDS}, which :core can never answer",
            )
        }
    }
}
