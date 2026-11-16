package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.goals.GoalPeriodSummary
import de.mediclever.tagebuch.core.goals.goalPeriodSummaries
import de.mediclever.tagebuch.core.model.ChartMode
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import de.mediclever.tagebuch.core.model.Trend
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TreeMap

/*
 * Punktwert ("score"): alle Durchschnitte und Diagramme rechnen mit 6 - Stufe.
 * Stufe 1 (beste Stimmung) hat den Punktwert 5, Stufe 5 den Punktwert 1.
 */

/** Ein Tag mit durchschnittlichem Punktwert ab diesem Wert gilt als guter Tag. */
const val GOOD_DAY_MIN_SCORE: Double = 4.0

/** So viele Eintraege mit bekannter Stimmung braucht eine Aktivitaet fuer den Einfluss auf die Stimmung. */
const val INFLUENCE_MIN_ENTRIES: Int = 3

/** Mindestzahl an Tagen mit Eintrag, ab der die Statistik eines Zeitraums insgesamt als aussagekraeftig gilt. */
const val ENOUGH_DATA_MIN_DAYS: Int = 3

/** Mindestzahl an Eintraegen mit Stimmung fuer die Auswertung je Wochentag. */
const val ENOUGH_DATA_WEEKDAY_MIN_ENTRIES: Int = 7

/** Mindestzahl an Eintraegen mit mindestens einer Aktivitaet fuer "Oft zusammen". */
const val ENOUGH_DATA_TOGETHER_MIN_ENTRIES: Int = 5

/** So viele Aktivitaeten zeigt "Oft zusammen" je Stufe in [PeriodStats.oftenTogether]. */
const val OFTEN_TOGETHER_LIMIT: Int = 5

/** Ein Punkt je Tag (Monatsansicht) oder je Monat (Jahresansicht, date = Monatserster). */
enum class Granularity { DAY, MONTH }

/** Ein Tag der Punktreihe "letzte fuenf Tage". */
data class DayFlag(val date: LocalDate, val hasEntry: Boolean)

/**
 * Tage in Folge. current: aktuelle Reihe, longest: laengste Reihe ueberhaupt,
 * lastFiveDays: die fuenf Tage bis einschliesslich heute, aeltester zuerst.
 */
data class DayStreak(val current: Int, val longest: Int, val lastFiveDays: List<DayFlag>)

/**
 * Ein Punkt des Stimmungsverlaufs. averageScore ist der mittlere Punktwert (6 - Stufe, 5 = beste Stimmung)
 * der Eintraege des Tages bzw. Monats, null ohne Eintrag mit Stimmung. overlay ist wahr, wenn die
 * ueberlagerte Aktivitaet an dem Tag bzw. in dem Monat in mindestens einem Eintrag vorkommt.
 */
data class MoodPoint(val date: LocalDate, val averageScore: Double?, val entryCount: Int, val overlay: Boolean)

/** Anzahl der Eintraege einer Stufe (1 = beste) und ihr Anteil 0.0 bis 1.0 an allen Eintraegen mit Stimmung. */
data class LevelCount(val level: Int, val count: Int, val share: Double)

/** Anzahl der Eintraege einer einzelnen Stimmung und ihr Anteil an allen Eintraegen mit Stimmung. */
data class MoodCount(val mood: Mood, val count: Int, val share: Double)

/**
 * Stimmungszaehler. total: Eintraege mit bekannter Stimmung. byLevel: immer fuenf Werte, Stufe 1 zuerst.
 * byMood: nur Stimmungen mit mindestens einem Eintrag, sortiert nach Stufe, sortOrder, Name.
 */
data class MoodCounter(val total: Int, val byLevel: List<LevelCount>, val byMood: List<MoodCount>)

/** Eine Aktivitaet mit Anzahl der Eintraege und einem Anteil 0.0 bis 1.0 (Bezug steht an der jeweiligen Liste). */
data class ActivityCount(val tag: Tag, val count: Int, val share: Double)

/**
 * Aktivitaetenzaehler. items: absteigend nach Anzahl, bei Gleichstand nach Name. total: Summe aller Anzahlen.
 * share eines Eintrags der Liste = Anzahl / total.
 */
data class ActivityCounter(val total: Int, val items: List<ActivityCount>)

/** Ein Ringsegment der Verteilung nach Gruppe. group ist null fuer "Ohne Gruppe". share = count / Summe. */
data class GroupSlice(val group: TagGroup?, val name: String, val colorIndex: Int?, val count: Int, val share: Double)

/** Durchschnittlicher Punktwert eines ISO-Wochentags (1 = Montag), null ohne Eintrag mit Stimmung. */
data class WeekdayMood(val isoDay: Int, val averageScore: Double?, val entryCount: Int)

