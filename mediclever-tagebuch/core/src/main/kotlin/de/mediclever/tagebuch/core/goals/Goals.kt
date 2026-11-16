package de.mediclever.tagebuch.core.goals

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.Goal
import de.mediclever.tagebuch.core.model.GoalCheck
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.stats.DateRange
import de.mediclever.tagebuch.core.stats.DiaryIndex
import de.mediclever.tagebuch.core.stats.monthGrid
import de.mediclever.tagebuch.core.stats.normalizeWeekStart
import de.mediclever.tagebuch.core.stats.parseDateOrNull
import de.mediclever.tagebuch.core.stats.startOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TreeSet

/** Schwellen der Stufen eines Ziels: Anzahl der Abschluesse, ab der die Stufe 1, 2, ... erreicht ist. */
val GOAL_LEVEL_THRESHOLDS: List<Int> = listOf(1, 7, 14, 30, 60, 100, 200, 365)

/** Einheit einer Ziel-Serie: Tage (taeglich, feste Wochentage) oder Wochen (x-mal pro Woche). */
enum class StreakUnit { DAYS, WEEKS }

/** Zustand eines Ziels an einem Tag: erledigt, offen (faellig und nicht abgehakt) oder nicht faellig. */
enum class GoalState { DONE, OPEN, NOT_DUE }

/**
 * Stufe eines Ziels. level 0 bedeutet noch keine Stufe, hoechste Stufe ist die Anzahl der Schwellen (8).
 * reachedThreshold ist die Schwelle der erreichten Stufe, nextThreshold die der naechsten (null auf der
 * hoechsten Stufe), remaining die bis dahin fehlenden Abschluesse.
 */
data class GoalLevel(
    val level: Int,
    val completions: Int,
    val reachedThreshold: Int?,
    val nextThreshold: Int?,
    val remaining: Int?,
)

/** Aktuelle und laengste Serie eines Ziels samt Einheit. */
data class GoalStreaks(val current: Int, val longest: Int, val unit: StreakUnit)

/** Anzahl der Abschluesse in einem Monat. */
data class MonthCount(val month: YearMonth, val count: Int)

/** Ein Tag im Ziel-Kalender. due nach [GoalTracker.isDueOn], checked = abgehakt, future = nach heute. */
data class GoalCalendarDay(val date: LocalDate, val due: Boolean, val checked: Boolean, val future: Boolean)

/**
 * Alle Kennzahlen eines Ziels fuer die Detailansicht.
 * successRate7 und successRate30 liegen zwischen 0.0 und 1.0 oder sind null, wenn im Fenster nichts faellig war.
 * weekTarget ist nur bei x-mal pro Woche gesetzt, weekCompletions zaehlt die Abschluesse der laufenden Woche.
 */
data class GoalOverview(
    val goal: Goal,
    val state: GoalState,
    val streaks: GoalStreaks,
    val successRate7: Double?,
    val successRate30: Double?,
    val totalCompletions: Int,
    val level: GoalLevel,
    val weekCompletions: Int,
    val weekTarget: Int?,
)

/** Ein Ziel in der Zeile "Ziele heute": Zustand, laufende Serie und Wochenfortschritt. */
data class GoalToday(
    val goal: Goal,
    val state: GoalState,
    val currentStreak: Int,
    val streakUnit: StreakUnit,
    val weekCompletions: Int,
    val weekTarget: Int?,
)

/**
 * Ein Ziel in einem Zeitraum (Statistik, Bericht).
 * completions: abgehakte Tage im Zeitraum. expected: erwartete Abschluesse nach [GoalTracker.expectedIn].
 * successRate: Anteil 0.0 bis 1.0 nach [GoalTracker.successRateIn], null wenn nichts erwartet war.
 */
data class GoalPeriodSummary(
    val goal: Goal,
    val completions: Int,
    val expected: Double,
    val successRate: Double?,
)

/**
 * Stufe zu einer Anzahl von Abschluessen nach [GOAL_LEVEL_THRESHOLDS]:
 * die Stufe ist die Anzahl der Schwellen, die kleiner oder gleich [completions] sind.
 */
fun goalLevel(completions: Int): GoalLevel {
    val level = GOAL_LEVEL_THRESHOLDS.count { it <= completions }
    val next = GOAL_LEVEL_THRESHOLDS.getOrNull(level)
    return GoalLevel(
        level = level,
        completions = completions,
        reachedThreshold = if (level == 0) null else GOAL_LEVEL_THRESHOLDS[level - 1],
        nextThreshold = next,
        remaining = next?.let { it - completions },
    )
}

