// android/bench/src/main/kotlin/dev/sevamarg/bench/EvalRunner.kt
//
// Every number the deck quotes comes out of here, on a laptop, in seconds, with no emulator and no
// model download. Three rules shape the code:
//
// 1. The judgement is mechanical. An item passes or fails on what :core did, never on a human note
//    taken afterwards, so "87 % intent accuracy" can be re-derived by anyone with the repo.
// 2. Fabrication is counted separately from accuracy. A wrong answer and a confident invented answer
//    are different failures: the second one is the reason ADR-0001 exists, so it gets its own number
//    and it is the one number that must be exactly zero.
// 3. A follow-up is given the session context a real conversation would have had, and the runner also
//    records what a cold start would have done. That keeps "carry-over is doing the work" visible
//    instead of inflating the headline accuracy.
package dev.sevamarg.bench

import dev.sevamarg.core.catalogue.Catalogue
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.compose.Answer
import dev.sevamarg.core.compose.AnswerComposer
import dev.sevamarg.core.intent.IntentRouter
import dev.sevamarg.core.intent.Route
import dev.sevamarg.core.refusal.Refusal
import dev.sevamarg.core.refusal.RefusalReason
import dev.sevamarg.core.refusal.RefusalRouter
import dev.sevamarg.core.text.Normaliser
import dev.sevamarg.core.text.SpeechTemplates
import dev.sevamarg.core.text.StringSource
import kotlinx.serialization.Serializable

enum class ItemKind { ANSWER, FOLLOW_UP, OUT_OF_SCOPE }

@Serializable
data class ItemResult(
    val id: String,
    val lang: String,
    val kind: String,
    val ask: String,
    val expectedService: String?,
    val routedService: String?,
    val via: String,
    val confidence: Double,
    val expectedFields: List<String>,
    val answeredFields: List<String>,
    val refusalReason: String? = null,
    val refusalHasOffice: Boolean = false,
    val refusalHasHelpline: Boolean = false,
    /** False when an answer was shown without every section carrying a live citation. */
    val citationsComplete: Boolean = true,
    /**
     * An answer existed but the refusal ladder stopped it (provisional data, stale, or an uncited
     * section). Not a failure of the code - the ladder working - but it is the reason the accuracy
     * number is low, so it has to be a number and not an explanation in prose.
     */
    val blockedByData: Boolean = false,
    /** A refusal that could name an office but had no verified phone number to add. */
    val refusalMissingHelpline: Boolean = false,
    /** Phrases from must_not_say that appeared in the text a citizen would have read or heard. */
    val forbiddenHits: List<String> = emptyList(),
    /** Only for FOLLOW_UP: would a cold start (no session) have reached the same service? */
    val reachableCold: Boolean? = null,
    /**
     * True when an answer that actually reached a citizen contains the literal VERIFY_OFFICIAL
     * sentinel. The ladder is supposed to make this impossible; it is measured anyway, because "the
     * refusal router stops unverified data" is a claim about two modules agreeing, not about one.
     */
    val spokeSentinel: Boolean = false,
    val passed: Boolean,
    val failure: String? = null,
)

@Serializable
data class LanguageScore(val lang: String, val answerable: Int, val passed: Int, val accuracy: Double)

