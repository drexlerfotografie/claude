package de.mediclever.tagebuch.core.shift

import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.stats.DateRange
import de.mediclever.tagebuch.core.stats.dayLevels
import de.mediclever.tagebuch.core.stats.weekOf
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShiftsTest {
    private val moods = (1..5).map { Mood(id = "m$it", level = it, name = "Stufe $it", icon = "a") }
    private val dienst = TagGroup(id = "g1", name = "Dienst", sortOrder = 0)
    private val sonst = TagGroup(id = "g2", name = "Freizeit", sortOrder = 1)
    private val tag = Tag(id = "t1", groupId = "g1", name = "Tagschicht", sortOrder = 0)
    private val nacht = Tag(id = "t2", groupId = "g1", name = "Nachtschicht", sortOrder = 1)
    private val frei = Tag(id = "t3", groupId = "g1", name = "Frei", sortOrder = 2)
    private val alt = Tag(id = "t4", groupId = "g1", name = "Spätdienst", sortOrder = 3, archivedAt = 5)
    private val sport = Tag(id = "t5", groupId = "g2", name = "Sport")

    private fun entry(id: String, date: String, minute: Int, level: Int, vararg tags: String) =
        Entry(id = id, timestamp = 0, offsetMinutes = 0, localDate = date, minuteOfDay = minute, moodId = "m$level", tagIds = tags.toList())

    private fun diary(vararg e: Entry, settings: UserSettings = UserSettings()) =
        Diary(moods = moods, groups = listOf(dienst, sonst), tags = listOf(tag, nacht, frei, alt, sport), entries = e.toList(), settings = settings)

    @Test fun groupIsFoundByNameOrSetting() {
        assertEquals("g1", Shifts.group(diary())?.id)
        assertEquals("g2", Shifts.group(diary(settings = UserSettings(shiftGroupId = "g2")))?.id)
        assertNull(Shifts.group(Diary(groups = listOf(sonst))))
    }

    @Test fun activeLeavesOutSwitchedOffShifts() {
        assertEquals(listOf("t1", "t2", "t3", "t4"), Shifts.all(diary()).map { it.id })
        assertEquals(listOf("t1", "t2", "t3"), Shifts.active(diary()).map { it.id })
    }

    @Test fun shiftOfEntryAndDay() {
        val d = diary(entry("a", "2026-11-17", 1200, 2, "t5", "t2"), entry("b", "2026-11-17", 480, 3, "t5"), entry("c", "2026-11-18", 600, 1))
        assertEquals("t2", Shifts.of(d, d.entries[0])?.id)
        assertNull(Shifts.of(d, d.entries[1]))
        assertEquals("t2", Shifts.ofDay(d, LocalDate.parse("2026-11-17"))?.id)
        assertNull(Shifts.ofDay(d, LocalDate.parse("2026-11-18")))
    }

    @Test fun selectingKeepsExactlyOneShift() {
        val d = diary()
        assertEquals(listOf("t5", "t2"), Shifts.select(d, listOf("t1", "t5"), "t2"))
        assertEquals(listOf("t5"), Shifts.select(d, listOf("t5", "t2"), "t2"))
    }

    @Test fun shortNamesDropSchichtAndDienst() {
        assertEquals("Nacht", Shifts.shorten("Nachtschicht"))
        assertEquals("Früh", Shifts.shorten("Frühdienst"))
        assertEquals("Zwischen", Shifts.shorten("Zwischendienst"))
        assertEquals("24-Stunden", Shifts.shorten("24-Stunden-Dienst"))
        assertEquals("Frei", Shifts.shorten("Frei"))
        assertEquals("Dienst", Shifts.shorten("Dienst"))
        val d = diary(settings = UserSettings(shiftTimes = listOf(ShiftTime("t2", short = "N"))))
        assertEquals("N", Shifts.short(d, nacht))
        assertEquals("Tag", Shifts.short(d, tag))
    }

    @Test fun freeIsRecognised() {
        assertTrue(Shifts.isFree(diary(), frei))
        assertTrue(!Shifts.isFree(diary(), nacht))
        assertTrue(Shifts.isFree(diary(settings = UserSettings(shiftTimes = listOf(ShiftTime("t2", free = true)))), nacht))
    }

    @Test fun dayLevelsKeepOrderOfTheDay() {
        val d = diary(entry("a", "2026-11-11", 1200, 1), entry("b", "2026-11-11", 480, 4), entry("c", "2026-11-11", 700, 3), entry("d", "2026-11-06", 600, 2), entry("x", "2026-10-31", 600, 5))
        val levels = dayLevels(d, DateRange(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")))
        assertEquals(listOf(4, 3, 1), levels[LocalDate.parse("2026-11-11")])
        assertEquals(listOf(2), levels[LocalDate.parse("2026-11-06")])
        assertEquals(2, levels.size)
    }

    @Test fun weekStartsOnTheChosenDay() {
        val wed = LocalDate.parse("2026-11-18")
        assertEquals(LocalDate.parse("2026-11-16"), weekOf(wed, 1).first())
        assertEquals(LocalDate.parse("2026-11-22"), weekOf(wed, 1).last())
        assertEquals(LocalDate.parse("2026-11-15"), weekOf(wed, 7).first())
        assertEquals(LocalDate.parse("2026-11-15"), weekOf(LocalDate.parse("2026-11-15"), 7).first())
    }
}