/**
 * Auswertung eines Ziels ueber seine abgehakten Tage.
 *
 * Begriffe:
 *  - aktiv ist das Ziel an einem Tag, wenn es nicht geloescht ist, startDate <= Tag gilt, der Tag nicht nach
 *    endDate liegt und vor dem Tag der Archivierung liegt (archivedAt in [zone] umgerechnet; der Archivtag
 *    selbst ist nicht mehr aktiv).
 *  - geplant: DAILY an jedem aktiven Tag, WEEKDAYS an aktiven Tagen mit passendem ISO-Wochentag,
 *    TIMES_PER_WEEK an jedem aktiven Tag.
 *  - Woche: sieben Tage ab dem Wochentag [weekStart] (ISO 1..7).
 *
 * @param checkedDates abgehakte Tage; Tage ausserhalb der aktiven Zeit zaehlen als Abschluss, aber nie als geplant.
 */
class GoalTracker(
    val goal: Goal,
    checkedDates: Collection<LocalDate>,
    weekStart: Int = 1,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val weekStart: Int = normalizeWeekStart(weekStart)
    private val checked: TreeSet<LocalDate> = TreeSet(checkedDates)
    private val start: LocalDate? = parseDateOrNull(goal.startDate)
    private val lastActive: LocalDate? = run {
        val end = parseDateOrNull(goal.endDate)
        val beforeArchive = goal.archivedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().minusDays(1) }
        when {
            end == null -> beforeArchive
            beforeArchive == null -> end
            else -> if (end.isBefore(beforeArchive)) end else beforeArchive
        }
    }
    private val times: Int = goal.timesPerWeek.coerceIn(1, 7)

    /** Abgehakte Tage, aufsteigend. */
    val checkedDates: List<LocalDate> get() = checked.toList()

    /** Einheit der Serie: WEEKS bei TIMES_PER_WEEK, sonst DAYS. */
    val streakUnit: StreakUnit
        get() = if (goal.repeat == GoalRepeat.TIMES_PER_WEEK) StreakUnit.WEEKS else StreakUnit.DAYS

    /** Wahr, wenn das Ziel am [date] aktiv ist (siehe Klassenbeschreibung). */
    fun isActiveOn(date: LocalDate): Boolean {
        val s = start ?: return false
        if (goal.deleted || date.isBefore(s)) return false
        return lastActive == null || !date.isAfter(lastActive)
    }

    /** Wahr, wenn [date] abgehakt ist. */
    fun isCheckedOn(date: LocalDate): Boolean = date in checked

    /** Wahr, wenn das Ziel am [date] geplant ist (siehe Klassenbeschreibung), unabhaengig von Haken. */
    fun isScheduledOn(date: LocalDate): Boolean = isActiveOn(date) && when (goal.repeat) {
        GoalRepeat.DAILY, GoalRepeat.TIMES_PER_WEEK -> true
        GoalRepeat.WEEKDAYS -> date.dayOfWeek.value in goal.weekdays
    }

    /** Anzahl der abgehakten Tage in der Woche, in der [date] liegt. */
    fun weekCompletions(date: LocalDate): Int {
        val w = startOfWeek(date, weekStart)
        return checked.subSet(w, true, w.plusDays(6), true).size
    }

    /**
     * Faellig am [date]: DAILY und WEEKDAYS, wenn der Tag geplant ist. TIMES_PER_WEEK, wenn der Tag aktiv ist
     * und die uebrigen Tage derselben Woche zusammen weniger als timesPerWeek Haken haben (das Wochenziel
     * ist also ohne diesen Tag noch nicht erreicht).
     */
    fun isDueOn(date: LocalDate): Boolean = when (goal.repeat) {
        GoalRepeat.DAILY, GoalRepeat.WEEKDAYS -> isScheduledOn(date)
        GoalRepeat.TIMES_PER_WEEK ->
            isActiveOn(date) && weekCompletions(date) - (if (date in checked) 1 else 0) < times
    }

    /** DONE wenn [date] abgehakt ist, sonst OPEN wenn faellig, sonst NOT_DUE. */
    fun stateOn(date: LocalDate): GoalState = when {
        date in checked -> GoalState.DONE
        isDueOn(date) -> GoalState.OPEN
        else -> GoalState.NOT_DUE
    }

    /**
     * Aktuelle und laengste Serie bis [today].
     *
     * DAILY und WEEKDAYS: gezaehlt werden aufeinanderfolgende geplante Tage, die abgehakt sind. Ein geplanter,
     * nicht abgehakter Tag setzt die Serie auf 0; ist dieser Tag heute, bleibt die Serie bestehen.
     *
     * TIMES_PER_WEEK: gezaehlt werden aufeinanderfolgende Wochen (ab der Woche des Startdatums) mit mindestens
     * timesPerWeek Haken. Eine Woche mit weniger Haken setzt die Serie auf 0; die laufende Woche (in der heute
     * liegt) bricht die Serie nicht, zaehlt aber erst mit, wenn sie erfuellt ist.
     *
     * Betrachtet wird die Zeit vom Startdatum bis heute, hoechstens bis zum letzten aktiven Tag.
     * Die aktuelle Serie ist die Serie am Ende dieser Zeit.
     */
    fun streaks(today: LocalDate): GoalStreaks {
        val unit = streakUnit
        val s = start ?: return GoalStreaks(0, 0, unit)
        val end = if (lastActive != null && lastActive.isBefore(today)) lastActive else today
        if (goal.deleted || end.isBefore(s)) return GoalStreaks(0, 0, unit)
        var run = 0
        var longest = 0
        if (unit == StreakUnit.DAYS) {
            var d: LocalDate = s
            while (!d.isAfter(end)) {
                if (isScheduledOn(d)) {
                    if (d in checked) {
                        run++
                        if (run > longest) longest = run
                    } else if (d != today) {
                        run = 0
                    }
                }
                d = d.plusDays(1)
            }
        } else {
            var w = startOfWeek(s, weekStart)
            val lastWeek = startOfWeek(end, weekStart)
            val currentWeek = startOfWeek(today, weekStart)
            while (!w.isAfter(lastWeek)) {
                if (checked.subSet(w, true, w.plusDays(6), true).size >= times) {
                    run++
                    if (run > longest) longest = run
                } else if (w != currentWeek) {
                    run = 0
                }
                w = w.plusDays(7)
            }
        }
        return GoalStreaks(run, longest, unit)
    }

    /** Anzahl der abgehakten Tage im [range]. */
    fun completionsIn(range: DateRange): Int = checked.subSet(range.start, true, range.endInclusive, true).size

    /**
     * Erwartete Abschluesse im [range]: DAILY und WEEKDAYS die Anzahl der geplanten Tage,
     * TIMES_PER_WEEK timesPerWeek * aktive Tage / 7.
     */
    fun expectedIn(range: DateRange): Double {
        var n = 0
        var d = range.start
        while (!d.isAfter(range.endInclusive)) {
            if (isScheduledOn(d)) n++
            d = d.plusDays(1)
        }
        return if (goal.repeat == GoalRepeat.TIMES_PER_WEEK) times * n / 7.0 else n.toDouble()
    }

    /**
     * Erfolgsrate im [range] als Anteil 0.0 bis 1.0, null wenn nichts erwartet war.
     * DAILY und WEEKDAYS: abgehakte geplante Tage / geplante Tage.
     * TIMES_PER_WEEK: abgehakte Tage / [expectedIn], hoechstens 1.0.
     * Tage nach [today] zaehlen nicht. Ist heute geplant und noch nicht abgehakt, zaehlt heute nicht mit.
     */
    fun successRateIn(range: DateRange, today: LocalDate): Double? {
        var scheduled = 0
        var done = 0
        var d = range.start
        while (!d.isAfter(range.endInclusive) && !d.isAfter(today)) {
            if (isScheduledOn(d)) {
                val c = d in checked
                if (c || d != today) scheduled++
                if (c) done++
            }
            d = d.plusDays(1)
        }
        if (scheduled == 0) return null
        return if (goal.repeat == GoalRepeat.TIMES_PER_WEEK) {
            minOf(1.0, done / (times * scheduled / 7.0))
        } else {
            done.toDouble() / scheduled
        }
    }

    /** Erfolgsrate der letzten [days] Tage einschliesslich [today], Definition wie [successRateIn]. */
    fun successRate(today: LocalDate, days: Int): Double? =
        successRateIn(DateRange(today.minusDays((days - 1).toLong()), today), today)

    /** Abschluesse je Monat des Jahres [year]: immer zwoelf Werte, Januar zuerst. */
    fun completionsPerMonth(year: Int): List<MonthCount> = (1..12).map { m ->
        val ym = YearMonth.of(year, m)
        MonthCount(ym, completionsIn(DateRange.ofMonth(ym)))
    }

    /** Monatsraster (Wochen mit je sieben Feldern, null ausserhalb des Monats) mit Faelligkeit und Haken je Tag. */
    fun calendar(month: YearMonth, today: LocalDate): List<List<GoalCalendarDay?>> =
        monthGrid(month, weekStart).map { week ->
            week.map { d -> d?.let { GoalCalendarDay(it, isDueOn(it), it in checked, it.isAfter(today)) } }
        }

    /** Anzahl aller abgehakten Tage bis einschliesslich [today]. */
    fun totalCompletions(today: LocalDate): Int = checked.headSet(today, true).size

    /** Alle Kennzahlen fuer die Detailansicht, Stand [today]. */
    fun overview(today: LocalDate): GoalOverview {
        val total = totalCompletions(today)
        return GoalOverview(
            goal = goal,
            state = stateOn(today),
            streaks = streaks(today),
            successRate7 = successRate(today, 7),
            successRate30 = successRate(today, 30),
            totalCompletions = total,
            level = goalLevel(total),
            weekCompletions = weekCompletions(today),
            weekTarget = if (goal.repeat == GoalRepeat.TIMES_PER_WEEK) times else null,
        )
    }

    /** Kennzahlen des Ziels im [range]; Tage nach [today] zaehlen bei expected und successRate nicht. */
    fun periodSummary(range: DateRange, today: LocalDate): GoalPeriodSummary {
        val expected = if (range.start.isAfter(today)) 0.0 else {
            expectedIn(DateRange(range.start, if (range.endInclusive.isAfter(today)) today else range.endInclusive))
        }
        return GoalPeriodSummary(goal, completionsIn(range), expected, successRateIn(range, today))
    }

    internal fun overlaps(range: DateRange): Boolean {
        val s = start ?: return false
        if (goal.deleted || s.isAfter(range.endInclusive)) return false
        return lastActive == null || (!lastActive.isBefore(range.start) && !lastActive.isBefore(s))
    }
}

