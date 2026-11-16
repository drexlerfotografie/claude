package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Diary
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Erwartungen lassen sich aus der Tabelle an [ENTRIES] nachrechnen. */
class PeriodStatsTest {
    private val march = YearMonth.of(2026, 3)
    private val range = DateRange.ofMonth(march)
    private val today = d("2026-03-10")
    private val stats = monthStats(DIARY, march, today, overlayTagId = "sport", zone = UTC).stats

    @Test fun totals() {
        assertEquals(8, stats.entryCount)
        assertEquals(7, stats.dayCount)
        assertEquals(28.0 / 8, stats.averageScore)
        assertEquals(2, stats.previousEntryCount)
        assertEquals(3.0, stats.previousAverageScore) // (2 + 4) / 2
    }

    @Test fun daysInARow() {
        // Tage mit Eintrag: 27.02., 28.02., 02.-05.03., 07.-09.03.
        assertEquals(
            DayStreak(
                current = 3, longest = 4,
                lastFiveDays = listOf(
                    DayFlag(d("2026-03-06"), false), DayFlag(d("2026-03-07"), true), DayFlag(d("2026-03-08"), true),
                    DayFlag(d("2026-03-09"), true), DayFlag(d("2026-03-10"), false),
                ),
            ),
            stats.streak,
        )
        assertEquals(3, dayStreak(DIARY, d("2026-03-09")).current) // endet heute
        assertEquals(0, dayStreak(DIARY, d("2026-03-11")).current) // gestern und heute leer
        assertEquals(2, dayStreak(DIARY, d("2026-03-01")).current) // 27. und 28.02., endet gestern
        assertEquals(2, dayStreak(DIARY, d("2026-03-01")).longest) // spaetere Tage zaehlen noch nicht
    }

    @Test fun moodCourseByDay() {
        val course = stats.moodCourse
        assertEquals(31, course.size)
        assertEquals(MoodPoint(d("2026-03-01"), null, 0, false), course[0])
        assertEquals(MoodPoint(d("2026-03-02"), 5.0, 1, true), course[1])
        assertEquals(MoodPoint(d("2026-03-03"), 4.0, 1, true), course[2])
        assertEquals(MoodPoint(d("2026-03-04"), 3.0, 2, false), course[3]) // (4 + 2) / 2
        assertEquals(MoodPoint(d("2026-03-05"), 1.0, 1, false), course[4])
        assertEquals(MoodPoint(d("2026-03-06"), null, 0, false), course[5])
        assertEquals(MoodPoint(d("2026-03-07"), 5.0, 1, true), course[6])
        assertEquals(MoodPoint(d("2026-03-08"), 4.0, 1, true), course[7])
        assertEquals(MoodPoint(d("2026-03-09"), 3.0, 1, false), course[8])
        assertEquals(MoodPoint(d("2026-03-10"), null, 0, false), course[9]) // geloeschter Eintrag
        assertEquals("sport", stats.overlayTag?.id)
        assertEquals(setOf(d("2026-03-02"), d("2026-03-03"), d("2026-03-07"), d("2026-03-08")), activityDays(DIARY, range, "sport"))
    }

    @Test fun moodCounter() {
        val c = stats.moodCounter
        assertEquals(8, c.total)
        assertEquals(listOf(2, 3, 1, 1, 1), c.byLevel.map { it.count })
        assertEquals(listOf(1, 2, 3, 4, 5), c.byLevel.map { it.level })
        assertEquals(0.25, c.byLevel[0].share)
        assertEquals(
            listOf("m1" to 2, "m2" to 2, "m2b" to 1, "m3" to 1, "m4" to 1, "m5" to 1),
            c.byMood.map { it.mood.id to it.count },
        )
    }

    @Test fun activityCounterRankedByCountThenName() {
        val c = stats.activityCounter
        assertEquals(10, c.total)
        assertEquals(listOf("Sport" to 4, "Arbeit" to 2, "Freunde" to 2, "Lesen" to 2), c.items.map { it.tag.name to it.count })
        assertEquals(0.4, c.items[0].share)
    }

    @Test fun groupDistribution() {
        // Bewegung: Sport 4. Ruhe: Lesen 2 + Freunde 2. Ohne Gruppe: Arbeit 2.
        assertEquals(
            listOf(Triple("Bewegung", 3, 4), Triple("Ruhe", 5, 4), Triple("Ohne Gruppe", null, 2)),
            stats.groupDistribution.map { Triple(it.name, it.colorIndex, it.count) },
        )
        assertEquals(listOf(0.4, 0.4, 0.2), stats.groupDistribution.map { it.share })
        assertNull(stats.groupDistribution.last().group)
    }