/**
 * Einfluss einer Aktivitaet: delta = averageWith - averageWithout (mittlere Punktwerte der Eintraege mit
 * bzw. ohne die Aktivitaet). entryCount: Eintraege mit der Aktivitaet und bekannter Stimmung.
 */
data class ActivityInfluence(
    val tag: Tag,
    val entryCount: Int,
    val averageWith: Double,
    val averageWithout: Double,
    val delta: Double,
)

/** "Oft zusammen" fuer eine Stufe: entryCount Eintraege dieser Stufe, items mit share = Anzahl / entryCount. */
data class LevelActivities(val level: Int, val entryCount: Int, val items: List<ActivityCount>)

/**
 * Laengste Serie guter Tage. activities: Aktivitaeten der Eintraege dieser Tage, absteigend nach Anzahl,
 * share = Anzahl / Eintraege dieser Tage.
 */
data class GoodDayStreak(val length: Int, val start: LocalDate, val end: LocalDate, val activities: List<ActivityCount>)

/** Ein Punkt eines Skalen-Diagramms: Tag (Monatsansicht) oder Monatserster (Jahresansicht). */
data class ValuePoint(val date: LocalDate, val value: Double?)

/** Bewertung einer Veraenderung nach der Trendrichtung der Skala. */
enum class ChangeRating { GOOD, BAD, NEUTRAL }

/**
 * Auswertung einer Skala. series nach [Granularity]; average: Mittel der Tageswerte des Zeitraums;
 * previousAverage: dasselbe fuer den Vorzeitraum; change = average - previousAverage (null, wenn eines fehlt);
 * dayCount: Tage mit Wert im Zeitraum.
 */
data class ScaleStats(
    val scale: Scale,
    val series: List<ValuePoint>,
    val average: Double?,
    val previousAverage: Double?,
    val change: Double?,
    val changeRating: ChangeRating,
    val dayCount: Int,
)

/**
 * Welche Auswertungen genug Daten haben. Ist ein Wert falsch, zeigt die Oberflaeche "zu wenig Daten".
 *  - any: mindestens ein Eintrag im Zeitraum
 *  - overall: mindestens [ENOUGH_DATA_MIN_DAYS] Tage mit Eintrag
 *  - moodCourse: mindestens 2 Tage mit Stimmung
 *  - weekdayMood: mindestens [ENOUGH_DATA_WEEKDAY_MIN_ENTRIES] Eintraege mit Stimmung
 *  - activityInfluence: mindestens eine Aktivitaet erfuellt die Bedingungen von [activityInfluence]
 *  - stability: mindestens 2 Tage mit Stimmung
 *  - oftenTogether: mindestens [ENOUGH_DATA_TOGETHER_MIN_ENTRIES] Eintraege mit Aktivitaet
 */
data class EnoughData(
    val any: Boolean,
    val overall: Boolean,
    val moodCourse: Boolean,
    val weekdayMood: Boolean,
    val activityInfluence: Boolean,
    val stability: Boolean,
    val oftenTogether: Boolean,
)

/**
 * Alle Auswertungen der Seite "Statistiken" fuer einen Zeitraum. Die Definition jedes Feldes steht an der
 * gleichnamigen Funktion dieser Datei. previous* beziehen sich auf den Vormonat bzw. das Vorjahr.
 * averageScore: mittlerer Punktwert (6 - Stufe) aller Eintraege mit Stimmung im Zeitraum.
 * dayCount: Tage mit mindestens einem Eintrag.
 */
data class PeriodStats(
    val range: DateRange,
    val previousRange: DateRange,
    val entryCount: Int,
    val dayCount: Int,
    val averageScore: Double?,
    val previousEntryCount: Int,
    val previousAverageScore: Double?,
    val streak: DayStreak,
    val moodCourse: List<MoodPoint>,
    val overlayTag: Tag?,
    val moodCounter: MoodCounter,
    val activityCounter: ActivityCounter,
    val groupDistribution: List<GroupSlice>,
    val weekdayMood: List<WeekdayMood>,
    val activityInfluence: List<ActivityInfluence>,
    val stability: Int?,
    val oftenTogether: List<LevelActivities>,
    val goodDays: GoodDayStreak?,
    val scales: List<ScaleStats>,
    val goals: List<GoalPeriodSummary>,
    val achievements: AchievementSummary,
    val insights: List<String>,
    val enoughData: EnoughData,
)

/** Statistik eines Monats; der Stimmungsverlauf und die Skalen haben einen Punkt je Tag. */
data class MonthStats(val month: YearMonth, val stats: PeriodStats)

/** Statistik eines Jahres; der Stimmungsverlauf und die Skalen haben einen Punkt je Monat. */
data class YearStats(val year: Int, val stats: PeriodStats)

internal val TAG_ORDER: Comparator<ActivityCount> =
    compareByDescending<ActivityCount> { it.count }.thenBy { it.tag.name.lowercase() }.thenBy { it.tag.id }

