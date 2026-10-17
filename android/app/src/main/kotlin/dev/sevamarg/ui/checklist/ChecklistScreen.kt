// android/app/src/main/kotlin/dev/sevamarg/ui/checklist/ChecklistScreen.kt
//
// Route 3: the documents for the service on screen, as a tick-able list - the one place the app lets a
// citizen *do* something rather than read. Each row is still catalogue content dressed through i18n (the
// composer supplies the same AnswerLine the Ask card uses), and the tick state is pure view state held in
// memory: it is deliberately never persisted, because "what papers this person is gathering" is exactly
// the kind of detail that must not outlive the screen. The document text and its meta all come from the
// cited catalogue - the checkbox only marks readiness, it adds no fact.
package dev.sevamarg.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.SpeechTemplates
import dev.sevamarg.core.text.StringTable
import dev.sevamarg.data.AskPipeline
import dev.sevamarg.ui.components.Notice

@Composable
fun ChecklistScreen(pipeline: AskPipeline, strings: StringTable, lang: Lang, serviceId: String?) {
    if (serviceId == null) {
        Notice(strings, "ui.ask_first")
        return
    }
    val answer = pipeline.composeFields(serviceId, lang, listOf("documents"))
    val lines = answer?.nonEmptySections?.flatMap { it.lines } ?: emptyList()
    if (answer == null || lines.isEmpty()) {
        Notice(strings, "checklist.none")
        return
    }

    val speech = SpeechTemplates(strings)
    // Keyed per service so switching services cannot inherit last time's ticks.
    val ready = remember(serviceId) { mutableStateMapOf<Int, Boolean>() }
    val doneCount = (lines.indices).count { ready[it] == true }
    val remaining = lines.filterIndexed { i, _ -> ready[i] != true }.map { it.text }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            strings.format("checklist.title"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(answer.serviceName, style = MaterialTheme.typography.bodyMedium)
        Text(
            strings.format("checklist.done", mapOf("done" to doneCount.toString(), "total" to lines.size.toString())),
            style = MaterialTheme.typography.labelLarge,
        )

        lines.forEachIndexed { index, line ->
            val checked = ready[index] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = checked,
                        onValueChange = { ready[index] = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = { ready[index] = it })
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.text, style = MaterialTheme.typography.bodyLarge)
                    val labels = speech.metaLabels(line.meta)
                    if (labels.isNotEmpty()) {
                        Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (remaining.isNotEmpty()) {
            Text(
                strings.format("checklist.remaining", mapOf("items" to remaining.joinToString(", "))),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
