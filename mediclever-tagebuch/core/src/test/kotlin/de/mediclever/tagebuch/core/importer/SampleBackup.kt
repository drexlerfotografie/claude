package de.mediclever.tagebuch.core.importer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * Erzeugt ein kuenstliches Daylio-Backup mit erfundenen deutschen Inhalten, immer gleich
 * (fester Startwert). Aufbau wie das echte Format: ZIP mit backup.daylio (Base64-JSON)
 * und Fotos unter assets/photos/JAHR/MONAT/PRUEFSUMME.
 *
 * Die erwarteten Zaehlwerte ([Expected]) werden beim Erzeugen unabhaengig vom Import mitgezaehlt.
 * Die Feldnamen fuer Skalen, Skalenwerte und wichtige Tage sind Annahmen (siehe DaylioBackup).
 */
object SampleBackup {

    data class Expected(
        val moods: Int,
        val groups: Int,
        val tags: Int,
        val archivedTags: Int,
        val scales: Int,
        val entries: Int,
        val entriesPerLevel: List<Int>,
        val entriesWithNote: Int,
        val favorites: Int,
        val assets: Int,
        val assetLinks: Int,
        val goals: Int,
        val goalChecks: Int,
        val importantDays: Int,
        val templates: Int,
        val reminders: Int,
        val earliestDate: String,
        val latestDate: String,
        val entriesWithTextValue: Int,
        val entriesWithTimeValue: Int,
        /** Quell-ID -> (lokales Datum, Minute des Tages, Versatz in Minuten) fuer die Grenzfaelle. */
        val special: Map<Long, Triple<String, Int, Int>>,
    )

    class Sample(val json: String, val files: List<Pair<String, ByteArray>>, val expected: Expected)

    private const val H = 3_600_000L

    /** Quell-IDs der drei Eintraege an Tages-, Monats- und Jahresgrenzen. */
    const val SPECIAL_MONTH_BORDER = 9001L
    const val SPECIAL_TRIP_NIGHT = 9002L
    const val SPECIAL_YEAR_BORDER = 9003L

    private val NOTES = listOf(
        "Ruhiger Tag. <b>Viel geschafft</b> und früh Feierabend.",
        "Lange Schicht.<br>Abends nur noch aufs Sofa.",
        "<div>Spaziergang am Fluss</div><div>Danach Kaffee&nbsp;und Kuchen</div>",
        "Einkaufsliste:<ul><li>Brot</li><li>Äpfel</li><li>Käse</li></ul>",
        "<p>Mit <i>Jonas</i> telefoniert, <u>endlich</u> wieder.</p>",
        "Kopf voll. <span style=\"color:red\">Morgen besser planen</span> &amp; früher ins Bett.",
        "Training fiel aus<script>alert('x')</script>, dafür gelesen.",
        "Regen den ganzen Tag. <s>Radtour</s> Brettspiele mit den Kindern.",
    )
    private val TITLES = listOf("Guter Start", "Mittendrin", "Notiz an mich")

