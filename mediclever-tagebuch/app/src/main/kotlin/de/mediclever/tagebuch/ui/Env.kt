package de.mediclever.tagebuch.ui

import androidx.compose.runtime.compositionLocalOf
import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.data.DiaryRepository
import de.mediclever.tagebuch.data.LocalState
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Alles, was Bildschirme zum Anzeigen und Aendern brauchen. In Tests und Vorschauen wird
 * eine Attrappe mit festen Daten und festem Datum eingesetzt.
 */
class UiEnv(
    val diary: Diary,
    val local: LocalState = LocalState(),
    val now: () -> LocalDateTime = { LocalDateTime.now() },
    /** Fuehrt eine Aenderung am Bestand aus. */
    val act: (suspend DiaryRepository.() -> Unit) -> Unit = {},
    val assetFile: (Asset) -> File? = { null },
) {
    val today: LocalDate get() = now().toLocalDate()
    fun asset(id: String): Asset? = diary.assets.firstOrNull { it.id == id }
}

val LocalEnv = compositionLocalOf { UiEnv(Diary()) }

private val DE: Locale = Locale.GERMANY
private val dayLong = DateTimeFormatter.ofPattern("EEEE, d. MMMM", DE)
private val dayLongYear = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", DE)
private val dayShort = DateTimeFormatter.ofPattern("d. MMM yyyy", DE)
private val dayNumeric = DateTimeFormatter.ofPattern("dd.MM.yyyy", DE)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", DE)

fun LocalDate.iso(): String = toString()
fun String.toDate(): LocalDate = LocalDate.parse(this)

fun fmtDay(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Heute, " + d.format(DateTimeFormatter.ofPattern("d. MMMM", DE))
    today.minusDays(1) -> "Gestern, " + d.format(DateTimeFormatter.ofPattern("d. MMMM", DE))
    else -> if (d.year == today.year) d.format(dayLong) else d.format(dayLongYear)
}
fun fmtDayShort(d: LocalDate): String = d.format(dayShort)
fun fmtDayNumeric(d: LocalDate): String = d.format(dayNumeric)
fun fmtMonth(m: YearMonth): String = m.format(monthYear)
fun fmtTime(minuteOfDay: Int): String = "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
fun fmtDuration(minutes: Int): String = "%d:%02d Std.".format(minutes / 60, minutes % 60)
fun weekdayShort(iso: Int): String = java.time.DayOfWeek.of(iso).getDisplayName(TextStyle.SHORT, DE).removeSuffix(".")
fun weekdayLong(iso: Int): String = java.time.DayOfWeek.of(iso).getDisplayName(TextStyle.FULL, DE)
fun plural(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"
