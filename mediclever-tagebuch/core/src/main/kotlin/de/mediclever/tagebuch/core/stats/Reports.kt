package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.goals.GoalPeriodSummary
import de.mediclever.tagebuch.core.goals.goalPeriodSummaries
import de.mediclever.tagebuch.core.model.Diary
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Art eines Berichts. */
enum class ReportKind { WEEK, MONTH }

/** Zaehlwert mit Vorperiode; change = value - previous. */
data class CountChange(val value: Int, val previous: Int) {
    val change: Int get() = value - previous
}

/** Messwert mit Vorperiode; change = value - previous, null wenn einer der beiden fehlt. */
data class ValueChange(val value: Double?, val previous: Double?) {
    val change: Double? get() = if (value != null && previous != null) value - previous else null
}

/**
 * Wochen- oder Monatsbericht.
 *  - entries: Anzahl der Eintraege.
 *  - activities: Summe der Aktivitaeten ueber alle Eintraege (jede Aktivitaet je Eintrag einmal).
 *  - averageScore: mittlerer Punktwert (6 - Stufe, 5 = beste Stimmung) der Eintraege mit Stimmung.
 *  - words: Woerter in Titel und Notiz aller Eintraege, gezaehlt mit [wordCount] auf dem Text ohne Tags.
 *  - moodCourse: ein Punkt je Tag des Zeitraums, siehe [moodCourse].
 *  - goals: Ziele des Zeitraums, siehe [goalPeriodSummaries]; goalCompletions: Summe ihrer abgehakten Tage.
 *  - goalsAchieved: Ziele mit Erfolgsrate 1.0 im Zeitraum.
 *  - photoCount: verschiedene Fotos der Eintraege; photoAssetIds: die ersten davon in zeitlicher Reihenfolge.
 * Die Vorperiode ist die Woche bzw. der Monat direkt davor.
 */
data class Report(
    val kind: ReportKind,
    val title: String,
    val range: DateRange,
    val previousRange: DateRange,
    val entries: CountChange,
    val activities: CountChange,
    val averageScore: ValueChange,
    val words: CountChange,
    val moodCourse: List<MoodPoint>,
    val goals: List<GoalPeriodSummary>,
    val goalCompletions: CountChange,
    val goalsAchieved: Int,
    val photoCount: Int,
    val photoAssetIds: List<String>,
)

/**
 * Wochenbericht fuer die Woche, in der [dateInWeek] liegt (Wochenanfang [weekStart], ISO 1..7), verglichen
 * mit der Woche davor. Felder siehe [Report]. [photoLimit] begrenzt photoAssetIds.
 */
fun weeklyReport(
    diary: Diary,
    dateInWeek: LocalDate,
    today: LocalDate,
    weekStart: Int = diary.settings.weekStart,
    photoLimit: Int = 6,
    zone: ZoneId = ZoneId.systemDefault(),
): Report {
    val range = DateRange.ofWeek(dateInWeek, weekStart)
    val previous = DateRange(range.start.minusDays(7), range.start.minusDays(1))
    val title = "Woche vom ${germanDate(range.start)} bis ${germanDate(range.endInclusive)}"
    return report(diary, ReportKind.WEEK, title, range, previous, today, photoLimit, zone)
}

/** Monatsbericht fuer [month], verglichen mit dem Vormonat. Felder siehe [Report]. */
fun monthlyReport(
    diary: Diary,
    month: YearMonth,
    today: LocalDate,
    photoLimit: Int = 6,
    zone: ZoneId = ZoneId.systemDefault(),
): Report {
    val title = "Monatsbericht ${MONTH_NAMES[month.monthValue - 1]} ${month.year}"
    return report(
        diary, ReportKind.MONTH, title, DateRange.ofMonth(month), DateRange.ofMonth(month.minusMonths(1)),
        today, photoLimit, zone,
    )
}

private fun report(
    diary: Diary,
    kind: ReportKind,
    title: String,
    range: DateRange,
    previousRange: DateRange,
    today: LocalDate,
    photoLimit: Int,
    zone: ZoneId,
): Report {
    val idx = DiaryIndex.of(diary)
    val now = idx.entriesIn(range)
    val before = idx.entriesIn(previousRange)
    fun words(list: List<IndexedEntry>) = list.sumOf { wordCount(it.entry.title) + wordCount(it.entry.note) }
    val goals = goalPeriodSummaries(diary, range, today, zone)
    val goalsBefore = goalPeriodSummaries(diary, previousRange, today, zone)
    val photos = now.flatMap { it.photoIds }.distinct()
    return Report(
        kind = kind,
        title = title,
        range = range,
        previousRange = previousRange,
        entries = CountChange(now.size, before.size),
        activities = CountChange(now.sumOf { it.tagIds.size }, before.sumOf { it.tagIds.size }),
        averageScore = ValueChange(averageScore(now), averageScore(before)),
        words = CountChange(words(now), words(before)),
        moodCourse = moodCourse(idx, range, Granularity.DAY, null),
        goals = goals,
        goalCompletions = CountChange(goals.sumOf { it.completions }, goalsBefore.sumOf { it.completions }),
        goalsAchieved = goals.count { it.successRate == 1.0 },
        photoCount = photos.size,
        photoAssetIds = photos.take(photoLimit),
    )
}
