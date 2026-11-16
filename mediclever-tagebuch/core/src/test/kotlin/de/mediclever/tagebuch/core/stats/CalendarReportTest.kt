package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Diary
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarReportTest {
    private val march = YearMonth.of(2026, 3)
    private val today = d("2026-03-10")

    // ------------------------------------------------------------ Kalender

    @Test fun gridWithMondayStart() {
        // Der 01.03.2026 ist ein Sonntag: sechs leere Felder, dann der 1.
        val cal = calendarMonth(DIARY, march, today, weekStart = 1)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), cal.weekdays)
        assertEquals(6, cal.weeks.size)
        assertTrue(cal.weeks.all { it.size == 7 })
        assertEquals(listOf(null, null, null, null, null, null), cal.weeks[0].take(6))
        assertEquals(CalendarDay(d("2026-03-01"), 0, null, null, 0, isToday = false, isFuture = false), cal.weeks[0][6])
        assertEquals(CalendarDay(d("2026-03-02"), 1, 5.0, 1, 1, isToday = false, isFuture = false), cal.weeks[1][0])
        // 04.03.: Punktwerte 4 und 2 -> 3,0 -> Stufe 3
        assertEquals(CalendarDay(d("2026-03-04"), 2, 3.0, 3, 2, isToday = false, isFuture = false), cal.weeks[1][2])
        assertEquals(CalendarDay(d("2026-03-10"), 0, null, null, 0, isToday = true, isFuture = false), cal.weeks[2][1])
        assertEquals(true, cal.weeks[2][2]?.isFuture)
        assertEquals(d("2026-03-31"), cal.weeks[5][1]?.date)
        assertNull(cal.weeks[5][2])
        assertEquals(8, cal.matchingEntryCount)
        assertEquals(7, cal.matchingDayCount)
        assertEquals(listOf(2, 3, 1, 1, 1), cal.moodCounter.byLevel.map { it.count })
        assertEquals("An 7 von 31 Tagen gibt es einen Eintrag.", cal.hint)
        assertEquals(emptyList(), cal.counters)
    }

    @Test fun gridWithSundayStart() {
        val cal = calendarMonth(DIARY, march, today, weekStart = 7)
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), cal.weekdays)
        assertEquals(5, cal.weeks.size) // 31 Tage ab Sonntag: 4 volle Wochen + 3 Tage
        assertEquals(d("2026-03-01"), cal.weeks[0][0]?.date)
        assertEquals(d("2026-03-31"), cal.weeks[4][2]?.date)
        assertNull(cal.weeks[4][3])
        // Standard ist die Einstellung des Tagebuchs (Montag)
        assertEquals(6, calendarMonth(DIARY, march, today).weeks.size)
    }

    @Test fun roundingOfDayColour() {
        assertEquals(1, roundedLevel(5.0))
        assertEquals(1, roundedLevel(4.5))
        assertEquals(2, roundedLevel(4.49))
        assertEquals(2, roundedLevel(3.5))
        assertEquals(3, roundedLevel(3.0))
        assertEquals(5, roundedLevel(1.0))
        assertEquals(4, roundedLevel(1.5))
    }

    @Test fun filterIsUnionOfSelections() {
        // Stufe 1: e1 (02.), e6 (07.). Arbeit: e4 (04.), e5 (05.). Vereinigung: 4 Eintraege an 4 Tagen.
        val cal = calendarMonth(DIARY, march, today, weekStart = 1, filter = CalendarFilter(levels = setOf(1), tagIds = setOf("arbeit")))
        assertEquals(4, cal.matchingEntryCount)
        assertEquals(4, cal.matchingDayCount)
        assertEquals(
            listOf(FilterCounter(FilterKind.LEVEL, "1", "Stufe 1", 2, 2), FilterCounter(FilterKind.ACTIVITY, "arbeit", "Arbeit", 2, 2)),
            cal.counters,
        )
        // 04.03.: zwei Eintraege, nur e4 passt; Farbe bleibt der Durchschnitt aller Eintraege
        val fourth = cal.weeks[1][2]!!
        assertEquals(2, fourth.entryCount)
        assertEquals(1, fourth.matchCount)
        assertEquals(3, fourth.level)
        assertEquals(0, cal.weeks[1][1]!!.matchCount) // 03.03. passt nicht
        assertEquals(listOf(2, 0, 0, 1, 1), cal.moodCounter.byLevel.map { it.count })
        assertEquals("4 Einträge an 4 Tagen passen zum Filter.", cal.hint)
    }

    @Test fun filterByMoodAndActivityOverlapping() {
        // Stimmung m2: e2 (03.), e3 (04.). Sport: e1, e2, e6, e7. Vereinigung: e1, e2, e3, e6, e7 = 5 Eintraege.
        val cal = calendarMonth(DIARY, march, today, filter = CalendarFilter(moodIds = setOf("m2"), tagIds = setOf("sport", "gibt-es-nicht")))
        assertEquals(5, cal.matchingEntryCount)
        assertEquals(
            listOf(FilterCounter(FilterKind.MOOD, "m2", "gut", 2, 2), FilterCounter(FilterKind.ACTIVITY, "sport", "Sport", 4, 4)),
            cal.counters,
        )
        val one = calendarMonth(DIARY, march, today, filter = CalendarFilter(levels = setOf(3)))
        assertEquals("1 Eintrag an 1 Tag passt zum Filter.", one.hint)
        assertEquals("In diesem Monat gibt es noch keinen Eintrag.", calendarMonth(DIARY, YearMonth.of(2025, 6), today).hint)
    }

    // ------------------------------------------------------------ Berichte

    @Test fun monthlyReport() {
        val r = monthlyReport(DIARY, march, today, zone = UTC)
        assertEquals(ReportKind.MONTH, r.kind)
        assertEquals("Monatsbericht März 2026", r.title)
        assertEquals(DateRange(d("2026-02-01"), d("2026-02-28")), r.previousRange)
        assertEquals(CountChange(8, 2), r.entries)
        assertEquals(6, r.entries.change)
        // Aktivitaeten: 2+1+1+1+1+2+2+0 = 10; Februar 1+1
        assertEquals(CountChange(10, 2), r.activities)
        assertEquals(ValueChange(3.5, 3.0), r.averageScore)
        assertEquals(0.5, r.averageScore.change)
        // Woerter: e1 Titel "Guter Start" (2) + "Heute war ich laufen im Park." (6); e5 "Kaffee & Kuchen" (3)
        assertEquals(CountChange(11, 0), r.words)
        assertEquals(31, r.moodCourse.size)
        assertEquals(3.0, r.moodCourse[3].averageScore)
        // Ziele: Sportziel 4 Haken, Schreibziel 3 Haken und vollstaendig
        assertEquals(listOf(4, 3), r.goals.map { it.completions })
        assertEquals(CountChange(7, 0), r.goalCompletions)
        assertEquals(1, r.goalsAchieved)
        // Fotos: e1 hat a1 (a2 Sprachnotiz, a3 geloescht), e6 hat a4 und nochmals a1
        assertEquals(2, r.photoCount)
        assertEquals(listOf("a1", "a4"), r.photoAssetIds)
        assertEquals(listOf("a1"), monthlyReport(DIARY, march, today, photoLimit = 1, zone = UTC).photoAssetIds)
    }

    @Test fun weeklyReport() {
        // Woche Mo 02.03. bis So 08.03.: e1 bis e7. Vorwoche 23.02.-01.03.: f1, f2.
        val r = weeklyReport(DIARY, d("2026-03-04"), today, weekStart = 1, zone = UTC)
        assertEquals(ReportKind.WEEK, r.kind)
        assertEquals("Woche vom 02.03.2026 bis 08.03.2026", r.title)
        assertEquals(DateRange(d("2026-03-02"), d("2026-03-08")), r.range)
        assertEquals(DateRange(d("2026-02-23"), d("2026-03-01")), r.previousRange)
        assertEquals(CountChange(7, 2), r.entries)
        assertEquals(CountChange(10, 2), r.activities)
        assertEquals(ValueChange(25.0 / 7, 3.0), r.averageScore) // 5+4+4+2+1+5+4
        assertEquals(CountChange(11, 0), r.words)
        assertEquals(7, r.moodCourse.size)
        assertEquals(listOf(5.0, 4.0, 3.0, 1.0, null, 5.0, 4.0), r.moodCourse.map { it.averageScore })
        // Sportziel: 4 von 7 Tagen. Schreibziel: Sa 07. und So 08. abgehakt -> erreicht. Vorwoche: Schreibziel am 01.03.
        assertEquals(listOf(4.0 / 7, 1.0), r.goals.map { it.successRate })
        assertEquals(CountChange(6, 1), r.goalCompletions)
        assertEquals(1, r.goalsAchieved)
        assertEquals(2, r.photoCount)
    }

    @Test fun weeklyReportWithSundayStartAndEmptyWeek() {
        // Wochenanfang Sonntag: So 08.03. bis Sa 14.03. -> e7, e8
        val r = weeklyReport(DIARY, d("2026-03-10"), today, weekStart = 7, zone = UTC)
        assertEquals(DateRange(d("2026-03-08"), d("2026-03-14")), r.range)
        assertEquals(CountChange(2, 6), r.entries) // Vorwoche 01.-07.: e1 bis e6
        assertEquals(ValueChange(3.5, 21.0 / 6), r.averageScore) // (4+3)/2 und (5+4+4+2+1+5)/6

        val empty = weeklyReport(Diary(), d("2026-03-10"), today, zone = UTC)
        assertEquals(CountChange(0, 0), empty.entries)
        assertNull(empty.averageScore.value)
        assertNull(empty.averageScore.change)
        assertEquals(0, empty.photoCount)
    }
}
