package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.ImportantDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Die Eintraege eines Tages, neuester zuerst. */
data class DayEntries(val date: LocalDate, val entries: List<Entry>)

/** Luecke ohne Eintrag: die Tage from bis to (beide ohne Eintrag), days Tage, text z. B. "3 Tage fehlen". */
data class MissingDays(val from: LocalDate, val to: LocalDate, val days: Int, val text: String)

/** Ein Element der Eintragsliste eines Monats. */
sealed interface TimelineItem {
    /** Ein Tag mit seinen Eintraegen. */
    data class Day(val day: DayEntries) : TimelineItem

    /** Hinweis "x Tage fehlen" zwischen zwei Tagen. */
    data class Gap(val gap: MissingDays) : TimelineItem
}

/**
 * Eintraege des Monats [month], gruppiert nach Tag, neuester Tag zuerst; innerhalb eines Tages der spaeteste
 * Eintrag zuerst (nach Uhrzeit, dann Zeitstempel).
 */
fun entriesByDay(diary: Diary, month: YearMonth): List<DayEntries> {
    val range = DateRange.ofMonth(month)
    return DiaryIndex.of(diary).byDate.subMap(range.start, true, range.endInclusive, true).descendingMap()
        .map { (d, list) -> DayEntries(d, list.asReversed().map { it.entry }) }
}

private fun gap(afterDay: LocalDate, beforeDay: LocalDate): MissingDays? {
    val n = ChronoUnit.DAYS.between(afterDay, beforeDay).toInt() - 1
    if (n <= 0) return null
    return MissingDays(afterDay.plusDays(1), beforeDay.minusDays(1), n, if (n == 1) "1 Tag fehlt" else "$n Tage fehlen")
}

/**
 * Luecken im Monat [month], neueste zuerst. Eine Luecke sind die Tage ohne Eintrag zwischen zwei
 * aufeinanderfolgenden Tagen mit Eintrag innerhalb des Monats. Liegt [today] im Monat und nach dem letzten
 * Tag mit Eintrag, kommen die Tage zwischen diesem und heute hinzu; heute selbst zaehlt nie als fehlend.
 * Tage vor dem ersten Eintrag des Monats und nach dem letzten (ausser bis heute) zaehlen nicht.
 */
fun missingDays(diary: Diary, month: YearMonth, today: LocalDate): List<MissingDays> =
    monthTimeline(diary, month, today).mapNotNull { (it as? TimelineItem.Gap)?.gap }

/**
 * Eintragsliste des Monats: Tage nach [entriesByDay], dazwischen die Luecken nach [missingDays],
 * alles von neu nach alt.
 */
fun monthTimeline(diary: Diary, month: YearMonth, today: LocalDate): List<TimelineItem> {
    val days = entriesByDay(diary, month)
    val out = ArrayList<TimelineItem>()
    if (days.isNotEmpty() && YearMonth.from(today) == month) {
        gap(days.first().date, today)?.let { out += TimelineItem.Gap(it) }
    }
    for ((i, day) in days.withIndex()) {
        out += TimelineItem.Day(day)
        days.getOrNull(i + 1)?.let { older -> gap(older.date, day.date)?.let { out += TimelineItem.Gap(it) } }
    }
    return out
}

// ---------------------------------------------------------------- Rueckblick

/** Rueckblick auf einen Tag eines frueheren Jahres. label: "Vor 1 Jahr" bzw. "Vor n Jahren". */
data class Memory(
    val yearsAgo: Int,
    val date: LocalDate,
    val label: String,
    val entries: List<Entry>,
    val photoAssetIds: List<String>,
)

/**
 * Rueckblick: Eintraege und Fotos vom selben Tag (Monat und Tag wie [today]) in frueheren Jahren, juengstes
 * Jahr zuerst. Nur Jahre mit mindestens einem Eintrag an dem Tag erscheinen.
 * 29. Februar: Ist heute der 29. Februar, erscheinen nur fruehere Schaltjahre. Ist heute der 28. Februar
 * eines Jahres ohne Schalttag, erscheinen zusaetzlich die Eintraege vom 29. Februar frueherer Schaltjahre
 * als eigener Rueckblick, damit sie nicht in Nicht-Schaltjahren verloren gehen.
 * Eintraege innerhalb eines Tages aufsteigend nach Uhrzeit, Fotos in Anzeige-Reihenfolge ohne Doppelte.
 */
fun memories(diary: Diary, today: LocalDate): List<Memory> {
    val idx = DiaryIndex.of(diary)
    if (idx.byDate.isEmpty()) return emptyList()
    val firstYear = idx.byDate.firstKey().year
    val alsoLeapDay = today.monthValue == 2 && today.dayOfMonth == 28 && !today.isLeapYear
    val out = ArrayList<Memory>()
    for (year in today.year - 1 downTo firstYear) {
        val candidates = ArrayList<LocalDate>(2)
        if (alsoLeapDay && java.time.Year.isLeap(year.toLong())) candidates += LocalDate.of(year, 2, 29)
        if (today.dayOfMonth <= YearMonth.of(year, today.monthValue).lengthOfMonth()) {
            candidates += LocalDate.of(year, today.monthValue, today.dayOfMonth)
        }
        for (date in candidates) {
            val list = idx.byDate[date] ?: continue
            val n = today.year - year
            out += Memory(
                yearsAgo = n,
                date = date,
                label = if (n == 1) "Vor 1 Jahr" else "Vor $n Jahren",
                entries = list.map { it.entry },
                photoAssetIds = list.flatMap { it.photoIds }.distinct(),
            )
        }
    }
    return out
}

