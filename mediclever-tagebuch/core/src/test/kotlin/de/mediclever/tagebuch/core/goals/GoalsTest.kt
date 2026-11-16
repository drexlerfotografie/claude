package de.mediclever.tagebuch.core.goals

import de.mediclever.tagebuch.core.model.Goal
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.stats.CHECKS
import de.mediclever.tagebuch.core.stats.DIARY
import de.mediclever.tagebuch.core.stats.ENTRIES
import de.mediclever.tagebuch.core.stats.GOALS
import de.mediclever.tagebuch.core.stats.UTC
import de.mediclever.tagebuch.core.stats.check
import de.mediclever.tagebuch.core.stats.d
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalsTest {
    private fun days(vararg text: String) = text.map { d(it) }

    // ------------------------------------------------------------ taeglich

    private val daily = Goal(id = "g", name = "Täglich", startDate = "2026-03-01")

    /** Haken: 01., 02., 03. | 05., 06. | 08., 09. Maerz. */
    private val dailyTracker = GoalTracker(
        daily,
        days("2026-03-01", "2026-03-02", "2026-03-03", "2026-03-05", "2026-03-06", "2026-03-08", "2026-03-09"),
        zone = UTC,
    )

    @Test fun dailyDueRespectsStartEndAndArchive() {
        assertFalse(dailyTracker.isDueOn(d("2026-02-28")))
        assertTrue(dailyTracker.isDueOn(d("2026-03-01")))
        assertTrue(dailyTracker.isDueOn(d("2027-01-01")))

        val ended = GoalTracker(daily.copy(endDate = "2026-03-20"), emptyList(), zone = UTC)
        assertTrue(ended.isDueOn(d("2026-03-20")))
        assertFalse(ended.isDueOn(d("2026-03-21")))

        val archivedAt = LocalDateTime.of(2026, 3, 15, 10, 0).toInstant(UTC).toEpochMilli()
        val archived = GoalTracker(daily.copy(archivedAt = archivedAt), emptyList(), zone = UTC)
        assertTrue(archived.isDueOn(d("2026-03-14")))
        assertFalse(archived.isDueOn(d("2026-03-15")))

        assertFalse(GoalTracker(daily.copy(deleted = true), emptyList(), zone = UTC).isDueOn(d("2026-03-05")))
    }

    @Test fun dailyStreaks() {
        // 01.-03. = 3, 04. fehlt, 05.-06. = 2, 07. fehlt, 08.-09. = 2, 10. ist heute und noch offen
        assertEquals(GoalStreaks(2, 3, StreakUnit.DAYS), dailyTracker.streaks(d("2026-03-10")))
        // am 11. ist der 10. versaeumt
        assertEquals(GoalStreaks(0, 3, StreakUnit.DAYS), dailyTracker.streaks(d("2026-03-11")))
        assertEquals(GoalStreaks(3, 3, StreakUnit.DAYS), dailyTracker.streaks(d("2026-03-03")))
        assertEquals(GoalStreaks(0, 0, StreakUnit.DAYS), dailyTracker.streaks(d("2026-02-20")))
    }

    @Test fun dailySuccessRates() {
        // 7 Tage bis 10.03.: 04.-10.; heute offen zaehlt nicht -> 6 Tage, abgehakt 05., 06., 08., 09.
        assertEquals(4.0 / 6, dailyTracker.successRate(d("2026-03-10"), 7))
        // 30 Tage: geplant ab 01.03. -> 01.-09. = 9 Tage, 7 abgehakt
        assertEquals(7.0 / 9, dailyTracker.successRate(d("2026-03-10"), 30))
        // vor dem Start ist nichts faellig
        assertNull(dailyTracker.successRate(d("2026-02-20"), 7))
        // ist heute abgehakt, zaehlt heute mit: 03.-09. = 7 Tage, 5 abgehakt
        assertEquals(5.0 / 7, dailyTracker.successRate(d("2026-03-09"), 7))
    }

    @Test fun completionsLevelAndCalendar() {
        assertEquals(7, dailyTracker.totalCompletions(d("2026-03-10")))
        assertEquals(3, dailyTracker.totalCompletions(d("2026-03-04")))
        assertEquals(listOf(0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0), dailyTracker.completionsPerMonth(2026).map { it.count })
        assertEquals(YearMonth.of(2026, 3), dailyTracker.completionsPerMonth(2026)[2].month)

        val overview = dailyTracker.overview(d("2026-03-10"))
        assertEquals(GoalState.OPEN, overview.state)
        assertEquals(GoalLevel(level = 2, completions = 7, reachedThreshold = 7, nextThreshold = 14, remaining = 7), overview.level)
        assertEquals(1, overview.weekCompletions) // Woche ab Mo 09.03.: nur der 09.
        assertNull(overview.weekTarget)
    }

    @Test fun weekCompletionsFollowWeekStart() {
        // Woche Mo 09.-So 15.03.: Haken nur am 09. Woche Mo 02.-So 08.: 02., 03., 05., 06., 08.
        assertEquals(1, dailyTracker.weekCompletions(d("2026-03-10")))
        assertEquals(5, dailyTracker.weekCompletions(d("2026-03-04")))
        // Wochenanfang Sonntag: Woche So 08.-Sa 14.: 08., 09.
        val sunday = GoalTracker(daily, dailyTracker.checkedDates, weekStart = 7, zone = UTC)
        assertEquals(2, sunday.weekCompletions(d("2026-03-10")))
    }

    @Test fun levels() {
        assertEquals(GoalLevel(0, 0, null, 1, 1), goalLevel(0))
        assertEquals(GoalLevel(1, 1, 1, 7, 6), goalLevel(1))
        assertEquals(GoalLevel(1, 6, 1, 7, 1), goalLevel(6))
        assertEquals(GoalLevel(4, 59, 30, 60, 1), goalLevel(59))
        assertEquals(GoalLevel(7, 364, 200, 365, 1), goalLevel(364))
        assertEquals(GoalLevel(8, 400, 365, null, null), goalLevel(400))
        assertEquals(listOf(1, 7, 14, 30, 60, 100, 200, 365), GOAL_LEVEL_THRESHOLDS)
    }

    @Test fun calendarGrid() {
        // Maerz 2026 beginnt am Sonntag: bei Wochenanfang Montag stehen 6 leere Felder davor, 6 Wochen.
        val weeks = dailyTracker.calendar(YearMonth.of(2026, 3), today = d("2026-03-10"))
        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(listOf(null, null, null, null, null, null), weeks[0].take(6))
        assertEquals(GoalCalendarDay(d("2026-03-01"), due = true, checked = true, future = false), weeks[0][6])
        assertEquals(GoalCalendarDay(d("2026-03-04"), due = true, checked = false, future = false), weeks[1][2])
        assertEquals(GoalCalendarDay(d("2026-03-11"), due = true, checked = false, future = true), weeks[2][2])
        assertEquals(d("2026-03-31"), weeks[5][1]?.date)
        assertNull(weeks[5][2])
    }

    // ------------------------------------------------------------ feste Wochentage

    /** Montag, Mittwoch, Freitag ab Mo 02.03. Geplant: 02., 04., 06., 09., 11., 13. Haken: 02., 04., 09., 10. (Di), 11. */
    private val weekdayTracker = GoalTracker(
        Goal(id = "w", name = "MoMiFr", repeat = GoalRepeat.WEEKDAYS, weekdays = listOf(1, 3, 5), startDate = "2026-03-02"),
        days("2026-03-02", "2026-03-04", "2026-03-09", "2026-03-10", "2026-03-11"),
        zone = UTC,
    )

    @Test fun weekdayGoal() {
        assertTrue(weekdayTracker.isDueOn(d("2026-03-06")))
        assertFalse(weekdayTracker.isDueOn(d("2026-03-03")))
        assertFalse(weekdayTracker.isDueOn(d("2026-03-10")))
        assertEquals(GoalState.DONE, weekdayTracker.stateOn(d("2026-03-10"))) // abgehakt, obwohl nicht faellig
        assertEquals(GoalState.NOT_DUE, weekdayTracker.stateOn(d("2026-03-12")))
        // 02., 04. = 2; 06. fehlt; 09., 11. = 2; 13. ist heute und offen. Der Dienstag zaehlt nicht zur Serie.
        assertEquals(GoalStreaks(2, 2, StreakUnit.DAYS), weekdayTracker.streaks(d("2026-03-13")))
        assertEquals(5, weekdayTracker.totalCompletions(d("2026-03-13")))
        // 7 Tage (07.-13.): geplant 09., 11. (13. offen) -> 2 von 2
        assertEquals(1.0, weekdayTracker.successRate(d("2026-03-13"), 7))
        // 30 Tage: geplant 02., 04., 06., 09., 11. -> 4 von 5
        assertEquals(0.8, weekdayTracker.successRate(d("2026-03-13"), 30))
    }

    // ------------------------------------------------------------ x-mal pro Woche

    private val twice = Goal(id = "t", name = "Zweimal", repeat = GoalRepeat.TIMES_PER_WEEK, timesPerWeek = 2, startDate = "2026-03-02")

    /**
     * Wochen ab Montag: 02.-08.: 03., 05. (erfuellt) | 09.-15.: 10. (nicht) | 16.-22.: 16., 17., 20. (erfuellt)
     * 23.-29.: 23., 24. (erfuellt) | 30.03.-05.04.: 31.
     */
    private val twiceDates = days(
        "2026-03-03", "2026-03-05", "2026-03-10", "2026-03-16", "2026-03-17", "2026-03-20",
        "2026-03-23", "2026-03-24", "2026-03-31",
    )
    private val twiceTracker = GoalTracker(twice, twiceDates, weekStart = 1, zone = UTC)

    @Test fun timesPerWeekStreakCountsWeeks() {
        // 1, 0, 1, 2, laufende Woche noch offen -> bleibt 2
        assertEquals(GoalStreaks(2, 2, StreakUnit.WEEKS), twiceTracker.streaks(d("2026-04-01")))
        // zweiter Haken in der laufenden Woche -> 3
        val more = GoalTracker(twice, twiceDates + d("2026-04-02"), weekStart = 1, zone = UTC)
        assertEquals(GoalStreaks(3, 3, StreakUnit.WEEKS), more.streaks(d("2026-04-02")))
        // in der Woche darauf ist die Woche mit nur einem Haken versaeumt
        assertEquals(GoalStreaks(0, 2, StreakUnit.WEEKS), twiceTracker.streaks(d("2026-04-06")))
    }

    @Test fun timesPerWeekDue() {
        // Fr 06.03.: die Woche hat schon 2 Haken an anderen Tagen
        assertFalse(twiceTracker.isDueOn(d("2026-03-06")))
        // Di 03.03. selbst abgehakt, sonst nur der 05. -> ohne diesen Tag waere das Ziel offen
        assertTrue(twiceTracker.isDueOn(d("2026-03-03")))
        // Mi 11.03.: bisher 1 Haken in der Woche
        assertTrue(twiceTracker.isDueOn(d("2026-03-11")))
        assertFalse(twiceTracker.isDueOn(d("2026-03-01"))) // vor dem Start
        val o = twiceTracker.overview(d("2026-04-01"))
        assertEquals(1, o.weekCompletions)
        assertEquals(2, o.weekTarget)
        assertEquals(GoalState.OPEN, o.state)
    }

    @Test fun timesPerWeekSuccessRate() {
        // 7 Tage bis Mi 01.04.: 26.03.-01.04., heute offen -> 6 Tage, erwartet 2 * 6 / 7, abgehakt nur der 31.
        assertEquals(1 / (2 * 6 / 7.0), twiceTracker.successRate(d("2026-04-01"), 7)!!, 1e-12)
        // 7 Tage bis So 22.03.: 16., 17., 20. -> 3 von erwarteten 2, gedeckelt auf 1
        assertEquals(1.0, twiceTracker.successRate(d("2026-03-22"), 7))
    }

    @Test fun weekStartChangesWhichWeekACheckBelongsTo() {
        // Haken am Sa 07. und So 08.03. Montag als Wochenanfang: beide in der Woche 02.-08. -> erfuellt.
        val dates = days("2026-03-07", "2026-03-08")
        assertEquals(GoalStreaks(0, 1, StreakUnit.WEEKS), GoalTracker(twice, dates, weekStart = 1, zone = UTC).streaks(d("2026-03-20")))
        // Sonntag als Wochenanfang: 07. in der Woche 01.-07., 08. in der Woche 08.-14. -> keine erfuellt.
        assertEquals(GoalStreaks(0, 0, StreakUnit.WEEKS), GoalTracker(twice, dates, weekStart = 7, zone = UTC).streaks(d("2026-03-20")))
    }

    // ------------------------------------------------------------ Tagebuch

    @Test fun autoChecksFromEntries() {
        // Sport steht in f2 (28.02., vor dem Start), e1 02.03., e2 03.03., e6 07.03., e7 08.03. und im geloeschten Eintrag.
        val auto = autoGoalChecks(GOALS, ENTRIES, now = 99, zone = UTC)
        assertEquals(
            listOf("sportziel|2026-03-02", "sportziel|2026-03-03", "sportziel|2026-03-07", "sportziel|2026-03-08"),
            auto.map { it.id },
        )
        assertTrue(auto.all { it.goalId == "sportziel" && it.updatedAt == 99L && !it.deleted })
        assertEquals("2026-03-07", auto[2].date)
    }

    @Test fun missingAutoChecksSkipExistingOnes() {
        val diary = DIARY.copy(goalChecks = CHECKS + check("sportziel", "2026-03-02") + check("sportziel", "2026-03-03", deleted = true))
        assertEquals(
            listOf("sportziel|2026-03-03", "sportziel|2026-03-07", "sportziel|2026-03-08"),
            missingAutoGoalChecks(diary, zone = UTC).map { it.id },
        )
    }

    @Test fun effectiveDatesCombineManualAndLinkedChecks() {
        val dates = effectiveGoalDates(DIARY, UTC)
        assertEquals(days("2026-03-02", "2026-03-03", "2026-03-07", "2026-03-08"), dates.getValue("sportziel").toList())
        assertEquals(days("2026-03-01", "2026-03-07", "2026-03-08"), dates.getValue("schreibziel").toList())
    }

    @Test fun goalsDueToday() {
        // So 08.03.: beide abgehakt. Sportziel: 07., 08. -> Serie 2. Schreibziel: 01., 07., 08. (Sa/So) -> Serie 3.
        val sunday = goalsToday(DIARY, d("2026-03-08"), UTC)
        assertEquals(listOf("sportziel", "schreibziel"), sunday.map { it.goal.id })
        assertEquals(listOf(GoalState.DONE, GoalState.DONE), sunday.map { it.state })
        assertEquals(listOf(2, 3), sunday.map { it.currentStreak })
        // Mo 09.03.: Sportziel offen (Serie bleibt 2), Schreibziel nicht faellig
        val monday = goalsToday(DIARY, d("2026-03-09"), UTC)
        assertEquals(listOf("sportziel"), monday.map { it.goal.id })
        assertEquals(GoalState.OPEN, monday[0].state)
        assertEquals(2, monday[0].currentStreak)
        assertNull(monday[0].weekTarget)
        // archivierte und geloeschte Ziele erscheinen nicht
        val hidden = DIARY.copy(goals = listOf(GOALS[0].copy(archivedAt = 1), GOALS[1].copy(deleted = true)))
        assertEquals(emptyList(), goalsToday(hidden, d("2026-03-08"), UTC))
    }

    @Test fun overviewFromDiary() {
        val o = goalOverviews(DIARY, d("2026-03-10"), zone = UTC)
        assertEquals(listOf("sportziel", "schreibziel"), o.map { it.goal.id })
        assertEquals(4, o[0].totalCompletions)
        assertEquals(1, o[0].level.level)
        assertEquals(GoalStreaks(0, 2, StreakUnit.DAYS), o[0].streaks) // 09. versaeumt
        assertEquals(GoalStreaks(3, 3, StreakUnit.DAYS), o[1].streaks)
        assertEquals(1.0, o[1].successRate30)
        assertNull(goalTracker(DIARY, "gibt-es-nicht", UTC))
    }
}
