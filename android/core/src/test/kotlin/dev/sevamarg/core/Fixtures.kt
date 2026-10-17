// android/core/src/test/kotlin/dev/sevamarg/core/Fixtures.kt
//
// One place that loads the two catalogues the tests need: the synthetic fixture (fully verified, so
// composition, staleness and speech can be asserted at all) and the real shipped asset (so a schema
// drift or a broken generator fails a test instead of failing on stage).
//
// [today] is pinned. A test that reads the wall clock passes today and fails in three months, and a
// staleness test that fails for the wrong reason is worse than no test - it teaches you to ignore it.
package dev.sevamarg.core

import dev.sevamarg.core.catalogue.Catalogue
import dev.sevamarg.core.catalogue.CatalogueLoader
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.compose.AnswerComposer
import dev.sevamarg.core.intent.IntentRouter
import dev.sevamarg.core.refusal.RefusalRouter
import dev.sevamarg.core.text.SpeechTemplates
import dev.sevamarg.core.text.StringBundles
import dev.sevamarg.core.text.StringTable

object Fixtures {

    /** Synthetic, fully cited, non-sentinel. Never shipped - it lives in src/test/resources. */
    val catalogue: Catalogue = CatalogueLoader.fromResource("/fixture-catalogue.json")

    /** The actual bytes build.mjs wrote into android/core/src/main/assets/catalogue.json. */
    val shipped: Catalogue by lazy { CatalogueLoader.fromResource() }

    val hi: StringTable = StringBundles.fromResource(Lang.HI, Lang.EN)
    val en: StringTable = StringBundles.fromResource(Lang.EN)

    val router: IntentRouter = IntentRouter(catalogue)
    val composer: AnswerComposer = AnswerComposer(catalogue)
    val refusal: RefusalRouter = RefusalRouter(catalogue)
    val speechHi: SpeechTemplates = SpeechTemplates(hi)
    val speechEn: SpeechTemplates = SpeechTemplates(en)

    val income: String = "income-certificate"
    val residence: String = "residence-certificate"
    val sampleUnverified: String = "unverified-sample-service"

    val today: Long = requireNotNull(IsoDate.epochDayOrNull("2026-10-16")) { "fixture date must parse" }
}
