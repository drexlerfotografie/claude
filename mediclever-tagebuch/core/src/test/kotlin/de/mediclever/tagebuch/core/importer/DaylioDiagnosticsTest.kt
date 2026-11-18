package de.mediclever.tagebuch.core.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaylioDiagnosticsTest {
    private val json: String = ZipFile(File("../tests/fixtures/sample.daylio")).use { z ->
        DaylioBackup.decodeBackupText(z.getInputStream(z.entries().asSequence().first { it.name.endsWith("backup.daylio") }).readBytes())
    }

    private fun texts(e: JsonElement, into: MutableSet<String>) {
        when (e) {
            is JsonObject -> e.values.forEach { texts(it, into) }
            is JsonArray -> e.forEach { texts(it, into) }
            is JsonPrimitive -> if (e.isString) into += e.content
            else -> {}
        }
    }

    @Test fun reportNamesFieldsAndNumbers() {
        val r = DaylioDiagnostics.describe(json)
        assertTrue("customMoods" in r && "mood_group_id" in r && "dayEntries: 120 Einträge" in r)
    }

    /** Kein Text aus dem Backup darf im Bericht stehen: weder Namen noch Notizen noch Titel. */
    @Test fun reportContainsNoTextFromTheBackup() {
        val all = HashSet<String>().also { texts(Json.parseToJsonElement(json), it) }
        val report = DaylioDiagnostics.describe(json)
        val words = all.flatMap { it.replace(Regex("<[^>]+>"), " ").split(Regex("[^\\p{L}]+")) }.filter { it.length >= 5 }.toSet()
        // Feldnamen und die festen Woerter des Berichts selbst sind erlaubt
        val allowed = Regex("[\\p{L}_]+").findAll(DaylioDiagnostics.describe("{}") + " Einträge Zeilen Zahlen Liste Länge Felder darin fehlt verschiedene falsch Farben Objekt Werte Farbe").map { it.value }.toSet()
        val fieldNames = HashSet<String>().also { names -> fun walk(e: JsonElement) { if (e is JsonObject) { names += e.keys; e.values.forEach(::walk) } else if (e is JsonArray) e.forEach(::walk) }; walk(Json.parseToJsonElement(json)) }
        val leaked = words.filter { w -> w !in allowed && fieldNames.none { w in it } && Regex("(?<![\\p{L}_])" + Regex.escape(w) + "(?![\\p{L}_])").containsMatchIn(report) }
        assertTrue(leaked.isEmpty(), "Im Bericht gefunden: $leaked")
        assertFalse(report.contains("<p>") || report.contains("<b>"))
    }
}
