// android/core/src/main/kotlin/dev/sevamarg/core/catalogue/Catalogue.kt
//
// Mirror of packages/catalogue/schema/service.schema.json as emitted by build.mjs. These classes are
// the ONLY shape the app trusts, and they exist for one reason: a citizen-facing number can arrive
// here or not at all. There is no field on these models that a developer is allowed to fill in by
// hand - if the catalogue does not carry it, the answer composer has nothing to say and the refusal
// router takes over. That is the difference between this app and a chatbot.
package dev.sevamarg.core.catalogue

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The two languages the product guarantees. Voice may be Hindi-only; text answers are never. */
@Serializable
enum class Lang {
    @SerialName("hi") HI,
    @SerialName("en") EN,
    ;

    /** Locale tag used in asset file names and by TTS/ASR engine selection. */
    val code: String
        get() = when (this) {
            HI -> "hi"
            EN -> "en"
        }

    companion object {
        /** Accepts "hi"/"en"/"HI"/"hin"/"eng" - ASR and settings both end up here. */
        fun parse(tag: String): Lang = when (tag.lowercase().trim()) {
            "hi", "hin", "hindi", "हिन्दी" -> HI
            else -> EN
        }
    }
}

@Serializable
data class Bilingual(val hi: String, val en: String) {
    fun pick(lang: Lang): String = when (lang) {
        Lang.HI -> hi
        Lang.EN -> en
    }

    /** True when a "translation" is actually the same string pasted twice (see parity rules). */
    val untranslated: Boolean get() = hi.trim() == en.trim()
}

@Serializable
data class LangEntry(val name: String, val aliases: List<String> = emptyList())

@Serializable
data class LangBlock(val hi: LangEntry, val en: LangEntry) {
    /** The service name in the citizen's chosen language - the title of every answer card. */
    fun pick(lang: Lang): String = when (lang) {
        Lang.HI -> hi.name
        Lang.EN -> en.name
    }
}

/**
 * A cited fact. Every row below repeats [sourceUrl]/[verifiedOn] because the citation belongs to the
 * fact, not to the service: an answer section can be quoted with its source attached, on screen and
 * in the spoken sentence, without any lookup.
 */
@Serializable
data class EligibilityRow(
    val text: Bilingual,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
)

@Serializable
data class DocumentRow(
    val item: Bilingual,
    val who: String? = null,
    val copies: Int? = null,
    val attestation: Boolean? = null,
    val format: String? = null,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
)

@Serializable
data class FeeRow(
    val item: Bilingual,
    @SerialName("amount_inr") val amountInr: Double,
    val mode: String? = null,
    @SerialName("free_below_income_inr") val freeBelowIncomeInr: Double? = null,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
)

@Serializable
data class Sla(
    val days: Int,
    val scope: Bilingual,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
)

@Serializable
data class Step(val n: Int, val action: Bilingual, @SerialName("needs_visit") val needsVisit: Boolean)

@Serializable
data class Channel(
    val kind: String,
    val steps: List<Step>,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
) {
    val visitCount: Int get() = steps.count { it.needsVisit }
}

@Serializable
data class Office(
    val type: List<String> = emptyList(),
    @SerialName("how_to_find") val howToFind: Bilingual,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
)

@Serializable
data class Escalation(
    // `when` is a Kotlin keyword; the JSON key stays correct through @SerialName
    @SerialName("when") val whenItHappens: Bilingual,
    @SerialName("do") val doWhen: Bilingual? = null,
    val contact: String? = null,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
) {
    /** The line the refusal router offers instead of a guess: never a number we made up. */
    val hasVerifiedContact: Boolean get() = !contact.isNullOrBlank() && !contact.contains("VERIFY_OFFICIAL")
}

@Serializable
data class NextStep(
    val status: String,
    val means: Bilingual,
    @SerialName("eta_days") val etaDays: Int? = null,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("verified_on") val verifiedOn: String,
)

@Serializable
data class Faq(val q: Bilingual, val a: Bilingual, @SerialName("source_url") val sourceUrl: String, @SerialName("verified_on") val verifiedOn: String)

/** One row of the hand-counted step baseline. This is the denominator of the step-reduction claim. */
@Serializable
data class ChannelBaseline(
    val kind: String,
    val steps: Int,
    val visits: Int,
    val verified: Boolean,
)

