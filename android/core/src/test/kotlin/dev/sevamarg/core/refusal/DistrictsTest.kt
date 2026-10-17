// android/core/src/test/kotlin/dev/sevamarg/core/refusal/DistrictsTest.kt
//
// The district guard has to fail in one direction only. Missing a district name means a citizen gets
// the state figure with its citation - honest, and the pre-override behaviour. Matching a name that
// was never meant as a district means a question we *can* answer gets refused - that is the failure
// these tests are about, because an over-eager substring list is how you get there.
package dev.sevamarg.core.refusal

import dev.sevamarg.core.text.Normaliser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DistrictsTest {

    @Test
    fun `a named district is found in both scripts`() {
        assertEquals("gautam buddha nagar", Districts.mentioned("income certificate ki fee gautam buddha nagar me kitni hai"))
        assertEquals("greater noida", Districts.mentioned("greater noida me office kahan hai"))
        // The fold does not bridge "gh"/"g", so both spellings are carried in the list and both work.
        assertEquals("ghaziabad", Districts.mentioned("ghaziabad me fees kitni hai"))
        assertEquals("gaziabad", Districts.mentioned("gaziabad me fees kitni hai"))

        // Devanagari cases are derived from the list itself, never re-typed here: a hand-copied
        // place name would test this file's keyboard layout instead of the matching rule.
        val devanagari = Districts.knownNames.filter { Normaliser.hasDevanagari(it) }
        assertTrue(devanagari.size >= 20, "the Hindi shortlist is too thin: ${devanagari.size} names")
        for (name in devanagari.take(5)) {
            assertEquals(name, Districts.mentioned("$name me income certificate ki fee"), "not found: $name")
        }
    }

    @Test
    fun `a shorter name inside a longer one never wins`() {
        // "noida" is a district; "greater noida" is a different one, and the longest-first ordering is
        // what makes the guard report the name the citizen actually used.
        assertEquals("greater noida", Districts.mentioned("greater noida jila me fee"))
        assertEquals("noida", Districts.mentioned("noida me fee kitni hai"))
    }

    @Test
    fun `short names match whole words only`() {
        // "mau" is a district of UP. As a substring it is also the start of "mausam" (weather), and a
        // guard that fires on that would refuse questions nothing to do with districts.
        assertNull(Districts.mentioned("mausam kaisa hai aaj"))
        assertEquals("mau", Districts.mentioned("mau me fee kitni hai"))
        assertNull(Districts.mentioned("agrah milta kya"))
        assertEquals("agra", Districts.mentioned("agra me income certificate"))
    }

    @Test
    fun `an explicit district word marks a name that is not in the shortlist`() {
        val phrase = Districts.mentioned("kuch anjaan jila me income certificate ki fee")
        assertNotNull(phrase, "an unnamed-but-located question is still district-scoped")
        assertEquals(Normaliser.phrase("kuch anjaan jila me income certificate ki fee"), phrase)
    }

    @Test
    fun `every name in the shortlist is found when it is the only thing said`() {
        // A regression net for the list itself: a name that cannot be matched is a district that will
        // silently get the state figure instead of a refusal.
        for (name in Districts.knownNames) {
            assertEquals(name, Districts.mentioned("$name ki fee kitni hai"), "unmatchable name: $name")
        }
    }

    @Test
    fun `a question that locates nothing is left alone`() {
        // The Hindi line is written as escapes for the same reason the coverage test below writes
        // "आगरा" as one: a hand-carried Devanagari literal is the thing most likely to arrive here
        // with a stray split in a conjunct, and a corrupt fixture in a "must not match" test would
        // pass for the wrong reason.
        for (query in listOf(
                "income certificate ki fee kitni hai",
                "\u0906\u092F \u092A\u094D\u0930\u092E\u093E\u0923 \u092A\u0924\u094D\u0930 \u0915\u0947 \u0932\u093F\u090F \u0915\u093E\u0917\u091C \u091A\u093E\u0939\u093F\u090F",
                "what documents are required for an income certificate",
            )) {
            assertNull(Districts.mentioned(query), "refused district scope for: $query")
        }
    }

    @Test
    fun `coverage is read from data and never assumed`() {
        // Until packages/catalogue/overrides ships, coveredDistricts() is empty and every mention
        // refuses. When it ships, this list is what the guard checks - no code change in Districts.
        assertFalse(Districts.isCovered("agra", emptyList()))
        assertTrue(Districts.isCovered("agra", listOf("Agra")), "case must not matter")
        assertTrue(Districts.isCovered("Gautam Buddha Nagar", listOf("gautam buddha nagar")))
        assertFalse(Districts.isCovered("mirzapur", listOf("Agra", "Mathura")))
        // Known limit, asserted so it is a decision and not a surprise: coverage compares folded forms
        // inside one script. When overrides/ ships, its district names must carry the Devanagari too -
        // otherwise a question asked in Hindi about a covered district would still be refused.
        assertFalse(Districts.isCovered("\u0906\u0917\u0930\u093e", listOf("agra")), "cross-script coverage is Phase 2.4 work")
    }

    @Test
    fun `empty and punctuation-only input are not districts`() {
        assertNull(Districts.mentioned(""))
        assertNull(Districts.mentioned("   "))
        assertNull(Districts.mentioned("?!"))
        assertFalse(Districts.isCovered("", listOf("agra")))
    }
}