/** Zaehlt je Aktivitaet die Eintraege, die sie enthalten; share = Anzahl / [shareBase] (0.0 bei Basis 0). */
internal fun rankTags(idx: DiaryIndex, entries: Collection<IndexedEntry>, shareBase: Int?, skip: String? = null): List<ActivityCount> {
    val counts = HashMap<String, Int>()
    for (e in entries) for (t in e.tagIds) if (t != skip) counts[t] = (counts[t] ?: 0) + 1
    val base = shareBase ?: counts.values.sum()
    return counts.map { (id, n) -> ActivityCount(idx.tags.getValue(id), n, if (base == 0) 0.0 else n.toDouble() / base) }
        .sortedWith(TAG_ORDER)
}

// ---------------------------------------------------------------- Tage in Folge

/**
 * Tage in Folge, Stand [today]. Es zaehlen Tage mit mindestens einem nicht geloeschten Eintrag bis
 * einschliesslich heute.
 *  - current: Laenge der Reihe aufeinanderfolgender Tage mit Eintrag, die heute endet; hat heute noch keinen
 *    Eintrag, die Reihe, die gestern endet; sonst 0.
 *  - longest: laengste solche Reihe ueberhaupt.
 *  - lastFiveDays: heute und die vier Tage davor, aeltester zuerst, je mit "hat Eintrag".
 */
fun dayStreak(diary: Diary, today: LocalDate): DayStreak = dayStreak(DiaryIndex.of(diary), today)

internal fun dayStreak(idx: DiaryIndex, today: LocalDate): DayStreak {
    val days = idx.byDate.headMap(today, true).keys
    var cursor = if (today in days) today else today.minusDays(1)
    var current = 0
    while (cursor in days) {
        current++
        cursor = cursor.minusDays(1)
    }
    val flags = (4 downTo 0).map { today.minusDays(it.toLong()) }.map { DayFlag(it, it in days) }
    return DayStreak(current, longestRun(days), flags)
}

// ---------------------------------------------------------------- Stimmungsverlauf

/**
 * Stimmungsverlauf im [range]. [Granularity.DAY]: ein Punkt je Tag mit dem mittleren Punktwert (6 - Stufe)
 * der Eintraege des Tages. [Granularity.MONTH]: ein Punkt je Kalendermonat (date = Monatserster) mit dem
 * mittleren Punktwert aller Eintraege des Monats, soweit sie im [range] liegen.
 * averageScore ist null ohne Eintrag mit bekannter Stimmung. overlay ist wahr, wenn [overlayTagId] an dem Tag
 * bzw. in dem Monat in mindestens einem Eintrag vorkommt.
 */
fun moodCourse(
    diary: Diary,
    range: DateRange,
    granularity: Granularity = Granularity.DAY,
    overlayTagId: String? = null,
): List<MoodPoint> = moodCourse(DiaryIndex.of(diary), range, granularity, overlayTagId)

internal fun moodCourse(idx: DiaryIndex, range: DateRange, granularity: Granularity, overlayTagId: String?): List<MoodPoint> {
    fun point(date: LocalDate, list: List<IndexedEntry>) = MoodPoint(
        date, averageScore(list), list.size,
        overlayTagId != null && list.any { overlayTagId in it.tagIds },
    )
    return when (granularity) {
        Granularity.DAY -> range.dates().map { d -> point(d, idx.byDate[d].orEmpty()) }
        Granularity.MONTH -> monthsOf(range).map { m ->
            point(m.atDay(1), idx.entriesIn(clip(DateRange.ofMonth(m), range)))
        }
    }
}

private fun monthsOf(range: DateRange): List<YearMonth> {
    val out = ArrayList<YearMonth>()
    var m = YearMonth.from(range.start)
    val last = YearMonth.from(range.endInclusive)
    while (!m.isAfter(last)) {
        out.add(m)
        m = m.plusMonths(1)
    }
    return out
}

private fun clip(a: DateRange, b: DateRange): DateRange = DateRange(
    if (a.start.isBefore(b.start)) b.start else a.start,
    if (a.endInclusive.isAfter(b.endInclusive)) b.endInclusive else a.endInclusive,
)

/** Tage im [range], an denen die Aktivitaet [tagId] in mindestens einem Eintrag vorkommt (fuer die Ueberlagerung). */
fun activityDays(diary: Diary, range: DateRange, tagId: String): Set<LocalDate> =
    DiaryIndex.of(diary).datesByTag[tagId]?.subSet(range.start, true, range.endInclusive, true)?.toSet().orEmpty()

// ---------------------------------------------------------------- Zaehler

/**
 * Stimmungszaehler im [range]: Eintraege je Stufe 1..5 und je einzelner Stimmung. Eintraege ohne bekannte
 * Stimmung zaehlen nicht. Anteile beziehen sich auf total.
 */
