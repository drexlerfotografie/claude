package de.mediclever.tagebuch.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Datenmodell des Tagebuchs. Dieselben Klassen dienen als Room-Tabellen (Geraet),
 * als Firestore-Dokumente (users/{uid}/{sammlung}/{id}) und als Sicherungsformat.
 *
 * Gemeinsame Felder fuer den Abgleich:
 *  - id         zufaellige UUID, auf jedem Geraet gleich
 *  - updatedAt  Millisekunden UTC der letzten Aenderung (die juengste Aenderung gewinnt)
 *  - deleted    Loeschmarke, damit Loeschungen andere Geraete erreichen
 *  - dirty      nur lokal: noch nicht hochgeladen
 * Archivieren ist ein Zeitstempel (archivedAt), kein Loeschen.
 */
interface SyncRow {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val dirty: Boolean
}

fun newId(): String = UUID.randomUUID().toString()

/** Stimmung. level 1 ist die beste, 5 die schlechteste Stufe. Die fuenf Stufen sind fest. */
@Serializable
@Entity(tableName = "moods")
data class Mood(
    @PrimaryKey override val id: String = newId(),
    val level: Int,
    val name: String,
    /** Schluessel eines eigenen Stimmungssymbols, siehe MoodIcons in der App. */
    val icon: String,
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Aktivitaetsgruppe. Teilt sich die Reihenfolge (sortOrder) mit den Skalen. */
@Serializable
@Entity(tableName = "tag_groups")
data class TagGroup(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    val collapsed: Boolean = false,
    val archivedAt: Long? = null,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Aktivitaet. groupId null bedeutet "ohne Gruppe". colorIndex null erbt die Gruppenfarbe. */
@Serializable
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey override val id: String = newId(),
    val groupId: String? = null,
    val name: String,
    /** Schluessel aus dem Symbolkatalog, siehe ActivityIcons in der App. */
    val icon: String = "circle",
    val colorIndex: Int? = null,
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

enum class ScaleType { NUMBER, TEXT, DURATION }

/** Wie mehrere Werte eines Tages im Diagramm zusammengefasst werden. */
enum class ChartMode { DAILY_AVERAGE, DAILY_SUM }

enum class Trend { HIGHER_IS_BETTER, LOWER_IS_BETTER, NEUTRAL }

@Serializable
data class ScalePoint(
    val id: String = newId(),
    /** 1-basiert, aufsteigend. Zaehlt als Zahlenwert in Auswertungen. */
    val position: Int,
    val label: String,
)

/**
 * Skala. NUMBER: ganzer Wert zwischen min und max. TEXT: einer der benannten Punkte.
 * DURATION: Minuten zwischen min und max, Schrittweite step.
 */
@Serializable
@Entity(tableName = "scales")
data class Scale(
    @PrimaryKey override val id: String = newId(),
    val type: ScaleType,
    val name: String,
    val icon: String = "tune",
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    val chartMode: ChartMode = ChartMode.DAILY_AVERAGE,
    val trend: Trend = Trend.HIGHER_IS_BETTER,
    val min: Int = 1,
    val max: Int = 5,
    val step: Int = 1,
    val points: List<ScalePoint> = emptyList(),
    val archivedAt: Long? = null,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Genau eines von pointId (TEXT), number (NUMBER) oder minutes (DURATION) ist gesetzt. */
@Serializable
data class ScaleValue(
    val scaleId: String,
    val pointId: String? = null,
    val number: Int? = null,
    val minutes: Int? = null,
)

enum class EntrySource { APP, IMPORT, MCP }

/**
 * Tagebucheintrag. timestamp ist UTC, offsetMinutes der damalige Zeitzonen-Versatz.
 * localDate (JJJJ-MM-TT) und minuteOfDay sind die Ortszeit des Eintrags und werden
 * gespeichert, damit Monatsansichten nie Zeitzonen umrechnen muessen.
 */
@Serializable
@Entity(tableName = "entries", indices = [Index("localDate")])
data class Entry(
    @PrimaryKey override val id: String = newId(),
    val timestamp: Long,
    val offsetMinutes: Int,
    val localDate: String,
    val minuteOfDay: Int,
    val moodId: String,
    val title: String = "",
    /** Bereinigtes HTML, siehe NoteHtml.sanitize. */
    val note: String = "",
    val favorite: Boolean = false,
    val source: EntrySource = EntrySource.APP,
    val tagIds: List<String> = emptyList(),
    val scaleValues: List<ScaleValue> = emptyList(),
    /** Fotos und Sprachnotizen in Anzeige-Reihenfolge. */
    val assetIds: List<String> = emptyList(),
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

enum class AssetType { PHOTO, AUDIO }

/**
 * Foto oder Sprachnotiz. Die Datei liegt nur auf dem Geraet (files/assets/<fileName>),
 * abgeglichen werden in Version 1 nur diese Angaben.
 */
@Serializable
@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey override val id: String = newId(),
    val type: AssetType = AssetType.PHOTO,
    val fileName: String,
    val checksum: String = "",
    val takenAt: Long = 0,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

enum class GoalRepeat { DAILY, TIMES_PER_WEEK, WEEKDAYS }

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    /** Verknuepfte Aktivitaet: ein Eintrag mit dieser Aktivitaet hakt das Ziel fuer den Tag ab. */
    val tagId: String? = null,
    val icon: String = "flag",
    val colorIndex: Int = 0,
    val repeat: GoalRepeat = GoalRepeat.DAILY,
    /** Nur bei TIMES_PER_WEEK: 1 bis 7. */
    val timesPerWeek: Int = 1,
    /** Nur bei WEEKDAYS: ISO-Wochentage 1 (Montag) bis 7 (Sonntag). */
    val weekdays: List<Int> = emptyList(),
    val reminderMinuteOfDay: Int? = null,
    val reminderText: String = "",
    val startDate: String,
    val endDate: String? = null,
    val note: String = "",
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Ein abgehakter Tag eines Ziels. id ist immer "<goalId>|<date>". */
@Serializable
@Entity(tableName = "goal_checks", indices = [Index("goalId"), Index("date")])
data class GoalCheck(
    @PrimaryKey override val id: String,
    val goalId: String,
    val date: String,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow {
    companion object {
        fun idFor(goalId: String, date: String) = "$goalId|$date"
    }
}

/** Wichtiger Tag (Jahrestag, Geburtstag, Ereignis). */
@Serializable
@Entity(tableName = "important_days")
data class ImportantDay(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val date: String,
    val category: String = "",
    val note: String = "",
    val assetId: String? = null,
    val yearly: Boolean = true,
    val remindOnDay: Boolean = false,
    /** Erinnerung so viele Tage vorher, z. B. [1, 7]. */
    val remindDaysBefore: List<Int> = emptyList(),
    val sortOrder: Int = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Schreibvorlage fuer Notizen. */
@Serializable
@Entity(tableName = "templates")
data class Template(
    @PrimaryKey override val id: String = newId(),
    val title: String,
    val body: String = "",
    val sortOrder: Int = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Taegliche Erinnerung ans Eintragen. */
@Serializable
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey override val id: String = newId(),
    val minuteOfDay: Int,
    val text: String = "",
    val active: Boolean = true,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncRow

/** Verhalten bei Eintraegen zwischen 0 und 5 Uhr. */
enum class MidnightRule { ASK, PREVIOUS_DAY, CURRENT_DAY }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Einstellungen eines Nutzers. Wird als ein Dokument (settings/main) abgeglichen. */
@Serializable
data class UserSettings(
    /** ISO-Wochentag, 1 = Montag, 7 = Sonntag. */
    val weekStart: Int = 1,
    val midnightRule: MidnightRule = MidnightRule.ASK,
    val showQuote: Boolean = true,
    val showMemories: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Schluessel eines Farbschemas fuer die fuenf Stufen, "custom" nutzt customMoodColors. */
    val moodScheme: String = "klar",
    /** Fuenf Farben als ARGB-Hex ohne #, Stufe 1 zuerst. */
    val customMoodColors: List<String> = emptyList(),
    val fontChoice: String = "roboto",
    val reportNotifications: Boolean = true,
    val updatedAt: Long = 0,
)

/** Vollstaendiger Datenbestand eines Nutzers, z. B. fuer Sicherung, Import und Auswertung. */
@Serializable
data class Diary(
    val moods: List<Mood> = emptyList(),
    val groups: List<TagGroup> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val scales: List<Scale> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalChecks: List<GoalCheck> = emptyList(),
    val importantDays: List<ImportantDay> = emptyList(),
    val templates: List<Template> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val settings: UserSettings = UserSettings(),
) {
    val moodById: Map<String, Mood> by lazy { moods.associateBy { it.id } }
    val tagById: Map<String, Tag> by lazy { tags.associateBy { it.id } }
    val scaleById: Map<String, Scale> by lazy { scales.associateBy { it.id } }
    val groupById: Map<String, TagGroup> by lazy { groups.associateBy { it.id } }

    /** Stufe 1 bis 5 eines Eintrags, oder null bei unbekannter Stimmung. */
    fun levelOf(entry: Entry): Int? = moodById[entry.moodId]?.level
}