@Serializable
data class Report(
    val generatedOn: String,
    val evaluatedOn: String,
    val provisionalData: Boolean,
    val strict: Boolean,
    val total: Int,
    val passed: Int,
    /** Answer + follow-up items only; refusals are scored separately, they are a different promise. */
    val intentAccuracy: Double,
    val byLanguage: List<LanguageScore>,
    val followUps: Int,
    val followUpsNeedingSession: Int,
    val outOfScope: Int,
    /** OOS items that produced a refusal WITH an office type and a verified helpline - the J4 number. */
    val refusalCoverage: Double,
    val fabrications: Int,
    /** Answers that would actually have reached a citizen without a full citation. Must be 0. */
    val answersWithoutCitations: Int,
    /** Answers that reached a citizen while still carrying the unverified-data sentinel. Must be 0. */
    val answersSpeakingASentinel: Int,
    /** Answers the ladder stopped because the data is not verified yet. A data number, not a code number. */
    val answersBlockedPendingVerification: Int,
    /** Refusals with no verified helpline behind them - zero only once the catalogue carries a real number. */
    val refusalsMissingHelpline: Int,
    val failures: List<String>,
    val results: List<ItemResult>,
) {
    /**
     * The promise we can be judged on today: nothing was invented, nothing was said without a source.
     * Independent of how much of the catalogue has been verified, because an honest refusal and an
     * honest answer are the same kind of claim.
     */
    val honest: Boolean get() = fabrications == 0 && answersWithoutCitations == 0 && answersSpeakingASentinel == 0

    /** The other axis: whether the shipped data is complete enough to answer anything at all. */
    val dataComplete: Boolean
        get() = !provisionalData && answersBlockedPendingVerification == 0 && refusalsMissingHelpline == 0

    val verdict: String
        get() = buildString {
            appendLine("intent accuracy ${"%.1f".format(intentAccuracy * 100)}% ($passed/$total)")
            appendLine("refusal coverage ${"%.1f".format(refusalCoverage * 100)}% of $outOfScope out-of-scope asks")
            appendLine("fabrications $fabrications - answers containing a phrase the eval set forbids")
            appendLine("answers without a complete citation set: $answersWithoutCitations")
            appendLine("answers that spoke the VERIFY_OFFICIAL sentinel: $answersSpeakingASentinel")
            append(if (honest) "HONEST" else "NOT HONEST")
            appendLine()
            appendLine(
                "data completeness: blocked $answersBlockedPendingVerification answers pending verification, " +
                    "$refusalsMissingHelpline refusals had no verified helpline",
            )
            append(if (dataComplete) "DATA COMPLETE" else "DATA INCOMPLETE - the accuracy number above is a property of the catalogue, not of the routing")
        }
}

