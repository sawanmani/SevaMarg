// android/app/src/main/kotlin/dev/sevamarg/ui/ask/AskScreen.kt
//
// The Ask tab (route 1 of 5). It owns only the text field; the question goes out through [onAsk] and the
// answer/refusal comes back through [outcome], so the same AskPipeline a :bench test drives is what this
// screen shows - the UI holds no routing logic of its own.
//
// Accessibility is not a later pass on this file: the answer area is a live region so TalkBack reads a
// new answer unprompted, every section title is a heading (see AnswerCard), and the touch targets are
// >= 48 dp.
package dev.sevamarg.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.StringTable
import dev.sevamarg.data.AskOutcome
import dev.sevamarg.ui.components.AnswerCard
import dev.sevamarg.ui.components.Header
import dev.sevamarg.ui.components.LanguageToggle
import dev.sevamarg.ui.components.RefusalCard
import dev.sevamarg.ui.components.VoiceNote

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    strings: StringTable,
    lang: Lang,
    onLangChange: (Lang) -> Unit,
    outcome: AskOutcome?,
    recentServiceNames: List<Pair<String, String>>,
    onAsk: (String) -> Unit,
) {
    var query by remember(lang) { mutableStateOf("") }

    fun submit() = onAsk(query)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(strings)
        LanguageToggle(strings, lang, onLangChange)
        VoiceNote(strings)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(strings.format("ask.title")) },
            placeholder = { Text(strings.format("ask.hint")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            minLines = 2,
        )
        Button(
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(strings.format("ask.send"))
        }

        // Reaching a service you have asked about before is one tap, and the chip asks by that service's
        // own name - so it goes through the router exactly like a typed question, no special path.
        if (recentServiceNames.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.format("ask.chips.label"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentServiceNames.forEach { (_, name) ->
                        AssistChip(
                            onClick = { onAsk(name) },
                            label = { Text(name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }

        // A live region is what makes "TalkBack reads the answer unprompted" true rather than aspirational.
        // LiveRegionMode, not Politeness: composeBom 2024.12.01 resolves ui 1.7.6, and the renamed
        // property arrives with 1.8. Same behaviour, one less version to chase before the accessibility pass.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = strings.format("a11y.answer_updated")
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val result = outcome) {
                null -> Unit
                is AskOutcome.Blank -> Unit
                is AskOutcome.Answered -> AnswerCard(result.answer, strings)
                is AskOutcome.Refused -> RefusalCard(result.refusal, strings)
            }
        }
    }
}