// ---------------------------------------------------------------- Wichtige Tage

/**
 * Kennzahlen eines wichtigen Tags, Stand heute.
 *  - date: das eingetragene Datum.
 *  - daysSince: Tage seit dem Datum (0 am Tag selbst), null wenn das Datum in der Zukunft liegt.
 *  - nextDate: naechster Termin ab heute (heute zaehlt mit), null wenn es keinen mehr gibt.
 *  - daysUntil: Tage bis nextDate (0 = heute).
 *  - anniversaryNumber: der wievielte Jahrestag nextDate ist (0 = das Datum selbst).
 */
data class ImportantDayInfo(
    val day: ImportantDay,
    val date: LocalDate,
    val daysSince: Long?,
    val nextDate: LocalDate?,
    val daysUntil: Long?,
    val anniversaryNumber: Int?,
    val isToday: Boolean,
)

/** Ein wichtiger Tag, der in einen bestimmten Monat faellt. anniversaryNumber 0 = das Datum selbst. */
data class ImportantDayOccurrence(val day: ImportantDay, val date: LocalDate, val anniversaryNumber: Int)

/**
 * Jahrestag von [date] im Jahr [year]: derselbe Monat und Tag; der 29. Februar faellt in Jahren ohne
 * Schalttag auf den 28. Februar.
 */
fun anniversaryIn(date: LocalDate, year: Int): LocalDate =
    LocalDate.of(year, date.monthValue, minOf(date.dayOfMonth, YearMonth.of(year, date.monthValue).lengthOfMonth()))

/**
 * Kennzahlen fuer [day], Stand [today]; null bei ungueltigem Datum.
 * Naechster Termin: liegt das Datum heute oder in der Zukunft, ist es das Datum selbst. Sonst bei jaehrlicher
 * Wiederholung der naechste Jahrestag ab heute nach [anniversaryIn]; ohne Wiederholung gibt es keinen.
 */
fun importantDayInfo(day: ImportantDay, today: LocalDate): ImportantDayInfo? {
    val date = parseDateOrNull(day.date) ?: return null
    val next: LocalDate? = when {
        !date.isBefore(today) -> date
        !day.yearly -> null
        else -> anniversaryIn(date, today.year).let { if (it.isBefore(today)) anniversaryIn(date, today.year + 1) else it }
    }
    return ImportantDayInfo(
        day = day,
        date = date,
        daysSince = if (date.isAfter(today)) null else ChronoUnit.DAYS.between(date, today),
        nextDate = next,
        daysUntil = next?.let { ChronoUnit.DAYS.between(today, it) },
        anniversaryNumber = next?.let { it.year - date.year },
        isToday = next == today,
    )
}

/**
 * Alle nicht geloeschten wichtigen Tage mit Kennzahlen, Stand [today]: zuerst die mit naechstem Termin,
 * aufsteigend nach daysUntil; danach die ohne Termin. Bei Gleichstand nach sortOrder, Name.
 */
fun importantDayInfos(diary: Diary, today: LocalDate): List<ImportantDayInfo> =
    diary.importantDays.filter { !it.deleted }.mapNotNull { importantDayInfo(it, today) }
        .sortedWith(
            compareBy<ImportantDayInfo> { it.daysUntil ?: Long.MAX_VALUE }
                .thenBy { it.day.sortOrder }.thenBy { it.day.name.lowercase() }.thenBy { it.day.id },
        )

/**
 * Wichtige Tage, die in den Monat [month] fallen, aufsteigend nach Datum (dann sortOrder, Name).
 * Ein Tag ohne Wiederholung erscheint nur im Monat seines Datums. Ein jaehrlicher Tag erscheint im Monat
 * seines Datums und in jedem spaeteren Jahr im Monat des Jahrestags nach [anniversaryIn], nie vor dem Datum.
 */
fun importantDaysInMonth(diary: Diary, month: YearMonth): List<ImportantDayOccurrence> =
    diary.importantDays.filter { !it.deleted }.mapNotNull { day ->
        val date = parseDateOrNull(day.date) ?: return@mapNotNull null
        when {
            YearMonth.from(date) == month -> ImportantDayOccurrence(day, date, 0)
            day.yearly && month.year > date.year && month.monthValue == date.monthValue ->
                ImportantDayOccurrence(day, anniversaryIn(date, month.year), month.year - date.year)
            else -> null
        }
    }.sortedWith(
        compareBy<ImportantDayOccurrence> { it.date }
            .thenBy { it.day.sortOrder }.thenBy { it.day.name.lowercase() }.thenBy { it.day.id },
    )
