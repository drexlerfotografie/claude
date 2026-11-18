package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Diary
import java.time.LocalDate
import java.time.YearMonth

/**
 * Kalenderfilter. Leere Mengen bedeuten "kein Filter". Mehrere Auswahlen werden ueber "hinzu" als
 * Vereinigung kombiniert: ein Eintrag passt, wenn er eine der Stimmungen [moodIds] hat ODER seine Stufe in
 * [levels] liegt ODER er mindestens eine der Aktivitaeten [tagIds] enthaelt.
 */
data class CalendarFilter(
    val moodIds: Set<String> = emptySet(),
    val levels: Set<Int> = emptySet(),
    val tagIds: Set<String> = emptySet(),
) {
    /** Wahr, wenn nichts ausgewaehlt ist. */
    val isEmpty: Boolean get() = moodIds.isEmpty() && levels.isEmpty() && tagIds.isEmpty()
}

/** Art einer Filterauswahl. */
enum class FilterKind { MOOD, LEVEL, ACTIVITY }

/**
 * Zaehler einer einzelnen Filterauswahl im Monat: entryCount Eintraege und dayCount Tage, auf die genau diese
 * Auswahl zutrifft. id ist die Stimmungs- oder Aktivitaets-id bzw. die Stufe als Text, label der Anzeigename.
 */
data class FilterCounter(val kind: FilterKind, val id: String, val label: String, val entryCount: Int, val dayCount: Int)

/**
 * Ein Tag im Kalender. averageScore: mittlerer Punktwert (6 - Stufe) aller Eintraege des Tages, level die
 * daraus gerundete Stufe fuer die Farbe ([roundedLevel]); beide null ohne Eintrag mit Stimmung.
 * matchCount: Eintraege, die zum Filter passen (ohne Filter gleich entryCount).
 */
data class CalendarDay(
    val date: LocalDate,
    val entryCount: Int,
    val averageScore: Double?,
    val level: Int?,
    val matchCount: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/**
 * Monatsansicht des Kalenders. weekdays: ISO-Wochentage der sieben Spalten. weeks: Wochen mit je sieben
 * Feldern, null ausserhalb des Monats. counters: je Filterauswahl ein Zaehler (Stimmungen, Stufen, dann
 * Aktivitaeten). matchingEntryCount und matchingDayCount: Eintraege bzw. Tage, die zum gesamten Filter
 * passen. moodCounter: Stimmungszaehler der passenden Eintraege. hint: Hinweiszeile unter dem Raster.
 */
data class CalendarMonth(
    val month: YearMonth,
    val weekdays: List<Int>,
    val weeks: List<List<CalendarDay?>>,
    val filter: CalendarFilter,
    val counters: List<FilterCounter>,
    val matchingEntryCount: Int,
    val matchingDayCount: Int,
    val moodCounter: MoodCounter,
    val hint: String,
)

/**
 * Stufe 1..5 zur Einfaerbung eines durchschnittlichen Punktwerts: der Punktwert wird kaufmaennisch auf eine
 * ganze Zahl gerundet (x,5 rundet zur besseren Stimmung), Stufe = 6 - gerundeter Punktwert.
 * Beispiel: 3,5 ergibt Punktwert 4 und damit Stufe 2.
 */
fun roundedLevel(averageScore: Double): Int = (6 - Math.floor(averageScore + 0.5).toInt()).coerceIn(1, 5)

/**
 * Kalender des Monats [month] mit Wochenanfang [weekStart] (ISO 1..7), Stand [today].
 * Farbe und Durchschnitt eines Tages beziehen sich immer auf alle Eintraege des Tages; der [filter] bestimmt
 * nur matchCount und die Zaehler. Unbekannte oder geloeschte Stimmungen und Aktivitaeten im Filter erhalten
 * keinen Zaehler.
 */
fun calendarMonth(
    diary: Diary,
    month: YearMonth,
    today: LocalDate,
    weekStart: Int = diary.settings.weekStart,
    filter: CalendarFilter = CalendarFilter(),
): CalendarMonth {
    val idx = DiaryIndex.of(diary)
    val range = DateRange.ofMonth(month)
    val entries = idx.entriesIn(range)
    fun matches(e: IndexedEntry): Boolean = filter.isEmpty ||
        e.entry.moodId in filter.moodIds && e.level != null ||
        e.level != null && e.level in filter.levels ||
        e.tagIds.any { it in filter.tagIds }

    val matching = entries.filter(::matches)
    val matchPerDay = matching.groupingBy { it.date }.eachCount()
    val weeks = monthGrid(month, weekStart).map { week ->
        week.map { d ->
            d?.let {
                val list = idx.byDate[it].orEmpty()
                val avg = idx.dailyScore[it]
                CalendarDay(it, list.size, avg, avg?.let(::roundedLevel), matchPerDay[it] ?: 0, it == today, it.isAfter(today))
            }
        }
    }
    fun counter(kind: FilterKind, id: String, label: String, test: (IndexedEntry) -> Boolean): FilterCounter {
        val hits = entries.filter(test)
        return FilterCounter(kind, id, label, hits.size, hits.distinctBy { it.date }.size)
    }
    val counters = ArrayList<FilterCounter>()
    filter.moodIds.mapNotNull { idx.moods[it] }
        .sortedWith(compareBy({ it.level }, { it.sortOrder }, { it.name.lowercase() }))
        .forEach { m -> counters += counter(FilterKind.MOOD, m.id, m.name) { it.entry.moodId == m.id && it.level != null } }
    filter.levels.filter { it in 1..5 }.sorted()
        .forEach { l -> counters += counter(FilterKind.LEVEL, l.toString(), "Stufe $l") { it.level == l } }
    filter.tagIds.mapNotNull { idx.tags[it] }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
        .forEach { t -> counters += counter(FilterKind.ACTIVITY, t.id, t.name) { e -> t.id in e.tagIds } }

    val dayCount = idx.byDate.subMap(range.start, true, range.endInclusive, true).size
    val hint = when {
        entries.isEmpty() -> "In diesem Monat gibt es noch keinen Eintrag."
        filter.isEmpty -> "An $dayCount von ${range.days} Tagen gibt es einen Eintrag."
        else -> "${entriesText(matching.size)} an ${if (matchPerDay.size == 1) "1 Tag" else "${matchPerDay.size} Tagen"} " +
            "${if (matching.size == 1) "passt" else "passen"} zum Filter."
    }
    return CalendarMonth(
        month = month,
        weekdays = weekdayOrder(weekStart),
        weeks = weeks,
        filter = filter,
        counters = counters,
        matchingEntryCount = matching.size,
        matchingDayCount = matchPerDay.size,
        moodCounter = moodCounter(idx, matching),
        hint = hint,
    )
}
