// android/core/src/main/kotlin/dev/sevamarg/core/compose/AnswerComposer.kt
//
// Turns (service, fields) into the card the citizen sees. Two rules make this the safe half of the
// product: (1) every line carries its own citation, so a source chip is never optional; (2) a field
// that the catalogue does not have produces no section at all - the composer cannot fill a gap, and
// that is exactly when the refusal router speaks.
package dev.sevamarg.core.compose

import dev.sevamarg.core.catalogue.Catalogue
import dev.sevamarg.core.catalogue.DocumentRow
import dev.sevamarg.core.catalogue.FeeRow
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.catalogue.Service
import dev.sevamarg.core.intent.FieldKeywords
import dev.sevamarg.core.intent.MatchedVia
import dev.sevamarg.core.intent.Route

class AnswerComposer(private val catalogue: Catalogue) {

    /**
     * @param todayEpochDay injected so staleness tests are deterministic; production passes
     *   IsoDate.todayEpochDay(). Never a clock inside the pure module would be testable otherwise.
     */
    fun compose(route: Route, lang: Lang, todayEpochDay: Long = IsoDate.todayEpochDay()): Answer? {
        val service = route.serviceId?.let { catalogue.service(it) } ?: return null
        val sections = route.fields.mapNotNull { sectionFor(service, it, lang) }
        if (sections.isEmpty()) return null
        return Answer(
            serviceId = service.serviceId,
            serviceName = service.lang.pick(lang),
            lang = lang,
            sections = sections,
            via = route.via,
            confidence = route.confidence,
            // Per answer, not per catalogue. build.mjs stamps provisional:true on the whole asset
            // while any field in any service still holds the sentinel - that is the build's data
            // state, and the About screen shows it. It is not a licence to refuse a question the
            // catalogue *can* answer: the fee read from the e-District service list on 2026-10-17 is
            // as citable as the day the SLA row is verified too. What decides honesty here is the
            // section the citizen actually asked for, so only a sentinel inside these sections makes
            // this answer provisional.
            provisional = sectionsTouchSentinel(sections),
            staleness = service.staleness(todayEpochDay),
        )
    }

    /** The overview card (J1): everything the citizen needs before deciding to go. */
    fun composeOverview(serviceId: String, lang: Lang, todayEpochDay: Long = IsoDate.todayEpochDay()): Answer? {
        val service = catalogue.service(serviceId) ?: return null
        val fields = FieldKeywords.OVERVIEW_FIELDS.filter { it in service.availableFields() }
        return compose(Route(serviceId, 1.0, fields, MatchedVia.ALIAS_PHRASE), lang, todayEpochDay)
    }

    private fun sectionsTouchSentinel(sections: List<AnswerSection>): Boolean =
        sections.any { s -> s.lines.any { it.text.contains(SENTINEL) || it.citation.sourceUrl.contains(SENTINEL) } }

    private fun sectionFor(service: Service, field: String, lang: Lang): AnswerSection? {
        val lines: List<AnswerLine> = when (field) {
            "eligibility" -> service.eligibility.map {
                AnswerLine(it.text.pick(lang), emptyList(), cite(it.sourceUrl, it.verifiedOn))
            }

            "documents" -> service.documents.map { d ->
                AnswerLine(d.item.pick(lang), documentMeta(d), cite(d.sourceUrl, d.verifiedOn))
            }

            "fees" -> service.fees.map { f ->
                AnswerLine(f.item.pick(lang), feeMeta(f), cite(f.sourceUrl, f.verifiedOn))
            }

            "sla" -> service.sla?.let {
                listOf(AnswerLine(it.scope.pick(lang), listOf("days=${it.days}"), cite(it.sourceUrl, it.verifiedOn)))
            } ?: emptyList()

            // The step baseline. Rendered as an ordered list; needs_visit is what the step-reduction
            // claim counts, so it travels with the line instead of being recomputed in the UI.
            "channel" -> service.channel.flatMap { ch ->
                ch.steps.map { s ->
                    AnswerLine(
                        s.action.pick(lang),
                        listOf("channel=${ch.kind}", "step=${s.n}", if (s.needsVisit) "visit=yes" else "visit=no"),
                        cite(ch.sourceUrl, ch.verifiedOn),
                    )
                }
            }

            "office" -> service.office?.let { o ->
                listOf(AnswerLine(o.howToFind.pick(lang), o.type.map { "office_type=$it" }, cite(o.sourceUrl, o.verifiedOn)))
            } ?: emptyList()

            "next_steps" -> service.nextSteps.map { n ->
                val meta = listOf("status=${n.status}") + (n.etaDays?.let { listOf("eta_days=$it") } ?: emptyList())
                AnswerLine(n.means.pick(lang), meta, cite(n.sourceUrl, n.verifiedOn))
            }

            // The refusal path's positive half: where to go and which verified number to call.
            "escalation" -> service.escalation.map { e ->
                val text = listOfNotNull(e.whenItHappens.pick(lang), e.doWhen?.pick(lang)).joinToString(" - ")
                val meta = listOfNotNull(e.contact?.takeIf { e.hasVerifiedContact }?.let { "contact=$it" })
                AnswerLine(text, meta, cite(e.sourceUrl, e.verifiedOn))
            }

            "faqs" -> service.faqs.map { f ->
                AnswerLine("${f.q.pick(lang)} - ${f.a.pick(lang)}", emptyList(), cite(f.sourceUrl, f.verifiedOn))
            }

            else -> emptyList()
        }
        if (lines.isEmpty()) return null
        return AnswerSection(field = field, titleKey = UiKeys.section(field), lines = lines)
    }

    private fun documentMeta(d: DocumentRow): List<String> = listOfNotNull(
        d.copies?.let { "copies=$it" },
        d.format?.takeIf { !it.contains(SENTINEL) }?.let { "format=$it" },
        d.who?.let { "who=$it" },
        d.attestation?.let { "attested=${if (it) "yes" else "no"}" },
    )

    private fun feeMeta(f: FeeRow): List<String> = listOfNotNull(
        "amount_inr=${trimAmount(f.amountInr)}",
        f.mode?.let { "mode=$it" },
        f.freeBelowIncomeInr?.let { "free_below_income_inr=${trimAmount(it)}" },
    )

    private fun trimAmount(v: Double): String = if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

    private fun cite(url: String, verifiedOn: String): Citation = Citation(url, verifiedOn)

    companion object {
        private const val SENTINEL = "VERIFY_OFFICIAL"
    }
}