    fun generate(): Sample {
        val rnd = Random(20260116)
        fun checksum() = (1..32).map { "0123456789abcdef"[rnd.nextInt(16)] }.joinToString("")

        // --- Stimmungen: fuenf Standardnamen (leerer Name) und eine eigene mit Leerzeichen am Ende
        val moodLevels = mapOf(1L to 1, 2L to 2, 3L to 3, 4L to 4, 5L to 5, 6L to 4)
        val customMoods = buildJsonArray {
            for (level in 1..5) add(buildJsonObject {
                put("id", level); put("custom_name", ""); put("mood_group_id", level); put("mood_group_order", 0)
                put("icon_id", level); put("predefined_name_id", level); put("state", 0); put("createdAt", 1672531200000 + level)
            })
            add(buildJsonObject {
                put("id", 6); put("custom_name", "Erschöpft "); put("mood_group_id", 4); put("mood_group_order", 1)
                put("icon_id", 23); put("predefined_name_id", -1); put("state", 0); put("createdAt", 1680000000000)
            })
        }

        // --- Gruppen
        val groupNames = listOf("Alltag", "Bewegung ", "Menschen", "Dienst")
        val tagGroups = buildJsonArray {
            groupNames.forEachIndexed { i, name ->
                add(buildJsonObject { put("id", i + 1); put("name", name); put("is_expanded", i != 2); put("order", i + 1); put("id_color", 3 + i * 5) })
            }
        }

        // --- Aktivitaeten: Name, Gruppe (0 = ohne), archiviert
        val tagDefs = listOf(
            Triple("Schlaf", 1, false), Triple("Einkaufen", 1, false), Triple("Putzen ", 1, false), Triple("Lesen", 1, false),
            Triple("Sport", 2, false), Triple("Spaziergang", 2, false), Triple("Radfahren", 2, true),
            Triple("Familie", 3, false), Triple("Freunde", 3, false), Triple("Verabredung", 3, true),
            Triple("Nachtschicht", 4, false), Triple("Frühdienst", 4, false), Triple("Fortbildung", 4, true),
            Triple("Sonstiges", 0, false),
        )
        val tags = buildJsonArray {
            tagDefs.forEachIndexed { i, (name, group, archived) ->
                add(buildJsonObject {
                    put("id", i + 1); put("name", name); put("icon", 40 + i); put("order", i + 1)
                    put("state", if (archived) 1 else 0); put("id_tag_group", group); put("createdAt", 1672531200000 + i)
                })
            }
        }

        // --- Skalen: zwei Textskalen (4 und 5 Punkte), eine Zeitskala
        val stressPoints = listOf("Kaum", "Etwas", "Deutlich", "Sehr hoch")
        val energyPoints = listOf("Leer", "Niedrig", "Mittel", "Gut", "Voll")
        val scales = buildJsonArray {
            add(buildJsonObject {
                put("id", 1); put("name", "Stress"); put("order", 1); put("state", 0); put("id_color", 2); put("id_icon", 7)
                put("trend", 2); put("chart_type", 0)
                putJsonArray("text_values") { stressPoints.forEachIndexed { i, n -> add(buildJsonObject { put("id", 11 + i); put("name", n); put("order", i + 1) }) } }
            })
            add(buildJsonObject {
                put("id", 2); put("name", "Energie "); put("order", 2); put("state", 0); put("id_color", 14); put("id_icon", 9)
                put("trend", 1); put("chart_type", 0)
                putJsonArray("text_values") { energyPoints.forEachIndexed { i, n -> add(buildJsonObject { put("id", 21 + i); put("name", n); put("order", i + 1) }) } }
            })
            add(buildJsonObject {
                put("id", 3); put("name", "Schlafdauer"); put("order", 3); put("state", 0); put("id_color", 5); put("id_icon", 1)
                put("trend", 0); put("chart_type", 1)
            })
        }

        // --- Fotos: neun Dateien, davon ein PNG und ein WEBP; ein Name beginnt mit "/"
        val files = ArrayList<Pair<String, ByteArray>>()
        val assetDefs = ArrayList<JsonObject>()
        for (i in 1..9) {
            val sum = checksum()
            val created = LocalDateTime.of(2023 + i % 3, 1 + i, 3 + i, 14, 5).toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
            val body = rnd.nextBytes(48)
            val bytes = when (i) {
                4 -> byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A) + body
                7 -> "RIFF".toByteArray() + byteArrayOf(52, 0, 0, 0) + "WEBPVP8 ".toByteArray() + body
                else -> byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16) + "JFIF".toByteArray() + body +
                    byteArrayOf(0xFF.toByte(), 0xD9.toByte())
            }
            val name = "assets/photos/${2023 + i % 3}/${1 + i}/$sum"
            files.add((if (i == 2) "/$name" else name) to bytes)
            assetDefs.add(buildJsonObject {
                put("id", 100 + i); put("checksum", sum); put("type", 1); put("createdAt", created); put("createdAtOffset", 2 * H)
                put("android_metadata", "{}")
            })
        }
        // Acht Zuordnungen zu Eintraegen (ein Eintrag mit drei Fotos), das neunte Foto gehoert zu einem wichtigen Tag.
        val assetLinks = mapOf(3 to listOf(101L, 102L, 103L), 20 to listOf(104L), 47 to listOf(105L, 106L), 80 to listOf(107L), 110 to listOf(108L))

        // --- Eintraege
        data class E(val id: Long, val local: LocalDateTime, val offsetHours: Int)
        val plan = ArrayList<E>()
        val start = LocalDate.of(2023, 1, 5)
        var nextId = 1L
        for (k in 0 until 97) {
            val date = start.plusDays(10L * k)
            // Sommerzeit +2, sonst +1; zwei Reisen mit +3.
            val offset = when {
                date.year == 2023 && date.monthValue == 8 -> 3
                date in LocalDate.of(2024, 7, 10)..LocalDate.of(2024, 7, 25) -> 3
                date.monthValue in 4..10 -> 2
                else -> 1
            }
            val hour = 6 + rnd.nextInt(10)
            plan.add(E(nextId++, date.atTime(hour, rnd.nextInt(60)), offset))
            if (k % 5 == 0) plan.add(E(nextId++, date.atTime(hour + 3 + rnd.nextInt(4), rnd.nextInt(60)), offset))
        }
        // Grenzfaelle: Ortszeit und UTC liegen an verschiedenen Tagen, Monaten, Jahren.
        plan.add(E(SPECIAL_MONTH_BORDER, LocalDateTime.of(2024, 2, 1, 0, 30), 1))
        plan.add(E(SPECIAL_TRIP_NIGHT, LocalDateTime.of(2024, 7, 15, 1, 30), 3))
        plan.add(E(SPECIAL_YEAR_BORDER, LocalDateTime.of(2025, 1, 1, 0, 15), 1))

        val perLevel = IntArray(5)
        var withNote = 0
        var favorites = 0
        var withText = 0
        var withTime = 0
        val entryObjects = ArrayList<Pair<Long, JsonObject>>()
        plan.forEachIndexed { index, e ->
            val moodId = when (rnd.nextInt(100)) {
                in 0..19 -> 1L
                in 20..54 -> 2L
                in 55..77 -> 3L
                in 78..86 -> 4L
                in 87..92 -> 6L
                else -> 5L
            }
            perLevel[moodLevels.getValue(moodId) - 1]++
            val note = if (rnd.nextInt(100) < 40) NOTES[rnd.nextInt(NOTES.size)] else ""
            if (note.isNotEmpty()) withNote++
            val title = if (index % 23 == 4) TITLES[index % TITLES.size] else ""
            val favorite = index % 17 == 0
            if (favorite) favorites++
            val tagIds = (1..14).shuffled(rnd).take(rnd.nextInt(5)).sorted()
            val stress = if (rnd.nextInt(100) < 50) 11 + rnd.nextInt(4) else null
            val energy = if (rnd.nextInt(100) < 30) 21 + rnd.nextInt(5) else null
            val sleep = if (rnd.nextInt(100) < 40) 240 + 15 * rnd.nextInt(25) else null
            if (stress != null || energy != null) withText++
            if (sleep != null) withTime++
            val datetime = e.local.toInstant(ZoneOffset.ofHours(e.offsetHours)).toEpochMilli()
            entryObjects.add(datetime to buildJsonObject {
                put("id", e.id)
                put("minute", e.local.minute); put("hour", e.local.hour); put("day", e.local.dayOfMonth)
                put("month", e.local.monthValue - 1) // ab 0 gezaehlt, wie im echten Backup
                put("year", e.local.year)
                put("datetime", datetime)
                put("timeZoneOffset", e.offsetHours * H)
                put("mood", moodId)
                put("note", note)
                put("note_title", title)
                putJsonArray("tags") { tagIds.forEach { add(it) } }
                putJsonArray("assets") { assetLinks[index].orEmpty().forEach { add(it) } }
                put("isFavorite", favorite)
                putJsonArray("scales") {
                    if (stress != null) add(buildJsonObject { put("id_scale", 1); put("id_text_scale_value", stress) })
                    if (energy != null) add(buildJsonObject { put("id_scale", 2); put("id_text_scale_value", energy) })
                    if (sleep != null) add(buildJsonObject { put("id_scale", 3); put("time_value", sleep) })
                }
            })
        }
        // Neuester zuerst, wie im echten Backup.
        val dayEntries = JsonArray(entryObjects.sortedByDescending { it.first }.map { it.second })

        // --- Ziele mit abgehakten Tagen (Monat ab 1; ein Dezember ist dabei)
        val goalDefs = listOf(
            // id, Name, Aktivitaet, archiviert, Wiederholung, Wert
            listOf(1, "Jeden Tag lesen", 4, 0, 1, 0),
            listOf(2, "Sport ", 5, 0, 2, 3),
            listOf(3, "", 7, 1, 2, 2),
            listOf(4, "Früh ins Bett", 0, 1, 1, 0),
        )
        val goals = buildJsonArray {
            goalDefs.forEachIndexed { i, g ->
                add(buildJsonObject {
                    put("id", g[0] as Int); put("goal_id", 100 + (g[0] as Int)); put("name", g[1] as String); put("id_tag", g[2] as Int)
                    put("created_at", LocalDateTime.of(2023, 10 + i % 2, 1, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                    put("reminder_time", "20:30"); put("reminder_enabled", i == 0); put("state", g[3] as Int)
                    put("repeat_type", g[4] as Int); put("repeat_value", g[5] as Int); put("note", if (i == 0) "Mindestens zehn Seiten" else "")
                    put("order", i + 1); put("id_icon", 60 + i)
                })
            }
        }
        var checkId = 1
        var goalCheckCount = 0
        val goalEntries = buildJsonArray {
            for ((g, count) in listOf(1 to 20, 2 to 12, 3 to 5, 4 to 3)) {
                for (n in 0 until count) {
                    val date = LocalDate.of(2023, 11, 2).plusDays(n * (2L + g))
                    add(buildJsonObject {
                        put("id", checkId++); put("goalId", 100 + g)
                        put("createdAt", date.atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                        put("minute", 0); put("hour", 20); put("day", date.dayOfMonth); put("month", date.monthValue); put("year", date.year)
                    })
                    goalCheckCount++
                }
            }
        }

        // --- Wichtige Tage (Monat ab 1)
        val milestones = buildJsonArray {
            add(buildJsonObject {
                put("id", 1); put("name", "Hochzeitstag"); put("year", 2015); put("month", 6); put("day", 20); put("id_category", 2)
                put("note", "Tisch reservieren"); put("id_asset", 109); put("is_yearly", true); put("order", 1)
                putJsonArray("reminders") { add(buildJsonObject { put("days_before", 0) }); add(buildJsonObject { put("days_before", 7) }) }
            })
            add(buildJsonObject {
                put("id", 2); put("name", "Geburtstag Oma "); put("year", 1941); put("month", 12); put("day", 3); put("id_category", 1)
                put("note", ""); put("is_yearly", true); put("order", 2)
                putJsonArray("reminders") { add(buildJsonObject { put("days_before", 1) }) }
            })
            add(buildJsonObject {
                put("id", 3); put("name", "Umzug"); put("year", 2022); put("month", 1); put("day", 31); put("id_category", 4)
                put("note", "Neue Wohnung"); put("is_yearly", false); put("order", 3)
                putJsonArray("reminders") {}
            })
        }

        val templates = buildJsonArray {
            add(buildJsonObject { put("id", 1); put("order", 1); put("title", "Dankbarkeit"); put("body", "<b>Drei Dinge, für die ich dankbar bin:</b><ul><li></li><li></li><li></li></ul>") })
            add(buildJsonObject { put("id", 2); put("order", 2); put("title", "Dienst-Rückblick "); put("body", "Was lief gut?<br>Was nehme ich mit?") })
            add(buildJsonObject { put("id", 3); put("order", 3); put("title", "Abend"); put("body", "<div>Heute war …</div>") })
        }
        val reminders = buildJsonArray {
            add(buildJsonObject { put("id", 1); put("hour", 20); put("minute", 0); put("state", 0); put("customTextEnabled", false); put("customText", "") })
            add(buildJsonObject { put("id", 2); put("hour", 7); put("minute", 45); put("state", 1); put("customTextEnabled", true); put("customText", "Wie hast Du geschlafen?") })
        }
        val prefs = buildJsonArray {
            add(buildJsonObject { put("key", "FIRST_DAY_OF_WEEK"); put("pref_name", "default"); put("value", 1) })
            add(buildJsonObject { put("key", "ONBOARDING_DONE"); put("pref_name", "default"); put("value", true) })
        }

        val root = buildJsonObject {
            put("version", 15)
            put("isReminderOn", true)
            put("customMoods", customMoods)
            put("tags", tags)
            put("dayEntries", dayEntries)
            put("achievements", buildJsonArray { add(buildJsonObject { put("name", "AC_ENTRIES"); put("AC_ENTRIES_CURRENT_LEVEL", 3) }) })
            put("daysInRowLongestChain", 12)
            put("goals", goals)
            put("prefs", prefs)
            put("tag_groups", tagGroups)
            put("metadata", buildJsonObject { put("number_of_entries", plan.size); put("platform", "android"); put("number_of_photos", 9) })
            put("assets", JsonArray(assetDefs))
            put("goalEntries", goalEntries)
            put("goalSuccessWeeks", buildJsonArray { add(buildJsonObject { put("goal_id", 101); put("year", 2023); put("week", 45) }) })
            put("reminders", reminders)
            put("writingTemplates", templates)
            put("milestones", milestones)
            put("scales", scales)
            put("platform", JsonPrimitive("android"))
        }

        val dates = plan.map { it.local.toLocalDate().toString() }
        val expected = Expected(
            moods = 6, groups = 4, tags = tagDefs.size, archivedTags = tagDefs.count { it.third }, scales = 3,
            entries = plan.size, entriesPerLevel = perLevel.toList(), entriesWithNote = withNote, favorites = favorites,
            assets = 9, assetLinks = assetLinks.values.sumOf { it.size }, goals = 4, goalChecks = goalCheckCount,
            importantDays = 3, templates = 3, reminders = 2, earliestDate = dates.min(), latestDate = dates.max(),
            entriesWithTextValue = withText, entriesWithTimeValue = withTime,
            special = mapOf(
                SPECIAL_MONTH_BORDER to Triple("2024-02-01", 30, 60),
                SPECIAL_TRIP_NIGHT to Triple("2024-07-15", 90, 180),
                SPECIAL_YEAR_BORDER to Triple("2025-01-01", 15, 60),
            ),
        )
        return Sample(root.toString(), files, expected)
    }

    /** Schreibt das Backup als ZIP. Zeitstempel der Eintraege sind fest, damit die Datei immer gleich ist. */
    fun writeZip(sample: Sample, out: OutputStream) {
        val fixedTime = LocalDateTime.of(2026, 1, 16, 12, 0)
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name).apply { timeLocal = fixedTime })
                zip.write(bytes)
                zip.closeEntry()
            }
            put("backup.daylio", Base64.getEncoder().encode(sample.json.toByteArray(Charsets.UTF_8)))
            for ((name, bytes) in sample.files) put(name, bytes)
        }
    }

    fun zipBytes(sample: Sample = generate()): ByteArray = ByteArrayOutputStream().also { writeZip(sample, it) }.toByteArray()
}
