package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.ChartMode
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.model.ScaleType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Abnahmetest mit dem echten Backup. Die Datei liegt nur lokal unter private/ und nie im
 * Repository; ohne sie wird der Test uebersprungen. Geprueft werden ausschliesslich Zaehlwerte.
 */
class RealBackupTest {
    private val file = File("../private/backup.daylio")

    private val loaded: ImportResult by lazy { DaylioBackup.readDataFile(file) }

    private fun load(): ImportResult {
        assumeTrue(file.isFile, "private/backup.daylio fehlt")
        return loaded
    }

    private fun rawJson(): JsonObject = Json.parseToJsonElement(DaylioBackup.decodeBackupText(file.readBytes())) as JsonObject

    private fun JsonObject.list(name: String): List<JsonObject> = (this[name] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    @Test fun checkNumbersFromTheProjectDescription() {
        val p = load().preview
        assertEquals(1765, p.entries)
        assertEquals(listOf(375, 680, 426, 154, 130), p.entriesPerLevel)
        assertEquals(375, p.entriesWithNote)
        assertEquals(20, p.favorites)
        assertEquals(1751, p.assets)
        assertEquals(1743, p.assetLinks)
        assertEquals(138, p.tags); assertEquals(34, p.archivedTags)
        assertEquals(14, p.groups); assertEquals(10, p.scales)
        assertEquals(15, p.goals); assertEquals(1553, p.goalChecks)
        assertEquals(12, p.importantDays); assertEquals(11, p.templates); assertEquals(2, p.reminders)
        assertEquals("2017-11-27", p.earliestDate); assertEquals("2026-11-16", p.latestDate)
    }

    @Test fun everyScaleValueIsImported() {
        val result = load()
        val inFile = rawJson().list("dayEntries").sumOf { (it["scaleValues"] as? JsonArray)?.size ?: 0 }
        assertTrue(inFile > 0)
        assertEquals(inFile, result.diary.entries.sumOf { it.scaleValues.size })
        val text = result.warnings.joinToString("\n")
        assertFalse("Skalenwerte" in text, text)
        assertFalse("Textskalen ohne erkennbare Punkte" in text, text)

        val scales = result.diary.scales
        val rawScales = rawJson().list("scales")
        assertEquals(rawScales.count { it["text_scale"] is JsonObject }, scales.count { it.type == ScaleType.TEXT })
        assertEquals(rawScales.count { it["time_scale"] is JsonObject }, scales.count { it.type == ScaleType.DURATION })
        assertEquals(0, scales.count { it.type == ScaleType.NUMBER })
        assertEquals(
            rawScales.sumOf { ((it["text_scale"] as? JsonObject)?.get("values") as? JsonArray)?.size ?: 0 },
            scales.sumOf { it.points.size },
        )
        for (scale in scales) {
            when (scale.type) {
                ScaleType.TEXT -> {
                    assertEquals(1, scale.min); assertEquals(scale.points.size, scale.max)
                    assertEquals((1..scale.points.size).toList(), scale.points.map { it.position })
                    assertEquals(ChartMode.DAILY_AVERAGE, scale.chartMode)
                }
                else -> {
                    assertEquals(0, scale.min); assertEquals(24 * 60, scale.max); assertEquals(15, scale.step)
                    assertEquals(ChartMode.DAILY_SUM, scale.chartMode)
                }
            }
        }
        // Gruppen und Skalen teilen sich eine Reihenfolge ohne doppelte Plaetze.
        val orders = result.diary.groups.map { it.sortOrder } + scales.map { it.sortOrder }
        assertEquals(orders.size, orders.toSet().size)
    }

    @Test fun nothingIsReportedAsAssumedOrUnknown() {
        val text = load().warnings.joinToString("\n")
        for (word in listOf("Annahme", "Angenommen", "erschlossen", "unbekannte Felder", "Unbekannte Liste", "Unbekannte Angabe", "übersprungen", "COLOR")) {
            assertFalse(word in text, "$word in: $text")
        }
        // Genau ein Hinweis zu Farbnummern ohne Zuordnung, nur mit Zahlen.
        val colors = load().warnings.filter { it.startsWith("Farben:") }
        assertEquals(1, colors.size, text)
        assertTrue(load().warnings.any { "nur die Datendatei" in it }, text)
        assertTrue(load().assetFiles.isEmpty())
    }

    @Test fun coloursGoalsAndSettings() {
        val d = load().diary
        assertTrue((d.groups.map { it.colorIndex } + d.scales.map { it.colorIndex } + d.tags.mapNotNull { it.colorIndex }).all { it in 0..17 })
        val rawTags = rawJson().list("tags")
        assertEquals(rawTags.count { "id_color" !in it }, d.tags.count { it.colorIndex == null })

        assertEquals("custom", d.settings.moodScheme)
        assertEquals(5, d.settings.customMoodColors.size)
        assertTrue(d.settings.customMoodColors.all { Regex("[0-9A-F]{8}").matches(it) })
        assertEquals(1, d.settings.weekStart)

        val rawGoals = rawJson().list("goals")
        fun raw(type: Int, predicate: (Int) -> Boolean) = rawGoals.count {
            it["repeat_type"].toString() == "$type" && predicate(it["repeat_value"].toString().toInt())
        }
        assertEquals(raw(1) { it == 127 }, d.goals.count { it.repeat == GoalRepeat.DAILY })
        assertEquals(raw(1) { it in 1..126 }, d.goals.count { it.repeat == GoalRepeat.WEEKDAYS })
        assertEquals(raw(2) { true } + raw(3) { true }, d.goals.count { it.repeat == GoalRepeat.TIMES_PER_WEEK })
        assertTrue(d.goals.filter { it.repeat == GoalRepeat.WEEKDAYS }.all { it.weekdays.isNotEmpty() && it.weekdays.all { day -> day in 1..7 } })
        assertEquals(rawGoals.count { it["end_date"].toString().toLong() > 0 }, d.goals.count { it.endDate != null })
        assertEquals(rawGoals.count { it["reminder_enabled"].toString() == "true" }, d.goals.count { it.reminderMinuteOfDay != null })
        assertTrue(d.goals.all { it.endDate == null || it.endDate!! >= it.startDate })
        // Jeder abgehakte Tag haengt an einem Ziel.
        assertEquals(rawJson().list("goalEntries").size, d.goalChecks.size)

        assertEquals(rawJson().list("milestones").count { it["isAnniversary"].toString() == "true" }, d.importantDays.count { it.yearly })
        assertTrue(d.importantDays.all { !it.remindOnDay && it.remindDaysBefore.isEmpty() && it.category.isEmpty() })
    }
}
