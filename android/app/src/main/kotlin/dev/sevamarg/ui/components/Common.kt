// android/app/src/main/kotlin/dev/sevamarg/ui/components/Common.kt
//
// The shared building blocks every route renders through. There is exactly one way an answer becomes
// pixels - AnswerCard - and Journey/Checklist/Office reuse it rather than inventing their own layout,
// so the provisional banner, the per-line meta labels and the source chip are impossible to forget on
// one screen while present on another. A fact is dressed the same way everywhere or it is not shown.
//
// Accessibility lives here, not in a later pass: section titles carry the heading role, touch targets
// are >= 48 dp, and the answer area is a live region (set by the caller that owns the state).
package dev.sevamarg.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sevamarg.core.catalogue.IsoDate
import dev.sevamarg.core.compose.Answer
import dev.sevamarg.core.compose.UiKeys
import dev.sevamarg.core.refusal.Refusal
import dev.sevamarg.core.text.SpeechTemplates
import dev.sevamarg.core.text.StringTable

@Composable
fun Header(strings: StringTable) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            strings.format("app.name"),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(strings.format("app.tagline"), style = MaterialTheme.typography.bodyMedium)
        // Not a badge that decorates: it is the claim the whole architecture exists to keep true.
        Text(strings.format("app.offline_badge"), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LanguageToggle(strings: StringTable, current: dev.sevamarg.core.catalogue.Lang, onChange: (dev.sevamarg.core.catalogue.Lang) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.format("lang.toggle"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The language names are the one place wording is shown in its own script in both locales: a
            // हिन्दी speaker choosing between languages must read both options, so lang.hi / lang.en are
            // deliberately untranslated in en.json too.
            ToggleButton(strings.format("lang.hi"), current == dev.sevamarg.core.catalogue.Lang.HI) { onChange(dev.sevamarg.core.catalogue.Lang.HI) }
            ToggleButton(strings.format("lang.en"), current == dev.sevamarg.core.catalogue.Lang.EN) { onChange(dev.sevamarg.core.catalogue.Lang.EN) }
        }
    }
}

@Composable
fun ToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
    }
}

@Composable
fun VoiceNote(strings: StringTable) {
    // Honest, in both directions: the button is disabled and the reason is on screen, because :voice has
    // no engine until the ADR-0003 spike picks one. No fake recording animation.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = strings.format("a11y.mic_button") },
        ) {
            Text(strings.format("ask.mic.start"))
        }
        Text(strings.format("ask.voice_unavailable"), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The one and only way an [Answer] reaches the screen. Everything a citizen could act on - a fee, a
 * document count, a step - is rendered here from catalogue content plus an i18n label, with its source
 * line at the bottom. Journey, Office and the Ask overview all call this; none of them re-derives a
 * number, which is what makes "the tabs can't disagree with each other" true instead of aspirational.
 */
@Composable
fun AnswerCard(answer: Answer, strings: StringTable) {
    val speech = SpeechTemplates(strings)
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(answer.serviceName, style = MaterialTheme.typography.titleMedium)

            if (answer.provisional) {
                // The sample-data banner: a provisional answer can never present itself as a fact.
                Text(strings.format(UiKeys.provisionalBanner), style = MaterialTheme.typography.bodyMedium)
            }
            answer.staleness.ageDays?.let { age ->
                if (answer.isStale) {
                    Text(strings.format(UiKeys.staleWarning, mapOf("days" to age.toString())))
                }
            }
            answer.staleness.oldest?.let { oldest ->
                Text(strings.format(UiKeys.verifiedUpto, mapOf("date" to IsoDate.fromEpochDay(oldest))))
            }

            answer.nonEmptySections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        strings.format(section.titleKey),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    section.lines.forEach { line ->
                        Text(line.text, style = MaterialTheme.typography.bodyLarge)
                        // Raw catalogue values ("copies=1") become words only through i18n; if a label is
                        // missing, metaLabels drops it rather than reading "copies=1" aloud.
                        val labels = speech.metaLabels(line.meta)
                        if (labels.isNotEmpty()) {
                            Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SourceChip(answer, strings)
        }
    }
}

/** Every distinct official page the shown facts rest on. Kept together so no section loses its source. */
@Composable
private fun SourceChip(answer: Answer, strings: StringTable) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(strings.format("answer.source"), style = MaterialTheme.typography.labelLarge)
        answer.citations.forEach { Text(it.sourceUrl, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * The refusal is a card, not an error. "Says no" is worthless without "and here is where to go", so the
 * office types, the verified helpline and the did-you-mean list are all rendered from the [Refusal] the
 * ladder built - no text is written here.
 */
@Composable
fun RefusalCard(refusal: Refusal, strings: StringTable) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.format(refusal.messageKey), style = MaterialTheme.typography.bodyLarge)

            if (refusal.officeTypeKeys.isNotEmpty()) {
                Text(strings.format(UiKeys.refusalWhereToAsk), style = MaterialTheme.typography.labelLarge)
                refusal.officeTypeKeys.forEach { key ->
                    Text(strings.format(key), style = MaterialTheme.typography.bodyMedium)
                }
            }
            refusal.helpline?.let { number ->
                // The number itself is a catalogue fact; only the label is an i18n template.
                Text(strings.format(UiKeys.refusalHelpline, mapOf("contact" to number)))
            }
            if (refusal.suggestions.isNotEmpty()) {
                Text(strings.format(UiKeys.refusalSuggestions), style = MaterialTheme.typography.labelLarge)
                refusal.suggestions.forEach { suggestion ->
                    Text(suggestion.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * A tab with nothing to show yet. The wording is an i18n key, never a hardcoded string, so a screen that
 * exists only in one language is caught by parity.mjs instead of appearing half-translated on stage.
 */
@Composable
fun Notice(strings: StringTable, key: String) {
    Card {
        Text(
            strings.format(key),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        )
    }
}
