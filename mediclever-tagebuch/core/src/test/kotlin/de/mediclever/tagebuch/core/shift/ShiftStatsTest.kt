package de.mediclever.tagebuch.core.shift

import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.stats.DateRange
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShiftStatsTest {
    private val moods = (1..5).map { Mood(id = "m$it", level = it, name = "Stufe $it", icon = "a") }
    private val group = TagGroup(id = "g", name = "Dienst")
    private val tag = Tag(id = "tag", groupId = "g", name = "Tagschicht", sortOrder = 0)
    private val nacht = Tag(id = "nacht", groupId = "g", name = "Nachtschicht", sortOrder = 1)
    private val frei = Tag(id = "frei", groupId = "g", name = "Frei", sortOrder = 2)
    private val start = LocalDate.parse("2026-10-01")
    private var n = 0

    /** score ist der Tageswert 1 bis 5, 5 ist der beste. */
    private fun e(day: Int, score: Int, shift: String?) = Entry(id = "e${n++}", timestamp = 0, offsetMinutes = 0, localDate = start.plusDays(day.toLong()).toString(),
        minuteOfDay = 600, moodId = "m${6 - score}", tagIds = listOfNotNull(shift))

    private fun diary(entries: List<Entry>) = Diary(moods = moods, groups = listOf(group), tags = listOf(tag, nacht, frei), entries = entries)
    private val range = DateRange(start, start.plusDays(60))

    @Test fun averagesPerShiftBestFirst() {
        val d = diary(listOf(e(0, 4, "tag"), e(1, 2, "nacht"), e(2, 3, "nacht"), e(3, 5, "frei"), e(4, 1, null)))
        val a = ShiftStats.averages(d, range)
        assertEquals(listOf("frei", "tag", "nacht"), a.map { it.tag.id })
        assertEquals(2.5, a.last().average)
        assertEquals(2, a.last().entries)
        assertTrue(a.first().free)
    }

    @Test fun blockCourseAveragesPositionsAcrossBlocks() {
        val entries = mutableListOf<Entry>()
        // drei Nachtbloecke mit je drei Naechten und zwei freien Tagen danach
        for (b in 0..2) {
            val o = b * 10
            entries += listOf(e(o, 4, "nacht"), e(o + 1, 3, "nacht"), e(o + 2, 2, "nacht"), e(o + 3, 2, "frei"), e(o + 4, 4, null), e(o + 5, 5, "tag"))
        }
        val c = ShiftStats.blockCourse(diary(entries), nacht, range)
        assertEquals(3, c.blocks)
        assertEquals(listOf(4.0, 3.0, 2.0), c.during)
        assertEquals(listOf(2.0, 4.0), c.after)
        assertTrue(c.enough)
        assertEquals(2.0, c.drop)
    }

    @Test fun singleShiftsAreNoBlockAndWorkEndsRecovery() {
        val entries = listOf(e(0, 4, "nacht"), e(2, 3, "nacht"), e(3, 3, "nacht"), e(4, 5, "tag"), e(5, 5, "frei"))
        val c = ShiftStats.blockCourse(diary(entries), nacht, range)
        assertEquals(1, c.blocks)
        // direkt nach dem Block folgt ein Dienst: keine freien Tage danach
        assertEquals(emptyList(), c.after)
        assertTrue(!c.enough)
    }

    @Test fun rarePositionsAreCutOff() {
        val entries = mutableListOf<Entry>()
        for (b in 0..3) { val o = b * 10; entries += listOf(e(o, 4, "tag"), e(o + 1, 3, "tag")) }
        entries += e(32, 1, "tag") // nur ein Block hat einen dritten Tag
        val c = ShiftStats.blockCourse(diary(entries), tag, range)
        assertEquals(4, c.blocks)
        assertEquals(listOf(4.0, 3.0), c.during)
    }

    @Test fun strongestBlockPicksTheBiggestDrop() {
        val entries = mutableListOf<Entry>()
        for (b in 0..2) {
            val o = b * 14
            entries += listOf(e(o, 4, "nacht"), e(o + 1, 2, "nacht"), e(o + 3, 4, "tag"), e(o + 4, 4, "tag"), e(o + 6, 5, "frei"), e(o + 7, 5, "frei"))
        }
        assertEquals("nacht", ShiftStats.strongestBlock(diary(entries), range)?.tag?.id)
    }
}
