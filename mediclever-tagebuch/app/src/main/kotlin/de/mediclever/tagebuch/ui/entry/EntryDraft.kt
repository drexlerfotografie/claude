package de.mediclever.tagebuch.ui.entry

import de.mediclever.tagebuch.core.markup.NoteMarkup
import de.mediclever.tagebuch.core.model.*
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Bearbeitungsstand eines Eintrags. Uebersteht Drehen und Prozess-Neustart als JSON. */
@Serializable
data class EntryDraft(
    val id: String = newId(),
    val isNew: Boolean = true,
    val date: String,
    val minuteOfDay: Int,
    val moodId: String? = null,
    val tagIds: List<String> = emptyList(),
    val scaleValues: List<ScaleValue> = emptyList(),
    val title: String = "",
    /** Notiz als Text mit Auszeichnungen, siehe NoteMarkup. */
    val note: String = "",
    val assetIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** Ziele, die fuer diesen Tag abgehakt sein sollen. */
    val checkedGoalIds: List<String> = emptyList(),
    val originalDate: String = "",
    val originalMinute: Int = -1,
    val originalTimestamp: Long = 0,
    val originalOffset: Int = 0,
    val source: EntrySource = EntrySource.APP,
) {
    fun valueOf(scaleId: String): ScaleValue? = scaleValues.firstOrNull { it.scaleId == scaleId }
    fun withValue(scaleId: String, v: ScaleValue?): EntryDraft =
        copy(scaleValues = scaleValues.filter { it.scaleId != scaleId } + listOfNotNull(v))
    fun toggleTag(id: String): EntryDraft = copy(tagIds = if (id in tagIds) tagIds - id else tagIds + id)

    /**
     * Fertiger Eintrag. Bei unveraendertem Datum und Uhrzeit bleibt der urspruengliche Zeitpunkt
     * erhalten. Sonst gilt bei bestehenden Eintraegen deren eigener Zeitzonen-Versatz, bei neuen
     * die Zeitzone des Geraets.
     */
    fun toEntry(zone: ZoneId): Entry {
        val ldt = LocalDateTime.of(LocalDate.parse(date), LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        val (ts, offset) = when {
            !isNew && date == originalDate && minuteOfDay == originalMinute -> originalTimestamp to originalOffset
            !isNew -> ldt.toEpochSecond(ZoneOffset.ofTotalSeconds(originalOffset * 60)) * 1000 to originalOffset
            else -> ldt.atZone(zone).let { it.toInstant().toEpochMilli() to it.offset.totalSeconds / 60 }
        }
        return Entry(
            id = id, timestamp = ts, offsetMinutes = offset, localDate = date, minuteOfDay = minuteOfDay,
            moodId = requireNotNull(moodId), title = title.trim(), note = NoteMarkup.toHtml(note),
            favorite = favorite, source = source, tagIds = tagIds, scaleValues = scaleValues, assetIds = assetIds,
        )
    }

    companion object {
        fun from(e: Entry, checkedGoals: List<String>) = EntryDraft(
            id = e.id, isNew = false, date = e.localDate, minuteOfDay = e.minuteOfDay, moodId = e.moodId,
            tagIds = e.tagIds, scaleValues = e.scaleValues, title = e.title, note = NoteMarkup.fromHtml(e.note),
            assetIds = e.assetIds, favorite = e.favorite, checkedGoalIds = checkedGoals,
            originalDate = e.localDate, originalMinute = e.minuteOfDay, originalTimestamp = e.timestamp,
            originalOffset = e.offsetMinutes, source = e.source,
        )
    }
}

/** Welcher Tag ist bei einem neuen Eintrag kurz nach Mitternacht gemeint? */
object Midnight {
    /** Bis zu dieser Stunde zaehlt die Nacht noch als Fortsetzung des Vortags. */
    const val UNTIL_HOUR = 5

    fun applies(now: LocalDateTime): Boolean = now.hour < UNTIL_HOUR

    /** Datum und Uhrzeit fuer "noch zum Vortag": der Vortag um 23:59. */
    fun previousDay(now: LocalDateTime): Pair<LocalDate, Int> = now.toLocalDate().minusDays(1) to (23 * 60 + 59)
}
