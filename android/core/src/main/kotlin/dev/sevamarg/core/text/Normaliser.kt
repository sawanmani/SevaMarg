// android/core/src/main/kotlin/dev/sevamarg/core/text/Normaliser.kt
//
// Everything about matching a citizen's words to a catalogue entry starts here, and it is
// deterministic on purpose (ADR-0001: no language model on the answer path). A real input is one of:
//   - Devanagari, typed or ASR'd: "आय प्रमाण पत्र की फीस कितनी है"
//   - Hinglish, romanised the way people text: "aay praman patra ki fees kitni hai"
//   - English: "what is the fee for an income certificate"
// So the normaliser folds scripts and spelling variants into one comparable space, then drops
// postpositions that carry no routing signal. No stemming beyond that: a stemming algorithm is
// another thing to get wrong on stage, and the alias list is where colloquial phrasing belongs.
package dev.sevamarg.core.text

import java.text.Normalizer
import java.util.Locale

object Normaliser {

    // U+093C. Under NFKD the precomposed letters with a nukta (क़ ख़ ड़...) split into base + this
    // mark, so dropping it folds the whole set in one rule instead of a per-letter table.
    private const val NUKTA = '\u093C'
    private const val ZWJ = '\u200D'
    private const val ZWNJ = '\u200C'

    /**
     * Combining marks (matras, virama, the accents NFKD exposes) are not letters, so isLetterOrDigit()
     * alone would drop them and प्रमाण would silently become पमण.
     *
     * Block ranges, not java.lang.Character.isMark: Kotlin maps java.lang.Character onto kotlin.Char and
     * hides its statics, so the JDK category lookup is not reachable from here at all - and a range test
     * over the four scripts the app accepts is easier to reason about than a Unicode general category.
     */
    private fun isCombiningMark(ch: Char): Boolean = when (ch.code) {
        in 0x0300..0x036F -> true                       // Latin: NFKD splits é into e + U+0301
        in 0x0900..0x097F -> true                       // Devanagari: matras, virama, nukta, extended letters
        in 0x0A00..0x0A7F, in 0x0B00..0x0B7F -> true    // Gurmukhi / Oriya, for the accent spread in 3.4
        else -> false
    }

