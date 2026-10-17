// android/core/src/main/kotlin/dev/sevamarg/core/refusal/Districts.kt
//
// Fees and office procedure vary by district across UP. The v1 catalogue is verified at STATE scope
// (service.district_scope == "state", no overrides/ files yet), so a question that names a district
// is a question about data we have not read. That is the only reason this file exists: to turn the
// mention into a refusal instead of reading out a state figure as if it were the citizen's district.
//
// Deliberately a SHORTLIST in both scripts, not the canonical 75. The authoritative coverage becomes
// data, not code: packages/catalogue/overrides/district-<code>.yaml (Phase 2.4) plus the bundled
// offices-<district>.json, after which "do we cover this district" is read from the asset and this
// list shrinks to nothing. A district that is not listed here does not produce a wrong number - it
// produces the state figure with its own citation and source - which is the honest pre-override
// behaviour and no more than the deck is allowed to claim.
//
// No citizen-facing fact is in this file: district names are administrative vocabulary, in the same
// category as the stopword list in Normaliser.
package dev.sevamarg.core.refusal

import dev.sevamarg.core.text.Normaliser

object Districts {

    /** Romanised/Hinglish forms. Multi-word names are kept as phrases; the fold handles spelling drift. */
    private val ROMAN: List<String> = listOf(
        "gautam buddha nagar", "greater noida", "noida", "ghaziabad", "gaziabad", "meerut", "saharanpur",
        "muzaffarnagar", "baghpat", "bulandshahr", "hapur", "aligarh",
        "hathras", "kasganj", "kashi ram nagar", "sambhal", "moradabad", "bijnor", "rampur", "bareilly",
        "badaun", "ayodhya", "sultanpur", "amethi", "raebareli", "prayagraj", "allahabad", "kaushambi",
        "fatehpur", "banda", "hamirpur", "mahoba", "chitrakoot", "kanpur nagar", "kanpur dehat",
        "swaroop nagar", "kannauj", "farrukhabad", "etawah", "mainpuri", "agra", "mathura", "etah",
        "firozabad", "auraiya", "jhansi", "lalitpur", "jalaun", "mirzapur", "sonbhadra", "varanasi",
        "kashi", "chandauli", "ghazipur", "ballia", "jaunpur", "azamgarh", "mau", "sant ravidas nagar",
        "bhadohi", "sant kabir nagar", "gorakhpur", "maharajganj", "deoria", "kushinagar", "siddharthnagar",
        "basti", "siddharth nagar", "shrawasti", "gonda", "bahraich", "balrampur", "sitapur", "hardoi",
        "shahjahanpur", "unnao", "lucknow", "pratapgarh", "ambedkar nagar",
        "lakhimpur kheri", "bansgaon", "pilibhit", "budaun", "shamli", "jyotiba phule nagar",
    )

    /**
     * Devanagari forms, kept as whole phrases (splitting on spaces here would turn "गौतम बुद्ध नगर"
     * into a bare "नगर" and every question mentioning any city would look district-scoped).
     */
    private val DEVANAGARI: List<String> = listOf(
        "गौतम बुद्ध नगर", "गाज़ियाबाद", "नोएडा", "मेरठ", "सहारनपुर", "मुज़फ़्फ़रनगर", "बाग़पत", "बुलंदशहर",
        "हापुड़", "अलीगढ़", "हाथरस", "कासगंज", "संभल", "मुरादाबाद", "बिजनौर", "रामपुर", "बरेली", "बदायूं",
        "अयोध्या", "सुल्तानपुर", "अमेठी", "रायबरेली", "प्रयागराज", "इलाहाबाद", "कौशांबी", "फ़तेहपुर", "बांदा",
        "हमीरपुर", "महोबा", "चित्रकूट", "कानपुर नगर", "कानपुर देहट", "कन्नौज", "फ़र्रुखाबाद", "इटावा",
        "मैनपुरी", "आगरा", "मथुरा", "एटा", "फ़िरोज़ाबाद", "औरैया", "झांसी", "ललितपुर", "जौनपुर", "मिर्ज़ापुर",
        "सोनभद्र", "वाराणसी", "काशी", "चंदौली", "गाजीपुर", "बलिया", "आजमगढ़", "मऊ", "भदोही", "गोरखपुर",
        "महाराजगंज", "देवरिया", "कुशीनगर", "सिद्धार्थनगर", "बस्ती", "श्रावस्ती", "गोंडा", "बहराइच",
        "बलरामपुर", "सीतापुर", "हरदोई", "शाहजहांपुर", "उन्नाओ", "लखनऊ", "प्रतापगढ़", "अम्बेडकर नगर",
        "लखीमपुर खीरी", "पीलीभीत", "शामली", "बागपत",
    )

    /**
     * (name as written, name as matched) pairs, longest folded form first so "greater noida" is found
     * before the "noida" inside it.
     *
     * Both forms are kept because they are not the same thing: the folded form is what a substring
     * comparison can use, and the written form is what a report and a refusal may quote. Returning the
     * fold would print "gotam buddha nagar" - an artefact of one spelling rule, not a place name.
     */
    private val NAMES: List<Name> = (ROMAN + DEVANAGARI)
        .map { Name(it, Normaliser.phrase(it)) }
        .filter { it.folded.isNotBlank() }
        .distinctBy { it.folded }
        .sortedByDescending { it.folded.length }

    /**
     * Short names ("agra", "etah", "mau", "आगरा") are only allowed to match a whole word. As a
     * substring they fire inside ordinary words - "mau" would catch "mausam", a bare "नगर" would
     * catch any city mention - and an over-eager guard refuses questions we can answer truthfully.
     */
    private val SUBSTRING: List<Name> = NAMES.filter { it.folded.length > 4 }
    private val WHOLE_WORD: List<Name> = NAMES.filter { it.folded.length <= 4 }

    /** An explicit "district" word makes the scope of the question clear even for an unlisted name. */
    private val MARKERS: List<String> = listOf("jila", "zila", "zilla", "district", "जिला", "ज़िला")
        .map { Normaliser.phrase(it) }

    /**
     * The names the guard actually matches, in its own matching order, longest first. Public because a
     * test that re-types a Devanagari place name is testing its own keyboard layout: deriving cases
     * from this list tests the matching rule instead. :bench prints it too, so "which districts would
     * refuse today" is a reportable number rather than a guess.
     */
    val knownNames: List<String> get() = NAMES.map { it.name }

    /** True when we hold district-scoped data for this name. Never true until overrides/ ships. */
    fun isCovered(name: String, coveredDistricts: List<String>): Boolean =
        coveredDistricts.any { Normaliser.phrase(it) == Normaliser.phrase(name) }

    /**
     * The district (or the whole locative phrase, for a marker hit) the citizen named, or null when
     * they did not locate their question anywhere.
     */
    fun mentioned(query: String): String? {
        val phrase = Normaliser.phrase(query)
        if (phrase.isBlank()) return null
        SUBSTRING.firstOrNull { phrase.contains(it.folded) }?.let { return it.name }
        val tokens = Normaliser.tokens(query).toSet()
        WHOLE_WORD.firstOrNull { it.folded in tokens }?.let { return it.name }
        return if (MARKERS.any { phrase.contains(it) }) phrase else null
    }

    /** A district name as it is written, and as the matcher sees it. */
    private data class Name(val name: String, val folded: String)
}
