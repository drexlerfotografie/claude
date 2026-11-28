package de.mediclever.schichtclever.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Hält alle App-Daten im Speicher und schreibt sie als JSON in den privaten App-Ordner.
 * Bewusst ohne Datenbank und ohne Netzwerk: alles bleibt auf dem Gerät.
 */
object Repository {
    private const val TAG = "SchichtClever"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private lateinit var file: AtomicFile
    private val listeners = mutableListOf<(AppData) -> Unit>()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            file = AtomicFile(File(context.filesDir, "schichtclever.json"))
            _data.value = runCatching {
                json.decodeFromString(AppData.serializer(), file.readFully().decodeToString())
            }.getOrElse {
                if (it !is java.io.FileNotFoundException) Log.w(TAG, "Daten konnten nicht gelesen werden", it)
                AppData()
            }
            loaded = true
            scope.launch {
                for (signal in saveSignal) persist()
            }
        }
    }

    /** Wird nach jeder Änderung aufgerufen (Fokus, Kacheln, Blaufilter synchronisieren). */
    fun addListener(listener: (AppData) -> Unit) { listeners += listener }

    fun update(block: (AppData) -> AppData) {
        _data.update(block)
        saveSignal.trySend(Unit)
        val snapshot = _data.value
        listeners.forEach { runCatching { it(snapshot) } }
    }

    private fun persist() {
        val text = json.encodeToString(AppData.serializer(), _data.value)
        val out = file.startWrite()
        try {
            out.write(text.encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "Speichern fehlgeschlagen", e)
        }
    }

    // ---------- Schichten ----------

    fun startShift(type: ShiftType, startAt: Long = System.currentTimeMillis(), unit: String = "", focus: Boolean? = null) = update { d ->
        if (d.activeShift != null) return@update d
        val shift = Shift(
            type = type,
            startAt = startAt,
            plannedEndAt = startAt + type.hours * 3_600_000L,
            unit = unit,
            focus = focus ?: d.settings.focusAutoWithShift,
        )
        d.copy(shifts = d.shifts + shift)
    }

    fun endShift(rating: Int? = null, note: String = "") = update { d ->
        val active = d.activeShift ?: return@update d
        val ended = active.copy(endAt = System.currentTimeMillis(), rating = rating, note = note)
        d.copy(
            shifts = d.shifts.map { if (it.id == active.id) ended else it },
            settings = d.settings.copy(focusManual = false, filterManualOn = false),
        )
    }

    fun setShiftFocus(on: Boolean) = update { d ->
        val active = d.activeShift ?: return@update d.copy(settings = d.settings.copy(focusManual = on))
        d.copy(
            shifts = d.shifts.map { if (it.id == active.id) it.copy(focus = on) else it },
            settings = d.settings.copy(focusManual = if (on) d.settings.focusManual else false),
        )
    }

    fun deleteShift(id: String) = update { d ->
        d.copy(shifts = d.shifts.filterNot { it.id == id }, einsaetze = d.einsaetze.map { if (it.shiftId == id) it.copy(shiftId = null) else it })
    }

    // ---------- Einsätze ----------

    fun saveEinsatz(einsatz: Einsatz) = update { d ->
        val withShift = if (einsatz.shiftId == null) einsatz.copy(shiftId = d.shiftAt(einsatz.alarmAt)?.id) else einsatz
        val exists = d.einsaetze.any { it.id == einsatz.id }
        d.copy(einsaetze = if (exists) d.einsaetze.map { if (it.id == einsatz.id) withShift else it } else d.einsaetze + withShift)
    }

    /** Schnellerfassung per Kachel: nur Zeitstempel, Details später. */
    fun quickEinsatz(): Einsatz {
        val e = Einsatz(alarmAt = System.currentTimeMillis(), quick = true)
        saveEinsatz(e)
        return e
    }

    fun deleteEinsatz(id: String) = update { d -> d.copy(einsaetze = d.einsaetze.filterNot { it.id == id }) }

    // ---------- Skills ----------

    fun addPractice(practice: Practice) = update { d -> d.copy(practices = d.practices + practice) }

    fun setGoal(skillId: String, goal: SkillGoal) = update { d -> d.copy(goals = d.goals + (skillId to goal)) }

    fun addCustomSkill(name: String, group: String) = update { d ->
        val skill = Skill(id = "custom-" + newId().take(8), name = name.trim(), group = group)
        d.copy(settings = d.settings.copy(customSkills = d.settings.customSkills + skill))
    }

    // ---------- Einstellungen ----------

    fun settings(block: (Settings) -> Settings) = update { d -> d.copy(settings = block(d.settings)) }

    fun deleteAll() = update { d -> AppData(settings = d.settings.copy(customSkills = emptyList())) }
}

fun AppData.shiftAt(time: Long): Shift? =
    shifts.firstOrNull { time >= it.startAt - 15 * 60_000 && time <= (it.endAt ?: Long.MAX_VALUE) }