@Serializable
data class Service(
    @SerialName("service_id") val serviceId: String,
    val state: String,
    @SerialName("district_scope") val districtScope: String,
    val lang: LangBlock,
    val eligibility: List<EligibilityRow> = emptyList(),
    val documents: List<DocumentRow> = emptyList(),
    val fees: List<FeeRow> = emptyList(),
    val sla: Sla? = null,
    val channel: List<Channel> = emptyList(),
    val office: Office? = null,
    val escalation: List<Escalation> = emptyList(),
    @SerialName("next_steps") val nextSteps: List<NextStep> = emptyList(),
    val faqs: List<Faq> = emptyList(),
    @SerialName("stale_after_days") val staleAfterDays: Int = 180,
    @SerialName("confidence_floor") val confidenceFloor: Double = 0.85,
    // written by build.mjs, not by hand
    @SerialName("search_terms") val searchTerms: List<String> = emptyList(),
    @SerialName("channels_baseline") val channelsBaseline: List<ChannelBaseline> = emptyList(),
    @SerialName("district_override_fields") val districtOverrideFields: List<String> = emptyList(),
) {
    val names: List<String> get() = listOf(lang.hi.name, lang.en.name)
    val aliases: List<String> get() = lang.hi.aliases + lang.en.aliases + searchTerms

    /** Fields the composer can actually answer for this service - the router never promises more. */
    fun availableFields(): Set<String> = buildSet {
        if (eligibility.isNotEmpty()) add("eligibility")
        if (documents.isNotEmpty()) add("documents")
        if (fees.isNotEmpty()) add("fees")
        if (sla != null) add("sla")
        if (channel.isNotEmpty()) add("channel")
        if (office != null) add("office")
        if (escalation.isNotEmpty()) add("escalation")
        if (nextSteps.isNotEmpty()) add("next_steps")
        if (faqs.isNotEmpty()) add("faqs")
    }

    fun isStale(todayEpochDay: Long): Boolean = staleness(todayEpochDay).isStale

    /**
     * Oldest citation wins: if any fact we are about to read out was verified more than
     * stale_after_days ago, the whole answer is labelled. Partial labelling would let a stale fee
     * hide behind a fresh document list.
     */
    fun staleness(todayEpochDay: Long): Staleness {
        val dates = allCitationDates()
        if (dates.isEmpty()) return Staleness(oldest = null, ageDays = null, isStale = true)
        val oldest = dates.min()
        val age = (todayEpochDay - oldest).toInt()
        return Staleness(oldest = oldest, ageDays = age, isStale = age > staleAfterDays)
    }

    private fun allCitationDates(): List<Long> =
        (eligibility.map { it.verifiedOn } + documents.map { it.verifiedOn } + fees.map { it.verifiedOn } +
            listOfNotNull(sla?.verifiedOn) + channel.map { it.verifiedOn } + listOfNotNull(office?.verifiedOn) +
            escalation.map { it.verifiedOn } + nextSteps.map { it.verifiedOn } + faqs.map { it.verifiedOn })
            .mapNotNull { IsoDate.epochDayOrNull(it) }
}

@Serializable
data class Staleness(val oldest: Long?, val ageDays: Int?, val isStale: Boolean)

/**
 * The compiled asset the APK ships. [provisional] is the honest flag: build.mjs sets it whenever any
 * field still holds VERIFY_OFFICIAL, and the UI must then show a "not verified" banner instead of
 * presenting the number as fact.
 */
@Serializable
data class Catalogue(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val generator: String = "",
    @SerialName("generated_on") val generatedOn: String = "",
    val provisional: Boolean = false,
    val langs: List<String> = listOf("hi", "en"),
    val statement: String = "UPNIC-004",
    @SerialName("citation_rule") val citationRule: String = "",
    @SerialName("source_hosts") val sourceHosts: List<String> = emptyList(),
    val services: List<Service> = emptyList(),
) {
    // A getter, deliberately: a body property on a @Serializable class is the kind of thing that
    // reads as "obviously fine" and then decides how the class serialises. The catalogue holds a
    // handful of services, so building the index per lookup costs nothing and keeps the serializer
    // seeing exactly the declared constructor properties - i.e. exactly the JSON asset.
    private val byId: Map<String, Service> get() = services.associateBy { it.serviceId }

    fun service(id: String): Service? = byId[id]

    val serviceCount: Int get() = services.size

    /** Every verified helpline in the catalogue, for the refusal path to offer as escalation. */
    fun verifiedContacts(): List<String> =
        services.flatMap { it.escalation }.filter { it.hasVerifiedContact }.mapNotNull { it.contact }.distinct()
}

/** Minimal ISO-date handling without pulling java.time into a module that also runs on old JDKs. */
object IsoDate {
    /** "2026-10-16" -> epoch day (days since 1970-01-01), or null if unparseable/sentinel. */
    fun epochDayOrNull(iso: String): Long? {
        val parts = iso.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y < 1970 || m !in 1..12 || d !in 1..31) return null
        // Days from a civil date, Howard Hinnant's algorithm - no external dependency, no timezone.
        // The m<=2 shift is load-bearing and easy to "forget": the algorithm counts from March, so a
        // January date left at its own year returns 365 days too high. Round-trip with fromEpochDay
        // below is what proves it (see CatalogueLoaderTest).
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun todayEpochDay(): Long {
        val millis = System.currentTimeMillis()
        return Math.floorDiv(millis, 86_400_000L)
    }

    /**
     * Inverse of [epochDayOrNull] (Hinnant's civil_from_days again, so a round trip is exact). The
     * staleness banner and the spoken "verified up to" sentence need a date and this module must not
     * gain a java.time dependency just to print one.
     */
    fun fromEpochDay(epochDay: Long): String {
        var z = epochDay + 719468L
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        z = if (m <= 2) y + 1 else y
        return "%04d-%02d-%02d".format(if (z <= 0) z - 1 else z, m, d)
    }
}
