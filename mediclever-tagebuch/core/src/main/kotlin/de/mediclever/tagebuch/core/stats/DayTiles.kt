package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Diary
import java.time.LocalDate

/**
 * Stufen (1 beste bis 5 schlechteste) aller Eintraege je Tag, in der Reihenfolge des Tages.
 * Ein Eintrag faerbt die Kachel einfarbig, zwei teilen sie, ab drei wird es ein Verlauf.
 */
fun dayLevels(d: Diary, range: DateRange): Map<LocalDate, List<Int>> {
    val from = range.start.toString(); val to = range.endInclusive.toString()
    return d.entries.asSequence()
        .filter { !it.deleted && it.localDate >= from && it.localDate <= to }
        .sortedWith(compareBy({ it.localDate }, { it.minuteOfDay }))
        .mapNotNull { e -> d.levelOf(e)?.let { LocalDate.parse(e.localDate) to it } }
        .groupBy({ it.first }, { it.second })
}

/** Die sieben Tage der Woche, in der day liegt. weekStart ist der ISO-Wochentag (1 = Montag). */
fun weekOf(day: LocalDate, weekStart: Int): List<LocalDate> {
    val back = Math.floorMod(day.dayOfWeek.value - weekStart, 7)
    val first = day.minusDays(back.toLong())
    return (0L..6L).map { first.plusDays(it) }
}