private fun autoDates(goal: Goal, tracker: GoalTracker, datesWithTag: Collection<LocalDate>?): List<LocalDate> {
    if (goal.deleted || goal.tagId == null || datesWithTag == null) return emptyList()
    return datesWithTag.filter { tracker.isActiveOn(it) }
}

/**
 * Regel fuer verknuepfte Ziele: enthaelt ein nicht geloeschter Eintrag die Aktivitaet tagId eines nicht
 * geloeschten Ziels an einem Tag, an dem das Ziel aktiv ist (siehe [GoalTracker]), gilt das Ziel an diesem
 * Tag als abgehakt. Liefert die [GoalCheck]-Zeilen, die es danach fuer [entries] geben muss: je Ziel und Tag
 * genau eine, id "<goalId>|<Datum>", updatedAt = [now], sortiert nach Ziel-Reihenfolge der Eingabe und Datum.
 */
fun autoGoalChecks(
    goals: Collection<Goal>,
    entries: Collection<Entry>,
    now: Long = 0,
    zone: ZoneId = ZoneId.systemDefault(),
): List<GoalCheck> {
    val datesByTag = HashMap<String, TreeSet<LocalDate>>()
    val wanted = goals.mapNotNullTo(HashSet()) { if (it.deleted) null else it.tagId }
    if (wanted.isEmpty()) return emptyList()
    for (e in entries) {
        if (e.deleted) continue
        for (t in e.tagIds) if (t in wanted) {
            val d = parseDateOrNull(e.localDate) ?: break
            datesByTag.getOrPut(t) { TreeSet() }.add(d)
        }
    }
    return goals.flatMap { g ->
        autoDates(g, GoalTracker(g, emptyList(), 1, zone), datesByTag[g.tagId]).map { d ->
            GoalCheck(GoalCheck.idFor(g.id, d.toString()), g.id, d.toString(), updatedAt = now)
        }
    }
}

