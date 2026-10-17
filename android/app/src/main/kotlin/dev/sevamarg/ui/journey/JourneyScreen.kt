// android/app/src/main/kotlin/dev/sevamarg/ui/journey/JourneyScreen.kt
//
// Route 2: the whole process for the service on screen - how to apply (the hand-counted step baseline)
// and what happens after. It is the same AnswerCard as Ask, fed only the channel + next_steps fields, so
// the steps carry their needs_visit meta and their source exactly as the composer produced them. The
// step-reduction claim lives here; nothing on this screen re-counts a step in Kotlin.
package dev.sevamarg.ui.journey

import androidx.compose.runtime.Composable
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.StringTable
import dev.sevamarg.data.AskPipeline
import dev.sevamarg.ui.components.AnswerCard
import dev.sevamarg.ui.components.Notice

private val JOURNEY_FIELDS = listOf("channel", "next_steps")

@Composable
fun JourneyScreen(pipeline: AskPipeline, strings: StringTable, lang: Lang, serviceId: String?) {
    when {
        serviceId == null -> Notice(strings, "ui.ask_first")
        else -> {
            val answer = pipeline.composeFields(serviceId, lang, JOURNEY_FIELDS)
            if (answer == null) Notice(strings, "journey.empty") else AnswerCard(answer, strings)
        }
    }
}
