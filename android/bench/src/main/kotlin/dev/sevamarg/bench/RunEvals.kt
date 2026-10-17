// android/bench/src/main/kotlin/dev/sevamarg/bench/RunEvals.kt
//
// `./gradlew :bench:run` - the two-minute reproducibility check the pitch promises. Print by default,
// write JSON when asked:
//
//   ./gradlew :bench:run --args="--out ../../results/bench/latest.json"
//   ./gradlew :bench:run --args="--today 2026-11-06"        # the submission date, staleness and all
//
// Two exit codes, because two different things can be wrong and a reader cannot tell them apart from a
// print: 1 means the honesty promise broke (a forbidden phrase was said, or an uncited answer reached a
// citizen) and 2 means the data is not verified yet, which shows up as low accuracy and refusals with no
// phone number to give. Accuracy itself is never a gate - with sample questions a 60 % headline is
// expected, and explaining the number is the presenter's job, not the build's.
package dev.sevamarg.bench

import dev.sevamarg.core.catalogue.CatalogueLoader
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.StringBundles
import dev.sevamarg.core.text.StringSource
import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json

private fun flag(args: List<String>, name: String): String? {
    val i = args.indexOf(name)
    return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
}

fun main(args: Array<String>) {
    val argList = args.toList()
    val outPath = flag(argList, "--out")
    // Pinning --today is how a stale-data claim gets re-checked on the submission date without waiting
    // for the calendar; the default is the actual day, so a normal run needs no arguments.
    val today = flag(argList, "--today")?.let { IsoDate.epochDayOrNull(it) ?: error("--today expects an ISO date like 2026-11-06") }
        ?: IsoDate.todayEpochDay()

    val catalogue = CatalogueLoader.fromResource()
    val eval = EvalSet.fromResource()
    val strings: Map<Lang, StringSource> = Lang.entries.associateWith { StringBundles.fromResource(it) }

    val report = EvalRunner(catalogue, eval, strings, todayEpochDay = today).run()
    printReport(report)

    if (outPath != null) {
        val out = File(outPath)
        out.parentFile?.mkdirs()
        out.writeText(Json { prettyPrint = true }.encodeToString(Report.serializer(), report))
        println("\nwrote $outPath (${out.length()} bytes)")
    }

    if (!report.honest) {
        System.err.println("\nFAIL: an untruthful answer reached a citizen. See the failure lines above.")
        exitProcess(1)
    }
    if (!report.dataComplete) {
        // Exit 2, deliberately not 1. Nothing was invented and nothing was said without a source - the
        // catalogue still holds VERIFY_OFFICIAL placeholders, so the honest behaviour is a refusal and the
        // accuracy number is low because of that. `npm run validate:catalogue` owns this state; conflating
        // the two exit codes would make a data gap look like a routing bug in CI.
        System.err.println("\nINCOMPLETE DATA (not a code failure): answers are blocked pending verification.")
        exitProcess(2)
    }
}

private fun printReport(report: Report) {
    println(
        "Seva-Marg eval - ${report.total} items, eval set ${report.generatedOn}, evaluated ${report.evaluatedOn}" +
            if (report.provisionalData) "  [PROVISIONAL DATA]" else "",
    )
    println("provisional=${report.provisionalData} strict=${report.strict}")
    println()
    println("%-9s %-7s %-14s %-9s %-8s %s".format("id", "lang", "kind", "via", "conf", "outcome"))
    for (r in report.results) {
        val outcome = if (r.passed) "ok" else "FAIL: ${r.failure}"
        println("%-9s %-7s %-14s %-9s %-8s %s".format(r.id, r.lang, r.kind, r.via, "%.2f".format(r.confidence), outcome))
    }
    println()
    report.byLanguage.forEach { s ->
        println("${s.lang}: ${s.passed}/${s.answerable} = ${"%.1f".format(s.accuracy * 100)}%")
    }
    println("follow-ups ${report.followUps}, of which ${report.followUpsNeedingSession} need session context")
    println()
    System.out.print(report.verdict)
}
