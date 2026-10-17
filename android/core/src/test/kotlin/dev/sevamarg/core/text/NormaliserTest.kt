// android/core/src/test/kotlin/dev/sevamarg/core/text/NormaliserTest.kt
//
// These are the tests that protect the one thing a voice-first Hindi app cannot afford: silently
// mangling the script it is trying to match. Every case here is a fold that must hold, plus the two
// folds that must NOT happen (dropping matras, or dropping the nukta distinction in a way that breaks
// a word).
package dev.sevamarg.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NormaliserTest {

    @Test
    fun `devanagari matras survive the fold`() {
        // The bug this guards: NFKD + an isLetterOrDigit() filter alone would strip every vowel sign
        // and turn "प्रमाण" into "पमण", silently breaking every Hindi match.
        val folded = Normaliser.normalise("प्रमाण पत्र")
        assertTrue(folded.contains("ा"), "vowel sign AA must survive: $folded")
        // U+094D VIRAMA, written as an escape because a standalone consonant-killer is invisible in
        // most fonts and easy to corrupt in transit. This is the mark that builds the conjuncts
        // (प्र, त्र); lose it and "प्रमाण" becomes a sequence of lone consonants.
        assertTrue(folded.contains("\u094D"), "virama must survive: $folded")
        assertEquals(listOf("प्रमाण", "पत्र"), Normaliser.tokens("प्रमाण पत्र"))
    }

    @Test
    fun `nukta letters fold onto their base letter`() {
        // ज़ and ज are the same word to a speaker and different codepoints to a matcher.
        assertEquals(Normaliser.normalise("ज"), Normaliser.normalise("ज़"))
        assertEquals(Normaliser.tokens("ज़िंदगी"), Normaliser.tokens("जिंदगी"))
        assertFalse(Normaliser.normalise("क़").contains('़'), "nukta must be dropped, not kept")
    }

    @Test
    fun `devanagari digits fold to ascii digits`() {
        assertEquals("123", Normaliser.normalise("१२३"))
    }

    @Test
    fun `latin long vowels collapse so one alias covers four spellings`() {
        assertEquals(Normaliser.tokens("pramaan patra"), Normaliser.tokens("praman patra"))
        assertEquals("praman", Normaliser.foldLatin("pramaan"))
        // and the fold never touches Devanagari
        assertEquals("प्रमाण", Normaliser.foldLatin("प्रमाण"))
    }

    @Test
    fun `postpositions and helpers are dropped from tokens`() {
        val tokens = Normaliser.tokens("income certificate ke liye kya hai")
        assertEquals(listOf("income", "certificate"), tokens)
        // the same question typed in Devanagari must not carry four times the noise
        assertEquals(listOf("प्रमाण", "पत्र", "फीस"), Normaliser.tokens("प्रमाण पत्र की फीस है"))
    }

    @Test
    fun `phrase keeps word order and cleans punctuation`() {
        assertEquals("income certificate", Normaliser.phrase("Income Certificate?"))
        assertEquals("आय प्रमाण पत्र", Normaliser.phrase("आय प्रमाण पत्र।"))
    }

    @Test
    fun `overlap is the fraction of the smaller set`() {
        // a four-word alias found inside a fifteen-word question is a real hit, and Jaccard would
        // bury it - this is the arithmetic behind that decision
        assertEquals(1.0, Normaliser.overlap(setOf("आय", "प्रमाण", "पत्र"), setOf("प्रमाण", "पत्र")))
        assertEquals(0.5, Normaliser.overlap(setOf("a", "b"), setOf("b", "c", "d")))
        assertEquals(0.0, Normaliser.overlap(emptySet(), setOf("a")))
        assertEquals(0.0, Normaliser.overlap(setOf("a"), emptySet()))
    }

    @Test
    fun `normalising twice changes nothing`() {
        for (raw in listOf("आय प्रमाण पत्र की फीस?", "aay praman patra  fees", "३० दिन में", "CSCcentre")) {
            val once = Normaliser.normalise(raw)
            assertEquals(once, Normaliser.normalise(once), "not idempotent: $raw")
        }
    }

    @Test
    fun `script detection drives the voice model choice`() {
        assertTrue(Normaliser.hasDevanagari("आय प्रमाण पत्र"))
        assertFalse(Normaliser.hasDevanagari("income certificate"))
    }
}
