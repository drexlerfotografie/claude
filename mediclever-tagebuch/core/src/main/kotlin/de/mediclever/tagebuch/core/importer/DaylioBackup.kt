package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.ChartMode
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.EntrySource
import de.mediclever.tagebuch.core.model.Goal
import de.mediclever.tagebuch.core.model.GoalCheck
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.model.ImportantDay
import de.mediclever.tagebuch.core.model.MidnightRule
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.Reminder
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScalePoint
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import de.mediclever.tagebuch.core.model.Template
import de.mediclever.tagebuch.core.model.Trend
import de.mediclever.tagebuch.core.model.UserSettings
import de.mediclever.tagebuch.core.text.NoteHtml
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Liest ein .daylio-Backup: ein ZIP mit backup.daylio (Base64-kodiertes JSON) und den
 * Fotos unter assets/photos/JAHR/MONAT/PRUEFSUMME. Das Ergebnis ist ein [Diary] mit
 * festen IDs (aus Art und Quell-ID berechnet), sodass ein zweiter Import derselben
 * Datei dieselben IDs liefert und [DiaryMerge] keine Doppelten anlegt.
 *
 * Das JSON wird nachsichtig gelesen: fehlende Felder fuehren nie zum Absturz, unbekannte
 * Felder werden je Liste in den Hinweisen genannt (nur Feldnamen, keine Inhalte).
 * Alles, was nicht belegt ist, traegt im Code die Marke "ANNAHME:".
 */
object DaylioBackup {

    private const val MAX_JSON_BYTES = 256L * 1024 * 1024

    /** Feste ID aus Art und Quell-ID, z. B. deterministicId("entry", "17"). */
    fun deterministicId(kind: String, sourceId: String): String =
        UUID.nameUUIDFromBytes("daylio:$kind:$sourceId".toByteArray(Charsets.UTF_8)).toString()

    /**
     * Liest das Backup aus der ZIP-Datei. Fotos werden nicht geladen: von jeder Datei werden
     * nur die ersten Bytes gelesen, um den Dateityp zu erkennen.
     * @throws ImportException wenn die Datei kein lesbares Daylio-Backup ist.
     */
    fun read(zipFile: File, now: Long = System.currentTimeMillis()): DaylioImport {
        val zip = try {
            ZipFile(zipFile)
        } catch (e: ZipException) {
            throw ImportException("Die Datei ist kein Daylio-Backup (kein ZIP-Archiv).", e)
        } catch (e: java.io.IOException) {
            throw ImportException("Die Datei konnte nicht gelesen werden.", e)
        }
        zip.use {
            val all = zip.entries().toList().filter { !it.isDirectory }
            val backup = all.firstOrNull { ImportArchive.safePath(it.name) == "backup.daylio" }
                ?: all.firstOrNull { ImportArchive.safePath(it.name)?.let { p -> '/' !in p && p.endsWith(".daylio") } == true }
                ?: throw ImportException("In der Datei fehlt backup.daylio. Ist es wirklich ein Daylio-Backup?")
            if (backup.size > MAX_JSON_BYTES) throw ImportException("Das Backup ist zu groß.")
            val json = decodeBackupText(zip.getInputStream(backup).use { it.readBytes() })
            val archive = Archive(
                names = all.filter { it !== backup }.map { it.name },
                size = { name -> zip.getEntry(name)?.size ?: 0 },
                extension = { name -> zip.getEntry(name)?.let { e -> ImportArchive.detectExtension(ImportArchive.readHead(zip, e)) } },
            )
            return Parser(parseRoot(json), archive, now).parse()
        }
    }

    /**
     * Wie [read], aber mit dem schon entpackten JSON. [zipEntryNames] sind die Namen der
     * Dateien im Archiv; null bedeutet, dass kein Archiv vorliegt (dann gibt es keine
     * assetFiles und keinen Hinweis auf fehlende Fotos). Dateitypen werden hier nicht erkannt.
     */
    fun fromJson(json: String, zipEntryNames: Collection<String>? = null, now: Long = System.currentTimeMillis()): DaylioImport {
        val archive = zipEntryNames?.let { Archive(it.toList(), { 0 }, { null }) }
        return Parser(parseRoot(json), archive, now).parse()
    }

    /** Inhalt von backup.daylio zu JSON-Text: Base64, zur Sicherheit auch unkodiertes JSON. */
    fun decodeBackupText(bytes: ByteArray): String {
        val raw = String(bytes, Charsets.UTF_8).removePrefix("﻿").trim()
        if (raw.startsWith("{")) return raw
        return try {
            String(Base64.getMimeDecoder().decode(raw), Charsets.UTF_8).removePrefix("﻿")
        } catch (e: IllegalArgumentException) {
            throw ImportException("backup.daylio ist beschädigt (keine gültige Base64-Kodierung).", e)
        }
    }

    /**
     * Kopiert eine Datei aus dem Archiv nach [targetDir]. Der Dateityp wird am Inhalt
     * erkannt (JPEG, PNG, WEBP, ...), die Datei heisst danach [baseName].<endung>.
     * Fuer Ergebnisse von [read] steht der fertige Name schon in [AssetFile.fileName];
     * dann genuegt [ImportArchive.copyAsset] bzw. [ImportArchive.copyAssets].
     */
    fun copyAsset(zipFile: File, zipEntryName: String, targetDir: File, baseName: String): File =
        ImportArchive.copyDetectingType(zipFile, zipEntryName, targetDir, baseName)

    private fun parseRoot(json: String): JsonObject = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (e: Exception) {
        null
    } ?: throw ImportException("backup.daylio enthält kein gültiges JSON.")

    // ------------------------------------------------------------------ Hilfen

    internal class Archive(
        val names: List<String>,
        val size: (String) -> Long,
        val extension: (String) -> String?,
    )

    /** Merkt sich je Liste, welche Felder vorkamen und welche der Import kennt. */
    private class Fields {
        val known = HashSet<String>()
        val seen = LinkedHashMap<String, String>()
        fun unknown(): List<String> = seen.filterKeys { it !in known }.map { "${it.key} (${it.value})" }
    }

    private class Obj(val json: JsonObject, val fields: Fields) {
        init {
            for ((key, value) in json) fields.seen.putIfAbsent(key, typeLabel(value))
        }

        fun element(vararg keys: String): JsonElement? {
            fields.known.addAll(keys)
            return keys.firstNotNullOfOrNull { key -> json[key]?.takeUnless { it is JsonNull } }
        }

        /** Welcher der Feldnamen tatsaechlich vorkommt. */
        fun usedKey(vararg keys: String): String? = keys.firstOrNull { json[it] != null && json[it] !is JsonNull }

        fun long(vararg keys: String): Long? = element(*keys).asLong()
        fun int(vararg keys: String): Int? = long(*keys)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        fun string(vararg keys: String): String? = (element(*keys) as? JsonPrimitive)?.content
        fun bool(vararg keys: String): Boolean? = element(*keys).asBool()
        fun array(vararg keys: String): JsonArray? = element(*keys) as? JsonArray
    }

    private fun typeLabel(e: JsonElement): String = when (e) {
        is JsonNull -> "leer"
        is JsonArray -> "Liste"
        is JsonObject -> "Objekt"
        is JsonPrimitive -> when {
            e.isString -> "Text"
            e.content == "true" || e.content == "false" -> "Ja/Nein"
            else -> "Zahl"
        }
    }

