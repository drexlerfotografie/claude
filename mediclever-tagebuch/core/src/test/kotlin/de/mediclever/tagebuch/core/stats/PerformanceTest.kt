package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Goal
import de.mediclever.tagebuch.core.model.Tag
import java.time.YearMonth
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceTest {
    /** 2.000 Eintraege ueber 1.000 Tage (zwei je Tag), 30 Aktivitaeten, 3 Skalen, 5 Ziele, 1.000 Fotos. */
    private fun bigDiary(): Diary {
        val rnd = Random(42)
        val tags = (0 until 30).map { Tag(id = "t$it", groupId = if (it % 3 == 0) "g1" else if (it % 3 == 1) "g2" else null, name = "Aktivität $it") }
        val moods = listOf("m1", "m2", "m3", "m4", "m5")
        val first = d("2026-03-31").minusDays(999)
        val entries = (0 until 2000).map { i ->
            entry(
                "e$i", first.plusDays((i / 2).toLong()).toString(), moods[rnd.nextInt(5)],
                *List(rnd.nextInt(6)) { "t${rnd.nextInt(30)}" }.toTypedArray(),
                minute = if (i % 2 == 0) 480 else 1200,
                note = "<p>Ein kurzer Text mit einigen Wörtern.</p>",
                assets = if (i % 2 == 0) listOf("p${i / 2}") else emptyList(),
                scales = listOf(schlaf(300 + rnd.nextInt(240)), kaffee(rnd.nextInt(5)), energie("p${1 + rnd.nextInt(3)}")),
            )
        }
        return Diary(
            moods = MOODS, groups = GROUPS, tags = tags, scales = SCALES, entries = entries,
            assets = (0 until 1000).map { Asset(id = "p$it", fileName = "$it.jpg") },
            goals = (0 until 5).map { Goal(id = "g$it", name = "Ziel $it", tagId = "t$it", startDate = first.toString()) },
        )
    }

    @Test fun monthStatsForTwoThousandEntriesAreFast() {
        val diary = bigDiary()
        val month = YearMonth.of(2026, 3)
        val today = d("2026-03-31")
        // Aufwaermen (Klassen laden, JIT); jede Messung bekommt eine Kopie, damit der Index neu aufgebaut wird.
        repeat(5) { monthStats(diary.copy(), month, today, zone = UTC) }
        var best = Long.MAX_VALUE
        var result: MonthStats? = null
        repeat(5) {
            val copy = diary.copy()
            val t0 = System.nanoTime()
            result = monthStats(copy, month, today, zone = UTC)
            best = minOf(best, System.nanoTime() - t0)
        }
        val ms = best / 1_000_000.0
        println("monthStats mit 2000 Eintraegen: %.1f ms".format(ms))
        assertEquals(62, result!!.stats.entryCount) // 31 Tage mit je zwei Eintraegen
        assertEquals(1000, result!!.stats.streak.longest)
        assertTrue(ms < 500, "monthStats brauchte $ms ms")
    }

    @Test fun yearStatsAndCalendarAreFast() {
        val diary = bigDiary()
        val today = d("2026-03-31")
        repeat(3) { yearStats(diary.copy(), 2025, today, zone = UTC) }
        val t0 = System.nanoTime()
        val year = yearStats(diary.copy(), 2025, today, zone = UTC)
        calendarMonth(diary, YearMonth.of(2026, 3), today)
        monthlyReport(diary, YearMonth.of(2026, 3), today, zone = UTC)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        println("yearStats, Kalender und Bericht mit 2000 Eintraegen: %.1f ms".format(ms))
        assertEquals(730, year.stats.entryCount)
        assertTrue(ms < 1000, "brauchte $ms ms")
    }
}