    @Test fun weekdayAverages() {
        // Mo: e1 (5), e8 (3). Di: 4. Mi: 4 und 2. Do: 1. Fr: nichts. Sa: 5. So: 4.
        assertEquals(listOf(4.0, 4.0, 3.0, 1.0, null, 5.0, 4.0), stats.weekdayMood.map { it.averageScore })
        assertEquals(listOf(2, 1, 2, 1, 0, 1, 1), stats.weekdayMood.map { it.entryCount })
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), stats.weekdayMood.map { it.isoDay })
    }

    @Test fun influenceOfActivities() {
        // Sport: mit (5+4+5+4)/4 = 4,5; ohne (28-18)/4 = 2,5. Alle anderen haben nur 2 Eintraege.
        val sport = stats.activityInfluence.single()
        assertEquals("sport", sport.tag.id)
        assertEquals(4, sport.entryCount)
        assertEquals(4.5, sport.averageWith)
        assertEquals(2.5, sport.averageWithout)
        assertEquals(2.0, sport.delta)

        val all = activityInfluence(DIARY, range, minEntries = 2)
        assertEquals(listOf("Sport", "Freunde", "Lesen", "Arbeit"), all.map { it.tag.name })
        assertEquals(4.5 - 19.0 / 6, all[1].delta, 1e-9) // Freunde: mit (5+4)/2, ohne (28-9)/6
        assertEquals(1.5 - 25.0 / 6, all[3].delta, 1e-9) // Arbeit: mit (2+1)/2, ohne (28-3)/6
    }

    @Test fun stability() {
        // Tagesmittel 5, 4, 3, 1, 5, 4, 3 -> Spruenge 1+1+2+4+1+1 = 10, Mittel 10/6; 100 * (1 - 10/24) = 58,3
        assertEquals(58, stats.stability)
        assertNull(moodStability(DIARY, DateRange(d("2026-03-03"), d("2026-03-03")))) // nur ein Tag
        // 28.02. (4) und 03.03. (4): keine Veraenderung
        val flat = Diary(moods = MOODS, entries = ENTRIES.filter { it.id == "f2" || it.id == "e2" })
        assertEquals(100, moodStability(flat, DateRange(d("2026-02-01"), d("2026-03-31"))))
        // 5 und 1 im Wechsel: jeder Sprung ist 4
        val swing = Diary(
            moods = MOODS,
            entries = listOf(entry("a", "2026-03-01", "m1"), entry("b", "2026-03-02", "m5"), entry("c", "2026-03-03", "m1")),
        )
        assertEquals(0, moodStability(swing, range))
    }

    @Test fun oftenTogether() {
        // Stufe 2: e2 [Sport], e3 [Lesen], e7 [Sport, Freunde]
        val level2 = stats.oftenTogether[1]
        assertEquals(2, level2.level)
        assertEquals(3, level2.entryCount)
        assertEquals(listOf("Sport" to 2, "Freunde" to 1, "Lesen" to 1), level2.items.map { it.tag.name to it.count })
        assertEquals(2.0 / 3, level2.items[0].share)
        // Stufe 3: e8 ohne Aktivitaet
        assertEquals(LevelActivities(3, 1, emptyList()), stats.oftenTogether[2])
        assertEquals(level2, frequentActivitiesForLevel(DIARY, range, 2))
        assertEquals(listOf("Sport"), frequentActivitiesForLevel(DIARY, range, 2, limit = 1).items.map { it.tag.name })
        // Stimmung m2: e2 [Sport], e3 [Lesen]
        assertEquals(listOf("Lesen" to 0.5, "Sport" to 0.5), frequentActivitiesForMood(DIARY, range, "m2").map { it.tag.name to it.share })
        // Mit Sport (e1, e2, e6, e7): Freunde 2-mal, Lesen 1-mal
        assertEquals(
            listOf(Triple("Freunde", 2, 0.5), Triple("Lesen", 1, 0.25)),
            coOccurringActivities(DIARY, range, "sport").map { Triple(it.tag.name, it.count, it.share) },
        )
    }

    @Test fun longestSeriesOfGoodDays() {
        // Gut (>= 4): 02., 03. | 07., 08. Gleich lang, die spaetere zaehlt. Eintraege e6 [Sport, Lesen], e7 [Sport, Freunde].
        val g = stats.goodDays!!
        assertEquals(2, g.length)
        assertEquals(d("2026-03-07"), g.start)
        assertEquals(d("2026-03-08"), g.end)
        assertEquals(listOf(Triple("Sport", 2, 1.0), Triple("Freunde", 1, 0.5), Triple("Lesen", 1, 0.5)), g.activities.map { Triple(it.tag.name, it.count, it.share) })
        assertNull(longestGoodDayStreak(DIARY, DateRange(d("2026-03-04"), d("2026-03-05"))))
        // Ueber die Monatsgrenze: 28.02. (4) ist gut, 01.03. leer -> im Februar Laenge 1
        assertEquals(1, longestGoodDayStreak(DIARY, DateRange.ofMonth(YearMonth.of(2026, 2)))!!.length)
    }

    @Test fun scales() {
        val (schlaf, kaffee, energie) = stats.scales
        // Schlaf (Tagesdurchschnitt, Minuten): 04.03. (420+480)/2 = 450, 05.03. 360 -> 405; Vormonat 480
        assertEquals("schlaf", schlaf.scale.id)
        assertEquals(31, schlaf.series.size)
        assertEquals(ValuePoint(d("2026-03-04"), 450.0), schlaf.series[3])
        assertEquals(ValuePoint(d("2026-03-05"), 360.0), schlaf.series[4])
        assertEquals(ValuePoint(d("2026-03-06"), null), schlaf.series[5])
        assertEquals(405.0, schlaf.average)
        assertEquals(480.0, schlaf.previousAverage)
        assertEquals(-75.0, schlaf.change)
        assertEquals(ChangeRating.BAD, schlaf.changeRating) // hoeher waere besser
        assertEquals(2, schlaf.dayCount)
        // Kaffee (Tagessumme): 04.03. 2+1 = 3, 05.03. 1 -> 2,0; Vormonat 4; weniger ist besser
        assertEquals(3.0, kaffee.series[3].value)
        assertEquals(2.0, kaffee.average)
        assertEquals(-2.0, kaffee.change)
        assertEquals(ChangeRating.GOOD, kaffee.changeRating)
        // Energie (Text, Position): 04.03. (3+1)/2 = 2; Vormonat 1; Trend neutral
        assertEquals(2.0, energie.average)
        assertEquals(1.0, energie.change)
        assertEquals(ChangeRating.NEUTRAL, energie.changeRating)
        assertEquals(1, energie.dayCount)
    }

    @Test fun goalsAndAchievements() {
        val (sport, schreiben) = stats.goals
        // Sportziel: Haken am 02., 03., 07., 08.; geplant 01.-10. = 10; Rate ohne das offene Heute: 4 von 9
        assertEquals("sportziel", sport.goal.id)
        assertEquals(4, sport.completions)
        assertEquals(10.0, sport.expected)
        assertEquals(4.0 / 9, sport.successRate)
        // Schreibziel: Sa/So bis 10.03. sind 01., 07., 08. -> alle abgehakt
        assertEquals(3, schreiben.completions)
        assertEquals(3.0, schreiben.expected)
        assertEquals(1.0, schreiben.successRate)

        val a = stats.achievements
        // Stand 10.03.: 10 Eintraege, laengste Reihe 4, 7 Haken, 2 Fotos, 0 Jahre.
        assertEquals(5, a.unlockedCount)
        assertEquals(35, a.totalCount)
        // Stand 28.02.: 2 Eintraege, Reihe 2, keine Haken, keine Fotos -> nur entries_1 war schon erreicht.
        assertEquals(listOf("entries_10", "streak_3", "goals_1", "photos_1"), a.unlockedInPeriod.map { it.key })
        // Anteile der offenen: goals_10 7/10, streak_7 4/7, entries_50 10/50
        assertEquals("goals_10", a.next?.key)
        assertEquals(7, a.next?.progress)
    }

    @Test fun insightsAreCalmGermanSentences() {
        assertEquals(
            listOf(
                "Im März 2026 hast Du 8 Einträge an 7 Tagen geschrieben.",
                "Das sind 6 Einträge mehr als im Vormonat.",
                "Deine durchschnittliche Stimmung lag bei 3,5 von 5 und damit um 0,5 höher als im Vormonat.",
                "Dein bester Wochentag war der Samstag mit durchschnittlich 5,0 von 5.",
                "Deine häufigste Aktivität war „Sport“ in 4 Einträgen.",
                "Deine längste Serie guter Tage umfasste 2 Tage.",
            ),
            stats.insights,
        )
        assertTrue(stats.insights.none { '!' in it })
    }

    @Test fun insightsForFebruaryWithoutComparison() {
        val feb = monthStats(DIARY, YearMonth.of(2026, 2), today, zone = UTC).stats
        // Fr 27.02. (2 Punkte) [Arbeit], Sa 28.02. (4) [Sport]; im Januar nichts.
        assertEquals(
            listOf(
                "Im Februar 2026 hast Du 2 Einträge an 2 Tagen geschrieben.",
                "Deine durchschnittliche Stimmung lag bei 3,0 von 5.",
                "Dein bester Wochentag war der Samstag mit durchschnittlich 4,0 von 5.",
                "Deine häufigste Aktivität war „Arbeit“ in 1 Eintrag.",
            ),
            feb.insights,
        )
    }

    @Test fun enoughDataFlags() {
        assertEquals(EnoughData(true, true, true, true, true, true, true), stats.enoughData)

        val feb = monthStats(DIARY, YearMonth.of(2026, 2), today, zone = UTC).stats.enoughData
        // 2 Tage, 2 Eintraege mit Stimmung, 2 mit Aktivitaet
        assertEquals(EnoughData(any = true, overall = false, moodCourse = true, weekdayMood = false, activityInfluence = false, stability = true, oftenTogether = false), feb)

        val empty = monthStats(DIARY, YearMonth.of(2025, 6), today, zone = UTC).stats
        assertEquals(EnoughData(false, false, false, false, false, false, false), empty.enoughData)
        assertEquals(emptyList(), empty.insights)
        assertNull(empty.averageScore)
        assertNull(empty.stability)
        assertNull(empty.goodDays)
        assertEquals(0, empty.moodCounter.total)
        assertEquals(30, empty.moodCourse.size)
        assertFalse(empty.moodCourse.any { it.averageScore != null })
    }

    @Test fun entryWithDeletedOrUnknownMoodCountsAsEntryButNotAsMood() {
        val diary = Diary(
            moods = MOODS, tags = TAGS,
            entries = listOf(entry("a", "2026-03-02", "m2", "sport"), entry("b", "2026-03-02", "gone", "sport"), entry("c", "2026-03-03", "fehlt")),
        )
        val s = monthStats(diary, march, today, zone = UTC).stats
        assertEquals(3, s.entryCount)
        assertEquals(2, s.dayCount)
        assertEquals(4.0, s.averageScore)
        assertEquals(1, s.moodCounter.total)
        assertEquals(2, s.activityCounter.items.single().count)
        assertNull(s.moodCourse[2].averageScore)
        assertEquals(1, s.moodCourse[2].entryCount)
    }

    @Test fun yearStatsUseOnePointPerMonth() {
        val year = yearStats(DIARY, 2026, today, overlayTagId = "sport", zone = UTC)
        assertEquals(2026, year.year)
        val s = year.stats
        assertEquals(10, s.entryCount)
        assertEquals(9, s.dayCount)
        assertEquals(12, s.moodCourse.size)
        assertEquals(MoodPoint(d("2026-01-01"), null, 0, false), s.moodCourse[0])
        assertEquals(MoodPoint(d("2026-02-01"), 3.0, 2, true), s.moodCourse[1])
        assertEquals(MoodPoint(d("2026-03-01"), 3.5, 8, true), s.moodCourse[2])
        // Schlaf: Tageswerte 480 (28.02.), 450, 360 -> Jahr 430; Februar 480, Maerz 405; kein Vorjahr
        val schlaf = s.scales.first()
        assertEquals(12, schlaf.series.size)
        assertEquals(ValuePoint(d("2026-02-01"), 480.0), schlaf.series[1])
        assertEquals(ValuePoint(d("2026-03-01"), 405.0), schlaf.series[2])
        assertEquals(430.0, schlaf.average)
        assertNull(schlaf.change)
        assertEquals(ChangeRating.NEUTRAL, schlaf.changeRating)
        assertEquals("Im Jahr 2026 hast Du 10 Einträge an 9 Tagen geschrieben.", s.insights.first())
        // Sport: mit (4+5+4+5+4)/5 = 4,4; ohne (2+4+2+1+3)/5 = 2,4
        assertEquals(2.0, s.activityInfluence.first().delta, 1e-9)
        assertEquals(4, s.streak.longest)
    }
}
