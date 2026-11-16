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
 * Feldnamen und Verschachtelung entsprechen einem echten Backup (Version 15): Textskalen mit
 * text_scale.values und number_scale, Zeitskala mit time_scale, Werte der Eintraege unter
 * scaleValues, id_color nur an manchen Gruppen und Aktivitaeten, Ziele mit goal_id und
 * Wochentagsmaske, wichtige Tage mit isAnniversary und remindersValue, die fuenf CUSTOM_COLOR_*.
 * Alle Inhalte sind frei erfunden.
 *
 * Die erwarteten Zaehlwerte ([Expected]) werden beim Erzeugen unabhaengig vom Import mitgezaehlt.
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
        /** Alle Skalenwerte ueber alle Eintraege. */
        val scaleValues: Int,
        /** Je Aktivitaet in Reihenfolge: Index in der App-Palette oder null (erbt die Gruppenfarbe). */
        val tagColors: List<Int?>,
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

        // --- Gruppen: Reihenfolge teilt sich den Zahlenraum mit den Skalen (3, 6, 7); eine Gruppe ohne id_color
        val groupDefs = listOf(Triple("Alltag", 1, 39), Triple("Bewegung ", 2, 25), Triple("Menschen", 4, null), Triple("Dienst", 5, 11))
        val tagGroups = buildJsonArray {
            groupDefs.forEachIndexed { i, (name, order, color) ->
                add(buildJsonObject {
                    put("id", i + 1); put("name", name); put("is_expanded", i != 2); put("order", order); put("id_predefined", if (i == 0) 1 else -1)
                    if (color != null) put("id_color", color)
                })
            }
        }

        // --- Aktivitaeten: Name, Gruppe (0 = ohne), archiviert. Nur manche haben eine eigene Farbe.
        val tagDefs = listOf(
            Triple("Schlaf", 1, false), Triple("Einkaufen", 1, false), Triple("Putzen ", 1, false), Triple("Lesen", 1, false),
            Triple("Sport", 2, false), Triple("Spaziergang", 2, false), Triple("Radfahren", 2, true),
            Triple("Familie", 3, false), Triple("Freunde", 3, false), Triple("Verabredung", 3, true),
            Triple("Nachtschicht", 4, false), Triple("Frühdienst", 4, false), Triple("Fortbildung", 4, true),
            Triple("Sonstiges", 0, false),
        )
        // Farbnummer der Quell-Palette -> erwarteter Index in der App-Palette.
        val sourceColors = listOf<Pair<Int, Int>?>(1 to 17, null, 3 to 1, null, 11 to 0, null, 14 to 14, null, 24 to 7, null, 30 to 16, 31 to 13, null, 39 to 12)
        val tags = buildJsonArray {
            tagDefs.forEachIndexed { i, (name, group, archived) ->
                add(buildJsonObject {
                    put("id", i + 1); put("name", name); put("createdAt", 1672531200000 + i); put("icon", 40 + i); put("order", i + 1)
                    put("state", if (archived) 1 else 0); put("id_tag_group", group)
                    sourceColors[i]?.let { put("id_color", it.first) }
                })
            }
        }

        // --- Skalen: zwei Textskalen (4 und 5 Punkte), eine Zeitskala. Die Kennungen der Punkte
        // folgen nicht der Reihenfolge; massgeblich ist das Feld order.
        val stressPoints = listOf(11 to "Kaum", 14 to "Etwas", 13 to "Deutlich", 12 to "Sehr hoch")
        val energyPoints = listOf(21 to "Leer", 22 to "Niedrig", 23 to "Mittel", 24 to "Gut", 25 to "Voll")
        fun scale(id: Int, type: Int, name: String, color: Int, order: Int, trend: Int, points: List<Pair<Int, String>>?) = buildJsonObject {
            put("id", id); put("type", type); put("name", name); put("color", color)
            if (points != null) put("icon_id", 300 + id)
            put("order", order)
            if (points != null) put("predefined", -1)
            put("created_at", 1700000000000 + id); put("created_at_offset", H); put("state", 0)
            put("is_expanded_in_form", id != 2); put("is_expanded_in_stats", true); put("is_expanded_in_stats_detail", false)
            put("trend", trend)
            if (points != null) {
                put("text_scale", buildJsonObject {
                    put("id", id); put("id_scale", id); put("metric", 1)
                    putJsonArray("values") {
                        points.forEachIndexed { i, (pointId, label) ->
                            add(buildJsonObject { put("id", pointId); put("id_text_scale", id); put("order", i + 1); put("name", label) })
                        }
                    }
                })
                put("number_scale", buildJsonObject {
                    put("id", id); put("id_scale", id); put("metric", 1); put("min", 1); put("max", 10); put("predefined_unit", 1)
                })
            } else {
                put("time_scale", buildJsonObject { put("id", 1); put("id_scale", id); put("metric", 2) })
            }
        }
        val scales = buildJsonArray {
            add(scale(1, 0, "Stress", 3, 3, trend = 1, stressPoints))    // niedriger ist besser
            add(scale(2, 0, "Energie ", 14, 6, trend = 2, energyPoints)) // hoeher ist besser
            add(scale(3, 2, "Schlafdauer", 27, 7, trend = 2, null))      // Farbnummer 27 hat keine bekannte Zuordnung
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
        var scaleValueCount = 0
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
            scaleValueCount += listOfNotNull(stress, energy, sleep).size
            val datetime = e.local.toInstant(ZoneOffset.ofHours(e.offsetHours)).toEpochMilli()
            entryObjects.add(datetime to buildJsonObject {
                put("id", e.id)
                put("minute", e.local.minute); put("hour", e.local.hour); put("day", e.local.dayOfMonth)
                put("month", e.local.monthValue - 1) // ab 0 gezaehlt, wie im echten Backup
                put("year", e.local.year)
                put("datetime", datetime)
                put("timeZoneOffset", e.offsetHours * H)
                put("mood", moodId)
                putJsonArray("tags") { tagIds.forEach { add(it) } }
                putJsonArray("assets") { assetLinks[index].orEmpty().forEach { add(it) } }
                put("isFavorite", favorite)
                // Leere Notizen und Titel fehlen im echten Backup ganz.
                if (note.isNotEmpty()) put("note", note)
                if (title.isNotEmpty()) put("note_title", title)
                putJsonArray("scaleValues") {
                    if (stress != null) add(buildJsonObject { put("id_scale", 1); put("id_text_scale_value", stress) })
                    if (energy != null) add(buildJsonObject { put("id_scale", 2); put("id_text_scale_value", energy) })
                    if (sleep != null) add(buildJsonObject { put("id_scale", 3); put("time_value", sleep) })
                }
            })
        }
        // Neuester zuerst, wie im echten Backup.
        val dayEntries = JsonArray(entryObjects.sortedByDescending { it.first }.map { it.second })

        // --- Ziele. goalEntries.goalId verweist auf goal_id, nicht auf id; die Zahlenraeume ueberschneiden sich.
        // Der Name fehlt bei Zielen mit Aktivitaet. repeat_type 1 = Wochentage als Maske (Bit 0 = Sonntag),
        // 2 = mehrmals pro Woche, 3 = mehrmals pro Monat.
        data class G(val goalId: Int, val name: String?, val tag: Int, val archived: Boolean, val type: Int, val value: Int, val reminder: Pair<Int, Int>?, val note: String?)
        val goalDefs = listOf(
            G(101, null, 4, false, 1, 127, 20 to 30, "Mindestens zehn Seiten"), // jeden Tag
            G(102, null, 5, false, 2, 3, null, null),                           // dreimal pro Woche
            G(103, null, 7, true, 2, 2, null, null),
            G(104, "Früh ins Bett", -1, true, 1, 62, null, null),               // Montag bis Freitag
            G(105, null, 6, false, 1, 1, 9 to 30, "Runde um den See"),          // nur sonntags
            G(106, "Großeinkauf", -1, true, 3, 2, null, null),                  // zweimal pro Monat
        )
        val goals = buildJsonArray {
            goalDefs.forEachIndexed { i, g ->
                add(buildJsonObject {
                    put("id", g.goalId + 2); put("goal_id", g.goalId)
                    // Mitternacht Ortszeit (Sommerzeit): in UTC ist das noch der Vortag.
                    put("created_at", LocalDateTime.of(2023, 10, 1 + i % 2, 0, 0).toInstant(ZoneOffset.ofHours(2)).toEpochMilli())
                    if (g.note != null) put("note", g.note)
                    put("reminder_enabled", g.reminder != null); put("reminder_minute", g.reminder?.second ?: 0); put("reminder_hour", g.reminder?.first ?: 9)
                    put("state", if (g.archived) 1 else 0); put("repeat_type", g.type); put("repeat_value", g.value); put("id_tag", g.tag)
                    put("end_date", if (g.archived) LocalDateTime.of(2025, 8, 23, 15, 21, 7).toInstant(ZoneOffset.ofHours(2)).toEpochMilli() + i else -1)
                    put("id_challenge", -1)
                    if (g.name != null) put("name", g.name)
                    put("id_icon", if (g.name != null) 60 + i else -1); put("id_avatar", 7); put("order", i + 1)
                })
            }
        }
        var checkId = 1
        var goalCheckCount = 0
        val goalEntries = buildJsonArray {
            for ((g, count) in listOf(1 to 20, 2 to 12, 3 to 5, 4 to 3, 5 to 4, 6 to 2)) {
                for (n in 0 until count) {
                    // Ziel 5 nur an Sonntagen (5.11.2023 ist ein Sonntag), die anderen in festen Abstaenden.
                    val date = if (g == 5) LocalDate.of(2023, 11, 5).plusWeeks(n.toLong()) else LocalDate.of(2023, 11, 2).plusDays(n * (2L + g))
                    add(buildJsonObject {
                        put("id", checkId++); put("goalId", 100 + g)
                        put("year", date.year); put("month", date.monthValue); put("day", date.dayOfMonth) // Monat ab 1
                        put("hour", 20); put("minute", 0); put("second", 7 * n % 60)
                        put("createdAt", date.atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
                    })
                    goalCheckCount++
                }
            }
        }

        // --- Wichtige Tage (Monat ab 1). Zwei teilen sich ein Foto; der dritte hat keins (-1, erfundener Fall).
        fun milestone(id: Int, name: String, date: LocalDate, category: Int, note: String, asset: Int, yearly: Boolean, reminders: String) = buildJsonObject {
            put("id", id); put("createdAt", 1710103107877 + id); put("createdAtOffset", H)
            put("day", date.dayOfMonth); put("month", date.monthValue); put("year", date.year)
            put("categoryId", category); put("predefinedMilestoneId", if (id == 2) 1 else 0)
            put("name", name); put("note", note); put("assetId", asset); put("hasCustomPhoto", asset > 0)
            put("isAnniversary", yearly); put("remindersValue", reminders); put("stateValue", 0)
        }
        val milestones = buildJsonArray {
            add(milestone(1, "Hochzeitstag", LocalDate.of(2015, 6, 20), 2, "Tisch reservieren", 109, true, "2,4,3,1"))
            add(milestone(2, "Geburtstag Oma ", LocalDate.of(1941, 12, 3), 1, "", 109, true, "2,4"))
            add(milestone(3, "Umzug", LocalDate.of(2022, 1, 31), 0, "Neue Wohnung", -1, false, ""))
        }

        val templates = buildJsonArray {
            add(buildJsonObject { put("id", 1); put("order", 1); put("predefined_template_id", 3); put("title", "Dankbarkeit"); put("body", "<b>Drei Dinge, für die ich dankbar bin:</b><ul><li></li><li></li><li></li></ul>") })
            add(buildJsonObject { put("id", 2); put("order", 2); put("predefined_template_id", -1); put("title", "Dienst-Rückblick "); put("body", "Was lief gut?<br>Was nehme ich mit?") })
            add(buildJsonObject { put("id", 3); put("order", 3); put("predefined_template_id", -1); put("title", "Abend"); put("body", "<div>Heute war …</div>") })
        }
        val reminders = buildJsonArray {
            add(buildJsonObject { put("id", 1); put("hour", 20); put("minute", 0); put("state", 0); put("custom_text", ""); put("custom_text_enabled", false) })
            add(buildJsonObject { put("id", 2); put("hour", 7); put("minute", 45); put("state", 1); put("custom_text", "Wie hast Du geschlafen?"); put("custom_text_enabled", true) })
        }
        // Einstellungen: Wochenanfang wie java.util.Calendar (1 = Sonntag), Farben als Nummern der Quell-Palette.
        val prefs = buildJsonArray {
            fun pref(key: String, value: JsonPrimitive) = add(buildJsonObject { put("key", key); put("pref_name", "default"); put("value", value) })
            pref("AUTO_BACKUP_IS_ON", JsonPrimitive(true))
            pref("FIRST_DAY_OF_THE_WEEK", JsonPrimitive(1))
            pref("WEEKLY_REPORT_NOTIFICATION_ENABLED", JsonPrimitive(false))
            pref("MONTHLY_REPORT_NOTIFICATION_ENABLED", JsonPrimitive(true))
            pref("COLOR_PALETTE_CODE", JsonPrimitive(15))
            pref("palette_rev_1", JsonPrimitive(true))
            pref("CUSTOM_COLOR_GREAT", JsonPrimitive(39))
            pref("CUSTOM_COLOR_GOOD", JsonPrimitive(40))
            pref("CUSTOM_COLOR_MEH", JsonPrimitive(25))
            pref("CUSTOM_COLOR_FUGLY", JsonPrimitive(1))
            pref("CUSTOM_COLOR_AWFUL", JsonPrimitive(11))
            pref("CUSTOM_COLOR_PRIMARY", JsonPrimitive(48))
            pref("COLOR_MODE", JsonPrimitive(2))
            pref("COLOR_MODE_SCHEDULED_LIGHT_MODE_START", JsonPrimitive(28800000))
            pref("PIN_LOCK_STATE", JsonPrimitive(1))
            pref("ARE_MEMORIES_VISIBLE_TO_USER", JsonPrimitive(true))
            pref("MEMORIES_ALLOWED_MOOD_GROUPS_IDS", JsonPrimitive("1,2,3,4,5"))
            pref("ARE_QUOTES_ENABLED", JsonPrimitive(false))
            pref("ENTRIES_AFTER_MIDNIGHT", JsonPrimitive(0))
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
            put("metadata", buildJsonObject {
                put("number_of_entries", plan.size); put("created_at", 1768564800000); put("is_auto_backup", false); put("platform", "android")
                put("android_version", 15); put("number_of_photos", 9); put("photos_size", files.sumOf { it.second.size }); put("backup_version", 2)
            })
            put("moodIconsPackId", 17)
            put("preferredMoodIconsIdsForMoodIdsForIconsPack", buildJsonObject { put("1", buildJsonObject { put("1", 1736) }) })
            put("assets", JsonArray(assetDefs))
            put("goalEntries", goalEntries)
            put("goalSuccessWeeks", buildJsonArray {
                add(buildJsonObject { put("goal_id", 101); put("year", 2023); put("week", 45); put("create_at_year", 2023); put("create_at_month", 11); put("create_at_day", 12) })
            })
            put("reminders", reminders)
            put("writingTemplates", templates)
            put("moodIconsDefaultFreePackId", 1)
            put("milestones", milestones)
            put("tag_colors", buildJsonArray {})
            put("scales", scales)
            put("fontSchemeId", 0)
        }

        val dates = plan.map { it.local.toLocalDate().toString() }
        val expected = Expected(
            moods = 6, groups = 4, tags = tagDefs.size, archivedTags = tagDefs.count { it.third }, scales = 3,
            entries = plan.size, entriesPerLevel = perLevel.toList(), entriesWithNote = withNote, favorites = favorites,
            assets = 9, assetLinks = assetLinks.values.sumOf { it.size }, goals = goalDefs.size, goalChecks = goalCheckCount,
            importantDays = 3, templates = 3, reminders = 2, earliestDate = dates.min(), latestDate = dates.max(),
            entriesWithTextValue = withText, entriesWithTimeValue = withTime,
            scaleValues = scaleValueCount, tagColors = sourceColors.map { it?.second },
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
