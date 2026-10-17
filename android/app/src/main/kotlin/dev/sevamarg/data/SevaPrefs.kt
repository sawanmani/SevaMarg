// android/app/src/main/kotlin/dev/sevamarg/data/SevaPrefs.kt
//
// Phase 2.9 wants the language choice to survive an app restart, and Phase 2.5 wants a session history
// with a one-tap clear. This is the whole persistence surface, and it is deliberately tiny: two keys and
// a recent-service list, all read back as plain values a composable can collect.
//
// No PII, by construction rather than by promise (docs/PHASES.md 2.5). What is stored:
//   - the chosen locale code ("hi"/"en"),
//   - the last service id and a bounded list of recent service ids.
// A service id is a catalogue key, not a person; the free-text the citizen typed is never persisted, so
// there is no column that could hold a name, a case number or a father's name. clearHistory() is the
// button on About, and it removes exactly the two keys that hold a record - the language is a setting,
// not data about a user, so it survives its own "delete my data".
package dev.sevamarg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sevamarg.core.catalogue.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One store per process. The delegate caches by name on the Context, so wrapping it in a SevaPrefs
// several times is cheap and they all read the same file.
private val Context.sevaDataStore: DataStore<Preferences> by preferencesDataStore(name = "seva_prefs")

class SevaPrefs(context: Context) {

    private val store = context.applicationContext.sevaDataStore

    /** Hindi is the default and the fallback for a corrupt or absent value (docs: HI default, EN one tap). */
    val lang: Flow<Lang> = store.data.map { prefs -> Lang.parse(prefs[KEY_LANG] ?: Lang.HI.code) }

    /** Most-recent-first list of service ids the citizen actually reached. Bounded to [MAX_RECENT]. */
    val recentServices: Flow<List<String>> = store.data.map { prefs ->
        (prefs[KEY_RECENT] ?: "").split(SEPARATOR).filter { it.isNotBlank() }
    }

    /** The last service reached, so Journey/Checklist/Office still describe it after a restart. */
    val lastService: Flow<String?> = store.data.map { prefs -> prefs[KEY_LAST_SERVICE]?.takeIf { it.isNotBlank() } }

    suspend fun setLang(lang: Lang) {
        store.edit { it[KEY_LANG] = lang.code }
    }

    /** Called when an answer is shown: remembers the service and makes it the tab's subject. */
    suspend fun rememberService(serviceId: String) {
        store.edit { prefs ->
            val current = (prefs[KEY_RECENT] ?: "").split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
            current.remove(serviceId)
            current.add(0, serviceId)
            prefs[KEY_RECENT] = current.take(MAX_RECENT).joinToString(SEPARATOR)
            prefs[KEY_LAST_SERVICE] = serviceId
        }
    }

    /** The one-tap clear on About. Wipes the record; leaves the language setting alone. */
    suspend fun clearHistory() {
        store.edit {
            it.remove(KEY_RECENT)
            it.remove(KEY_LAST_SERVICE)
        }
    }

    private companion object {
        val KEY_LANG = stringPreferencesKey("lang")
        val KEY_RECENT = stringPreferencesKey("recent_services")
        val KEY_LAST_SERVICE = stringPreferencesKey("last_service")

        // Service ids are catalogue keys (lowercase, hyphenated); a newline can never appear in one, so it
        // is a safe join character for a list stored in a single string value.
        const val SEPARATOR = "\n"
        const val MAX_RECENT = 6
    }
}
