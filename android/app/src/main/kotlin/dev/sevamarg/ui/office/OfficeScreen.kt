// android/app/src/main/kotlin/dev/sevamarg/ui/office/OfficeScreen.kt
//
// Route 4: where the work actually happens. The office types and the "how to find it" line are one
// composer field; the verified helpline rides along in escalation, so this screen is where J4's
// "here is where to go" has a permanent home, not just a refusal card. The map note is honest about
// Phase 2: there is no bundled OSM locator yet (that is 3.6), so the app says read the address rather
// than showing an empty map frame.
package dev.sevamarg.ui.office

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.StringTable
import dev.sevamarg.data.AskPipeline
import dev.sevamarg.ui.components.AnswerCard
import dev.sevamarg.ui.components.Notice

private val OFFICE_FIELDS = listOf("office", "escalation")

@Composable
fun OfficeScreen(pipeline: AskPipeline, strings: StringTable, lang: Lang, serviceId: String?) {
    if (serviceId == null) {
        Notice(strings, "ui.ask_first")
        return
    }
    val answer = pipeline.composeFields(serviceId, lang, OFFICE_FIELDS)
    if (answer == null) {
        Notice(strings, "office.empty")
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            strings.format("office.title"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        AnswerCard(answer, strings)
        // A live map needs a bundled location per district (Phase 3.6). Until then the honest statement.
        Text(
            strings.format("office.map_offline"),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