fun moodCounter(diary: Diary, range: DateRange): MoodCounter {
    val idx = DiaryIndex.of(diary)
    return moodCounter(idx, idx.entriesIn(range))
}

internal fun moodCounter(idx: DiaryIndex, entries: Collection<IndexedEntry>): MoodCounter {
    val levels = IntArray(6)
    val perMood = HashMap<String, Int>()
    var total = 0
    for (e in entries) {
        val l = e.level ?: continue
        levels[l]++
        total++
        perMood[e.entry.moodId] = (perMood[e.entry.moodId] ?: 0) + 1
    }
    fun share(n: Int) = if (total == 0) 0.0 else n.toDouble() / total
    return MoodCounter(
        total = total,
        byLevel = (1..5).map { LevelCount(it, levels[it], share(levels[it])) },
        byMood = perMood.map { (id, n) -> MoodCount(idx.moods.getValue(id), n, share(n)) }
            .sortedWith(compareBy<MoodCount> { it.mood.level }.thenBy { it.mood.sortOrder }
                .thenBy { it.mood.name.lowercase() }.thenBy { it.mood.id }),
    )
}

/**
 * Aktivitaetenzaehler im [range]: je Aktivitaet die Anzahl der Eintraege, die sie enthalten, absteigend nach
 * Anzahl, bei Gleichstand nach Name (ohne Gross-/Kleinschreibung). total ist die Summe aller Anzahlen,
 * share = Anzahl / total. Archivierte Aktivitaeten zaehlen mit, geloeschte nicht.
 */
fun activityCounter(diary: Diary, range: DateRange): ActivityCounter {
    val idx = DiaryIndex.of(diary)
    return activityCounter(idx, idx.entriesIn(range))
}

internal fun activityCounter(idx: DiaryIndex, entries: Collection<IndexedEntry>): ActivityCounter {
    val items = rankTags(idx, entries, null)
    return ActivityCounter(items.sumOf { it.count }, items)
}

/**
 * Verteilung nach Gruppe im [range]: je Gruppe die Summe der Anzahlen ihrer Aktivitaeten aus
 * [activityCounter]. Aktivitaeten ohne Gruppe oder mit fehlender oder geloeschter Gruppe bilden das Segment
 * "Ohne Gruppe" (group = null, colorIndex = null), das am Ende steht. Uebrige Segmente nach sortOrder, Name.
 * Segmente mit Anzahl 0 fehlen. share = Anzahl / Summe aller Segmente.
 */
fun groupDistribution(diary: Diary, range: DateRange): List<GroupSlice> {
    val idx = DiaryIndex.of(diary)
    return groupDistribution(idx, activityCounter(idx, idx.entriesIn(range)))
}

internal fun groupDistribution(idx: DiaryIndex, counter: ActivityCounter): List<GroupSlice> {
    val perGroup = HashMap<String?, Int>()
    for (item in counter.items) {
        val gid = item.tag.groupId?.takeIf { it in idx.groups }
        perGroup[gid] = (perGroup[gid] ?: 0) + item.count
    }
    val total = counter.total
    val slices = perGroup.filterKeys { it != null }.map { (gid, n) ->
        val g = idx.groups.getValue(gid!!)
        GroupSlice(g, g.name, g.colorIndex, n, n.toDouble() / total)
    }.sortedWith(compareBy<GroupSlice> { it.group!!.sortOrder }.thenBy { it.name.lowercase() }.thenBy { it.group!!.id })
    val none = perGroup[null]?.let { GroupSlice(null, "Ohne Gruppe", null, it, it.toDouble() / total) }
    return if (none == null) slices else slices + none
}

/**
 * Durchschnittliche Stimmung je Wochentag im [range]: immer sieben Werte in ISO-Reihenfolge Montag (1) bis
 * Sonntag (7). averageScore = mittlerer Punktwert (6 - Stufe) aller Eintraege mit Stimmung an diesem
 * Wochentag, null ohne solche Eintraege; entryCount zaehlt diese Eintraege.
 */
fun weekdayMood(diary: Diary, range: DateRange): List<WeekdayMood> = weekdayMood(DiaryIndex.of(diary).entriesIn(range))

internal fun weekdayMood(entries: Collection<IndexedEntry>): List<WeekdayMood> {
    val sum = IntArray(8)
    val n = IntArray(8)
    for (e in entries) {
        val s = e.score ?: continue
        val d = e.date.dayOfWeek.value
        sum[d] += s
        n[d]++
    }
    return (1..7).map { WeekdayMood(it, if (n[it] == 0) null else sum[it].toDouble() / n[it], n[it]) }
}

// ---------------------------------------------------------------- Einfluss, Stabilitaet

