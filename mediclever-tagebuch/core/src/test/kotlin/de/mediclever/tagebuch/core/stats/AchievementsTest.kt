package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Goal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementsTest {
    @Test fun catalogueIsFixed() {
        val all = achievements(Diary(), d("2026-03-10"), UTC)
        assertEquals(35, all.size) // 8 Eintraege + 7 Serien + 6 Ziele + 10 Jahre + 4 Fotos
        assertEquals(35, all.map { it.key }.toSet().size)
        assertEquals(35, all.map { it.title }.toSet().size)
        assertEquals(listOf(1, 10, 50, 100, 250, 500, 1000, 2000), all.filter { it.category == AchievementCategory.ENTRIES }.map { it.target })
        assertEquals(listOf(3, 7, 14, 30, 60, 100, 365), all.filter { it.category == AchievementCategory.STREAK }.map { it.target })
        assertEquals(listOf(1, 10, 50, 100, 500, 1000), all.filter { it.category == AchievementCategory.GOALS }.map { it.target })
        assertEquals((1..10).toList(), all.filter { it.category == AchievementCategory.YEARS }.map { it.target })
        assertEquals(listOf(1, 50, 250, 1000), all.filter { it.category == AchievementCategory.PHOTOS }.map { it.target })
        assertTrue(all.none { it.unlocked || it.progress != 0 })
        assertTrue(all.all { it.title.isNotBlank() && it.description.endsWith(".") && '!' !in it.title + it.description })
        assertEquals("entries_10", all[1].key)
        assertEquals("years_10", all.last { it.category == AchievementCategory.YEARS }.key)
    }

    @Test fun progressFromSmallDiary() {
        val a = achievements(DIARY, d("2026-03-10"), UTC).associateBy { it.key }
        // 10 Eintraege, laengste Reihe 4 Tage, 7 Haken, 2 Fotos, erster Eintrag vor 11 Tagen
        assertTrue(a.getValue("entries_10").unlocked)
        assertEquals(10, a.getValue("entries_50").progress)
        assertFalse(a.getValue("entries_50").unlocked)
        assertTrue(a.getValue("streak_3").unlocked)
        assertEquals(3, a.getValue("streak_3").progress) // nie mehr als das Ziel
        assertEquals(4, a.getValue("streak_7").progress)
        assertEquals(7, a.getValue("goals_10").progress)
        assertTrue(a.getValue("photos_1").unlocked)
        assertEquals(2, a.getValue("photos_50").progress)
        assertEquals(0, a.getValue("years_1").progress)
    }

    @Test fun onlyDataUpToTodayCounts() {
        // Stand 03.03.: f1, f2, e1, e2 -> 4 Eintraege; Reihe 02.-03. = 2; Haken: Schreibziel 01., Sportziel 02., 03.
        val a = achievements(DIARY, d("2026-03-03"), UTC).associateBy { it.key }
        assertEquals(4, a.getValue("entries_10").progress)
        assertEquals(2, a.getValue("streak_3").progress)
        assertEquals(3, a.getValue("goals_10").progress)
        assertEquals(1, a.getValue("photos_50").progress)
    }

    @Test fun yearsOfUseAndLongSeries() {
        // 400 Tage in Folge ab 01.01.2024, ein Eintrag je Tag, jeder mit eigenem Foto und abgehaktem Ziel
        val start = d("2024-01-01")
        val entries = (0 until 400).map { entry("e$it", start.plusDays(it.toLong()).toString(), "m3", "sport", assets = listOf("p$it")) }
        val diary = Diary(
            moods = MOODS, tags = TAGS, entries = entries,
            assets = (0 until 400).map { Asset(id = "p$it", fileName = "$it.jpg") },
            goals = listOf(Goal(id = "g", name = "Sport", tagId = "sport", startDate = "2024-01-01")),
        )
        // Stand 31.12.2025: erster Eintrag vor 1 Jahr und 364 Tagen -> 1 volles Jahr
        val a = achievements(diary, d("2025-12-31"), UTC).associateBy { it.key }
        assertTrue(a.getValue("years_1").unlocked)
        assertFalse(a.getValue("years_2").unlocked)
        assertEquals(1, a.getValue("years_2").progress)
        assertTrue(achievements(diary, d("2026-01-01"), UTC).first { it.key == "years_2" }.unlocked)
        assertTrue(a.getValue("streak_365").unlocked)
        assertTrue(a.getValue("entries_250").unlocked)
        assertEquals(400, a.getValue("entries_500").progress)
        assertTrue(a.getValue("goals_100").unlocked)
        assertEquals(400, a.getValue("goals_500").progress)
        assertTrue(a.getValue("photos_250").unlocked)
        assertEquals(400, a.getValue("photos_1000").progress)
        // Stand 09.01.2024: 9 Tage
        val early = achievements(diary, d("2024-01-09"), UTC).associateBy { it.key }
        assertTrue(early.getValue("streak_7").unlocked)
        assertEquals(9, early.getValue("streak_14").progress)
        assertEquals(9, early.getValue("entries_10").progress)

        // Januar 2024 schaltet frei: Eintraege 1 und 10, Serien 3, 7, 14 und 30, Ziele 1 und 10, Foto 1
        val january = achievementSummary(diary, DateRange(d("2024-01-01"), d("2024-01-31")), d("2025-12-31"), UTC)
        assertEquals(
            listOf("entries_1", "entries_10", "streak_3", "streak_7", "streak_14", "streak_30", "goals_1", "goals_10", "photos_1"),
            january.unlockedInPeriod.map { it.key },
        )
        assertEquals(9, january.unlockedCount)
        // Offene Anteile Ende Januar (31): entries_50 31/50, streak_60 31/60, goals_50 31/50, photos_50 31/50 -> der erste im Katalog
        assertEquals("entries_50", january.next?.key)
        // Februar 2024 (bis Tag 60): Eintraege 50, Serie 60, Ziele 50, Fotos 50
        val february = achievementSummary(diary, DateRange(d("2024-02-01"), d("2024-02-29")), d("2025-12-31"), UTC)
        assertEquals(listOf("entries_50", "streak_60", "goals_50", "photos_50"), february.unlockedInPeriod.map { it.key })
        // Zeitraum in der Zukunft: nichts neu
        assertEquals(emptyList(), achievementSummary(diary, DateRange(d("2026-01-01"), d("2026-01-31")), d("2025-12-31"), UTC).unlockedInPeriod)
    }
}
