// android/bench/src/test/kotlin/dev/sevamarg/bench/EvalSetRoutingTest.kt
//
// :bench:run prints numbers for the deck; this file is what makes them a claim instead of a story. The
// split follows the one the runner makes: an honesty invariant is gated absolutely (nothing invented,
// nothing uncited, every refusal names where to go), while accuracy on a sample-question set is only
// pinned to be *reported* correctly. A test that demanded 85 % intent accuracy today would be green only
// by loosening the judge, and a loosened judge is how a project ends up presenting a number it cannot
// re-derive.
//
// Everything here runs on the shipped bytes: catalogue.json and i18n/*.json are :core's assets directory
// published as JVM resources (core/build.gradle.kts), eval-set.json is evals/dist/ published the same
// way. No fixture, no mock, no second copy that can drift from what the APK carries.
package dev.sevamarg.bench

import dev.sevamarg.core.catalogue.CatalogueLoader
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.intent.FieldKeywords
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.text.StringBundles
import dev.sevamarg.core.text.StringSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EvalSetRoutingTest {

    private val catalogue = CatalogueLoader.fromResource()
    private val eval = EvalSet.fromResource()
    private val strings: Map<Lang, StringSource> = Lang.entries.associateWith { StringBundles.fromResource(it) }

    /**
     * Pinned to the submission date rather than `today`: a green run must mean the same thing in 2027
     * that it means now, and staleness is the one behaviour that would otherwise quietly change the
     * pass list as the calendar moves. `--today` on the runner is the same knob, for the same reason.
     */
    private val submissionDay: Long = checkNotNull(IsoDate.epochDayOrNull(SUBMISSION_DATE)) {
        "the pinned submission date is not an ISO date"
    }

    private fun run(): Report = EvalRunner(catalogue, eval, strings, todayEpochDay = submissionDay).run()

    @Test
    fun `the eval set on the classpath is the file the generator wrote`() {
        // A stale dist/ would otherwise be re-deriving last week's questions and calling it accuracy.
        assertEquals("evals/build.mjs", eval.generator)
        assertTrue(eval.generatedOn.isNotBlank(), "an untimestamped eval set cannot be quoted")
        assertEquals(eval.total, eval.items.size, "total does not match the item list")
        // counts is keyed by *source set* (evals/build.mjs), not by language: the out-of-scope questions
        // are all tagged lang hi today, so comparing counts.hi with the hi-language items would fail for
        // a reason that has nothing to do with the data. oos items in English are Phase 2.3 work - the
        // gate that needs them is "J4 in both languages", and it is listed in the gate report.
        assertEquals(eval.counts["hi"], eval.items.count { it.service != null && it.source.contains("questions_hi") }, "hi count")
        assertEquals(eval.counts["en"], eval.items.count { it.service != null && it.source.contains("questions_en") }, "en count")
        assertEquals(eval.counts["oos"], eval.items.count { it.service == null }, "oos count")
        assertEquals(eval.items.count { it.service == null }, eval.refusalCases, "refusal_cases")
        assertEquals(eval.items.map { it.id }.distinct().size, eval.items.size, "duplicate item id")
        assertTrue(eval.items.isNotEmpty(), "an empty eval set proves nothing")

        for (item in eval.items) {
            assertNotNull(Lang.entries.firstOrNull { it.code == item.lang }, "unparseable lang on ${item.id}")
            assertTrue(item.ask.isNotBlank(), "${item.id} asks nothing")
            for (field in item.expectFields) {
                assertTrue(
                    field in FieldKeywords.ANSWERABLE_FIELDS,
                    "${item.id} expects field '$field', which :core can never answer",
                )
            }
            item.twinOf?.let { twin ->
                assertTrue(eval.items.any { it.id == twin }, "${item.id} is the twin of a question that does not exist")
            }
        }
        for (unreachable in eval.unreachableByAliases) {
            assertTrue(eval.items.any { it.id == unreachable.id }, "unreachable list names ${unreachable.id}, which is not an item")
        }
    }

    @Test
    fun `nothing was invented and nothing was read out without a source`() {
        // The two numbers the whole project is built around. Absolute, not a threshold.
        val report = run()
        assertEquals(0, report.fabrications, "forbidden phrases said: ${report.results.filter { it.forbiddenHits.isNotEmpty() }.map { it.id to it.forbiddenHits }}")
        assertEquals(0, report.answersWithoutCitations, "uncited answers reached a citizen: ${report.results.filter { !it.citationsComplete }.map { it.id }}")
        assertTrue(report.honest, report.verdict)
    }

    @Test
    fun `every refusal names at least one office`() {
        // "Says no" is worthless without "and here is where to go". Office *types* are vocabulary plus
        // catalogue data, so this half of J4 is achievable with zero verified facts and must never slip.
        val refusals = run().results.filter { it.refusalReason != null }
        assertTrue(refusals.isNotEmpty(), "the eval set produced no refusal at all - check the router")
        for (r in refusals) {
            assertTrue(r.refusalHasOffice, "${r.id}: refused with ${r.refusalReason} and named no office")
        }
    }

    @Test
    fun `every out-of-scope question is refused rather than answered`() {
        val oos = run().results.filter { it.kind == ItemKind.OUT_OF_SCOPE.name }
        assertEquals(eval.refusalCases, oos.size, "an out-of-scope item was classified as answerable")
        for (r in oos) {
            assertNotNull(r.refusalReason, "${r.ask} was answered instead of refused (routed to ${r.routedService})")
        }
    }

    @Test
    fun `unverified data can never be read out as an answer`() {
        // The permanent safety property, independent of how much of the catalogue gets verified: a
        // composed answer whose sections are not all cited must be stopped by the ladder. If this ever
        // fires, the app is presenting a placeholder as a fact - the single worst failure available.
        //
        // It is gated per answer, not per catalogue: build.mjs stamps provisional:true while *any*
        // field anywhere holds the sentinel, but honesty is decided by the section the citizen actually
        // asked for. A verified fee can be read out even while the SLA is still a sentinel - refusing
        // it would be a different kind of wrong. What must never happen is an answer that reached a
        // citizen still carrying the sentinel text, or an uncited section.
        val report = run()
        val leaked = report.results.filter { it.answeredFields.isNotEmpty() && it.refusalReason == null }
        for (r in leaked) {
            assertTrue(r.citationsComplete, "${r.id}: sections ${r.answeredFields} shown with no refusal and incomplete citations")
            assertFalse(r.spokeSentinel, "${r.id}: an answer reached a citizen still carrying the VERIFY_OFFICIAL sentinel")
        }
        // The strongest form: measured against the literal text, across every answer shown or refused.
        assertEquals(0, report.answersSpeakingASentinel, "sentinel spoken to a citizen: ${report.results.filter { it.spokeSentinel }.map { it.id }}")
        if (catalogue.provisional) {
            // While the data still has gaps, at least one ask must hit a sentinel section and be stopped.
            // Zero blocked answers would mean the ladder is not running at all.
            assertTrue(report.results.any { it.blockedByData }, "provisional data produced no blocked answer - the ladder is not running")
        }
    }

    @Test
    fun `a helpline is quoted exactly when the catalogue holds a verified one`() {
        // Refusal-without-phone-number is a property of the data, and it must move when the data moves -
        // no more and no less. Both directions are asserted so neither a fabricated number nor a
        // suppressed real one can pass.
        val report = run()
        val refusals = report.results.filter { it.refusalReason != null }
        val verified = catalogue.verifiedContacts()
        if (verified.isEmpty()) {
            assertEquals(refusals.size, report.refusalsMissingHelpline, "a refusal quoted a number the catalogue does not hold")
        } else {
            assertEquals(0, report.refusalsMissingHelpline, "the catalogue has a verified helpline and ${refusals.map { it.id }.take(5)} did not offer it")
        }
    }

    @Test
    fun `session context reaches the service the aliases cannot`() {
        // The unreachable list is the generator's claim about cold-start routing; the runner's claim is
        // that a live session repairs exactly those items. Both halves are checked, so carry-over cannot
        // be quietly credited with accuracy it did not provide.
        val results = run().results.associateBy { it.id }
        assertTrue(eval.unreachableByAliases.isNotEmpty(), "the generator reported no unreachable items - re-check evals/build.mjs")
        for (unreachable in eval.unreachableByAliases) {
            val r = checkNotNull(results[unreachable.id]) { "unreachable item ${unreachable.id} is missing from the report" }
            assertEquals(ItemKind.FOLLOW_UP.name, r.kind, "${r.id} should have been given session context")
            assertEquals(unreachable.service, r.routedService, "${r.id}: session context did not reach the service on screen")
            assertEquals(MatchedVia.SESSION_CONTEXT.name, r.via, "${r.id} reached the service another way than carry-over")
        }
        // ...and none of them is credited as a pass on a cold start they did not have.
        val rescued = run().results.filter { it.kind == ItemKind.FOLLOW_UP.name }
        assertTrue(rescued.all { it.expectedService == it.routedService }, "a follow-up lost its service: ${rescued.filter { it.expectedService != it.routedService }.map { it.id }}")
        assertTrue(rescued.any { it.reachableCold == false }, "the cold-start comparison is not doing anything")
    }

    @Test
    fun `a service the catalogue does not hold can never be a silent pass`() {
        // Two of the sample questions ask about certificates that are not in v1. They must fail, loudly,
        // rather than be re-scored as successes because something answered them.
        for (r in run().results) {
            val expected = r.expectedService ?: continue
            if (catalogue.service(expected) == null) {
                assertFalse(r.passed, "${r.id} expects '$expected', which the catalogue does not hold, and passed anyway")
            }
        }
    }

    @Test
    fun `routing is deterministic`() {
        // ADR-0001: no model, no randomness, no clock on the answer path. Two identical runs of the same
        // 32 questions must produce identical rows - the reason a judge can re-run this and get the number
        // that is in the deck.
        val first = run()
        val second = run()
        assertEquals(first.results, second.results, "two runs of one eval set produced different rows")
        assertTrue(first.intentAccuracy == second.intentAccuracy, "accuracy moved between runs: ${first.intentAccuracy} vs ${second.intentAccuracy}")
        assertTrue(first.refusalCoverage == second.refusalCoverage, "refusal coverage moved between runs")
    }

    @Test
    fun `every headline number is derivable from the rows it summarises`() {
        // The deck quotes these five. If one of them stops matching its own detail list, the published
        // number is decoration - which is the failure mode this whole module exists to prevent.
        val report = run()
        val answerable = report.results.filter { it.kind != ItemKind.OUT_OF_SCOPE.name }
        val oos = report.results.filter { it.kind == ItemKind.OUT_OF_SCOPE.name }

        assertEquals(report.results.size, report.total)
        assertEquals(report.results.count { it.passed }, report.passed)
        assertEquals(report.total - report.passed, report.failures.size, "a failure is missing from the failure list")
        assertEquals(answerable.count { it.passed }.toDouble() / answerable.size, report.intentAccuracy, 1e-9)
        assertEquals(oos.count { it.passed }.toDouble() / oos.size, report.refusalCoverage, 1e-9)
        assertEquals(report.results.sumOf { it.forbiddenHits.size }, report.fabrications)
        assertEquals(report.results.count { !it.citationsComplete }, report.answersWithoutCitations)
        assertEquals(report.results.count { it.blockedByData }, report.answersBlockedPendingVerification)
        assertEquals(report.results.count { it.refusalMissingHelpline }, report.refusalsMissingHelpline)
        assertEquals(
            answerable.size,
            report.byLanguage.sumOf { it.answerable },
            "a language score does not cover every answerable item",
        )
        for (score in report.byLanguage) {
            val subset = answerable.filter { it.lang == score.lang }
            assertEquals(subset.count { it.passed }, score.passed, "${score.lang} passed count")
        }
        assertEquals(report.results.count { it.kind == ItemKind.FOLLOW_UP.name && it.reachableCold == false }, report.followUpsNeedingSession)
        assertEquals(SUBMISSION_DATE, report.evaluatedOn, "the report is not stamped with the date it was evaluated on")
    }

    companion object {
        /** 06.11 is the submission deadline in docs/PHASES.md; the benchmark is quoted as of that day. */
        private const val SUBMISSION_DATE = "2026-11-06"
    }
}
