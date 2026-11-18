package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.EntrySource
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScalePoint
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.Trend
import de.mediclever.tagebuch.core.text.NoteHtml
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * CSV im Format des Vorbilds: der einfachere Weg ohne Fotos und Ziele.
 *
 * Spalten: full_date,date,weekday,time,mood,activities,scales,note_title,note
 *  - activities: Namen, getrennt durch " | "
 *  - scales: "Name: Bezeichnung 2/5" (Textskala), "Name: 6:00" (Zeit) oder "Name: 7" (Zahl),
 *    mehrere ebenfalls durch " | " getrennt
 *  - Notizen als reiner Text; neuester Eintrag zuerst
 *
 * Import in drei Schritten: [parse] liest die Zeilen und meldet, welche Stimmungen,
 * Aktivitaeten und Skalen fehlen. Nach der Rueckfrage legt [resolve] das Fehlende an und
 * liefert einen Datenbestand, den [DiaryMerge.merge] mit [MergeMode.MERGE] einfuegt.
 */
object CsvFormat {

    val HEADER = listOf("full_date", "date", "weekday", "time", "mood", "activities", "scales", "note_title", "note")
    private const val SEPARATOR = " | "

    private val MONTHS = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

    /** Standardnamen des Vorbilds (englisch) und der eigenen App, fuer einen Vorschlag der Stufe. */
    private val KNOWN_MOOD_LEVELS = mapOf(
        "rad" to 1, "good" to 2, "meh" to 3, "bad" to 4, "awful" to 5,
        "super" to 1, "gut" to 2, "okay" to 3, "schlecht" to 4, "mies" to 5,
    )

    /** Ein Skalenwert, wie er in der Datei steht. Genau eine der drei Formen ist gefuellt. */
    data class CsvScaleValue(
        val scale: String,
        /** Textskala: Bezeichnung, Position (ab 1) und Anzahl der Punkte. */
        val label: String? = null,
        val position: Int? = null,
        val count: Int? = null,
        val minutes: Int? = null,
        val number: Int? = null,
    )

    data class CsvRow(
        /** Zeilennummer in der Datei (ab 1), fuer Hinweise. */
        val line: Int,
        val localDate: String,
        val minuteOfDay: Int,
        val mood: String,
        val activities: List<String>,
        val scales: List<CsvScaleValue>,
        val title: String,
        /** Reiner Text. */
        val note: String,
    )

    data class UnknownScale(
        val name: String,
        val type: ScaleType,
        /** Bezeichnungen der Punkte einer Textskala, Position 1 zuerst; nicht gesehene Positionen tragen ihre Nummer. */
        val points: List<String> = emptyList(),
    )

    data class CsvImport(
        val rows: List<CsvRow>,
        /** Was es im Tagebuch noch nicht gibt. Die Oberflaeche fragt nach, bevor [resolve] es anlegt. */
        val unknownMoods: List<String>,
        val unknownActivities: List<String>,
        val unknownScales: List<UnknownScale>,
        /** Deutsche Hinweise mit Zeilennummern und Anzahlen, ohne Inhalte. */
        val warnings: List<String>,
    )

    /** Vorschlag fuer die Stufe einer unbekannten Stimmung, 3 wenn der Name nichts verraet. */
    fun suggestedMoodLevel(name: String): Int = KNOWN_MOOD_LEVELS[key(name)] ?: 3

    private fun key(name: String) = name.trim().lowercase()

    // ------------------------------------------------------------------ Lesen