/**
 * Einfluss der Aktivitaeten auf die Stimmung im [range]. Betrachtet werden nur Eintraege mit bekannter
 * Stimmung. Fuer jede Aktivitaet mit mindestens [minEntries] solchen Eintraegen, sofern es auch mindestens
 * einen Eintrag ohne sie gibt: delta = mittlerer Punktwert der Eintraege mit der Aktivitaet minus mittlerer
 * Punktwert der Eintraege ohne sie. Sortiert absteigend nach delta, bei Gleichstand nach Name.
 */
fun activityInfluence(diary: Diary, range: DateRange, minEntries: Int = INFLUENCE_MIN_ENTRIES): List<ActivityInfluence> {
    val idx = DiaryIndex.of(diary)
    return activityInfluence(idx, idx.entriesIn(range), minEntries)
}

internal fun activityInfluence(idx: DiaryIndex, entries: Collection<IndexedEntry>, minEntries: Int): List<ActivityInfluence> {
    var total = 0
    var totalSum = 0
    val n = HashMap<String, Int>()
    val sum = HashMap<String, Int>()
    for (e in entries) {
        val s = e.score ?: continue
        total++
        totalSum += s
        for (t in e.tagIds) {
            n[t] = (n[t] ?: 0) + 1
            sum[t] = (sum[t] ?: 0) + s
        }
    }
    return n.mapNotNull { (id, with) ->
        val without = total - with
        if (with < minEntries || without == 0) return@mapNotNull null
        val avgWith = sum.getValue(id).toDouble() / with
        val avgWithout = (totalSum - sum.getValue(id)).toDouble() / without
        ActivityInfluence(idx.tags.getValue(id), with, avgWith, avgWithout, avgWith - avgWithout)
    }.sortedWith(compareByDescending<ActivityInfluence> { it.delta }.thenBy { it.tag.name.lowercase() }.thenBy { it.tag.id })
}

/**
 * Stimmungsstabilitaet 0 bis 100 im [range], null bei weniger als 2 Tagen mit Stimmung.
 *
 * Formel: Die Tage mit Stimmung werden in zeitlicher Reihenfolge betrachtet (Tage ohne Eintrag werden
 * uebersprungen). m ist der Mittelwert der Betraege der Unterschiede zwischen den durchschnittlichen
 * Punktwerten aufeinanderfolgender solcher Tage. Der groesste moegliche Unterschied ist 4 (von 1 auf 5).
 * Stabilitaet = gerundet(100 * (1 - m / 4)). 100 bedeutet keinerlei Veraenderung, 0 bedeutet an jedem Tag
 * den groesstmoeglichen Sprung.
 */
fun moodStability(diary: Diary, range: DateRange): Int? = moodStability(DiaryIndex.of(diary), range)

internal fun moodStability(idx: DiaryIndex, range: DateRange): Int? {
    val values = idx.dailyScore.subMap(range.start, true, range.endInclusive, true).values
    if (values.size < 2) return null
    var sum = 0.0
    var prev: Double? = null
    for (v in values) {
        if (prev != null) sum += Math.abs(v - prev)
        prev = v
    }
    val m = sum / (values.size - 1)
    return Math.round(100 * (1 - m / 4.0)).toInt()
}

// ---------------------------------------------------------------- Oft zusammen

/**
 * "Oft zusammen" fuer eine Stufe: die Aktivitaeten der Eintraege mit Stufe [level] im [range], absteigend
 * nach Anzahl (Gleichstand nach Name), hoechstens [limit]. share = Anzahl / Eintraege dieser Stufe.
 */
fun frequentActivitiesForLevel(diary: Diary, range: DateRange, level: Int, limit: Int = Int.MAX_VALUE): LevelActivities {
    val idx = DiaryIndex.of(diary)
    return levelActivities(idx, idx.entriesIn(range), level, limit)
}

internal fun levelActivities(idx: DiaryIndex, entries: Collection<IndexedEntry>, level: Int, limit: Int): LevelActivities {
    val selected = entries.filter { it.level == level }
    return LevelActivities(level, selected.size, rankTags(idx, selected, selected.size).take(limit))
}

/**
 * "Oft zusammen" fuer eine einzelne Stimmung: die Aktivitaeten der Eintraege mit Stimmung [moodId] im
 * [range], absteigend nach Anzahl (Gleichstand nach Name), hoechstens [limit].
 * share = Anzahl / Eintraege mit dieser Stimmung.
 */
fun frequentActivitiesForMood(diary: Diary, range: DateRange, moodId: String, limit: Int = Int.MAX_VALUE): List<ActivityCount> {
    val idx = DiaryIndex.of(diary)
    val selected = idx.entriesIn(range).filter { it.entry.moodId == moodId }
    return rankTags(idx, selected, selected.size).take(limit)
}

/**
 * Aktivitaeten, die im [range] am haeufigsten im selben Eintrag wie [tagId] vorkommen, absteigend nach
 * Anzahl (Gleichstand nach Name), hoechstens [limit]. share = Anzahl / Eintraege mit [tagId].
 */