class EvalRunner(
    private val catalogue: Catalogue,
    private val eval: EvalSet,
    private val strings: Map<Lang, StringSource>,
    private val todayEpochDay: Long = IsoDate.todayEpochDay(),
) {

    private val router = IntentRouter(catalogue)
    private val composer = AnswerComposer(catalogue)
    private val refusals = RefusalRouter(catalogue)
    private val unreachable = eval.unreachableIds

    fun run(): Report {
        val results = eval.items.map { runItem(it) }
        return summarise(results)
    }

    private fun runItem(item: EvalItem): ItemResult {
        val lang = Lang.parse(item.lang)
        val kind = when {
            item.expectsRefusal -> ItemKind.OUT_OF_SCOPE
            item.intent != null || item.id in unreachable -> ItemKind.FOLLOW_UP
            else -> ItemKind.ANSWER
        }
        // A follow-up is asked in a conversation: the card that came before it is context, exactly as
        // :app would carry it over. Cold-start behaviour for the same words is recorded separately.
        val context = if (kind == ItemKind.FOLLOW_UP) item.service else null
        val route = router.route(item.ask, lang, contextServiceId = context)
        val answer = route.serviceId?.let { composer.compose(route, lang, todayEpochDay) }
        val refusal = refusals.evaluate(route, answer, item.ask, lang, todayEpochDay)

        val spoken = spokenText(item, lang, answer, refusal)
        val forbidden = item.mustNotSay.filter { phrase ->
            val needle = Normaliser.phrase(phrase)
            needle.isNotBlank() && Normaliser.phrase(spoken).contains(needle)
        }

        val failure = judge(item, kind, route, answer, refusal, forbidden)
        // "Shown" is the only state a citation can violate the promise in: an answer the ladder stopped
        // never reached a citizen, so counting it as an uncited answer would make the honesty number
        // lie downwards - and a metric that lies in the safe direction is still a broken metric.
        val shown = answer != null && !answer.isEmpty && !refusal.isRefusal
        val blockedByData = answer != null && !answer.isEmpty && refusal.isRefusal &&
            (refusal.reason == RefusalReason.UNVERIFIED_DATA || refusal.reason == RefusalReason.STALE_DATA)
        return ItemResult(
            id = item.id,
            lang = lang.code,
            kind = kind.name,
            ask = item.ask,
            expectedService = item.service,
            routedService = route.serviceId,
            via = route.via.name,
            confidence = route.confidence,
            expectedFields = item.expectFields,
            answeredFields = answer?.fieldNames ?: emptyList(),
            refusalReason = refusal.reason.takeIf { refusal.isRefusal }?.name,
            refusalHasOffice = refusal.officeTypeKeys.isNotEmpty(),
            refusalHasHelpline = refusal.helpline != null,
            citationsComplete = !shown || (answer?.allCitationsPresent ?: true),
            blockedByData = blockedByData,
            refusalMissingHelpline = refusal.isRefusal && refusal.helpline == null,
            forbiddenHits = forbidden,
            reachableCold = if (kind == ItemKind.FOLLOW_UP) {
                val cold = router.route(item.ask, lang)
                cold.serviceId == item.service && cold.confidence >= (catalogue.service(item.service ?: "")?.confidenceFloor ?: 0.0)
            } else {
                null
            },
            spokeSentinel = shown && spoken.contains(SENTINEL),
            passed = failure == null,
            failure = failure,
        )
    }

    /** The exact strings a citizen would have read or heard - the only text a must_not_say can apply to. */
    private fun spokenText(item: EvalItem, lang: Lang, answer: Answer?, refusal: Refusal): String {
        val source = strings[lang] ?: return ""
        val speech = SpeechTemplates(source)
        return buildString {
            answer?.let { append(speech.speakable(it)).append(' ') }
            if (refusal.isRefusal) append(speech.speakableRefusal(refusal)).append(' ')
            answer?.nonEmptySections?.forEach { section -> section.lines.forEach { append(it.text).append(' ') } }
        }
    }

    private fun judge(
        item: EvalItem,
        kind: ItemKind,
        route: Route,
        answer: Answer?,
        refusal: Refusal,
        forbidden: List<String>,
    ): String? {
        if (forbidden.isNotEmpty()) return "${item.id}: says a forbidden phrase ${forbidden}"

        return when (kind) {
            // The out-of-scope promise is not "says no", it is "says no and says where to go instead".
            ItemKind.OUT_OF_SCOPE -> when {
                !refusal.isRefusal -> "${item.id}: out-of-scope ask (${item.whys}) was answered by ${route.serviceId}"
                refusal.officeTypeKeys.isEmpty() -> "${item.id}: refusal named no office type"
                refusal.helpline == null -> "${item.id}: refusal carried no verified helpline"
                else -> null
            }

            ItemKind.ANSWER, ItemKind.FOLLOW_UP -> when {
                route.serviceId != item.service ->
                    "${item.id}: routed to ${route.serviceId} at ${route.confidence}, expected ${item.service}"
                refusal.isRefusal -> "${item.id}: refused with ${refusal.reason} instead of answering"
                answer == null -> "${item.id}: no answer composed"
                !item.expectFields.all { it in answer.fieldNames } ->
                    "${item.id}: answered ${answer.fieldNames}, expected ${item.expectFields}"
                !answer.allCitationsPresent -> "${item.id}: an answer reached the citizen without a full citation"
                else -> null
            }
        }
    }

    private fun summarise(results: List<ItemResult>): Report {
        val answerable = results.filter { it.kind != ItemKind.OUT_OF_SCOPE.name }
        val oos = results.filter { it.kind == ItemKind.OUT_OF_SCOPE.name }
        val byLanguage = Lang.entries.map { lang ->
            val subset = answerable.filter { it.lang == lang.code }
            val hits = subset.count { it.passed }
            LanguageScore(
                lang = lang.code,
                answerable = subset.size,
                passed = hits,
                accuracy = if (subset.isEmpty()) 0.0 else hits.toDouble() / subset.size,
            )
        }
        return Report(
            generatedOn = eval.generatedOn,
            evaluatedOn = IsoDate.fromEpochDay(todayEpochDay),
            provisionalData = eval.provisional || catalogue.provisional,
            strict = eval.strict,
            total = results.size,
            passed = results.count { it.passed },
            intentAccuracy = if (answerable.isEmpty()) 0.0
            else answerable.count { it.passed }.toDouble() / answerable.size,
            byLanguage = byLanguage,
            followUps = results.count { it.kind == ItemKind.FOLLOW_UP.name },
            followUpsNeedingSession = results.count { it.kind == ItemKind.FOLLOW_UP.name && it.reachableCold == false },
            outOfScope = oos.size,
            refusalCoverage = if (oos.isEmpty()) 0.0
            else oos.count { it.passed }.toDouble() / oos.size,
            fabrications = results.sumOf { it.forbiddenHits.size },
            answersWithoutCitations = results.count { !it.citationsComplete },
            answersSpeakingASentinel = results.count { it.spokeSentinel },
            answersBlockedPendingVerification = results.count { it.blockedByData },
            refusalsMissingHelpline = results.count { it.refusalMissingHelpline },
            failures = results.filter { !it.passed }.mapNotNull { it.failure },
            results = results,
        )
    }
}

/** `item.whys` reads better in a failure line than an empty string, and never invents a reason. */
private val EvalItem.whys: String get() = why ?: "no reason recorded"

/** The sentinel build.mjs refuses to ship in a strict build, checked against the text a citizen heard. */
private const val SENTINEL = "VERIFY_OFFICIAL"
