package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.ImportantDay
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryListTest {
    private val march = YearMonth.of(2026, 3)

    @Test fun entriesOfMonthNewestFirst() {
        val days = entriesByDay(DIARY, march)
        assertEquals(
            listOf("2026-03-09", "2026-03-08", "2026-03-07", "2026-03-05", "2026-03-04", "2026-03-03", "2026-03-02"),
            days.map { it.date.toString() },
        )
        // am 04.03. steht der Eintrag von 20:00 vor dem von 08:00
        assertEquals(listOf("e4", "e3"), days[4].entries.map { it.id })
        assertEquals(emptyList(), entriesByDay(DIARY, YearMonth.of(2026, 1)))
    }

    @Test fun missingDaysBetweenEntries() {
        // Im Maerz fehlt zwischen 05. und 07. nur der 06. Der 01. (vor dem ersten Eintrag) zaehlt nicht.
        val one = MissingDays(d("2026-03-06"), d("2026-03-06"), 1, "1 Tag fehlt")
        // Heute 10.03.: letzter Eintrag am 09., heute selbst fehlt nicht.
        assertEquals(listOf(one), missingDays(DIARY, march, d("2026-03-10")))
        // Heute 13.03.: 10., 11., 12. fehlen
        assertEquals(
            listOf(MissingDays(d("2026-03-10"), d("2026-03-12"), 3, "3 Tage fehlen"), one),
            missingDays(DIARY, march, d("2026-03-13")),
        )
        // Rueckblick aus dem April: nur die Luecke im Monat
        assertEquals(listOf(one), missingDays(DIARY, march, d("2026-04-20")))
        // Februar: 27. und 28. liegen nebeneinander
        assertEquals(emptyList(), missingDays(DIARY, YearMonth.of(2026, 2), d("2026-04-20")))
    }

    @Test fun timelineInterleavesGaps() {
        val items = monthTimeline(DIARY, march, d("2026-03-13"))
        assertEquals(9, items.size) // 7 Tage + 2 Luecken
        assertTrue(items[0] is TimelineItem.Gap)
        assertEquals(d("2026-03-09"), (items[1] as TimelineItem.Day).day.date)
        assertEquals(d("2026-03-07"), (items[3] as TimelineItem.Day).day.date)
        assertEquals("1 Tag fehlt", (items[4] as TimelineItem.Gap).gap.text)
        assertEquals(d("2026-03-05"), (items[5] as TimelineItem.Day).day.date)
    }

    @Test fun memoriesFromSameDayInEarlierYears() {
        val diary = Diary(
            moods = MOODS, assets = ASSETS,
            entries = listOf(
                entry("y1b", "2025-03-02", "m1", minute = 900, assets = listOf("a4", "a2")),
                entry("y1a", "2025-03-02", "m2", minute = 600, assets = listOf("a1")),
                entry("y3", "2023-03-02", "m3"),
                entry("other", "2025-03-03", "m3"),
                entry("gone", "2024-03-02", "m3", deleted = true),
            ),
        )
        val list = memories(diary, d("2026-03-02"))
        assertEquals(listOf(1, 3), list.map { it.yearsAgo })
        assertEquals(listOf("Vor 1 Jahr", "Vor 3 Jahren"), list.map { it.label })
        assertEquals(listOf("y1a", "y1b"), list[0].entries.map { it.id })
        assertEquals(listOf("a1", "a4"), list[0].photoAssetIds) // a2 ist eine Sprachnotiz
        assertEquals(d("2023-03-02"), list[1].date)
        assertEquals(emptyList(), memories(diary, d("2026-03-04")))
        // am 02.03.2025 selbst: der eigene Tag ist kein Rueckblick, 2024 ist geloescht, bleibt 2023
        assertEquals(listOf("y3"), memories(diary, d("2025-03-02")).flatMap { m -> m.entries.map { it.id } })
        assertEquals(2, memories(diary, d("2025-03-02")).single().yearsAgo)
    }

    @Test fun memoriesAroundLeapDay() {
        val diary = Diary(
            moods = MOODS,
            entries = listOf(entry("s", "2024-02-29", "m1"), entry("a", "2024-02-28", "m1"), entry("b", "2025-02-28", "m1")),
        )
        // 28.02.2027 (kein Schaltjahr): auch der 29.02.2024 erscheint
        assertEquals(
            listOf("2025-02-28", "2024-02-29", "2024-02-28"),
            memories(diary, d("2027-02-28")).map { it.date.toString() },
        )
        assertEquals(listOf(2, 3, 3), memories(diary, d("2027-02-28")).map { it.yearsAgo })
        // 28.02.2028 (Schaltjahr): der 29. hat morgen seinen eigenen Tag
        assertEquals(listOf("2025-02-28", "2024-02-28"), memories(diary, d("2028-02-28")).map { it.date.toString() })
        // 29.02.2028: nur fruehere Schaltjahre
        assertEquals(listOf("2024-02-29"), memories(diary, d("2028-02-29")).map { it.date.toString() })
        assertEquals("Vor 4 Jahren", memories(diary, d("2028-02-29")).single().label)
    }

    private fun day(id: String, date: String, yearly: Boolean = true, sortOrder: Int = 0, deleted: Boolean = false) =
        ImportantDay(id = id, name = id, date = date, yearly = yearly, sortOrder = sortOrder, deleted = deleted)

    @Test fun importantDayNumbers() {
        val today = d("2026-03-10")
        // 01.03.2025 jaehrlich: 365 + 9 Tage her; naechster Jahrestag 01.03.2027 in 365 - 9 Tagen, der zweite
        val a = importantDayInfo(day("a", "2025-03-01"), today)!!
        assertEquals(374L, a.daysSince)
        assertEquals(d("2027-03-01"), a.nextDate)
        assertEquals(356L, a.daysUntil)
        assertEquals(2, a.anniversaryNumber)
        // 20.03.2025 jaehrlich: 365 - 10 Tage her; naechster Jahrestag in 10 Tagen, der erste
        val b = importantDayInfo(day("b", "2025-03-20"), today)!!
        assertEquals(355L, b.daysSince)
        assertEquals(d("2026-03-20"), b.nextDate)
        assertEquals(10L, b.daysUntil)
        assertEquals(1, b.anniversaryNumber)
        // einmalig und vorbei
        val c = importantDayInfo(day("c", "2026-03-01", yearly = false), today)!!
        assertEquals(9L, c.daysSince)
        assertNull(c.nextDate)
        assertNull(c.daysUntil)
        // einmalig in der Zukunft: 21 Tage bis 31.03. plus 1
        val e = importantDayInfo(day("e", "2026-04-01", yearly = false), today)!!
        assertNull(e.daysSince)
        assertEquals(d("2026-04-01"), e.nextDate)
        assertEquals(22L, e.daysUntil)
        assertEquals(0, e.anniversaryNumber)
        // Jahrestag ist heute
        val f = importantDayInfo(day("f", "2025-03-10"), today)!!
        assertEquals(365L, f.daysSince)
        assertEquals(0L, f.daysUntil)
        assertTrue(f.isToday)
        assertNull(importantDayInfo(day("kaputt", "kein Datum"), today))
    }

    @Test fun importantDayOnLeapDay() {
        val leap = day("s", "2024-02-29")
        // 2027 hat keinen 29.02. -> 28.02.
        val a = importantDayInfo(leap, d("2026-03-10"))!!
        assertEquals(d("2027-02-28"), a.nextDate)
        assertEquals(3, a.anniversaryNumber)
        // 2028 ist ein Schaltjahr
        val b = importantDayInfo(leap, d("2028-02-01"))!!
        assertEquals(d("2028-02-29"), b.nextDate)
        assertEquals(28L, b.daysUntil)
        assertEquals(d("2025-02-28"), anniversaryIn(d("2024-02-29"), 2025))
    }

    @Test fun importantDaysOfAMonthAndOrder() {
        val diary = Diary(
            importantDays = listOf(
                day("b", "2025-03-20"), day("a", "2025-03-01", sortOrder = 1), day("c", "2026-03-01", yearly = false, sortOrder = 0),
                day("e", "2026-04-01", yearly = false), day("s", "2024-02-29"), day("weg", "2025-03-05", deleted = true),
            ),
        )
        // Maerz 2026: c (01.03., das Datum selbst), a (01.03., 1. Jahrestag), b (20.03., 1. Jahrestag)
        assertEquals(
            listOf(Triple("c", "2026-03-01", 0), Triple("a", "2026-03-01", 1), Triple("b", "2026-03-20", 1)),
            importantDaysInMonth(diary, YearMonth.of(2026, 3)).map { Triple(it.day.id, it.date.toString(), it.anniversaryNumber) },
        )
        // Maerz 2027: der einmalige Tag c faellt weg
        assertEquals(listOf("a", "b"), importantDaysInMonth(diary, YearMonth.of(2027, 3)).map { it.day.id })
        // vor dem Datum gibt es keinen Jahrestag
        assertEquals(emptyList(), importantDaysInMonth(diary, YearMonth.of(2024, 3)))
        // Februar 2027: der 29.02. faellt auf den 28.02.
        assertEquals(listOf("2027-02-28" to 3), importantDaysInMonth(diary, YearMonth.of(2027, 2)).map { it.date.toString() to it.anniversaryNumber })
        // Stand 10.03.2026: b in 10 Tagen, e in 22, s in 355 (28.02.2027), a in 356, c ohne Termin
        assertEquals(listOf("b", "e", "s", "a", "c"), importantDayInfos(diary, d("2026-03-10")).map { it.day.id })
    }
}
