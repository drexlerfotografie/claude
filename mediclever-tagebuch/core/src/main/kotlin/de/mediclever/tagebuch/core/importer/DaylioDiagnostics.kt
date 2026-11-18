package de.mediclever.tagebuch.core.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Strukturbericht eines Backups zur Fehlersuche beim Import. Er nennt Feldnamen, Datentypen und
 * die Verteilung von Zahlenwerten, aber niemals Texte: keine Namen, keine Notizen, keine Titel.
 * Texte erscheinen nur als Laenge. Einzige Ausnahme sind Werte, die wie eine Farbe aussehen
 * (#RRGGBB), und die Schluesselnamen der Einstellungen, weil beides keine Tagebuchinhalte sind.
 */
object DaylioDiagnostics {
    private const val MAX_DISTINCT = 40
    private val colorLike = Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
    /** Listen, deren Zeilen vollstaendig (nur Zahlen und Wahrheitswerte) ausgegeben werden. */
    private val fullRows = setOf("customMoods", "tag_groups", "scales", "goals")
    /** Zahlenfelder, die Zeitpunkte oder laufende Nummern sind und deshalb nur als Bereich erscheinen. */
    private val rangeOnly = Regex("(?i)(date|time|created|updated|^id$|_at$|timestamp|year|^day$|minute|hour|second)")

    fun describe(json: String): String {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return "Kein JSON-Objekt."
        val out = StringBuilder("Strukturbericht (ohne Inhalte)\n")
        for ((key, value) in root) {
            when (value) {
                is JsonArray -> describeList(out, key, value)
                is JsonObject -> out.append("\n$key: Objekt mit Feldern ${value.keys.sorted()}\n")
                else -> out.append("\n$key: ${scalar(value)}\n")
            }
        }
        return out.toString()
    }

    private fun scalar(e: JsonElement): String = when {
        e is JsonNull -> "null"
        e is JsonPrimitive && e.isString -> if (colorLike.matches(e.content)) "Farbe ${e.content}" else "Text(${e.content.length})"
        e is JsonPrimitive -> e.content
        e is JsonArray -> "Liste(${e.size})"
        else -> "Objekt"
    }

    private fun describeList(out: StringBuilder, name: String, list: JsonArray) {
        out.append("\n$name: ${list.size} Einträge\n")
        val objects = list.filterIsInstance<JsonObject>()
        if (objects.isEmpty()) { if (list.isNotEmpty()) out.append("  Werte: ${list.take(5).map { scalar(it) }}\n"); return }
        if (name == "prefs") { describePrefs(out, objects); return }
        val keys = objects.flatMap { it.keys }.toSortedSet()
        for (key in keys) {
            val values = objects.mapNotNull { it[key] }
            val missing = objects.size - values.size
            out.append("  $key").append(if (missing > 0) " (fehlt ${missing}×)" else "").append(": ").append(summarize(key, values)).append('\n')
        }
        if (name in fullRows || (name == "tags" && objects.size <= 400)) {
            out.append("  Zeilen (nur Zahlen):\n")
            for (o in objects) out.append("    ").append(o.entries.filter { (_, v) -> v is JsonPrimitive && !v.isString }.joinToString(" ") { (k, v) -> "$k=${(v as JsonPrimitive).content}" }).append('\n')
        }
    }

    private fun summarize(key: String, values: List<JsonElement>): String {
        val prims = values.filterIsInstance<JsonPrimitive>().filter { it !is JsonNull }
        val nulls = values.count { it is JsonNull }
        val parts = ArrayList<String>()
        if (nulls > 0) parts += "null ${nulls}×"
        val strings = prims.filter { it.isString }
        if (strings.isNotEmpty()) {
            val colors = strings.filter { colorLike.matches(it.content) }
            if (colors.size == strings.size) parts += "Farben " + colors.groupingBy { it.content }.eachCount().entries.take(MAX_DISTINCT).joinToString { "${it.key} ${it.value}×" }
            else parts += "Text ${strings.size}×, leer ${strings.count { it.content.isEmpty() }}×, Länge bis ${strings.maxOf { it.content.length }}"
        }
        val bools = prims.filter { !it.isString && it.booleanOrNull != null }
        if (bools.isNotEmpty()) parts += "wahr ${bools.count { it.booleanOrNull == true }}×, falsch ${bools.count { it.booleanOrNull == false }}×"
        val nums = prims.filter { !it.isString && it.booleanOrNull == null }.mapNotNull { it.doubleOrNull }
        if (nums.isNotEmpty()) {
            val distinct = nums.groupingBy { it }.eachCount().toSortedMap()
            parts += if (distinct.size <= MAX_DISTINCT && !rangeOnly.containsMatchIn(key)) "Zahl " + distinct.entries.joinToString { "${fmt(it.key)} ${it.value}×" }
            else "Zahl ${nums.size}×, ${distinct.size} verschiedene, von ${fmt(nums.min())} bis ${fmt(nums.max())}"
        }
        val arrays = values.filterIsInstance<JsonArray>()
        if (arrays.isNotEmpty()) {
            val inner = arrays.flatMap { it }
            val innerKeys = inner.filterIsInstance<JsonObject>().flatMap { it.keys }.toSortedSet()
            parts += "Liste ${arrays.size}×, Länge bis ${arrays.maxOf { it.size }}" + if (innerKeys.isNotEmpty()) ", Felder darin: " + innerKeys.joinToString { k -> "$k [" + summarize(k, inner.filterIsInstance<JsonObject>().mapNotNull { it[k] }) + "]" } else ""
        }
        val objs = values.filterIsInstance<JsonObject>()
        if (objs.isNotEmpty()) parts += "Objekt ${objs.size}×, Felder ${objs.flatMap { it.keys }.toSortedSet()}"
        return parts.joinToString("; ")
    }

    /** Einstellungen: Schluesselname und Art des Werts. Texte nur als Laenge, Zahlen und Farben im Klartext. */
    private fun describePrefs(out: StringBuilder, prefs: List<JsonObject>) {
        for (p in prefs) {
            val key = (p["key"] as? JsonPrimitive)?.content ?: "?"
            val value = p["value"]
            val shown = when {
                value == null -> "kein Wert"
                value is JsonPrimitive && value.isString && value.content.length <= 400 && Regex("^[\\[\\]{}\",:#0-9a-fA-FxX\\s._-]*$").matches(value.content) -> "\"${value.content}\""
                else -> scalar(value)
            }
            out.append("  $key = $shown\n")
        }
    }

    private fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