/**
 * Die nach [autoGoalChecks] noetigen Haken, die im [diary] noch nicht als nicht geloeschte Zeile vorhanden sind.
 * Die App speichert diese Zeilen nach dem Sichern eines Eintrags.
 */
fun missingAutoGoalChecks(
    diary: Diary,
    now: Long = 0,
    zone: ZoneId = ZoneId.systemDefault(),
): List<GoalCheck> {
    val existing = diary.goalChecks.filter { !it.deleted }.mapTo(HashSet()) { it.id }
    val liveTags = DiaryIndex.of(diary).tags
    val goals = diary.goals.filter { it.tagId == null || it.tagId in liveTags }
    return autoGoalChecks(goals, diary.entries, now, zone).filter { it.id !in existing }
}

/**
 * Abgehakte Tage je Ziel-id, so wie alle Auswertungen sie verwenden: nicht geloeschte [GoalCheck]-Zeilen
 * nicht geloeschter Ziele, vereinigt mit den Tagen nach der Regel von [autoGoalChecks]
 * (eine geloeschte verknuepfte Aktivitaet hakt nichts ab).
 */
fun effectiveGoalDates(diary: Diary, zone: ZoneId = ZoneId.systemDefault()): Map<String, Set<LocalDate>> {
    val idx = DiaryIndex.of(diary)
    val goals = diary.goals.filter { !it.deleted }
    val out = LinkedHashMap<String, TreeSet<LocalDate>>()
    for (g in goals) out[g.id] = TreeSet()
    for (c in diary.goalChecks) {
        if (c.deleted) continue
        val set = out[c.goalId] ?: continue
        parseDateOrNull(c.date)?.let { set.add(it) }
    }
    for (g in goals) {
        val tagId = g.tagId ?: continue
        out.getValue(g.id).addAll(autoDates(g, GoalTracker(g, emptyList(), 1, zone), idx.datesByTag[tagId]))
    }
    return out
}