    private fun JsonElement?.asLong(): Long? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        val c = p.content.trim()
        return when (c) {
            "true" -> 1
            "false" -> 0
            else -> c.toLongOrNull() ?: c.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
        }
    }

    private fun JsonElement?.asBool(): Boolean? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return when (p.content.trim().lowercase()) {
            "true", "1" -> true
            "false", "0", "" -> false
            else -> p.content.toDoubleOrNull()?.let { it != 0.0 }
        }
    }

    private fun objects(array: JsonArray?): List<JsonObject> = array?.filterIsInstance<JsonObject>() ?: emptyList()

    private fun isoDate(year: Int, month: Int, day: Int): String? = try {
        LocalDate.of(year, month, day).toString()
    } catch (e: Exception) {
        null
    }

    private fun utcDate(millis: Long): String = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate().toString()

    // Feldnamen-Kandidaten fuer die nicht belegten Teile des Formats.
    // ANNAHME: Farbnummern stehen in einem dieser Felder.
    private val COLOR_KEYS = arrayOf("id_color", "color_id", "colorId", "color", "color_index", "id_colour")
    private val NAME_KEYS = arrayOf("name", "title", "custom_name", "label", "text")
    private val ORDER_KEYS = arrayOf("order", "position", "sort_order", "index")
    private val STATE_KEYS = arrayOf("state", "is_archived", "archived")
    // ANNAHME: Verweise auf Skala, Eintrag, Textpunkt und der Zeitwert heissen so oder aehnlich.
    private val SCALE_REF_KEYS = arrayOf("id_scale", "scale_id", "scaleId", "id_text_scale", "id_time_scale", "text_scale_id", "time_scale_id")
    private val ENTRY_REF_KEYS = arrayOf("id_day_entry", "day_entry_id", "dayEntryId", "id_entry", "entry_id", "entryId")
    private val TEXT_VALUE_KEYS = arrayOf("id_text_scale_value", "text_scale_value_id", "textScaleValueId", "id_scale_value", "id_value", "value_id")
    private val TIME_VALUE_KEYS = arrayOf("time_value", "timeValue", "duration", "time", "minutes")
    private val NUMBER_VALUE_KEYS = arrayOf("number_value", "numeric_value", "numberValue", "value")
    private val POINT_LIST_KEYS = arrayOf("text_values", "textValues", "values", "scale_values", "scaleValues", "text_scale_values", "textScaleValues", "points", "options", "items")
    private val ENTRY_SCALE_LIST_KEYS = arrayOf("scales", "scale_values", "scaleValues", "scaleEntries", "scale_entries", "dayEntryScales")

    private val TOP_LEVEL_HANDLED = setOf(
        "customMoods", "tag_groups", "tags", "dayEntries", "assets", "goals", "goalEntries",
        "writingTemplates", "reminders", "prefs", "milestones", "scales",
    )

    /** Bewusst nicht uebernommen: Erfolge und Wochenwerte rechnet die App neu, der Rest ist Verwaltung. */
    private val TOP_LEVEL_IGNORED = setOf(
        "version", "achievements", "goalSuccessWeeks", "daysInRowLongestChain", "metadata", "isReminderOn",
        "platform", "moodIconsPackId", "moodIconsDefaultFreePackId", "preferredMoodIconsIdsForMoodIdsForIconsPack",
    )

    private val DEFAULT_MOOD_NAMES = listOf("Super", "Gut", "Okay", "Schlecht", "Mies")

    private class RawScaleValue(
        val entryId: Long,
        val scaleId: Long?,
        val textPointId: Long?,
        val time: JsonElement?,
        val number: Long?,
    )

    private class RawPoint(val sourceId: Long?, val label: String, val order: Long?, val index: Int)

    // ------------------------------------------------------------------ Leser

    private class Parser(val root: JsonObject, val archive: Archive?, val now: Long) {
        val warnings = ArrayList<String>()
        val fields = LinkedHashMap<String, Fields>()
        /** Top-Level-Listen, die die Heuristik fuer Skalen oder wichtige Tage verbraucht hat. */
        val consumedTopLevel = HashSet<String>()

        fun fieldsOf(list: String) = fields.getOrPut(list) { Fields() }
        fun list(name: String): List<Obj> = objects(root[name] as? JsonArray).map { Obj(it, fieldsOf(name)) }
        fun id(kind: String, sourceId: Long) = deterministicId(kind, sourceId.toString())
        fun color(obj: Obj): Int? = obj.long(*COLOR_KEYS)?.let { Math.floorMod(it, 12L).toInt() }
        /** ANNAHME: state 0 ist aktiv, alles andere archiviert (belegt nur fuer tags: state 1). */
        fun archived(obj: Obj): Long? = if ((obj.long(*STATE_KEYS) ?: 0L) != 0L) now else null

        val moodIds = HashMap<Long, String>()
        val groupIds = HashMap<Long, String>()
        val groupColors = HashMap<String, Int>()
        val tagIds = HashMap<Long, String>()
        val tagsById = HashMap<String, Tag>()
        val assetIds = HashMap<Long, String>()

        fun parse(): DaylioImport {
            val moods = readMoods()
            val groups = readGroups()
            val tags = readTags()
            val (assets, assetFiles) = readAssets()
            val entryObjs = list("dayEntries")
            val scaleReader = ScaleReader(entryObjs)
            val scales = scaleReader.readScales()
            val entries = readEntries(entryObjs, scaleReader)
            val (goals, goalChecks) = readGoals()
            val importantDays = readMilestones()
            val templates = readTemplates()
            val reminders = readReminders()
            val settings = readPrefs()
            reportUnknown()

            val diary = Diary(
                moods = moods, groups = groups, tags = tags, scales = scales, entries = entries, assets = assets,
                goals = goals, goalChecks = goalChecks, importantDays = importantDays, templates = templates,
                reminders = reminders, settings = settings,
            )
            return ImportResult(diary, assetFiles, ImportPreview.of(diary), warnings)
        }

        // -------------------------------------------------- Stimmungen, Gruppen, Aktivitaeten

        fun readMoods(): List<Mood> {
            var noId = 0
            var badLevel = 0
            val result = ArrayList<Mood>()
            for (obj in list("customMoods")) {
                val sourceId = obj.long("id") ?: run { noId++; null } ?: continue
                val rawLevel = obj.int("mood_group_id")
                val predefined = obj.int("predefined_name_id")
                val level = rawLevel?.coerceIn(1, 5) ?: predefined?.coerceIn(1, 5) ?: 3
                if (rawLevel == null || rawLevel !in 1..5) badLevel++
                val custom = obj.string("custom_name")?.trim().orEmpty()
                // Leerer Name: Standardstimmung des Vorbilds (rad, good, meh, bad, awful).
                val name = custom.ifEmpty { DEFAULT_MOOD_NAMES[(predefined?.takeIf { it in 1..5 } ?: level) - 1] }
                obj.element("icon_id") // Symbolnummern des Vorbilds lassen sich nicht uebernehmen.
                val mood = Mood(
                    id = id("mood", sourceId),
                    level = level,
                    name = name,
                    icon = "mood$level",
                    sortOrder = obj.int("mood_group_order", "order") ?: 0,
                    archivedAt = archived(obj),
                    updatedAt = now,
                )
                moodIds[sourceId] = mood.id
                result.add(mood)
            }
            if (noId > 0) warnings.add("$noId Stimmungen ohne Kennung wurden übersprungen.")
            if (badLevel > 0) warnings.add("$badLevel Stimmungen hatten keine gültige Stufe (mood_group_id 1 bis 5) und wurden der nächstliegenden Stufe zugeordnet.")
            return result
        }

        fun readGroups(): List<TagGroup> {
            val result = ArrayList<TagGroup>()
            var noId = 0
            var noColor = 0
            list("tag_groups").forEachIndexed { index, obj ->
                val sourceId = obj.long("id") ?: run { noId++; return@forEachIndexed }
                val order = obj.int(*ORDER_KEYS) ?: index
                val colorIndex = color(obj) ?: run { noColor++; Math.floorMod(order, 12) }
                val group = TagGroup(
                    id = id("group", sourceId),
                    name = obj.string("name")?.trim().orEmpty().ifEmpty { "Gruppe ${index + 1}" },
                    colorIndex = colorIndex,
                    sortOrder = order,
                    collapsed = obj.bool("is_expanded", "isExpanded")?.not() ?: false,
                    archivedAt = archived(obj),
                    updatedAt = now,
                )
                groupIds[sourceId] = group.id
                groupColors[group.id] = group.colorIndex
                result.add(group)
            }
            if (noId > 0) warnings.add("$noId Gruppen ohne Kennung wurden übersprungen.")
            if (noColor > 0) warnings.add("$noColor Gruppen ohne erkennbare Farbnummer: die Farbe wurde nach der Reihenfolge vergeben.")
            return result
        }

        fun readTags(): List<Tag> {
            val result = ArrayList<Tag>()
            var noId = 0
            var lostGroup = 0
            list("tags").forEachIndexed { index, obj ->
                val sourceId = obj.long("id") ?: run { noId++; return@forEachIndexed }
                val name = obj.string("name")?.trim().orEmpty().ifEmpty { "Aktivität ${index + 1}" }
                val groupSource = obj.long("id_tag_group", "tag_group_id", "idTagGroup")
                val groupId = groupSource?.let { groupIds[it] }
                // 0 und negative Werte bedeuten "ohne Gruppe" und sind kein Fehler.
                if (groupSource != null && groupSource > 0 && groupId == null) lostGroup++
                obj.element("icon", "icon_id", "id_icon")
                val tag = Tag(
                    id = id("tag", sourceId),
                    groupId = groupId,
                    name = name,
                    icon = IconGuess.guess(name),
                    // ANNAHME: Farbnummer 0 oder fehlend heisst "Farbe der Gruppe".
                    colorIndex = obj.long(*COLOR_KEYS)?.takeIf { it > 0 }?.let { Math.floorMod(it, 12L).toInt() },
                    sortOrder = obj.int(*ORDER_KEYS) ?: index,
                    archivedAt = archived(obj),
                    updatedAt = now,
                )
                tagIds[sourceId] = tag.id
                tagsById[tag.id] = tag
                result.add(tag)
            }
            if (noId > 0) warnings.add("$noId Aktivitäten ohne Kennung wurden übersprungen.")
            if (lostGroup > 0) warnings.add("$lostGroup Aktivitäten verweisen auf eine unbekannte Gruppe und stehen jetzt ohne Gruppe.")
            return result
        }

        // -------------------------------------------------- Dateien

        fun readAssets(): Pair<List<Asset>, List<AssetFile>> {
            // Archivinhalt nach letztem Namensteil (der Pruefsumme) ordnen.
            val byName = HashMap<String, MutableList<Pair<String, String>>>() // Pruefsumme -> (Originalname, sicherer Pfad)
            var unsafe = 0
            for (name in archive?.names.orEmpty()) {
                val safe = ImportArchive.safePath(name)
                if (safe == null) { unsafe++; continue }
                if (!safe.startsWith("assets/")) continue
                val base = safe.substringAfterLast('/')
                byName.getOrPut(base) { ArrayList() }.add(name to safe)
                if ('.' in base) byName.getOrPut(base.substringBeforeLast('.')) { ArrayList() }.add(name to safe)
            }
            if (unsafe > 0) warnings.add("$unsafe Dateien im Archiv haben einen unzulässigen Pfad und wurden nicht übernommen.")

            val assets = ArrayList<Asset>()
            val files = ArrayList<AssetFile>()
            val types = sortedSetOf<Long>()
            var noId = 0
            var missing = 0
            for (obj in list("assets")) {
                val sourceId = obj.long("id") ?: run { noId++; null } ?: continue
                val checksum = obj.string("checksum")?.trim().orEmpty()
                val createdAt = obj.long("createdAt", "created_at") ?: 0L
                val offset = obj.long("createdAtOffset", "created_at_offset") ?: 0L
                obj.long("type")?.let { types.add(it) }
                val assetId = id("asset", sourceId)
                val candidates = byName[checksum].orEmpty()
                val year = utcDate(createdAt + offset).substring(0, 4)
                val match = candidates.firstOrNull { "/$year/" in it.second } ?: candidates.firstOrNull()
                // Ohne Archiv (reines JSON) ist der Typ unbekannt; die Fotos des Vorbilds sind JPEG.
                val extension = match?.let { archive?.extension?.invoke(it.first) } ?: "jpg"
                val fileName = "$assetId.$extension"
                if (match != null) files.add(AssetFile(assetId, match.first, fileName)) else if (archive != null) missing++
                assets.add(
                    Asset(
                        id = assetId,
                        type = if (ImportArchive.isAudioExtension(extension)) AssetType.AUDIO else AssetType.PHOTO,
                        fileName = fileName,
                        checksum = checksum,
                        takenAt = createdAt,
                        sizeBytes = match?.let { archive?.size?.invoke(it.first) } ?: 0L,
                        updatedAt = now,
                    ),
                )
                assetIds[sourceId] = assetId
            }
            if (noId > 0) warnings.add("$noId Dateien ohne Kennung wurden übersprungen.")
            if (missing > 0) warnings.add("$missing Dateien stehen im Backup, fehlen aber im Archiv.")
            // ANNAHME: das Feld type unterscheidet Foto und Sprachnotiz; entschieden wird am Dateiinhalt.
            if (types.size > 1) warnings.add("Dateien: mehrere Werte im Feld „type“ gefunden ($types). Foto oder Sprachnotiz wurde am Dateiinhalt erkannt.")
            return assets to files
        }

        // -------------------------------------------------- Skalen (Aufbau nicht belegt, daher Heuristik)

        inner class ScaleReader(entryObjs: List<Obj>) {
            private val rawValues = ArrayList<RawScaleValue>()
            private val scaleIds = HashMap<Long, String>()
            private val scaleTypes = HashMap<Long, ScaleType>()
            /** Skala -> (Quell-ID des Punkts -> Punkt). */
            private val points = HashMap<Long, Map<Long, ScalePoint>>()
            private val pointsByPosition = HashMap<Long, Map<Int, ScalePoint>>()
            /** Teiler, der einen Zeitwert in Minuten umrechnet. */
            private var timeDivisor = 1L
            private var entryListKey: String? = null
            private var valueListTopLevel: String? = null

            init {
                collectEntryValues(entryObjs)
            }

            private fun rawValue(entryId: Long, obj: Obj) = RawScaleValue(
                entryId = entryId.also { obj.element("id") },
                scaleId = obj.long(*SCALE_REF_KEYS),
                textPointId = obj.long(*TEXT_VALUE_KEYS),
                time = obj.element(*TIME_VALUE_KEYS),
                number = obj.long(*NUMBER_VALUE_KEYS),
            )

            private fun looksLikeValue(o: JsonObject) =
                (TEXT_VALUE_KEYS.asList() + TIME_VALUE_KEYS.asList() + SCALE_REF_KEYS.asList()).any { it in o }

            private fun collectEntryValues(entryObjs: List<Obj>) {
                // Variante 1: Liste im Eintrag.
                for (entry in entryObjs) {
                    val entryId = entry.json["id"].asLong() ?: continue
                    val key = ENTRY_SCALE_LIST_KEYS.firstOrNull { entry.json[it] is JsonArray }
                        ?: entry.json.entries.firstOrNull { (k, v) ->
                            k != "tags" && k != "assets" && v is JsonArray && objects(v).any(::looksLikeValue)
                        }?.key
                        ?: continue
                    entryListKey = key
                    val valueFields = fieldsOf("dayEntries.$key")
                    for (o in objects(entry.array(key))) rawValues.add(rawValue(entryId, Obj(o, valueFields)))
                }
                // Variante 2: eigene Liste auf oberster Ebene mit Verweis auf den Eintrag.
                for ((key, value) in root) {
                    if (key in TOP_LEVEL_HANDLED || key in TOP_LEVEL_IGNORED || value !is JsonArray) continue
                    val objs = objects(value)
                    if (objs.isEmpty() || !objs.all { o -> ENTRY_REF_KEYS.any { it in o } && looksLikeValue(o) }) continue
                    consumedTopLevel.add(key)
                    valueListTopLevel = key
                    for (o in objs) {
                        val obj = Obj(o, fieldsOf(key))
                        obj.element("id")
                        val entryId = obj.long(*ENTRY_REF_KEYS) ?: continue
                        rawValues.add(rawValue(entryId, obj))
                    }
                }
            }

            /** Punkte, die als eigene Liste auf oberster Ebene liegen: Skala -> Punkte. */
            private fun topLevelPoints(): Map<Long, List<RawPoint>> {
                val result = HashMap<Long, MutableList<RawPoint>>()
                for ((key, value) in root) {
                    if (key in TOP_LEVEL_HANDLED || key in TOP_LEVEL_IGNORED || key in consumedTopLevel || value !is JsonArray) continue
                    val objs = objects(value)
                    val isPointList = objs.isNotEmpty() && objs.all { o ->
                        SCALE_REF_KEYS.any { it in o } && NAME_KEYS.any { it in o } && ENTRY_REF_KEYS.none { it in o }
                    }
                    if (!isPointList) continue
                    consumedTopLevel.add(key)
                    warnings.add("Skalen: Die Punkte der Textskalen wurden aus der Liste „$key“ gelesen (Annahme).")
                    objs.forEachIndexed { index, o ->
                        val obj = Obj(o, fieldsOf(key))
                        val scale = obj.long(*SCALE_REF_KEYS) ?: return@forEachIndexed
                        result.getOrPut(scale) { ArrayList() }
                            .add(RawPoint(obj.long("id"), obj.string(*NAME_KEYS)?.trim().orEmpty(), obj.long(*ORDER_KEYS, "value"), index))
                    }
                }
                return result
            }

            private fun nestedPoints(scale: Obj, usedKeys: MutableSet<String>): List<RawPoint> {
                val key = POINT_LIST_KEYS.firstOrNull { scale.json[it] is JsonArray }
                    ?: scale.json.entries.firstOrNull { (_, v) -> v is JsonArray && objects(v).any { o -> NAME_KEYS.any { it in o } } }?.key
                    ?: return emptyList()
                usedKeys.add(key)
                val pointFields = fieldsOf("scales.$key")
                return scale.array(key).orEmpty().mapIndexedNotNull { index, element ->
                    when (element) {
                        is JsonObject -> {
                            val obj = Obj(element, pointFields)
                            obj.long(*SCALE_REF_KEYS)
                            RawPoint(obj.long("id"), obj.string(*NAME_KEYS)?.trim().orEmpty(), obj.long(*ORDER_KEYS, "value"), index)
                        }
                        is JsonPrimitive -> if (element is JsonNull) null else RawPoint(null, element.content.trim(), null, index)
                        else -> null
                    }
                }
            }

            fun readScales(): List<Scale> {
                val scaleObjs = list("scales")
                if (scaleObjs.isEmpty()) {
                    if (rawValues.isNotEmpty()) warnings.add("${rawValues.size} Skalenwerte gefunden, aber keine Liste „scales“: die Werte wurden nicht übernommen.")
                    return emptyList()
                }
                val external = topLevelPoints()
                val nestedKeys = sortedSetOf<String>()
                val typeGuesses = sortedSetOf<String>()
                val trendValues = sortedSetOf<String>()
                val chartValues = sortedSetOf<String>()
                var chartKey: String? = null
                val usage = rawValues.filter { it.scaleId != null }.groupBy { it.scaleId!! }
                determineTimeUnit()

                val result = ArrayList<Scale>()
                var noId = 0
                scaleObjs.forEachIndexed { index, obj ->
                    val sourceId = obj.long("id") ?: run { noId++; return@forEachIndexed }
                    val scaleId = id("scale", sourceId)
                    val name = obj.string(*NAME_KEYS)?.trim().orEmpty().ifEmpty { "Skala ${index + 1}" }
                    val rawPoints = nestedPoints(obj, nestedKeys).ifEmpty { external[sourceId].orEmpty() }

                    // Typ: zuerst ein ausdrueckliches Feld, dann die Punkte, dann die Art der Werte in den Eintraegen.
                    val typeText = obj.string("type", "scale_type", "scaleType", "id_type", "value_type", "id_scale_type", "kind")?.lowercase().orEmpty()
                    val used = usage[sourceId].orEmpty()
                    val type = when {
                        "text" in typeText || "select" in typeText || "choice" in typeText -> ScaleType.TEXT.also { typeGuesses.add("Feld für den Typ") }
                        "time" in typeText || "dur" in typeText -> ScaleType.DURATION.also { typeGuesses.add("Feld für den Typ") }
                        "num" in typeText || "int" in typeText -> ScaleType.NUMBER.also { typeGuesses.add("Feld für den Typ") }
                        rawPoints.isNotEmpty() -> ScaleType.TEXT.also { typeGuesses.add("vorhandene Textpunkte") }
                        used.any { it.time != null } -> ScaleType.DURATION.also { typeGuesses.add("Art der Werte in den Einträgen") }
                        used.any { it.textPointId != null } -> ScaleType.TEXT.also { typeGuesses.add("Art der Werte in den Einträgen") }
                        used.any { it.number != null } -> ScaleType.NUMBER.also { typeGuesses.add("Art der Werte in den Einträgen") }
                        // ANNAHME: eine Skala ohne Punkte und ohne Werte ist eine Zeitskala (es gibt im Vorbild nur Text und Zeit).
                        else -> ScaleType.DURATION.also { typeGuesses.add("keine Anhaltspunkte, als Zeitskala angenommen") }
                    }

                    // ANNAHME: die Punkte stehen in aufsteigender Reihenfolge (erster Punkt = Position 1).
                    val sorted = rawPoints.sortedWith(compareBy({ it.order ?: it.index.toLong() }, { it.index }))
                    val scalePoints = if (type != ScaleType.TEXT) emptyList() else sorted.mapIndexed { i, p ->
                        ScalePoint(
                            id = deterministicId("scalepoint", "$sourceId:${p.sourceId ?: "#${p.index}"}"),
                            position = i + 1,
                            label = p.label.ifEmpty { "${i + 1}" },
                        )
                    }
                    if (type == ScaleType.TEXT) {
                        points[sourceId] = sorted.mapIndexedNotNull { i, p -> p.sourceId?.let { it to scalePoints[i] } }.toMap()
                        pointsByPosition[sourceId] = scalePoints.associateBy { it.position }
                    }

                    // ANNAHME: trend 1 = hoeher ist besser, 2 = niedriger ist besser, sonst neutral; Text wird nach Stichwort gelesen.
                    val trendRaw = obj.string("trend", "trend_type", "id_trend")
                    trendRaw?.let { trendValues.add(it) }
                    val trend = when {
                        trendRaw == null -> if (type == ScaleType.DURATION) Trend.NEUTRAL else Trend.HIGHER_IS_BETTER
                        Regex("high|up|pos|more").containsMatchIn(trendRaw.lowercase()) || trendRaw == "1" -> Trend.HIGHER_IS_BETTER
                        Regex("low|down|neg|less").containsMatchIn(trendRaw.lowercase()) || trendRaw == "2" -> Trend.LOWER_IS_BETTER
                        else -> Trend.NEUTRAL
                    }

                    // ANNAHME: die Diagramm-Anzeige steht in einem dieser Felder; "sum"/"total" oder 1 bedeutet Tagessumme.
                    val chartKeys = arrayOf("chart_type", "chartType", "chart_mode", "chart_display", "chart", "id_chart_type", "display_type", "aggregation", "daily_aggregation")
                    obj.usedKey(*chartKeys)?.let { chartKey = it }
                    val chartRaw = obj.string(*chartKeys)
                    chartRaw?.let { chartValues.add(it) }
                    val chartMode = if (chartRaw != null && (Regex("sum|total").containsMatchIn(chartRaw.lowercase()) || chartRaw == "1")) {
                        ChartMode.DAILY_SUM
                    } else {
                        ChartMode.DAILY_AVERAGE
                    }

                    val minutesUsed = used.mapNotNull { minutesOf(it.time) }
                    val scale = Scale(
                        id = scaleId,
                        type = type,
                        name = name,
                        icon = IconGuess.guess(name).let { if (it == IconGuess.FALLBACK) "tune" else it },
                        colorIndex = color(obj) ?: Math.floorMod(index, 12),
                        sortOrder = obj.int(*ORDER_KEYS) ?: index,
                        chartMode = chartMode,
                        trend = trend,
                        min = when (type) {
                            ScaleType.TEXT -> 1
                            ScaleType.DURATION -> 0
                            ScaleType.NUMBER -> minOf(obj.int("min", "min_value", "minValue") ?: 1, used.mapNotNull { it.number }.minOrNull()?.toInt() ?: Int.MAX_VALUE)
                        },
                        max = when (type) {
                            ScaleType.TEXT -> scalePoints.size.coerceAtLeast(1)
                            // Ein Tag als Obergrenze, ausser es kommen laengere Werte vor.
                            ScaleType.DURATION -> maxOf(24 * 60, minutesUsed.maxOrNull() ?: 0)
                            ScaleType.NUMBER -> maxOf(obj.int("max", "max_value", "maxValue") ?: 10, used.mapNotNull { it.number }.maxOrNull()?.toInt() ?: Int.MIN_VALUE)
                        },
                        step = if (type == ScaleType.DURATION) durationStep(minutesUsed) else 1,
                        points = scalePoints,
                        archivedAt = archived(obj),
                        updatedAt = now,
                    )
                    obj.element("icon", "icon_id", "id_icon")
                    scaleIds[sourceId] = scaleId
                    scaleTypes[sourceId] = type
                    result.add(scale)
                }

                if (noId > 0) warnings.add("$noId Skalen ohne Kennung wurden übersprungen.")
                // Jede Annahme sichtbar machen, damit sich die echte Datei auf dem Handy pruefen laesst.
                warnings.add(
                    "Skalen: Der Aufbau im Backup ist nicht sicher bekannt und wurde erschlossen. " +
                        "Typ bestimmt über: ${typeGuesses.joinToString("; ").ifEmpty { "–" }}. " +
                        "Textpunkte aus: ${if (nestedKeys.isNotEmpty()) "Feld ${nestedKeys.joinToString { "„$it“" }} der Skala" else if (external.isNotEmpty()) "eigener Liste" else "nicht gefunden"}. " +
                        "Werte der Einträge aus: ${entryListKey?.let { "Feld „$it“ im Eintrag" } ?: valueListTopLevel?.let { "Liste „$it“" } ?: "nicht gefunden"}.",
                )
                if (result.any { it.type == ScaleType.DURATION } && rawValues.any { it.time != null }) {
                    val unit = when (timeDivisor) { 60_000L -> "Millisekunden"; 60L -> "Sekunden"; else -> "Minuten" }
                    warnings.add("Skalen: Zeitwerte wurden als $unit gelesen (Annahme anhand der Größe der Werte). Bitte an einem bekannten Eintrag prüfen.")
                }
                if (trendValues.isNotEmpty()) {
                    warnings.add("Skalen: Trend-Werte im Backup: ${trendValues.joinToString()}. Angenommen: 1 = höher ist besser, 2 = niedriger ist besser, sonst neutral.")
                }
                if (chartValues.isNotEmpty()) {
                    warnings.add("Skalen: Diagramm-Anzeige aus Feld „$chartKey“ mit den Werten ${chartValues.joinToString()}. Angenommen: 1 oder „sum“ = Tagessumme, sonst Tagesdurchschnitt.")
                }
                val textScales = result.filter { it.type == ScaleType.TEXT && it.points.isEmpty() }.size
                if (textScales > 0) warnings.add("$textScales Textskalen ohne erkennbare Punkte: ihre Werte konnten nicht übernommen werden.")
                return result
            }

            /**
             * ANNAHME zur Einheit des Zeitwerts: sind alle Werte durch 60.000 teilbar, sind es
             * Millisekunden; sind alle durch 60 teilbar und groesser als ein Tag in Minuten,
             * sind es Sekunden; sonst Minuten.
             */
            private fun determineTimeUnit() {
                val numbers = rawValues.mapNotNull { it.time.asLong()?.takeIf { v -> v > 0 } }
                if (numbers.isEmpty()) return
                val max = numbers.max()
                timeDivisor = when {
                    max >= 60_000 && numbers.all { it % 60_000 == 0L } -> 60_000
                    max > 24 * 60 && numbers.all { it % 60 == 0L } -> 60
                    else -> 1
                }
            }

            /** Zeitwert in Minuten; Text der Form "6:00" wird ebenfalls verstanden. */
            private fun minutesOf(time: JsonElement?): Int? {
                val p = time as? JsonPrimitive ?: return null
                if (p is JsonNull) return null
                Regex("^(\\d{1,3}):(\\d{2})(?::\\d{2})?$").find(p.content.trim())?.let {
                    return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()
                }
                val value = p.asLong() ?: return null
                if (value < 0) return null
                return (value / timeDivisor).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }

            private fun durationStep(minutes: List<Int>): Int =
                listOf(60, 30, 15, 10, 5).firstOrNull { step -> minutes.isNotEmpty() && minutes.all { it % step == 0 } } ?: if (minutes.isEmpty()) 15 else 1

            private val byEntry: Map<Long, List<RawScaleValue>> by lazy { rawValues.groupBy { it.entryId } }
            var dropped = 0
                private set

            /** Skalenwerte eines Eintrags im eigenen Modell. Nicht Zuordenbares wird gezaehlt. */
            fun valuesFor(entrySourceId: Long): List<ScaleValue> {
                val result = ArrayList<ScaleValue>()
                for (raw in byEntry[entrySourceId].orEmpty()) {
                    // Fehlt der Verweis auf die Skala, hilft bei Textwerten die Punkt-ID, wenn sie eindeutig ist.
                    val scaleSource = raw.scaleId
                        ?: raw.textPointId?.let { pid -> points.filterValues { pid in it }.keys.singleOrNull() }
                    val scaleId = scaleSource?.let { scaleIds[it] }
                    if (scaleSource == null || scaleId == null) { dropped++; continue }
                    val value = when (scaleTypes[scaleSource]) {
                        ScaleType.TEXT -> {
                            val point = raw.textPointId?.let { points[scaleSource]?.get(it) }
                                // ANNAHME: ohne Punkt-ID ist ein Zahlenwert die Position des Punkts.
                                ?: raw.number?.let { pointsByPosition[scaleSource]?.get(it.toInt()) }
                            point?.let { ScaleValue(scaleId = scaleId, pointId = it.id) }
                        }
                        ScaleType.DURATION -> (minutesOf(raw.time) ?: raw.number?.let { minutesOf(JsonPrimitive(it)) })
                            ?.let { ScaleValue(scaleId = scaleId, minutes = it) }
                        ScaleType.NUMBER -> raw.number?.let { ScaleValue(scaleId = scaleId, number = it.toInt()) }
                        null -> null
                    }
                    if (value == null) dropped++ else if (result.none { it.scaleId == scaleId }) result.add(value)
                }
                return result
            }
        }

        // -------------------------------------------------- Eintraege

        fun readEntries(entryObjs: List<Obj>, scaleReader: ScaleReader): List<Entry> {
            val result = ArrayList<Entry>()
            val seen = HashSet<Long>()
            var noId = 0
            var duplicate = 0
            var noMood = 0
            var noTime = 0
            var fromFields = 0
            var badOffset = 0
            var monthMismatch = 0
            var lostTags = 0
            var lostAssets = 0
            for (obj in entryObjs) {
                val sourceId = obj.long("id")
                if (sourceId == null) { noId++; continue }
                if (!seen.add(sourceId)) { duplicate++; continue }
                val moodId = obj.long("mood", "id_mood", "mood_id")?.let { moodIds[it] }
                val year = obj.int("year")
                val month = obj.int("month")
                val day = obj.int("day")
                val hour = obj.int("hour")
                val minute = obj.int("minute")
                var offsetMs = obj.long("timeZoneOffset", "time_zone_offset", "timezoneOffset") ?: 0L
                if (offsetMs % 1000 != 0L || offsetMs !in -18 * 3_600_000L..18 * 3_600_000L) { badOffset++; offsetMs = 0 }
                val offset = ZoneOffset.ofTotalSeconds((offsetMs / 1000).toInt())

                // Massgeblich ist datetime (Millisekunden UTC) mit timeZoneOffset. Die Einzelfelder
                // (Monat zaehlt dort ab 0) dienen nur als Ersatz und zur Gegenprobe.
                var timestamp = obj.long("datetime", "date_time", "dateTime")
                if (timestamp == null) {
                    val local = if (year != null && month != null && day != null) {
                        try {
                            LocalDateTime.of(year, month + 1, day, hour ?: 12, minute ?: 0)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    if (local == null) { noTime++; continue }
                    timestamp = local.toInstant(offset).toEpochMilli()
                    fromFields++
                }
                if (moodId == null) { noMood++; continue }
                val local = Instant.ofEpochMilli(timestamp).atOffset(offset)
                if (month != null && month + 1 != local.monthValue) monthMismatch++

                val tagSources = obj.array("tags").orEmpty().mapNotNull { it.asLong() }
                val tags = tagSources.mapNotNull { tagIds[it] }.distinct()
                lostTags += tagSources.count { it !in tagIds }
                val assetSources = obj.array("assets").orEmpty().mapNotNull { it.asLong() }
                val assets = assetSources.mapNotNull { assetIds[it] }.distinct()
                lostAssets += assetSources.count { it !in assetIds }

                result.add(
                    Entry(
                        id = id("entry", sourceId),
                        timestamp = timestamp,
                        offsetMinutes = (offsetMs / 60_000).toInt(),
                        localDate = local.toLocalDate().toString(),
                        minuteOfDay = local.hour * 60 + local.minute,
                        moodId = moodId,
                        title = obj.string("note_title", "noteTitle", "title")?.let { NoteHtml.toPlainText(it) }.orEmpty(),
                        note = NoteHtml.sanitize(obj.string("note").orEmpty()).trim(),
                        favorite = obj.bool("isFavorite", "is_favorite") ?: false,
                        source = EntrySource.IMPORT,
                        tagIds = tags,
                        scaleValues = scaleReader.valuesFor(sourceId),
                        assetIds = assets,
                        updatedAt = now,
                    ),
                )
            }
            if (noId > 0) warnings.add("$noId Einträge ohne Kennung wurden übersprungen.")
            if (duplicate > 0) warnings.add("$duplicate Einträge mit doppelter Kennung wurden übersprungen.")
            if (noMood > 0) warnings.add("$noMood Einträge wurden übersprungen, weil ihre Stimmung im Backup nicht vorkommt.")
            if (noTime > 0) warnings.add("$noTime Einträge wurden übersprungen, weil Datum und Uhrzeit fehlen.")
            if (fromFields > 0) warnings.add("$fromFields Einträge ohne „datetime“: der Zeitpunkt wurde aus den Einzelfeldern berechnet (Monat ab 0 gezählt).")
            if (badOffset > 0) warnings.add("$badOffset Einträge mit ungültigem Zeitzonen-Versatz: es wurde UTC angenommen.")
            if (monthMismatch > 0) warnings.add("$monthMismatch Einträge, bei denen das Feld „month“ (ab 0 gezählt) nicht zum Zeitpunkt passt. Maßgeblich ist der Zeitpunkt.")
            if (lostTags > 0) warnings.add("$lostTags Verweise auf unbekannte Aktivitäten wurden aus Einträgen entfernt.")
            if (lostAssets > 0) warnings.add("$lostAssets Verweise auf unbekannte Dateien wurden aus Einträgen entfernt.")
            if (scaleReader.dropped > 0) warnings.add("${scaleReader.dropped} Skalenwerte konnten keiner Skala oder keinem Punkt zugeordnet werden.")
            (root["metadata"] as? JsonObject)?.get("number_of_entries").asLong()?.let { expected ->
                if (expected != entryObjs.size.toLong()) warnings.add("Das Backup nennt $expected Einträge, enthalten sind ${entryObjs.size}.")
            }
            return result
        }

        // -------------------------------------------------- Ziele

        fun readGoals(): Pair<List<Goal>, List<GoalCheck>> {
            val goalObjs = list("goals")
            val checkObjs = list("goalEntries")

            // Worauf zeigt goalEntries.goalId: auf "goal_id" oder auf "id" des Ziels? Was oefter passt, gewinnt.
            val refs = checkObjs.mapNotNull { it.long("goalId", "goal_id", "id_goal") }
            val byGoalId = goalObjs.mapNotNull { it.long("goal_id") }.toSet()
            val byId = goalObjs.mapNotNull { it.long("id") }.toSet()
            val useGoalId = byGoalId.isNotEmpty() && refs.count { it in byGoalId } >= refs.count { it in byId }
            if (byGoalId.isNotEmpty() && byGoalId != byId) {
                warnings.add("Ziele: „id“ und „goal_id“ unterscheiden sich. Abgehakte Tage wurden über „${if (useGoalId) "goal_id" else "id"}“ zugeordnet.")
            }

            val goalIds = HashMap<Long, String>()
            val goals = ArrayList<Goal>()
            val createdDates = HashMap<String, String>()
            val repeatTypes = sortedSetOf<Long>()
            var noId = 0
            var weekdayMask = 0
            var monthly = 0
            goalObjs.forEachIndexed { index, obj ->
                val sourceId = (if (useGoalId) obj.long("goal_id") else null) ?: obj.long("id")
                obj.long("goal_id")
                if (sourceId == null) { noId++; return@forEachIndexed }
                val tag = obj.long("id_tag", "tag_id")?.let { tagIds[it] }?.let { tagsById[it] }
                val name = obj.string("name")?.trim().orEmpty().ifEmpty { tag?.name ?: "Ziel ${index + 1}" }
                val created = obj.long("created_at", "createdAt")
                val goalId = id("goal", sourceId)

                // ANNAHME: repeat_type 1 = taeglich, 2 = x-mal pro Woche (repeat_value = Anzahl), 3 = monatlich.
                val repeatType = obj.long("repeat_type", "repeatType") ?: 1L
                val repeatValue = obj.long("repeat_value", "repeatValue") ?: 0L
                repeatTypes.add(repeatType)
                var repeat = GoalRepeat.DAILY
                var times = 1
                when (repeatType) {
                    2L -> { repeat = GoalRepeat.TIMES_PER_WEEK; times = repeatValue.coerceIn(1, 7).toInt() }
                    3L -> { repeat = GoalRepeat.TIMES_PER_WEEK; times = 1; monthly++ }
                    // Bei taeglichen Zielen koennte repeat_value eine Wochentagsauswahl sein; die Kodierung ist unbekannt.
                    else -> if (repeatValue in 1..126) weekdayMask++
                }

                val reminderOn = obj.bool("reminder_enabled", "reminderEnabled") ?: false
                val reminder = reminderMinute(obj)
                val goal = Goal(
                    id = goalId,
                    name = name,
                    tagId = tag?.id,
                    icon = tag?.icon?.takeIf { it != IconGuess.FALLBACK } ?: IconGuess.guess(name).let { if (it == IconGuess.FALLBACK) "flag" else it },
                    colorIndex = tag?.colorIndex ?: tag?.groupId?.let { groupColors[it] } ?: 0,
                    repeat = repeat,
                    timesPerWeek = times,
                    reminderMinuteOfDay = if (reminderOn) reminder else null,
                    startDate = created?.let { utcDate(it) } ?: "",
                    note = NoteHtml.toPlainText(obj.string("note").orEmpty()),
                    sortOrder = obj.int(*ORDER_KEYS) ?: index,
                    archivedAt = archived(obj),
                    updatedAt = now,
                )
                obj.element("id_icon", "icon_id", "icon")
                goalIds[sourceId] = goalId
                if (created != null) createdDates[goalId] = goal.startDate
                goals.add(goal)
            }
            if (noId > 0) warnings.add("$noId Ziele ohne Kennung wurden übersprungen.")
            if (goals.isNotEmpty()) {
                warnings.add("Ziele: Wiederholung aus „repeat_type“ (Werte: ${repeatTypes.joinToString()}) gelesen. Angenommen: 1 = täglich, 2 = mehrmals pro Woche, 3 = monatlich.")
            }
            if (weekdayMask > 0) warnings.add("$weekdayMask tägliche Ziele haben möglicherweise eine Auswahl von Wochentagen. Sie wurde nicht übernommen, bitte prüfen.")
            if (monthly > 0) warnings.add("$monthly monatliche Ziele wurden als „einmal pro Woche“ übernommen, weil es monatliche Ziele hier nicht gibt.")

            // --- abgehakte Tage ---
            val base = goalMonthBase(checkObjs)
            val checks = LinkedHashMap<String, GoalCheck>()
            var unknownGoal = 0
            var badDate = 0
            var duplicates = 0
            for (obj in checkObjs) {
                obj.element("id", "hour", "minute")
                val goalId = obj.long("goalId", "goal_id", "id_goal")?.let { goalIds[it] }
                if (goalId == null) { unknownGoal++; continue }
                val year = obj.int("year")
                val month = obj.int("month")
                val day = obj.int("day")
                val created = obj.long("createdAt", "created_at")
                val date = if (year != null && month != null && day != null) {
                    isoDate(year, month - base + 1, day)
                } else {
                    created?.let { utcDate(it) }
                }
                if (date == null) { badDate++; continue }
                val checkId = GoalCheck.idFor(goalId, date)
                if (checkId in checks) { duplicates++; continue }
                checks[checkId] = GoalCheck(id = checkId, goalId = goalId, date = date, updatedAt = now)
            }
            if (unknownGoal > 0) warnings.add("$unknownGoal abgehakte Tage gehören zu keinem bekannten Ziel und wurden übersprungen.")
            if (badDate > 0) warnings.add("$badDate abgehakte Tage ohne gültiges Datum wurden übersprungen.")
            if (duplicates > 0) warnings.add("$duplicates abgehakte Tage waren doppelt und wurden zusammengefasst.")

            // Startdatum: der Anlagetag, aber nie nach dem ersten abgehakten Tag (und nie leer).
            val firstCheck = checks.values.groupBy { it.goalId }.mapValues { (_, list) -> list.minOf { it.date } }
            val fixed = goals.map { goal ->
                val candidates = listOfNotNull(createdDates[goal.id], firstCheck[goal.id])
                goal.copy(startDate = candidates.minOrNull() ?: utcDate(now))
            }
            return fixed to checks.values.toList()
        }

        /** Erinnerungszeit eines Ziels in Minuten seit Mitternacht; mehrere Schreibweisen. */
        private fun reminderMinute(obj: Obj): Int? {
            val hour = obj.int("reminder_hour", "reminderHour")
            val minute = obj.int("reminder_minute", "reminderMinute")
            val raw = obj.string("reminder_time", "reminderTime")
            if (hour != null && hour in 0..23) return hour * 60 + (minute ?: 0).coerceIn(0, 59)
            if (raw == null) return null
            Regex("^(\\d{1,2}):(\\d{2})").find(raw.trim())?.let {
                return (it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()).takeIf { m -> m in 0 until 1440 }
            }
            val number = raw.toLongOrNull() ?: return null
            // ANNAHME: kleine Zahlen sind Minuten seit Mitternacht, grosse Millisekunden seit Mitternacht.
            return when {
                number in 0 until 1440 -> number.toInt()
                number in 1440 until 86_400_000L -> (number / 60_000).toInt()
                else -> null
            }
        }

        /**
         * Monatszaehlung in goalEntries ist nicht belegt (vermutlich ab 1) und wird deshalb
         * an den Daten bestimmt: kommt Monat 0 vor, zaehlt er ab 0; kommt 12 vor, ab 1;
         * sonst entscheidet der Vergleich mit createdAt; ohne Anhaltspunkt gilt ab 1.
         */
        private fun goalMonthBase(checks: List<Obj>): Int {
            if (checks.isEmpty()) return 1
            val months = checks.mapNotNull { it.int("month") }
            val hasZero = 0 in months
            val hasTwelve = 12 in months
            var asOne = 0
            var asZero = 0
            for (obj in checks) {
                val month = obj.int("month") ?: continue
                val created = obj.long("createdAt", "created_at") ?: continue
                val createdMonth = Instant.ofEpochMilli(created).atOffset(ZoneOffset.UTC).monthValue
                if (month == createdMonth) asOne++
                if (month + 1 == createdMonth) asZero++
            }
            val (base, reason) = when {
                hasZero && hasTwelve -> (if (asZero > asOne) 0 else 1) to "Monat 0 und Monat 12 kommen beide vor, das ist widersprüchlich"
                hasZero -> 0 to "Monat 0 kommt vor"
                hasTwelve -> 1 to "Monat 12 kommt vor"
                asZero > asOne -> 0 to "Vergleich mit dem Zeitpunkt des Abhakens"
                asOne > asZero -> 1 to "Vergleich mit dem Zeitpunkt des Abhakens"
                else -> 1 to "kein Anhaltspunkt in den Daten, Vermutung laut Beschreibung"
            }
            warnings.add("Abgehakte Tage: Der Monat wurde als „ab $base gezählt“ gelesen ($reason).")
            return base
        }

        // -------------------------------------------------- Wichtige Tage (Aufbau nicht belegt, daher Heuristik)

        fun readMilestones(): List<ImportantDay> {
            val objs = list("milestones")
            if (objs.isEmpty()) return emptyList()

            // ANNAHME: Kategorien liegen als Nummer vor; Namen stehen, wenn ueberhaupt, in einer Liste mit "categor" im Namen.
            val categoryNames = HashMap<Long, String>()
            for ((key, value) in root) {
                if (!key.contains("categor", ignoreCase = true) || value !is JsonArray) continue
                consumedTopLevel.add(key)
                for (o in objects(value)) {
                    val obj = Obj(o, fieldsOf(key))
                    val catId = obj.long("id") ?: continue
                    categoryNames[catId] = obj.string(*NAME_KEYS)?.trim().orEmpty()
                }
            }

            val result = ArrayList<ImportantDay>()
            var noId = 0
            var badDate = 0
            var numericCategory = 0
            objs.forEachIndexed { index, obj ->
                val sourceId = obj.long("id")
                if (sourceId == null) { noId++; return@forEachIndexed }
                // Monat zaehlt hier ab 1 (belegt). Ohne Einzelfelder: Zeitstempel.
                val year = obj.int("year")
                val month = obj.int("month")
                val day = obj.int("day", "day_of_month")
                val millis = obj.long("date", "datetime", "date_time", "dateTime", "timestamp")
                val date = if (month != null && day != null) {
                    // Jahr 0 oder fehlend: jaehrlicher Tag ohne Jahr; 2000 als Platzhalter (Schaltjahr, damit der 29.2. gueltig ist).
                    isoDate(year?.takeIf { it > 0 } ?: 2000, month, day)
                } else {
                    millis?.let { utcDate(it + (obj.long("timeZoneOffset", "offset") ?: 0L)) }
                }
                if (date == null) { badDate++; return@forEachIndexed }

                val categoryRaw = obj.element("category", "category_name", "id_category", "category_id", "categoryId", "id_milestone_category")
                val category = when {
                    categoryRaw is JsonPrimitive && categoryRaw.isString && categoryRaw.content.toLongOrNull() == null -> categoryRaw.content.trim()
                    else -> categoryRaw.asLong()?.let { categoryNames[it] ?: run { numericCategory++; "" } }.orEmpty()
                }

                // ANNAHME: das Foto steht als Verweis auf einen Eintrag der Liste assets.
                val assetSource = obj.long("id_asset", "asset_id", "assetId", "id_photo", "photo_id", "photo")
                    ?: obj.array("assets", "photos")?.firstNotNullOfOrNull { it.asLong() ?: (it as? JsonObject)?.get("id").asLong() }

                // ANNAHME: Erinnerungen als Liste von Tagen vorher (0 = am Tag selbst) oder als Ja/Nein-Felder.
                val daysBefore = sortedSetOf<Int>()
                var onDay = obj.bool("remind_on_day", "reminder_on_day", "is_reminder_enabled", "reminder_enabled", "remindOnDay") ?: false
                val reminderList = obj.array("reminders", "reminder_days", "remind_days_before", "reminderDays")
                for (element in reminderList.orEmpty()) {
                    val days = element.asLong()
                        ?: (element as? JsonObject)?.let { o ->
                            val r = Obj(o, fieldsOf("milestones.reminders"))
                            r.element("id", "hour", "minute", "state")
                            r.long("days_before", "daysBefore", "days", "day_offset", "offset", "value")
                        }
                        ?: continue
                    if (days == 0L) onDay = true else if (days in 1..366) daysBefore.add(days.toInt())
                }

                result.add(
                    ImportantDay(
                        id = id("milestone", sourceId),
                        name = obj.string(*NAME_KEYS)?.trim().orEmpty().ifEmpty { "Wichtiger Tag ${index + 1}" },
                        date = date,
                        category = category,
                        note = NoteHtml.toPlainText(obj.string("note", "description").orEmpty()),
                        assetId = assetSource?.let { assetIds[it] },
                        // ANNAHME: ohne erkennbares Feld wiederholt sich der Tag jaehrlich.
                        yearly = obj.bool("is_yearly", "yearly", "is_anniversary", "is_repeating", "repeat_yearly", "isYearly") ?: true,
                        remindOnDay = onDay,
                        remindDaysBefore = daysBefore.toList(),
                        sortOrder = obj.int(*ORDER_KEYS) ?: index,
                        updatedAt = now,
                    ),
                )
            }
            if (noId > 0) warnings.add("$noId wichtige Tage ohne Kennung wurden übersprungen.")
            if (badDate > 0) warnings.add("$badDate wichtige Tage ohne gültiges Datum wurden übersprungen.")
            if (numericCategory > 0) warnings.add("$numericCategory wichtige Tage haben eine Kategorie-Nummer ohne Namen. Die Kategorie blieb leer.")
            warnings.add("Wichtige Tage: Der Aufbau im Backup ist nicht sicher bekannt. Datum (Monat ab 1), Kategorie, Foto, Wiederholung und Erinnerungen wurden über naheliegende Feldnamen gelesen. Bitte prüfen.")
            return result
        }

        // -------------------------------------------------- Vorlagen, Erinnerungen, Einstellungen

        fun readTemplates(): List<Template> {
            var noId = 0
            val result = list("writingTemplates").mapIndexedNotNull { index, obj ->
                val sourceId = obj.long("id") ?: run { noId++; return@mapIndexedNotNull null }
                obj.element("predefined_template_id")
                Template(
                    id = id("template", sourceId),
                    title = obj.string("title")?.trim().orEmpty().ifEmpty { "Vorlage ${index + 1}" },
                    body = NoteHtml.sanitize(obj.string("body").orEmpty()),
                    sortOrder = obj.int(*ORDER_KEYS) ?: index,
                    updatedAt = now,
                )
            }
            if (noId > 0) warnings.add("$noId Vorlagen ohne Kennung wurden übersprungen.")
            return result
        }

        fun readReminders(): List<Reminder> {
            var noId = 0
            val result = list("reminders").mapNotNull { obj ->
                val sourceId = obj.long("id") ?: run { noId++; return@mapNotNull null }
                val custom = obj.bool("customTextEnabled", "custom_text_enabled") ?: false
                val text = obj.string("customText", "custom_text")?.trim().orEmpty()
                Reminder(
                    id = id("reminder", sourceId),
                    minuteOfDay = ((obj.int("hour") ?: 20).coerceIn(0, 23)) * 60 + (obj.int("minute") ?: 0).coerceIn(0, 59),
                    text = if (custom) text else "",
                    // ANNAHME: state 0 = aktiv.
                    active = (obj.long("state") ?: 0L) == 0L,
                    updatedAt = now,
                )
            }
            if (noId > 0) warnings.add("$noId Erinnerungen ohne Kennung wurden übersprungen.")
            return result
        }

        /** Nur Wochenanfang, Mitternachtsregel und eigene Farben, und nur wo sicher erkennbar. */
        fun readPrefs(): UserSettings {
            var settings = UserSettings()
            val unclear = sortedSetOf<String>()
            for (obj in list("prefs")) {
                obj.element("pref_name")
                val key = obj.string("key") ?: continue
                val upper = key.uppercase()
                val value = obj.element("value") ?: continue
                when {
                    // ANNAHME: Wochenanfang als Java-Wochentag (1 = Sonntag, 2 = Montag ... 7 = Samstag).
                    "WEEK" in upper && ("FIRST" in upper || "START" in upper) -> {
                        val v = value.asLong()
                        if (v != null && v in 1..7) settings = settings.copy(weekStart = ((v + 5) % 7 + 1).toInt()) else unclear.add(key)
                    }
                    "MIDNIGHT" in upper -> {
                        // Nur eindeutige Textwerte; Zahlen und Ja/Nein sind ohne Kenntnis der Bedeutung nicht sicher.
                        val text = (value as? JsonPrimitive)?.content?.lowercase().orEmpty()
                        val rule = when {
                            "ask" in text -> MidnightRule.ASK
                            "prev" in text || "yesterday" in text -> MidnightRule.PREVIOUS_DAY
                            "current" in text || "today" in text || "same" in text -> MidnightRule.CURRENT_DAY
                            else -> null
                        }
                        if (rule != null) settings = settings.copy(midnightRule = rule) else unclear.add(key)
                    }
                    "COLOR" in upper || "COLOUR" in upper -> {
                        val colors = parseColors(value)
                        if (colors != null) settings = settings.copy(customMoodColors = colors, moodScheme = "custom") else unclear.add(key)
                    }
                }
            }
            if (unclear.isNotEmpty()) {
                warnings.add("Einstellungen: Diese Angaben wurden gefunden, aber nicht sicher verstanden und deshalb nicht übernommen: ${unclear.joinToString()}.")
            }
            return if (settings == UserSettings()) settings else settings.copy(updatedAt = now)
        }

        /** ANNAHME: eigene Farben als Liste von genau fuenf Farbwerten (Zahl ARGB oder "#RRGGBB"), Stufe 1 zuerst. */
        private fun parseColors(value: JsonElement): List<String>? {
            val array = when {
                value is JsonArray -> value
                value is JsonPrimitive && value.content.trim().startsWith("[") ->
                    try { Json.parseToJsonElement(value.content) as? JsonArray } catch (e: Exception) { null }
                else -> null
            } ?: return null
            if (array.size != 5) return null
            return array.map { element ->
                val p = element as? JsonPrimitive ?: return null
                val hex = p.content.trim().removePrefix("#")
                when {
                    p.isString && hex.length == 6 && hex.all { it.isLetterOrDigit() } && hex.toLongOrNull(16) != null -> "FF${hex.uppercase()}"
                    p.isString && hex.length == 8 && hex.toLongOrNull(16) != null -> hex.uppercase()
                    !p.isString && p.asLong() != null -> (p.asLong()!! and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
                    else -> return null
                }
            }
        }

        // -------------------------------------------------- Unbekanntes sichtbar machen

        fun reportUnknown() {
            for ((listName, f) in fields) {
                val unknown = f.unknown()
                if (unknown.isNotEmpty()) warnings.add("Liste „$listName“: unbekannte Felder, nicht übernommen: ${unknown.joinToString()}.")
            }
            for ((key, value) in root) {
                if (key in TOP_LEVEL_HANDLED || key in TOP_LEVEL_IGNORED || key in consumedTopLevel) continue
                if (value is JsonArray) {
                    val keys = LinkedHashMap<String, String>()
                    for (o in objects(value)) for ((k, v) in o) keys.putIfAbsent(k, typeLabel(v))
                    val detail = if (keys.isEmpty()) "" else ", Felder: " + keys.map { "${it.key} (${it.value})" }.joinToString()
                    warnings.add("Unbekannte Liste „$key“ mit ${value.size} Einträgen wurde nicht übernommen$detail.")
                } else {
                    warnings.add("Unbekannte Angabe „$key“ (${typeLabel(value)}) wurde nicht übernommen.")
                }
            }
        }
    }
}