fun coOccurringActivities(diary: Diary, range: DateRange, tagId: String, limit: Int = Int.MAX_VALUE): List<ActivityCount> {
    val idx = DiaryIndex.of(diary)
    val selected = idx.entriesIn(range).filter { tagId in it.tagIds }
    return rankTags(idx, selected, selected.size, skip = tagId).take(limit)
}

// ---------------------------------------------------------------- Gute Tage

/**
 * Laengste Serie guter Tage im [range], null wenn es keinen guten Tag gibt. Ein guter Tag hat einen
 * durchschnittlichen Punktwert (6 - Stufe) von mindestens [GOOD_DAY_MIN_SCORE]. Die Serie besteht aus
 * aufeinanderfolgenden Kalendertagen; ein Tag ohne Eintrag oder mit niedrigerem Wert beendet sie.
 * Bei gleich langen Serien gilt die spaeteste. activities: Aktivitaeten aller Eintraege dieser Tage.
 */
fun longestGoodDayStreak(diary: Diary, range: DateRange): GoodDayStreak? = longestGoodDayStreak(DiaryIndex.of(diary), range)

internal fun longestGoodDayStreak(idx: DiaryIndex, range: DateRange): GoodDayStreak? {
    var bestLen = 0
    var bestEnd: LocalDate? = null
    var run = 0
    var prev: LocalDate? = null
    for ((d, v) in idx.dailyScore.subMap(range.start, true, range.endInclusive, true)) {
        run = if (v < GOOD_DAY_MIN_SCORE) 0 else if (prev != null && prev.plusDays(1) == d && run > 0) run + 1 else 1
        if (run > 0 && run >= bestLen) {
            bestLen = run
            bestEnd = d
        }
        prev = d
    }
    val end = bestEnd ?: return null
    val start = end.minusDays((bestLen - 1).toLong())
    val entries = idx.entriesIn(DateRange(start, end))
    return GoodDayStreak(bestLen, start, end, rankTags(idx, entries, entries.size))
}

// ---------------------------------------------------------------- Skalen

/**
 * Zahlenwert eines Skalenwerts: TEXT = position des gewaehlten Punkts, NUMBER = number, DURATION = minutes.
 * null, wenn der passende Wert fehlt oder der Punkt nicht (mehr) zur Skala gehoert.
 */
fun scaleNumber(scale: Scale, value: ScaleValue): Double? = when (scale.type) {
    ScaleType.TEXT -> scale.points.firstOrNull { it.id == value.pointId }?.position?.toDouble()
    ScaleType.NUMBER -> value.number?.toDouble()
    ScaleType.DURATION -> value.minutes?.toDouble()
}

/** Tageswerte aller Skalen: je Tag der Durchschnitt oder die Summe der Werte des Tages nach chartMode. */
private fun dailyScaleValues(idx: DiaryIndex, range: DateRange): Map<String, TreeMap<LocalDate, Double>> {
    val sums = HashMap<String, TreeMap<LocalDate, DoubleArray>>()
    for ((d, list) in idx.byDate.subMap(range.start, true, range.endInclusive, true)) {
        for (e in list) for (v in e.entry.scaleValues) {
            val scale = idx.scales[v.scaleId] ?: continue
            val x = scaleNumber(scale, v) ?: continue
            val acc = sums.getOrPut(scale.id) { TreeMap() }.getOrPut(d) { DoubleArray(2) }
            acc[0] += x
            acc[1] += 1.0
        }
    }
    val out = HashMap<String, TreeMap<LocalDate, Double>>()
    for ((id, perDay) in sums) {
        val avg = idx.scales.getValue(id).chartMode == ChartMode.DAILY_AVERAGE
        val m = TreeMap<LocalDate, Double>()
        for ((d, acc) in perDay) m[d] = if (avg) acc[0] / acc[1] else acc[0]
        out[id] = m
    }
    return out
}

/**
 * Auswertung je Skala fuer [range] mit Vergleich zu [previousRange].
 *
 * Tageswert: alle Werte der Skala an einem Tag, zusammengefasst nach chartMode (DAILY_AVERAGE = Mittelwert,
 * DAILY_SUM = Summe). Zahlenwert nach [scaleNumber].
 * series: [Granularity.DAY] ein Punkt je Tag mit dem Tageswert; [Granularity.MONTH] ein Punkt je Monat mit dem
 * Mittel der Tageswerte des Monats. average: Mittel der Tageswerte ueber die Tage mit Wert im [range].
 * change = average - previousAverage. changeRating: NEUTRAL bei Trend NEUTRAL, bei change 0 oder ohne
 * Vergleichswert; sonst GOOD, wenn die Veraenderung in die gute Richtung geht (HIGHER_IS_BETTER: Anstieg,
 * LOWER_IS_BETTER: Rueckgang), sonst BAD.
 * Enthalten sind alle nicht geloeschten Skalen, die nicht archiviert sind oder im [range] Werte haben,
 * sortiert nach sortOrder, Name.
 */
