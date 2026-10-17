// android/app/src/main/kotlin/dev/sevamarg/MainActivity.kt
//
// One Activity, as docs/PROJECT_STRUCTURE.md says. Two decisions are visible from here:
//
// 1. The catalogue is loaded before the first frame, on the main thread. It is a few dozen kilobytes of
//    JSON that is already on the device, and the alternative - a splash that spins while an in-process
//    read finishes - costs more than it saves. Phase 2.7 measures launch time on the budget device; if
//    that number says otherwise, this moves into a ViewModel and nothing else changes.
// 2. A failure to load is a screen, not a crash. "This build has no verified data" is the honest state,
//    and the message names the command that fixes it.
package dev.sevamarg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.data.AppVersion
import dev.sevamarg.data.SevaPrefs
import dev.sevamarg.data.ShippedData
import dev.sevamarg.ui.SevaApp
import dev.sevamarg.ui.SevaMargTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val loaded = runCatching { ShippedData.load(assets) }
        val prefs = SevaPrefs(this)
        val version = AppVersion.name(this)
        setContent {
            SevaMargTheme {
                val data = loaded.getOrNull()
                if (data == null) {
                    CatalogueUnavailable(loaded.exceptionOrNull() ?: IllegalStateException("assets did not load"))
                } else {
                    // One host owns the session: language + recent services come from DataStore, the answer
                    // and the tab subject live in memory. The catalogue cannot change while running.
                    SevaApp(data, prefs, version)
                }
            }
        }
    }
}

/**
 * Shown when catalogue.json or an i18n asset is missing or unparseable. Deliberately not translated:
 * whoever sees this is fixing a build, not asking for a certificate, and the strings they need to grep
 * are the ones in the message.
 */
@Composable
private fun CatalogueUnavailable(error: Throwable) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Seva-Marg cannot start",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(error.message ?: error::class.qualifiedName ?: "unknown asset failure")
        Text("Assets expected: catalogue.json, i18n/${Lang.HI.code}.json, i18n/${Lang.EN.code}.json")
    }
}
