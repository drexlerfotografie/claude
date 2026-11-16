package de.mediclever.tagebuch.core.stats

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Zeitraum aus ganzen Tagen, beide Grenzen eingeschlossen. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Ende liegt vor dem Anfang" }
    }

    /** Anzahl der Tage im Zeitraum, beide Grenzen mitgezaehlt. */
    val days: Int get() = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt()

    /** Wahr, wenn [date] zwischen Anfang und Ende liegt (Grenzen eingeschlossen). */
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Alle Tage des Zeitraums in aufsteigender Reihenfolge. */
    fun dates(): List<LocalDate> = List(days) { start.plusDays(it.toLong()) }

    companion object {
        /** Erster bis letzter Tag des Monats. */
        fun ofMonth(month: YearMonth): DateRange = DateRange(month.atDay(1), month.atEndOfMonth())

        /** 1. Januar bis 31. Dezember des Jahres. */
        fun ofYear(year: Int): DateRange = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

        /** Die sieben Tage der Woche, in der [date] liegt; die Woche beginnt am ISO-Wochentag [weekStart]. */
        fun ofWeek(date: LocalDate, weekStart: Int): DateRange {
            val start = startOfWeek(date, weekStart)
            return DateRange(start, start.plusDays(6))
        }
    }
}

/** Wochenanfang als ISO-Wochentag 1 (Montag) bis 7 (Sonntag); jeder andere Wert gilt als Montag. */
fun normalizeWeekStart(weekStart: Int): Int = if (weekStart in 1..7) weekStart else 1

/** Erster Tag der Woche, in der [date] liegt, bei Wochenanfang [weekStart] (ISO 1..7). */
fun startOfWeek(date: LocalDate, weekStart: Int): LocalDate {
    val ws = normalizeWeekStart(weekStart)
    return date.minusDays(((date.dayOfWeek.value - ws + 7) % 7).toLong())
}

/** Die sieben ISO-Wochentage in Anzeige-Reihenfolge, beginnend mit [weekStart]. Beispiel 7: [7,1,2,3,4,5,6]. */
fun weekdayOrder(weekStart: Int): List<Int> {
    val ws = normalizeWeekStart(weekStart)
    return List(7) { (ws - 1 + it) % 7 + 1 }
}

/**
 * Monatsraster: Liste von Wochen mit je sieben Feldern. Felder vor dem 1. und nach dem
 * letzten Tag des Monats sind null. Die erste Spalte ist der Wochentag [weekStart].
 */
fun monthGrid(month: YearMonth, weekStart: Int): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - normalizeWeekStart(weekStart) + 7) % 7)
    val cells = ArrayList<LocalDate?>(42)
    repeat(lead) { cells.add(null) }
    for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

/** Liest ein Datum im Format JJJJ-MM-TT; null bei ungueltigem Text. */
fun parseDateOrNull(text: String?): LocalDate? =
    if (text == null) null else try { LocalDate.parse(text) } catch (e: Exception) { null }

internal val MONTH_NAMES = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember",
)
internal val WEEKDAY_NAMES = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

/** Zahl mit einer Nachkommastelle und Komma, z. B. 3,8. Gerundet wird kaufmaennisch. */
internal fun formatOneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    val sign = if (tenths < 0) "-" else ""
    val abs = Math.abs(tenths)
    return "$sign${abs / 10},${abs % 10}"
}

/** "1 Eintrag" oder "n Einträge". */
internal fun entriesText(n: Int): String = if (n == 1) "1 Eintrag" else "$n Einträge"

/** "1 Tag" oder "n Tage". */
internal fun daysText(n: Int): String = if (n == 1) "1 Tag" else "$n Tage"

internal fun germanDate(d: LocalDate): String =
    "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
