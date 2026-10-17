// android/app/src/main/kotlin/dev/sevamarg/ui/SevaApp.kt
//
// The whole app shell: one screen host, five routes behind a text tab bar, and the state every route
// shares. It is here, and only here, that a session lives:
//
//   lang      persisted (DataStore) - Phase 2.9, HI default, survives restart
//   recent    persisted (DataStore) - service ids only, no free text, cleared in one tap (Phase 2.5)
//   outcome   in-memory            - the last AskPipeline result the citizen is looking at
//   subject   the service the Journey/Checklist/Office tabs describe; it is whatever Ask last reached,
//             or the persisted last one after a restart, so those tabs are never a second source of fact.
//
// Every tab renders through AskPipeline/AnswerCard, so switching tabs cannot make the app say something
// about a service that asking about that service would not say. The tabs are different questions, not
// different answers.
package dev.sevamarg.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sevamarg.core.catalogue.Lang
import dev.sevamarg.core.text.StringTable
import dev.sevamarg.data.AskOutcome
import dev.sevamarg.data.AskPipeline
import dev.sevamarg.data.SevaPrefs
import dev.sevamarg.data.ShippedData
import dev.sevamarg.ui.about.AboutScreen
import dev.sevamarg.ui.ask.AskScreen
import dev.sevamarg.ui.checklist.ChecklistScreen
import dev.sevamarg.ui.components.ToggleButton
import dev.sevamarg.ui.journey.JourneyScreen
import dev.sevamarg.ui.office.OfficeScreen
import kotlinx.coroutines.launch

private enum class Tab(val key: String) {
    ASK("nav.ask"),
    JOURNEY("nav.journey"),
    CHECKLIST("nav.checklist"),
    OFFICE("nav.office"),
    ABOUT("nav.about"),
}

@Composable
fun SevaApp(data: ShippedData, prefs: SevaPrefs, version: String) {
    val pipeline = remember(data) { AskPipeline(data) }
    val scope = rememberCoroutineScope()

    // The only three values that outlive a recomposition are the ones DataStore owns. Everything else is
    // per-session view state.
    val lang by prefs.lang.collectAsState(initial = Lang.HI)
    val recentIds by prefs.recentServices.collectAsState(initial = emptyList())
    val persistedLast by prefs.lastService.collectAsState(initial = null)

    val strings = data.strings(lang)

    // The last submitted question. The answer is *derived* from it (see below), never stored, so a
    // language change re-renders the same facts in the new locale instead of showing last language's
    // text (docs/PHASES.md 2.9: "changing language mid-answer re-speaks/re-renders the same facts").
    var lastQuery by remember { mutableStateOf<String?>(null) }
    var contextServiceId by remember { mutableStateOf<String?>(null) }
    var selectedServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    var cleared by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.ASK) }

    // Recomputed whenever the question or the language changes; the router and composer are deterministic
    // and hold no clock, so the same query in the same language always yields byte-identical facts.
    val outcome = remember(lastQuery, lang) {
        lastQuery?.let { pipeline.ask(it, lang, contextServiceId) }
    }

    // After a restart the persisted service becomes the tab subject again, so Journey/Office describe the
    // thing the citizen was last looking at instead of an empty screen.
    LaunchedEffect(persistedLast) {
        if (selectedServiceId == null) selectedServiceId = persistedLast
    }

    // Reaching an answer is the only thing that updates the session: it carries the service for a
    // follow-up and records the id (never the free text) for the recent chips. contextServiceId is not a
    // key on [outcome], so this write cannot loop a recompute of the answer it just produced.
    LaunchedEffect(outcome) {
        if (outcome is AskOutcome.Answered) {
            val id = (outcome as AskOutcome.Answered).answer.serviceId
            contextServiceId = id
            selectedServiceId = id
            prefs.rememberService(id)
        }
    }

    fun ask(query: String) {
        cleared = false
        lastQuery = query
    }

    fun changeLang(newLang: Lang) {
        scope.launch { prefs.setLang(newLang) }
    }

    fun clearData() {
        scope.launch {
            prefs.clearHistory()
            lastQuery = null // the derived answer goes to null with it
            selectedServiceId = null
            contextServiceId = null
            cleared = true
        }
    }

    val recentNames = recentIds.mapNotNull { id ->
        data.catalogue.service(id)?.let { id to it.lang.pick(lang) }
    }

    Scaffold(bottomBar = { TabBar(strings, tab) { tab = it } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.ASK -> AskScreen(strings, lang, ::changeLang, outcome, recentNames, ::ask)
                Tab.JOURNEY -> ScrollPage { JourneyScreen(pipeline, strings, lang, selectedServiceId) }
                Tab.CHECKLIST -> ScrollPage { ChecklistScreen(pipeline, strings, lang, selectedServiceId) }
                Tab.OFFICE -> ScrollPage { OfficeScreen(pipeline, strings, lang, selectedServiceId) }
                Tab.ABOUT -> ScrollPage { AboutScreen(data, strings, lang, version, cleared, ::clearData) }
            }
        }
    }
}

/** The four fact tabs share one scrollable column; Ask manages its own layout. */
@Composable
private fun ScrollPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

/**
 * A text tab bar, not icons. Two reasons that are both measurable: 5 Hindi labels fit without a glyph the
 * Material icon set may not carry in a font sized for Devanagari, and a label TalkBack can read is better
 * for a screen-reader user than an unlabeled shape. Targets stay >= 48 dp.
 */
@Composable
private fun TabBar(strings: StringTable, current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tab.entries.forEach { entry ->
            ToggleButton(strings.format(entry.key), selected = entry == current) { onSelect(entry) }
        }
    }
}