fun scaleStats(
    diary: Diary,
    range: DateRange,
    previousRange: DateRange,
    granularity: Granularity = Granularity.DAY,
): List<ScaleStats> = scaleStats(DiaryIndex.of(diary), range, previousRange, granularity)

internal fun scaleStats(idx: DiaryIndex, range: DateRange, previousRange: DateRange, granularity: Granularity): List<ScaleStats> {
    if (idx.scales.isEmpty()) return emptyList()
    val now = dailyScaleValues(idx, range)
    val before = dailyScaleValues(idx, previousRange)
    fun mean(values: Collection<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size
    return idx.scales.values
        .filter { it.archivedAt == null || now.containsKey(it.id) }
        .sortedWith(compareBy<Scale> { it.sortOrder }.thenBy { it.name.lowercase() }.thenBy { it.id })
        .map { scale ->
            val daily = now[scale.id] ?: TreeMap()
            val series = when (granularity) {
                Granularity.DAY -> range.dates().map { ValuePoint(it, daily[it]) }
                Granularity.MONTH -> monthsOf(range).map { m ->
                    ValuePoint(m.atDay(1), mean(daily.subMap(m.atDay(1), true, m.atEndOfMonth(), true).values))
                }
            }
            val average = mean(daily.values)
            val previous = mean(before[scale.id]?.values.orEmpty())
            val change = if (average != null && previous != null) average - previous else null
            val rating = when {
                change == null || change == 0.0 || scale.trend == Trend.NEUTRAL -> ChangeRating.NEUTRAL
                (change > 0) == (scale.trend == Trend.HIGHER_IS_BETTER) -> ChangeRating.GOOD
                else -> ChangeRating.BAD
            }
            ScaleStats(scale, series, average, previous, change, rating, daily.size)
        }
}

// ---------------------------------------------------------------- Gesamt

/**
 * Statistik des Monats [month], Stand [today]. Vergleichszeitraum ist der Vormonat.
 * [overlayTagId] markiert im Stimmungsverlauf die Tage mit dieser Aktivitaet. [zone] dient nur dazu, den
 * Archiv-Zeitpunkt von Zielen in einen Tag umzurechnen.
 */
fun monthStats(
    diary: Diary,
    month: YearMonth,
    today: LocalDate,
    overlayTagId: String? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): MonthStats = MonthStats(
    month,
    periodStats(
        diary, DateRange.ofMonth(month), DateRange.ofMonth(month.minusMonths(1)), Granularity.DAY, today, overlayTagId, zone,
        periodLabel = "Im ${MONTH_NAMES[month.monthValue - 1]} ${month.year}", previousLabel = "im Vormonat",
    ),
)

/**
 * Statistik des Jahres [year], Stand [today]: dieselben Auswertungen wie [monthStats] ueber zwoelf Monate,
 * Stimmungsverlauf und Skalen mit einem Punkt je Monat. Vergleichszeitraum ist das Vorjahr.
 */
fun yearStats(
    diary: Diary,
    year: Int,
    today: LocalDate,
    overlayTagId: String? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): YearStats = YearStats(
    year,
    periodStats(
        diary, DateRange.ofYear(year), DateRange.ofYear(year - 1), Granularity.MONTH, today, overlayTagId, zone,
        periodLabel = "Im Jahr $year", previousLabel = "im Vorjahr",
    ),
)

/**
 * Alle Auswertungen fuer einen beliebigen Zeitraum [range] mit Vergleichszeitraum [previousRange].
 * [periodLabel] ("Im März 2026") und [previousLabel] ("im Vormonat") werden in die Hinweiszeilen eingesetzt.
 */
fun periodStats(
    diary: Diary,
    range: DateRange,
    previousRange: DateRange,
    granularity: Granularity,
    today: LocalDate,
    overlayTagId: String? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    periodLabel: String = "In diesem Zeitraum",
    previousLabel: String = "im Zeitraum davor",
): PeriodStats {
    val idx = DiaryIndex.of(diary)
    val entries = idx.entriesIn(range)
    val previous = idx.entriesIn(previousRange)
    val dayCount = idx.byDate.subMap(range.start, true, range.endInclusive, true).size
    val average = averageScore(entries)
    val previousAverage = averageScore(previous)
    val counter = activityCounter(idx, entries)
    val moods = moodCounter(idx, entries)
    val weekdays = weekdayMood(entries)
    val influence = activityInfluence(idx, entries, INFLUENCE_MIN_ENTRIES)
    val stability = moodStability(idx, range)
    val goodDays = longestGoodDayStreak(idx, range)
    val moodDays = idx.dailyScore.subMap(range.start, true, range.endInclusive, true).size
    return PeriodStats(
        range = range,
        previousRange = previousRange,
        entryCount = entries.size,
        dayCount = dayCount,
        averageScore = average,
        previousEntryCount = previous.size,
        previousAverageScore = previousAverage,
        streak = dayStreak(idx, today),
        moodCourse = moodCourse(idx, range, granularity, overlayTagId),
        overlayTag = overlayTagId?.let { idx.tags[it] },
        moodCounter = moods,
        activityCounter = counter,
        groupDistribution = groupDistribution(idx, counter),
        weekdayMood = weekdays,
        activityInfluence = influence,
        stability = stability,
        oftenTogether = (1..5).map { levelActivities(idx, entries, it, OFTEN_TOGETHER_LIMIT) },
        goodDays = goodDays,
        scales = scaleStats(idx, range, previousRange, granularity),
        goals = goalPeriodSummaries(diary, range, today, zone),
        achievements = achievementSummary(diary, range, today, zone),
        insights = insightLines(
            periodLabel, previousLabel, entries.size, dayCount, average, previous.size, previousAverage,
            weekdays, counter, goodDays,
        ),
        enoughData = EnoughData(
            any = entries.isNotEmpty(),
            overall = dayCount >= ENOUGH_DATA_MIN_DAYS,
            moodCourse = moodDays >= 2,
            weekdayMood = moods.total >= ENOUGH_DATA_WEEKDAY_MIN_ENTRIES,
            activityInfluence = influence.isNotEmpty(),
            stability = stability != null,
            oftenTogether = entries.count { it.tagIds.isNotEmpty() } >= ENOUGH_DATA_TOGETHER_MIN_ENTRIES,
        ),
    )
}

/**
 * Hinweiszeilen in Klartext. Ohne Eintrag im Zeitraum ist die Liste leer. Reihenfolge und Bedingungen:
 *  1. Anzahl der Eintraege und Tage (immer).
 *  2. Vergleich der Eintragszahl mit dem Vorzeitraum (nur wenn dieser Eintraege hat).
 *  3. Durchschnittliche Stimmung als Punktwert von 5 (nur mit Stimmung); mit Vergleich, wenn der Vorzeitraum
 *     einen Durchschnitt hat. Unterschiede unter 0,05 gelten als gleich.
 *  4. Bester Wochentag (nur wenn mindestens zwei Wochentage Werte haben; bei Gleichstand der fruehere).
 *  5. Haeufigste Aktivitaet (nur wenn es eine gibt).
 *  6. Laengste Serie guter Tage (nur ab 2 Tagen).
 */
internal fun insightLines(
    periodLabel: String,
    previousLabel: String,
    entryCount: Int,
    dayCount: Int,
    average: Double?,
    previousEntryCount: Int,
    previousAverage: Double?,
    weekdays: List<WeekdayMood>,
    activities: ActivityCounter,
    goodDays: GoodDayStreak?,
): List<String> {
    if (entryCount == 0) return emptyList()
    val lines = ArrayList<String>()
    val daysPart = if (dayCount == 1) "1 Tag" else "$dayCount Tagen"
    lines += "$periodLabel hast Du ${entriesText(entryCount)} an $daysPart geschrieben."
    if (previousEntryCount > 0) {
        val diff = entryCount - previousEntryCount
        val n = Math.abs(diff)
        lines += when {
            diff == 0 -> "Das sind genauso viele Einträge wie $previousLabel."
            n == 1 -> "Das ist 1 Eintrag ${if (diff > 0) "mehr" else "weniger"} als $previousLabel."
            else -> "Das sind $n Einträge ${if (diff > 0) "mehr" else "weniger"} als $previousLabel."
        }
    }
    if (average != null) {
        val base = "Deine durchschnittliche Stimmung lag bei ${formatOneDecimal(average)} von 5"
        lines += if (previousAverage == null) "$base." else {
            val diff = average - previousAverage
            when {
                Math.abs(diff) < 0.05 -> "$base und damit etwa gleich wie $previousLabel."
                diff > 0 -> "$base und damit um ${formatOneDecimal(diff)} höher als $previousLabel."
                else -> "$base und damit um ${formatOneDecimal(-diff)} niedriger als $previousLabel."
            }
        }
    }
    val withData = weekdays.filter { it.averageScore != null }
    if (withData.size >= 2) {
        val best = withData.maxByOrNull { it.averageScore!! }!!
        lines += "Dein bester Wochentag war der ${WEEKDAY_NAMES[best.isoDay - 1]} " +
            "mit durchschnittlich ${formatOneDecimal(best.averageScore!!)} von 5."
    }
    activities.items.firstOrNull()?.let {
        lines += "Deine häufigste Aktivität war „${it.tag.name}“ in ${if (it.count == 1) "1 Eintrag" else "${it.count} Einträgen"}."
    }
    if (goodDays != null && goodDays.length >= 2) {
        lines += "Deine längste Serie guter Tage umfasste ${goodDays.length} Tage."
    }
    return lines
}