/**
 * Auswertungen aller nicht geloeschten Ziele des [diary] (auch archivierte), sortiert nach sortOrder, Name, id.
 * Haken nach [effectiveGoalDates], Wochenanfang aus den Einstellungen.
 */
fun goalTrackers(diary: Diary, zone: ZoneId = ZoneId.systemDefault()): List<GoalTracker> {
    val dates = effectiveGoalDates(diary, zone)
    return diary.goals.filter { !it.deleted }
        .sortedWith(compareBy<Goal> { it.sortOrder }.thenBy { it.name.lowercase() }.thenBy { it.id })
        .map { GoalTracker(it, dates[it.id].orEmpty(), diary.settings.weekStart, zone) }
}

/** Auswertung des Ziels [goalId], null wenn es fehlt oder geloescht ist. Haken nach [effectiveGoalDates]. */
fun goalTracker(diary: Diary, goalId: String, zone: ZoneId = ZoneId.systemDefault()): GoalTracker? =
    goalTrackers(diary, zone).firstOrNull { it.goal.id == goalId }

/** Kennzahlen aller nicht geloeschten Ziele, Stand [today]; archivierte nur mit [includeArchived]. */
fun goalOverviews(
    diary: Diary,
    today: LocalDate,
    includeArchived: Boolean = true,
    zone: ZoneId = ZoneId.systemDefault(),
): List<GoalOverview> = goalTrackers(diary, zone)
    .filter { includeArchived || it.goal.archivedAt == null }
    .map { it.overview(today) }

/**
 * Zeile "Ziele heute": alle nicht geloeschten, nicht archivierten Ziele, die am [today] faellig oder
 * abgehakt sind ([GoalTracker.stateOn] ist nicht NOT_DUE), sortiert nach sortOrder, Name, id.
 */
fun goalsToday(diary: Diary, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<GoalToday> =
    goalTrackers(diary, zone).mapNotNull { t ->
        val state = t.stateOn(today)
        if (t.goal.archivedAt != null || state == GoalState.NOT_DUE) return@mapNotNull null
        GoalToday(
            goal = t.goal,
            state = state,
            currentStreak = t.streaks(today).current,
            streakUnit = t.streakUnit,
            weekCompletions = t.weekCompletions(today),
            weekTarget = if (t.goal.repeat == GoalRepeat.TIMES_PER_WEEK) t.goal.timesPerWeek.coerceIn(1, 7) else null,
        )
    }

/**
 * Ziele-Uebersicht eines Zeitraums: jedes nicht geloeschte Ziel, das im [range] an mindestens einem Tag aktiv
 * ist oder dort einen Haken hat, mit [GoalTracker.periodSummary]. Sortiert nach sortOrder, Name, id.
 */
fun goalPeriodSummaries(
    diary: Diary,
    range: DateRange,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<GoalPeriodSummary> = goalTrackers(diary, zone)
    .filter { it.overlaps(range) || it.completionsIn(range) > 0 }
    .map { it.periodSummary(range, today) }