    /**
     * @throws ImportException wenn die Kopfzeile nicht passt.
     */
    fun parse(csv: String, diary: Diary): CsvImport {
        val records = parseRecords(csv)
        if (records.isEmpty()) throw ImportException("Die Datei ist leer.")
        val header = records.first().second.map { it.trim().lowercase() }
        fun column(name: String) = header.indexOf(name)
        val cDate = column("full_date")
        val cTime = column("time")
        val cMood = column("mood")
        val cActivities = column("activities")
        val cScales = column("scales")
        val cTitle = column("note_title")
        val cNote = column("note")
        if (cDate < 0 || cMood < 0) {
            throw ImportException("Die Datei hat nicht die erwarteten Spalten. Mindestens „full_date“ und „mood“ müssen vorhanden sein.")
        }

        val warnings = ArrayList<String>()
        val rows = ArrayList<CsvRow>()
        val badDate = ArrayList<Int>()
        val badTime = ArrayList<Int>()
        val noMood = ArrayList<Int>()
        var badScale = 0
        for ((line, cells) in records.drop(1)) {
            if (cells.all { it.isBlank() }) continue
            fun cell(index: Int) = if (index in cells.indices) cells[index] else ""
            val date = parseDate(cell(cDate))
            if (date == null) { badDate.add(line); continue }
            val mood = cell(cMood).trim()
            if (mood.isEmpty()) { noMood.add(line); continue }
            val minute = parseTime(cell(cTime)) ?: run { badTime.add(line); 12 * 60 }
            val scales = split(cell(cScales)).mapNotNull { text -> parseScaleValue(text) ?: run { badScale++; null } }
            rows.add(
                CsvRow(
                    line = line,
                    localDate = date,
                    minuteOfDay = minute,
                    mood = mood,
                    activities = split(cell(cActivities)).distinctBy(::key),
                    scales = scales.distinctBy { key(it.scale) },
                    title = cell(cTitle).trim(),
                    note = cell(cNote).replace("\r\n", "\n").trim(),
                ),
            )
        }
        if (badDate.isNotEmpty()) warnings.add("${badDate.size} Zeilen ohne lesbares Datum wurden übersprungen (Zeilen ${lines(badDate)}).")
        if (noMood.isNotEmpty()) warnings.add("${noMood.size} Zeilen ohne Stimmung wurden übersprungen (Zeilen ${lines(noMood)}).")
        if (badTime.isNotEmpty()) warnings.add("${badTime.size} Zeilen ohne lesbare Uhrzeit: 12:00 angenommen (Zeilen ${lines(badTime)}).")
        if (badScale > 0) warnings.add("$badScale Skalenwerte hatten eine unbekannte Schreibweise und wurden nicht übernommen.")

        // Was fehlt im Tagebuch?
        val moods = diary.moods.filter { !it.deleted }.map { key(it.name) }.toSet()
        val tags = diary.tags.filter { !it.deleted }.map { key(it.name) }.toSet()
        val scales = diary.scales.filter { !it.deleted }.associateBy { key(it.name) }
        val unmatched = rows.sumOf { row ->
            row.scales.count { value -> scales[key(value.scale)]?.let { toScaleValue(it, value) == null } == true }
        }
        if (unmatched > 0) warnings.add("$unmatched Skalenwerte passen nicht zu den Punkten der vorhandenen Skala und werden nicht übernommen.")
        val unknownMoods = rows.map { it.mood }.distinctBy(::key).filter { key(it) !in moods }
        val unknownActivities = rows.flatMap { it.activities }.distinctBy(::key).filter { key(it) !in tags }
        val unknownScales = rows.flatMap { it.scales }.filter { key(it.scale) !in scales }.groupBy { key(it.scale) }.map { (_, values) ->
            val first = values.first()
            when {
                first.label != null -> {
                    val count = values.maxOf { maxOf(it.count ?: 0, it.position ?: 0) }
                    val labels = values.filter { it.position != null }.associate { it.position!! to it.label.orEmpty() }
                    UnknownScale(first.scale.trim(), ScaleType.TEXT, (1..count).map { labels[it]?.ifEmpty { null } ?: "$it" })
                }
                first.minutes != null -> UnknownScale(first.scale.trim(), ScaleType.DURATION)
                else -> UnknownScale(first.scale.trim(), ScaleType.NUMBER)
            }
        }
        return CsvImport(rows, unknownMoods, unknownActivities, unknownScales, warnings)
    }

    private fun lines(numbers: List<Int>): String =
        numbers.take(10).joinToString() + if (numbers.size > 10) " und weitere" else ""

    private fun split(cell: String): List<String> =
        cell.split(SEPARATOR).map { it.trim().trim('|').trim() }.filter { it.isNotEmpty() }