    private val DEVANAGARI_DIGITS = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9',
    )

    /**
     * Words that appear in almost every question and identify nothing. Deliberately includes the
     * romanised forms, because that is what ASR returns when someone speaks Hindi on a Hindi-less
     * keyboard. This list is vocabulary, not a fact: it can never change a fee or a document.
     */
    private val RAW_STOPWORDS: Set<String> = setOf(
        // Hindi / Hinglish
        "ka", "ke", "ki", "ko", "se", "mein", "me", "par", "aur", "bhi", "kya", "hai", "hain", "ho",
        "hota", "hoti", "hotey", "kare", "karna", "karu", "karoon", "karo", "krega", "karega",
        "mujhe", "mera", "meri", "mere", "hum", "aap", "apka", "apni", "yeh", "woh", "toh", "kaun",
        "kon", "kahan", "kab", "kitna", "kitne", "kitni", "kaise", "kese", "kyu", "kyon",
        "nahi", "nahin", "haan", "ji", "saab", "wala", "wali", "wale", "log", "lia", "liye",
        "or", "ek", "do", "teen", "sab", "bahut", "bohat", "ab", "phir", "bs",
        // The same words in Devanagari. Without these, a typed-Hindi question carries four times the
        // noise of the Hinglish one, and the overlap score would punish people who use the right script.
        "का", "के", "की", "को", "से", "में", "पर", "और", "भी", "क्या", "है", "हैं", "हो", "जाता", "जाती",
        "कौन", "कहां", "कहाँ", "कब", "कितना", "कितने", "कितनी", "कैसे", "क्यों", "नहीं", "हां", "हाँ", "जी",
        "वाला", "वाली", "वाले", "लिए", "लिया", "मेरा", "मेरी", "मेरे", "मुझे", "हम", "आप", "आपका", "आपकी",
        "यह", "वह", "वो", "तो", "एक", "सब", "बहुत", "अब", "फिर", "करना", "करने", "करो", "करता", "करती",
        "होता", "होती",
        // English
        "a", "an", "the", "is", "are", "of", "for", "to", "in", "on", "at", "and", "or", "my",
        "me", "i", "you", "your", "it", "this", "that", "what", "how", "when", "where", "which",
        "can", "do", "does", "will", "would", "should", "please", "tell", "want", "need", "there",
        "have", "has", "been", "being", "with", "from", "about", "get", "got", "am",
    )

    /**
     * Every stopword in exactly the shape [tokens] emits. Derived rather than hand-folded, so a
     * literal typed above can never silently fail to match - the usual way a stopword list looks
     * complete while half of it is dead code.
     */
    val STOPWORDS: Set<String> = RAW_STOPWORDS.map { foldLatin(normalise(it)) }.toSet()

    /** Devanagari range check: used to pick the ASR/TTS model pair and the TalkBack language. */
    fun hasDevanagari(text: String): Boolean = text.any { it in '\u0900'..'\u097F' }

    /**
     * Case-, punctuation-, nukta- and digit-folded form of the input, still space-separated so a
     * phrase can be matched as a substring ("praman patra" stays visible as two words).
     *
     * NFKD, not NFC: the precomposed Devanagari letters with a nukta only split apart under a
     * decomposing form, which is what makes one drop rule cover ज़/ज़, ख/ख़, ग/ग़...
     * Matras and other combining marks MUST survive - they are Unicode marks, not letters, so the
     * isCombiningMark() test below is load-bearing: drop it and "प्रमाण" silently becomes "पमण".
     */
    fun normalise(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        val folded = buildString(decomposed.length) {
            for (ch in decomposed) {
                when {
                    ch in DEVANAGARI_DIGITS -> append(DEVANAGARI_DIGITS.getValue(ch))
                    ch == NUKTA || ch == ZWJ || ch == ZWNJ -> Unit // dropped on purpose
                    ch == '।' || ch == '॥' || ch == '؟' -> append(' ')
                    // Marks are kept: see isCombiningMark for why this is not a JDK category lookup.
                    ch.isLetterOrDigit() || isCombiningMark(ch) -> append(ch.lowercase(Locale.ROOT))
                    else -> append(' ')
                }
            }
        }
        return folded.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Romanised-spelling fold. A speaker typing "pramaan" and one typing "praman" mean the same
     * service; collapsing long vowels to short ones makes the alias list writable once instead of
     * in four spellings. Applied to Latin tokens only - Devanagari has no such variance.
     */
    fun foldLatin(token: String): String {
        if (token.any { it in '\u0900'..'\u097F' }) return token
        return token
            .replace("aa", "a").replace("ee", "i").replace("oo", "u")
            .replace("ai", "e").replace("au", "o")
    }

    /** Normalised, stopword-free, folded tokens. The unit the router compares in sets. */
    fun tokens(text: String): List<String> =
        normalise(text).split(" ")
            .filter { it.isNotEmpty() }
            .map { foldLatin(it) }
            .filter { it.length > 1 && it !in STOPWORDS }

    /** Phrase-level form used for substring matching of aliases and field keywords. */
    fun phrase(text: String): String =
        normalise(text).split(" ").joinToString(" ") { foldLatin(it) }

    /**
     * Overlap of two token sets as a fraction of the smaller one, 0..1. Fraction of the *smaller*
     * set, not of the query: a four-word alias found inside a fifteen-word question is a real hit,
     * and Jaccard would bury it.
     */
    fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { b.contains(it) }
        val denominator = minOf(a.size, b.size)
        return inter.toDouble() / denominator
    }
}
