package de.mediclever.tagebuch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import de.mediclever.tagebuch.core.model.Table
import de.mediclever.tagebuch.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


/** Was nur dieses Geraet betrifft, plus die Nutzer-Einstellungen als JSON. */
data class LocalState(
    val settings: UserSettings = UserSettings(),
    val settingsDirty: Boolean = false,
    /** Zeitpunkt der Einwilligung zum Fuehren des Tagebuchs, 0 = noch nicht erteilt. */
    val consentAt: Long = 0,
    val onboardingDone: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val lastSyncAt: Long = 0,
    val lastSyncError: String = "",
    /** Hinweis "keine Patientendaten" wurde bestaetigt. */
    val hintSeen: Boolean = false,
    /** Konto, mit dem dieses Geraet zuletzt abgeglichen hat. */
    val lastUid: String = "",
    /** Wie oft ein Vorschlag aus der Sprachnotiz abgelehnt wurde, Schluessel "<ziel>|<wort>". */
    val voiceRejects: Map<String, Int> = emptyMap(),
    /** MediClever Tagebuch Plus ist freigeschaltet. Bis es einen Kauf gibt: Schalter in den Einstellungen. */
    val plus: Boolean = false,
) {
    /** Vorschlaege, die dreimal abgelehnt wurden, kommen nicht mehr. */
    val voiceRejected: Set<String> get() = voiceRejects.filterValues { it >= 3 }.keys
}

class LocalPrefs(private val store: DataStore<Preferences>) {
    /** scope bestimmt die Lebensdauer: endet er, gibt der Speicher seine Datei wieder frei. */
    constructor(context: Context, scope: CoroutineScope) : this(
        PreferenceDataStoreFactory.create(scope = scope) { context.applicationContext.preferencesDataStoreFile("tagebuch") },
    )

    private object K {
        val settings = stringPreferencesKey("settings")
        val settingsDirty = booleanPreferencesKey("settingsDirty")
        val consentAt = longPreferencesKey("consentAt")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val pinHash = stringPreferencesKey("pinHash")
        val pinSalt = stringPreferencesKey("pinSalt")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
        val lastSyncError = stringPreferencesKey("lastSyncError")
        val hintSeen = booleanPreferencesKey("hintSeen")
        val lastUid = stringPreferencesKey("lastUid")
        val voiceRejects = stringPreferencesKey("voiceRejects")
        val plus = booleanPreferencesKey("plus")
        fun cursor(t: Table) = longPreferencesKey("cursor_${t.path}")
    }

    val state: Flow<LocalState> = store.data.map { p ->
        LocalState(
            settings = p[K.settings]?.let { runCatching { AppJson.decodeFromString<UserSettings>(it) }.getOrNull() } ?: UserSettings(),
            settingsDirty = p[K.settingsDirty] ?: false,
            consentAt = p[K.consentAt] ?: 0,
            onboardingDone = p[K.onboardingDone] ?: false,
            pinHash = p[K.pinHash] ?: "",
            pinSalt = p[K.pinSalt] ?: "",
            lastSyncAt = p[K.lastSyncAt] ?: 0,
            lastSyncError = p[K.lastSyncError] ?: "",
            hintSeen = p[K.hintSeen] ?: false,
            lastUid = p[K.lastUid] ?: "",
            voiceRejects = p[K.voiceRejects]?.let { runCatching { AppJson.decodeFromString<Map<String, Int>>(it) }.getOrNull() } ?: emptyMap(),
            plus = p[K.plus] ?: false,
        )
    }

    suspend fun current(): LocalState = state.first()

    suspend fun setSettings(s: UserSettings, dirty: Boolean) {
        store.edit { it[K.settings] = AppJson.encodeToString(s); it[K.settingsDirty] = dirty }
    }
    suspend fun markSettingsClean(updatedAt: Long) {
        if (current().settings.updatedAt == updatedAt) store.edit { it[K.settingsDirty] = false }
    }
    suspend fun setConsent(at: Long) { store.edit { it[K.consentAt] = at } }
    suspend fun setOnboardingDone(done: Boolean) { store.edit { it[K.onboardingDone] = done } }
    suspend fun setLastUid(uid: String) { store.edit { it[K.lastUid] = uid } }
    suspend fun setPlus(on: Boolean) { store.edit { it[K.plus] = on } }
    /** Zaehlt abgelehnte Vorschlaege; angenommene setzen den Zaehler zurueck. */
    suspend fun countVoice(rejected: Collection<String>, accepted: Collection<String>) {
        if (rejected.isEmpty() && accepted.isEmpty()) return
        val now = current().voiceRejects.toMutableMap()
        for (k in rejected) now[k] = (now[k] ?: 0) + 1
        for (k in accepted) now.remove(k)
        store.edit { it[K.voiceRejects] = AppJson.encodeToString(now.toMap()) }
    }
    suspend fun setHintSeen() { store.edit { it[K.hintSeen] = true } }
    suspend fun setPin(hash: String, salt: String) { store.edit { it[K.pinHash] = hash; it[K.pinSalt] = salt } }
    suspend fun setSyncResult(at: Long, error: String) {
        store.edit {
            if (error.isEmpty()) it[K.lastSyncAt] = at
            it[K.lastSyncError] = error
        }
    }
    suspend fun cursor(t: Table): Long = store.data.first()[K.cursor(t)] ?: 0
    suspend fun setCursor(t: Table, c: Long) { store.edit { it[K.cursor(t)] = c } }
    /** Nach Abmelden oder Kontowechsel: alles neu abgleichen. */
    suspend fun resetCursors() {
        store.edit { p -> Table.entries.forEach { p.remove(K.cursor(it)) }; p.remove(K.lastSyncAt) }
    }
    suspend fun wipe() { store.edit { it.clear() } }
}