    private fun parseDate(text: String): String? {
        val t = text.trim()
        return try {
            Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$").find(t)?.let { m ->
                LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()).toString()
            } ?: LocalDate.parse(t).toString()
        } catch (e: Exception) {
            null
        }
    }

    /** "20:05", "8:05", "8:05 PM", "8:05 nachm." -> Minuten seit Mitternacht. */
    private fun parseTime(text: String): Int? {
        val m = Regex("^(\\d{1,2})[:.](\\d{2})(?::\\d{2})?\\s*(.*)$").find(text.trim()) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val suffix = m.groupValues[3].lowercase().replace(".", "").replace(" ", "")
        when {
            suffix.startsWith("pm") || suffix.startsWith("nachm") -> if (hour < 12) hour += 12
            suffix.startsWith("am") || suffix.startsWith("vorm") -> if (hour == 12) hour = 0
        }
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    private val TEXT_VALUE = Regex("^(.+?):\\s+(.*?)\\s*(\\d+)/(\\d+)$")
    private val TIME_VALUE = Regex("^(.+?):\\s*(\\d{1,3}):(\\d{2})$")
    private val NUMBER_VALUE = Regex("^(.+?):\\s*(-?\\d+)$")

    private fun parseScaleValue(text: String): CsvScaleValue? {
        TEXT_VALUE.find(text)?.let { m ->
            val position = m.groupValues[3].toIntOrNull() ?: return null
            val count = m.groupValues[4].toIntOrNull() ?: return null
            if (position < 1 || count < position || count > 100) return null
            return CsvScaleValue(m.groupValues[1].trim(), label = m.groupValues[2].trim(), position = position, count = count)
        }
        TIME_VALUE.find(text)?.let { m ->
            val minutes = m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
            return CsvScaleValue(m.groupValues[1].trim(), minutes = minutes)
        }
        NUMBER_VALUE.find(text)?.let { m ->
            return CsvScaleValue(m.groupValues[1].trim(), number = m.groupValues[2].toIntOrNull() ?: return null)
        }
        return null
    }

    /**
     * CSV nach RFC 4180 zerlegen: Felder in Anfuehrungszeichen duerfen Kommas, Zeilenumbrueche
     * und verdoppelte Anfuehrungszeichen enthalten. Ein BOM am Anfang wird ueberlesen.
     * Liefert je Datensatz die Zeilennummer seines Beginns und die Felder.
     */
    internal fun parseRecords(csv: String): List<Pair<Int, List<String>>> {
        val text = csv.removePrefix("﻿")
        val records = ArrayList<Pair<Int, List<String>>>()
        var cells = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false       // innerhalb von Anfuehrungszeichen
        var wasQuoted = false    // das aktuelle Feld war in Anfuehrungszeichen
        var line = 1
        var recordLine = 1
        var i = 0
        fun endRecord() {
            cells.add(cell.toString())
            cell.setLength(0)
            if (cells.size > 1 || cells[0].isNotEmpty() || wasQuoted) records.add(recordLine to cells)
            cells = ArrayList()
            wasQuoted = false
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> quoted = false
                    else -> {
                        if (c == '\n') line++
                        cell.append(c)
                    }
                }
            } else {
                when {
                    c == '"' && cell.isEmpty() && !wasQuoted -> { quoted = true; wasQuoted = true }
                    c == ',' -> { cells.add(cell.toString()); cell.setLength(0); wasQuoted = false }
                    c == '\r' || c == '\n' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        endRecord()
                        line++
                        recordLine = line
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || cells.isNotEmpty() || wasQuoted) endRecord()
        return records
    }

    // ------------------------------------------------------------------ Anlegen

    private fun csvId(kind: String, key: String): String =
        UUID.nameUUIDFromBytes("csv:$kind:$key".toByteArray(Charsets.UTF_8)).toString()

    /**
     * Macht aus den gelesenen Zeilen einen Datenbestand: fehlende Stimmungen, Aktivitaeten
     * und Skalen werden angelegt, Eintraege verweisen auf vorhandene oder neue Zeilen.
     * Das Ergebnis enthaelt neben dem Neuen auch die benutzte vorhandene Einrichtung
     * (gleiche IDs, [DiaryMerge] ueberspringt sie), damit die Vorschau stimmt.
     *
     * IDs sind aus dem Inhalt berechnet: dieselbe Datei zweimal eingelesen ergibt keine Doppelten.
     *
     * @param moodLevels Stufe 1 bis 5 je unbekannter Stimmung (Antwort des Nutzers); sonst [suggestedMoodLevel].
     * @param zone Zeitzone, in der die Uhrzeiten der Datei gelten. Die Datei selbst kennt keine.
     */
    fun resolve(
        import: CsvImport,
        diary: Diary,
        moodLevels: Map<String, Int> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): Diary {
        val levels = moodLevels.mapKeys { key(it.key) }
        val moods = LinkedHashMap<String, Mood>()
        diary.moods.filter { !it.deleted }.forEach { moods.putIfAbsent(key(it.name), it) }
        val tags = LinkedHashMap<String, Tag>()
        diary.tags.filter { !it.deleted }.forEach { tags.putIfAbsent(key(it.name), it) }
        val scales = LinkedHashMap<String, Scale>()
        diary.scales.filter { !it.deleted }.forEach { scales.putIfAbsent(key(it.name), it) }
        val used = HashSet<String>()

        for (name in import.unknownMoods) {
            val level = (levels[key(name)] ?: suggestedMoodLevel(name)).coerceIn(1, 5)
            moods.getOrPut(key(name)) {
                Mood(id = csvId("mood", key(name)), level = level, name = name.trim(), icon = "a", sortOrder = moods.size, updatedAt = now)
            }
        }
        for (name in import.unknownActivities) {
            tags.getOrPut(key(name)) {
                Tag(id = csvId("tag", key(name)), name = name.trim(), icon = IconGuess.guess(name), sortOrder = tags.size, updatedAt = now)
            }
        }
        for (unknown in import.unknownScales) {
            val k = key(unknown.name)
            scales.getOrPut(k) {
                val durations = import.rows.flatMap { it.scales }.filter { key(it.scale) == k }.mapNotNull { it.minutes }
                val numbers = import.rows.flatMap { it.scales }.filter { key(it.scale) == k }.mapNotNull { it.number }
                Scale(
                    id = csvId("scale", k),
                    type = unknown.type,
                    name = unknown.name,
                    icon = IconGuess.guess(unknown.name).let { if (it == IconGuess.FALLBACK) "tune" else it },
                    sortOrder = scales.size,
                    trend = if (unknown.type == ScaleType.DURATION) Trend.NEUTRAL else Trend.HIGHER_IS_BETTER,
                    min = when (unknown.type) {
                        ScaleType.TEXT -> 1
                        ScaleType.DURATION -> 0
                        ScaleType.NUMBER -> minOf(1, numbers.minOrNull() ?: 1)
                    },
                    max = when (unknown.type) {
                        ScaleType.TEXT -> unknown.points.size.coerceAtLeast(1)
                        ScaleType.DURATION -> maxOf(24 * 60, durations.maxOrNull() ?: 0)
                        ScaleType.NUMBER -> maxOf(10, numbers.maxOrNull() ?: 10)
                    },
                    step = if (unknown.type == ScaleType.DURATION) 5 else 1,
                    points = unknown.points.mapIndexed { i, label ->
                        ScalePoint(id = csvId("scalepoint", "$k:${i + 1}"), position = i + 1, label = label)
                    },
                    updatedAt = now,
                )
            }
        }

        val seen = HashMap<String, Int>()
        val entries = ArrayList<Entry>()
        for (row in import.rows) {
            val mood = moods[key(row.mood)] ?: continue
            used.add(mood.id)
            val rowTags = row.activities.mapNotNull { tags[key(it)] }
            val values = row.scales.mapNotNull { value -> scales[key(value.scale)]?.let { toScaleValue(it, value) } }
            rowTags.forEach { used.add(it.id) }
            values.forEach { used.add(it.scaleId) }

            val date = LocalDate.parse(row.localDate)
            val local = LocalDateTime.of(date, java.time.LocalTime.of(row.minuteOfDay / 60, row.minuteOfDay % 60))
            val zoned = local.atZone(zone)
            // Gleiche Zeilen (selten, aber moeglich) bekommen eine laufende Nummer, damit keine verloren geht.
            val content = listOf(
                row.localDate, row.minuteOfDay, key(row.mood), row.activities.map(::key).sorted().joinToString("|"),
                row.scales.joinToString("|") { "${key(it.scale)}=${it.position ?: it.minutes ?: it.number}" }, row.title, row.note,
            ).joinToString("\u0001")
            val occurrence = seen.merge(content, 1, Int::plus)!!
            entries.add(
                Entry(
                    id = csvId("entry", "$content\u0001$occurrence"),
                    timestamp = zoned.toInstant().toEpochMilli(),
                    offsetMinutes = zoned.offset.totalSeconds / 60,
                    localDate = row.localDate,
                    minuteOfDay = row.minuteOfDay,
                    moodId = mood.id,
                    title = row.title,
                    note = NoteHtml.fromPlainText(row.note),
                    source = EntrySource.IMPORT,
                    tagIds = rowTags.map { it.id }.distinct(),
                    scaleValues = values.distinctBy { it.scaleId },
                    updatedAt = now,
                ),
            )
        }
        return Diary(
            moods = moods.values.filter { it.id in used },
            groups = diary.groups.filter { g -> tags.values.any { it.id in used && it.groupId == g.id } },
            tags = tags.values.filter { it.id in used },
            scales = scales.values.filter { it.id in used },
            entries = entries,
        )
    }

    /** Wert der Datei auf eine Skala abbilden; null, wenn er nicht zur Skala passt. */
    private fun toScaleValue(scale: Scale, value: CsvScaleValue): ScaleValue? = when (scale.type) {
        ScaleType.TEXT -> {
            val point = value.label?.let { label -> scale.points.firstOrNull { key(it.label) == key(label) } }
                // Bezeichnung unbekannt: bei gleicher Punktzahl gilt die Position.
                ?: scale.points.firstOrNull { it.position == value.position }?.takeIf { value.count == scale.points.size }
            point?.let { ScaleValue(scaleId = scale.id, pointId = it.id) }
        }
        ScaleType.DURATION -> (value.minutes ?: value.number)?.let { ScaleValue(scaleId = scale.id, minutes = it) }
        ScaleType.NUMBER -> value.number?.let { ScaleValue(scaleId = scale.id, number = it) }
    }

    // ------------------------------------------------------------------ Schreiben

    /**
     * Alle nicht geloeschten Eintraege als CSV, neuester zuerst. [withBom] setzt die
     * UTF-8-Kennung an den Anfang, damit Tabellenprogramme Umlaute richtig anzeigen.
     */
    fun export(diary: Diary, withBom: Boolean = false): String {
        val sb = StringBuilder()
        if (withBom) sb.append('﻿')
        sb.append(HEADER.joinToString(",")).append('\n')
        val scaleOrder = diary.scales.sortedWith(compareBy({ it.sortOrder }, { it.name })).map { it.id }
        val entries = diary.entries.filter { !it.deleted }
            .sortedWith(compareByDescending<Entry> { it.localDate }.thenByDescending { it.minuteOfDay }.thenByDescending { it.timestamp })
        for (entry in entries) {
            val date = try { LocalDate.parse(entry.localDate) } catch (e: Exception) { null }
            val scaleTexts = entry.scaleValues
                .sortedBy { scaleOrder.indexOf(it.scaleId) }
                .mapNotNull { value -> diary.scaleById[value.scaleId]?.let { scaleText(it, value) } }
            val cells = listOf(
                entry.localDate,
                date?.let { "${it.dayOfMonth}. ${MONTHS[it.monthValue - 1]}" }.orEmpty(),
                date?.let { WEEKDAYS[it.dayOfWeek.value - 1] }.orEmpty(),
                "${two(entry.minuteOfDay / 60)}:${two(entry.minuteOfDay % 60)}",
                diary.moodById[entry.moodId]?.name?.trim().orEmpty(),
                entry.tagIds.mapNotNull { diary.tagById[it]?.name?.trim() }.joinToString(SEPARATOR),
                scaleTexts.joinToString(SEPARATOR),
                entry.title,
                NoteHtml.toPlainText(entry.note),
            )
            sb.append(cells.joinToString(",") { quote(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun scaleText(scale: Scale, value: ScaleValue): String? {
        val name = scale.name.trim()
        return when (scale.type) {
            ScaleType.TEXT -> scale.points.firstOrNull { it.id == value.pointId }
                ?.let { "$name: ${it.label.trim()} ${it.position}/${scale.points.size}" }
            ScaleType.DURATION -> value.minutes?.let { "$name: ${it / 60}:${two(it % 60)}" }
            ScaleType.NUMBER -> value.number?.let { "$name: $it" }
        }
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun quote(cell: String): String {
        val needed = cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            cell.startsWith(' ') || cell.endsWith(' ')
        return if (needed) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
    }
}
