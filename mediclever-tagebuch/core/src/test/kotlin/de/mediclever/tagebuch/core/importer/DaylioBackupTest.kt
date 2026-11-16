package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.ChartMode
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.model.MidnightRule
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.Trend
import de.mediclever.tagebuch.core.model.UserSettings
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Einzelfaelle des Daylio-Imports an kleinen, von Hand gebauten JSON-Stuecken. */
class DaylioBackupTest {

    private val now = 1_780_000_000_000L
    private val moods = """"customMoods":[{"id":1,"custom_name":"","mood_group_id":1,"predefined_name_id":1},{"id":2,"custom_name":" Mau ","mood_group_id":3,"predefined_name_id":-1}]"""

    private fun import(body: String, names: Collection<String>? = null) = DaylioBackup.fromJson("{$moods,$body}", names, now)

    private fun entry(id: Int, extra: String = "") =
        """{"id":$id,"datetime":1700000000000,"timeZoneOffset":3600000,"mood":1,"note":"","tags":[],"assets":[]$extra}"""

    @Test fun idsAreDeterministic() {
        assertEquals(DaylioBackup.deterministicId("entry", "1"), DaylioBackup.deterministicId("entry", "1"))
        assertFalse(DaylioBackup.deterministicId("entry", "1") == DaylioBackup.deterministicId("tag", "1"))
        assertEquals(
            java.util.UUID.nameUUIDFromBytes("daylio:mood:7".toByteArray()).toString(),
            DaylioBackup.deterministicId("mood", "7"),
        )
        val a = import(""""dayEntries":[${entry(5)}]""")
        assertEquals(DaylioBackup.deterministicId("entry", "5"), a.diary.entries.single().id)
        assertEquals(DaylioBackup.deterministicId("mood", "1"), a.diary.entries.single().moodId)
    }

    @Test fun emptyAndBrokenInputNeverCrashes() {
        val empty = DaylioBackup.fromJson("{}", null, now)
        assertEquals(0, empty.preview.entries)
        assertNull(empty.preview.earliestDate)
        assertTrue(empty.warnings.isEmpty())

        // Falsche Typen, fehlende Felder, null-Werte.
        val odd = DaylioBackup.fromJson(
            """{"customMoods":"x","tags":[1,null,{"name":"ohne id"},{"id":"7","name":null,"state":"1"}],
               "dayEntries":[{},{"id":1},{"id":2,"mood":99,"datetime":1},{"id":3,"mood":null,"year":2024,"month":0,"day":5}],
               "goals":[{}],"goalEntries":[{"goalId":5}],"milestones":[{"id":1}],"scales":[{}],"assets":[{}],
               "writingTemplates":[{}],"reminders":[{}],"prefs":[{"key":null},{"value":3}],"tag_groups":null}""",
            emptyList(), now,
        )
        assertEquals(0, odd.preview.entries)
        assertEquals(1, odd.preview.tags)
        assertEquals(1, odd.preview.archivedTags)
        val text = odd.warnings.joinToString("\n")
        assertTrue("1 Einträge ohne Kennung" in text, text)
        assertTrue("Stimmung im Backup nicht vorkommt" in text, text)
        assertTrue("Datum und Uhrzeit fehlen" in text, text)

        assertFailsWith<ImportException> { DaylioBackup.fromJson("kein json", null, now) }
        assertFailsWith<ImportException> { DaylioBackup.fromJson("[1,2]", null, now) }
    }

    @Test fun moodNames() {
        val d = import(""""dayEntries":[]""").diary
        assertEquals(listOf("Super", "Mau"), d.moods.map { it.name })
        assertEquals(listOf(1, 3), d.moods.map { it.level })
    }

    @Test fun datetimeBeatsTheZeroBasedMonthField() {
        // 31.12.2023 23:30 UTC, Versatz +2 h -> 1.1.2024 01:30 Ortszeit. Feld month = 0 (Januar, ab 0 gezaehlt).
        val utc = java.time.LocalDateTime.of(2023, 12, 31, 23, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val result = import(
            """"dayEntries":[
                {"id":1,"minute":30,"hour":1,"day":1,"month":0,"year":2024,"datetime":$utc,"timeZoneOffset":7200000,"mood":1,"isFavorite":true},
                {"id":2,"minute":5,"hour":7,"day":15,"month":2,"year":2024,"timeZoneOffset":3600000,"mood":2},
                {"id":3,"minute":0,"hour":0,"day":1,"month":5,"year":2024,"datetime":$utc,"timeZoneOffset":7200000,"mood":1}
            ]""",
        )
        val (first, second, third) = result.diary.entries
        assertEquals("2024-01-01", first.localDate)
        assertEquals(90, first.minuteOfDay)
        assertEquals(120, first.offsetMinutes)
        assertEquals(utc, first.timestamp)
        assertTrue(first.favorite)
        // Ohne datetime: Einzelfelder, Monat 2 ist der Maerz.
        assertEquals("2024-03-15", second.localDate)
        assertEquals(7 * 60 + 5, second.minuteOfDay)
        assertEquals(java.time.LocalDateTime.of(2024, 3, 15, 6, 5).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(), second.timestamp)
        // Widerspruch zwischen Feld und Zeitpunkt: der Zeitpunkt gilt, ein Hinweis nennt die Anzahl.
        assertEquals("2024-01-01", third.localDate)
        assertTrue(result.warnings.any { it.startsWith("1 Einträge, bei denen das Feld „month“") }, result.warnings.toString())
        assertTrue(result.warnings.any { it.startsWith("1 Einträge ohne „datetime“") })
    }

    @Test fun notesAreSanitized() {
        val note = """Hallo <b onclick=\"x()\">Welt</b><script>alert(1)</script><img src=x onerror=alert(1)>&nbsp;<div>Ende</div>"""
        val e = import(""""dayEntries":[${entry(1, ""","note":"$note","note_title":" Titel <i>x</i> """")}]""").diary.entries.single()
        assertEquals("Hallo <b>Welt</b>&nbsp;<p>Ende</p>", e.note)
        assertEquals("Titel x", e.title)
    }

    @Test fun unknownReferencesAreDroppedAndCounted() {
        val result = import(
            """"tags":[{"id":1,"name":"Lesen","id_tag_group":77},{"id":2,"name":"Sport","id_tag_group":0}],
               "assets":[{"id":10,"checksum":"abc"}],
               "dayEntries":[${entry(1).replace("\"tags\":[]", "\"tags\":[1,2,3,1]").replace("\"assets\":[]", "\"assets\":[10,11]")}]""",
        )
        val e = result.diary.entries.single()
        assertEquals(2, e.tagIds.size)
        assertEquals(1, e.assetIds.size)
        val text = result.warnings.joinToString("\n")
        assertTrue("1 Verweise auf unbekannte Aktivitäten" in text, text)
        assertTrue("1 Verweise auf unbekannte Dateien" in text, text)
        assertTrue("1 Aktivitäten verweisen auf eine unbekannte Gruppe" in text, text)
        assertFalse("Lesen" in text)
    }

    @Test fun unknownKeysAndListsAreReportedWithoutContent() {
        val result = import(
            """"dayEntries":[${entry(1, ""","geheim":"mein Text","anzahl":3,"liste":[1],"objekt":{}""")}],
               "neueListe":[{"feld_a":1,"feld_b":"streng geheim"}],"neueZahl":5,"achievements":[{"x":1}],"goalSuccessWeeks":[{"y":2}]""",
        )
        val text = result.warnings.joinToString("\n")
        assertTrue("Liste „dayEntries“: unbekannte Felder, nicht übernommen: geheim (Text), anzahl (Zahl), liste (Liste), objekt (Objekt)." in text, text)
        assertTrue("Unbekannte Liste „neueListe“ mit 1 Einträgen wurde nicht übernommen, Felder: feld_a (Zahl), feld_b (Text)." in text, text)
        assertTrue("Unbekannte Angabe „neueZahl“ (Zahl)" in text, text)
        assertFalse("mein Text" in text || "streng geheim" in text)
        assertFalse("achievements" in text || "goalSuccessWeeks" in text)
    }

    @Test fun fieldsOfTheRealShapeAreNotReportedAsUnknown() {
        val result = DaylioBackup.fromJson(
            """{"version":15,"isReminderOn":true,"daysInRowLongestChain":3,"moodIconsPackId":17,"moodIconsDefaultFreePackId":1,"fontSchemeId":0,"tag_colors":[],
               "preferredMoodIconsIdsForMoodIdsForIconsPack":{"1":{}},"metadata":{"number_of_entries":1,"platform":"android"},
               "customMoods":[{"id":9,"custom_name":"","mood_group_id":2,"mood_group_order":0,"icon_id":1712,"predefined_name_id":2,"state":0,"createdAt":0}],
               "tag_groups":[{"id":1,"name":"G","is_expanded":true,"order":1,"id_predefined":-1,"id_color":39}],
               "tags":[{"id":1,"name":"T","createdAt":1,"icon":5,"order":1,"state":0,"id_tag_group":1,"id_color":3}],
               "assets":[{"id":1,"checksum":"abc","type":1,"createdAt":1700000000000,"createdAtOffset":3600000,"android_metadata":"{}"}],
               "dayEntries":[{"id":1,"minute":13,"hour":23,"day":14,"month":10,"year":2023,"datetime":1700000000000,"timeZoneOffset":3600000,"mood":9,
                              "tags":[1],"assets":[1],"isFavorite":false,"scaleValues":[],"note":"n","note_title":"t"}],
               "goalEntries":[{"id":1,"goalId":77,"year":2024,"month":3,"day":1,"hour":8,"minute":0,"second":4,"createdAt":1709280000000}],
               "goals":[{"id":80,"goal_id":77,"created_at":1700000000000,"reminder_enabled":false,"reminder_minute":0,"reminder_hour":9,"state":0,
                         "repeat_type":2,"repeat_value":2,"id_tag":1,"end_date":-1,"id_challenge":-1,"id_icon":-1,"id_avatar":7,"order":1}],
               "goalSuccessWeeks":[{"goal_id":77,"year":2024,"week":9,"create_at_year":2024,"create_at_month":3,"create_at_day":1}],
               "reminders":[{"id":3,"hour":13,"minute":0,"state":0,"custom_text":"Text","custom_text_enabled":true}],
               "writingTemplates":[{"id":1,"order":1,"predefined_template_id":2,"title":"V","body":"b"}],
               "achievements":[{"name":"AC_X","AC_X_SEEN":true}]}""",
            listOf("assets/photos/2023/11/abc"), now,
        )
        assertEquals(1, result.preview.entries)
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
        assertEquals(1, result.diary.goalChecks.size)
        assertEquals("Text", result.diary.reminders.single().text)
        assertEquals("T", result.diary.goals.single().name)
        // Eine gefuellte Liste tag_colors waere dagegen neu und wird genannt.
        val filled = import(""""tag_colors":[{"id":1,"wert":2}]""")
        assertTrue(filled.warnings.single().startsWith("Unbekannte Liste „tag_colors“ mit 1 Einträgen"), filled.warnings.toString())
    }

    @Test fun dataFileWithoutArchive() {
        val json = """{$moods,"assets":[{"id":1,"checksum":"aaa","createdAt":1700000000000},{"id":2,"checksum":"bbb"}],
            "dayEntries":[${entry(1).replace("\"assets\":[]", "\"assets\":[1,2]")}]}"""
        val encoded = Base64.getEncoder().encode(json.toByteArray())
        val result = DaylioBackup.readData(encoded, now)
        assertEquals(1, result.preview.entries)
        assertEquals(2, result.preview.assets)
        assertEquals(2, result.preview.assetLinks)
        assertTrue(result.assetFiles.isEmpty())
        assertEquals(
            listOf("Es wurde nur die Datendatei ohne das Archiv geöffnet. Die 2 Fotos und Sprachnotizen des Backups fehlen deshalb; sie stecken nur in der vollständigen Sicherungsdatei."),
            result.warnings,
        )
        // Gleiche Kennungen wie beim Lesen aus dem Archiv: ein spaeterer Import des ganzen Archivs legt nichts doppelt an.
        assertEquals(DaylioBackup.fromJson(json, emptyList(), now).diary.entries, result.diary.entries)

        val file = Files.createTempFile("backup", ".daylio").toFile()
        try {
            file.writeBytes(encoded)
            assertEquals(result, DaylioBackup.readDataFile(file, now))
            assertFalse(DaylioBackup.looksLikeZip(file))
            // Ohne Fotos im Backup: trotzdem ein Hinweis, dass nur die Datendatei vorlag.
            file.writeBytes(Base64.getEncoder().encode("{$moods}".toByteArray()))
            assertTrue("nur die Datendatei" in DaylioBackup.readDataFile(file, now).warnings.single())
            file.writeText("%%% kein base64 %%%")
            assertFailsWith<ImportException> { DaylioBackup.readDataFile(file, now) }
        } finally {
            file.delete()
        }
        val archive = zip("backup.daylio" to encoded)
        try {
            assertTrue(DaylioBackup.looksLikeZip(archive))
            assertTrue("Archiv" in assertFailsWith<ImportException> { DaylioBackup.readDataFile(archive, now) }.message!!)
        } finally {
            archive.delete()
        }
    }

    // ---------------------------------------------------------------- Ziele

    private fun goalImport(checks: String) = import(
        """"goals":[{"id":1,"goal_id":1,"name":"Ziel","created_at":1690000000000,"state":0,"repeat_type":1}],"goalEntries":[$checks]""",
    )

    @Test fun goalMonthBaseIsDetected() {
        fun monthNote(r: ImportResult) = r.warnings.filter { it.startsWith("Abgehakte Tage: Der Monat") }

        // Belegter Aufbau: ab 1 gezaehlt, ohne Hinweis. Monat 12 bleibt Dezember.
        val twelve = goalImport("""{"id":1,"goalId":1,"day":5,"month":12,"year":2024},{"id":2,"goalId":1,"day":6,"month":1,"year":2024}""")
        assertEquals(listOf("2024-12-05", "2024-01-06"), twelve.diary.goalChecks.map { it.date })
        assertTrue(monthNote(twelve).isEmpty(), twelve.warnings.toString())

        val june = java.time.LocalDateTime.of(2024, 6, 15, 12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val byStampOne = goalImport("""{"id":1,"goalId":1,"createdAt":$june,"day":15,"month":6,"year":2024}""")
        assertEquals("2024-06-15", byStampOne.diary.goalChecks.single().date)
        assertTrue(monthNote(byStampOne).isEmpty(), byStampOne.warnings.toString())

        val plain = goalImport("""{"id":1,"goalId":1,"day":15,"month":6,"year":2024}""")
        assertEquals("2024-06-15", plain.diary.goalChecks.single().date)
        assertTrue(monthNote(plain).isEmpty(), plain.warnings.toString())

        // Abweichung vom belegten Aufbau wird erkannt und genannt: Monat 0 kommt vor -> ab 0 gezaehlt.
        val zero = goalImport("""{"id":1,"goalId":1,"day":5,"month":0,"year":2024},{"id":2,"goalId":1,"day":6,"month":11,"year":2024}""")
        assertEquals(listOf("2024-01-05", "2024-12-06"), zero.diary.goalChecks.map { it.date })
        assertTrue(monthNote(zero).single().let { "ab 0 gezählt" in it && "Monat 0 kommt vor" in it }, zero.warnings.toString())

        // Weder 0 noch 12: der Zeitpunkt des Abhakens entscheidet. 15.6.2024 12:00 UTC.
        val byStampZero = goalImport("""{"id":1,"goalId":1,"createdAt":$june,"day":15,"month":5,"year":2024}""")
        assertEquals("2024-06-15", byStampZero.diary.goalChecks.single().date)
        assertTrue(monthNote(byStampZero).single().let { "ab 0 gezählt" in it && "Vergleich" in it }, byStampZero.warnings.toString())
    }

    @Test fun goalChecksAreUniquePerDayAndStartDateIsNotAfterFirstCheck() {
        val result = goalImport(
            """{"id":1,"goalId":1,"day":5,"month":12,"year":2022},{"id":2,"goalId":1,"day":5,"month":12,"year":2022},
               {"id":3,"goalId":9,"day":5,"month":12,"year":2022},{"id":4,"goalId":1,"day":31,"month":2,"year":2023}""",
        )
        val check = result.diary.goalChecks.single()
        assertEquals("${result.diary.goals.single().id}|2022-12-05", check.id)
        assertEquals("2022-12-05", result.diary.goals.single().startDate)
        val text = result.warnings.joinToString("\n")
        assertTrue("1 abgehakte Tage waren doppelt" in text, text)
        assertTrue("1 abgehakte Tage gehören zu keinem bekannten Ziel" in text, text)
        assertTrue("1 abgehakte Tage ohne gültiges Datum" in text, text)
    }

    @Test fun goalEntriesMayPointToIdOrGoalId() {
        val json = """"goals":[{"id":1,"goal_id":50,"name":"A"},{"id":2,"goal_id":51,"name":"B"}],"goalEntries":[{"goalId":%s,"day":1,"month":12,"year":2024}]"""
        // Belegt: der Verweis meint goal_id. Kein Hinweis.
        val viaGoalId = import(json.format("51"))
        assertEquals("B", viaGoalId.diary.goals.single { it.id == viaGoalId.diary.goalChecks.single().goalId }.name)
        assertEquals(DaylioBackup.deterministicId("goal", "51"), viaGoalId.diary.goalChecks.single().goalId)
        assertTrue(viaGoalId.warnings.none { it.startsWith("Ziele:") }, viaGoalId.warnings.toString())
        // Abweichend: der Verweis passt nur zu id. Das wird genannt.
        val viaId = import(json.format("2"))
        assertEquals("B", viaId.diary.goals.single { it.id == viaId.diary.goalChecks.single().goalId }.name)
        assertTrue(viaId.warnings.any { it.startsWith("Ziele:") && "„id“" in it }, viaId.warnings.toString())
    }

    @Test fun goalIdWinsWhereIdAndGoalIdOverlap() {
        // Wie im echten Backup: die Kennung 35 ist zugleich id des einen und goal_id des anderen Ziels.
        val result = import(
            """"goals":[{"id":35,"goal_id":32,"name":"A"},{"id":40,"goal_id":35,"name":"B"}],
               "goalEntries":[{"id":1,"goalId":35,"day":1,"month":3,"year":2024,"hour":8,"minute":0,"second":4},
                              {"id":2,"goalId":32,"day":2,"month":3,"year":2024,"hour":8,"minute":0,"second":4}]""",
        )
        val names = result.diary.goals.associate { it.id to it.name }
        assertEquals(mapOf("2024-03-01" to "B", "2024-03-02" to "A"), result.diary.goalChecks.associate { it.date to names.getValue(it.goalId) })
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    @Test fun goalRepeatWeekdayMaskWeeklyAndMonthly() {
        fun goal(id: Int, type: Int, value: Int, extra: String = "") =
            """{"id":$id,"goal_id":${id + 100},"created_at":1690000000000,"reminder_enabled":false,"reminder_minute":0,"reminder_hour":9,"state":0,
                "repeat_type":$type,"repeat_value":$value,"id_tag":-1,"end_date":-1,"id_challenge":-1,"id_icon":-1,"id_avatar":7,"order":$id,"name":"Ziel $id"$extra}"""
        val result = import(
            """"goals":[${goal(1, 1, 127)},${goal(2, 1, 1)},${goal(3, 1, 2)},${goal(4, 1, 64)},${goal(5, 1, 65)},${goal(6, 1, 62)},
                        ${goal(7, 2, 4)},${goal(8, 3, 2)},${goal(9, 2, 99)},${goal(10, 7, 1)},${goal(11, 1, 0)}]""",
        )
        val g = result.diary.goals
        assertEquals(GoalRepeat.DAILY, g[0].repeat)
        assertTrue(g[0].weekdays.isEmpty())
        // Bit 0 = Sonntag, Bit 1 = Montag ... Bit 6 = Samstag; hier ISO: Montag = 1 ... Sonntag = 7.
        assertEquals(listOf(listOf(7), listOf(1), listOf(6), listOf(6, 7), listOf(1, 2, 3, 4, 5)), g.subList(1, 6).map { it.weekdays })
        assertTrue(g.subList(1, 6).all { it.repeat == GoalRepeat.WEEKDAYS })
        assertEquals(GoalRepeat.TIMES_PER_WEEK to 4, g[6].repeat to g[6].timesPerWeek)
        assertEquals(GoalRepeat.TIMES_PER_WEEK to 1, g[7].repeat to g[7].timesPerWeek)
        assertEquals(7, g[8].timesPerWeek)
        assertEquals(GoalRepeat.DAILY, g[9].repeat)
        assertEquals(GoalRepeat.DAILY, g[10].repeat)
        assertEquals(
            listOf(
                "Ziele: Die Wiederholungsart 7 („repeat_type“) ist nicht bekannt. Diese Ziele wurden als täglich übernommen, bitte prüfen.",
                "1 monatliche Ziele wurden als „einmal pro Woche“ übernommen, weil es monatliche Ziele hier nicht gibt.",
            ),
            result.warnings,
        )
    }

    @Test fun goalNameReminderAndDatesInRealShape() {
        // Eintraege liefern den Zeitzonen-Versatz: created_at ist Mitternacht Ortszeit (+2 h), in UTC der Vortag.
        val created = java.time.LocalDateTime.of(2024, 6, 30, 22, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val ended = java.time.LocalDateTime.of(2024, 8, 10, 22, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val entryAt = java.time.LocalDateTime.of(2024, 7, 3, 10, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val result = import(
            """"tags":[{"id":4,"name":"Lesen ","icon":1,"order":1,"state":0,"id_tag_group":0,"createdAt":5}],
               "dayEntries":[{"id":1,"datetime":$entryAt,"timeZoneOffset":7200000,"mood":1,"tags":[],"assets":[],"isFavorite":false,"scaleValues":[]}],
               "goals":[
                 {"id":7,"goal_id":3,"created_at":$created,"note":"Zehn Seiten","reminder_enabled":true,"reminder_minute":30,"reminder_hour":21,"state":0,
                  "repeat_type":1,"repeat_value":127,"id_tag":4,"end_date":-1,"id_challenge":-1,"id_icon":-1,"id_avatar":7,"order":1},
                 {"id":8,"goal_id":4,"created_at":$created,"reminder_enabled":false,"reminder_minute":0,"reminder_hour":9,"state":1,
                  "repeat_type":2,"repeat_value":2,"id_tag":-1,"end_date":$ended,"id_challenge":1,"name":"Ohne Aktivität","id_icon":28,"id_avatar":7,"order":2}]""",
        )
        val (linked, free) = result.diary.goals
        assertEquals("Lesen", linked.name)
        assertEquals(result.diary.tags.single().id, linked.tagId)
        assertEquals(21 * 60 + 30, linked.reminderMinuteOfDay)
        assertEquals("2024-07-01", linked.startDate)
        assertNull(linked.endDate)
        assertNull(linked.archivedAt)
        assertEquals("Zehn Seiten", linked.note)
        assertEquals("Ohne Aktivität", free.name)
        assertNull(free.tagId)
        assertNull(free.reminderMinuteOfDay, "Uhrzeit steht da, aber reminder_enabled ist falsch")
        assertEquals("2024-08-11", free.endDate)
        assertEquals(now, free.archivedAt)
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    // ---------------------------------------------------------------- Skalen

    @Test fun scalesWithTopLevelListsAndCamelCase() {
        // Anderer denkbarer Aufbau: Punkte und Werte als eigene Listen, Verweise in anderer Schreibweise.
        val result = import(
            """"scales":[{"id":1,"name":"Laune "},{"id":2,"name":"Schlaf"}],
               "textScaleValues":[{"id":7,"scaleId":1,"name":"tief","order":0},{"id":8,"scaleId":1,"name":"hoch","order":1}],
               "scaleEntries":[{"id":1,"dayEntryId":1,"scaleId":1,"textScaleValueId":8},{"id":2,"dayEntryId":1,"scaleId":2,"timeValue":27000000},
                               {"id":3,"dayEntryId":2,"scaleId":2,"timeValue":21600000},{"id":4,"dayEntryId":2,"scaleId":1,"textScaleValueId":99}],
               "dayEntries":[${entry(1)},${entry(2)}]""",
        )
        val (mood, sleep) = result.diary.scales
        assertEquals("Laune", mood.name)
        assertEquals(ScaleType.TEXT, mood.type)
        assertEquals(listOf("tief", "hoch"), mood.points.map { it.label })
        assertEquals(ScaleType.DURATION, sleep.type)
        val (first, second) = result.diary.entries
        assertEquals(mood.points[1].id, first.scaleValues.single { it.scaleId == mood.id }.pointId)
        assertEquals(450, first.scaleValues.single { it.scaleId == sleep.id }.minutes) // 7:30 in Millisekunden
        assertEquals(listOf(360), second.scaleValues.map { it.minutes })
        val text = result.warnings.joinToString("\n")
        assertTrue("Liste „textScaleValues“" in text, text)
        assertTrue("Liste „scaleEntries“" in text, text)
        assertTrue("Millisekunden" in text, text)
        assertTrue("1 Skalenwerte konnten keiner Skala oder keinem Punkt zugeordnet werden" in text, text)
        assertFalse("Unbekannte Liste" in text, text)
    }

    @Test fun timeUnitHeuristic() {
        fun minutes(vararg values: String): List<Int?> {
            val entries = values.mapIndexed { i, v -> entry(i + 1, ""","scales":[{"id_scale":1,"time_value":$v}]""") }
            return import(""""scales":[{"id":1,"name":"Dauer"}],"dayEntries":[${entries.joinToString(",")}]""")
                .diary.entries.map { it.scaleValues.singleOrNull()?.minutes }
        }
        assertEquals(listOf(360, 455), minutes("360", "455"))                 // Minuten
        assertEquals(listOf(360, 450), minutes("21600", "27000"))             // Sekunden
        assertEquals(listOf(360, 450), minutes("21600000", "27000000"))       // Millisekunden
        assertEquals(listOf(360, 450), minutes("\"6:00\"", "\"7:30\""))       // Text
    }

    @Test fun explicitTypeAndNumberScale() {
        val result = import(
            """"scales":[{"id":1,"name":"Schmerz","type":"number","min_value":0,"max_value":10},{"id":2,"name":"Leer","type":"text"}],
               "dayEntries":[${entry(1, ""","scale_values":[{"scale_id":1,"value":7}]""")}]""",
        )
        val scale = result.diary.scales.first()
        assertEquals(ScaleType.NUMBER, scale.type)
        assertEquals(0, scale.min)
        assertEquals(10, scale.max)
        assertEquals(7, result.diary.entries.single().scaleValues.single().number)
        assertTrue(result.warnings.any { "1 Textskalen ohne erkennbare Punkte" in it })
    }

    // ---------------------------------------------------------------- Einstellungen, wichtige Tage

    @Test fun scalesInRealShape() {
        fun values(vararg v: String) = entry(1, ""","scaleValues":[${v.joinToString(",")}]""")
        val result = import(
            """"tag_groups":[{"id":1,"name":"G","is_expanded":true,"order":1,"id_predefined":-1}],
               "scales":[
                 {"id":6,"type":0,"name":"Ruhe ","color":14,"icon_id":3,"order":4,"predefined":-1,"created_at":1,"created_at_offset":3600000,"state":0,
                  "is_expanded_in_form":false,"is_expanded_in_stats":true,"is_expanded_in_stats_detail":true,"trend":1,
                  "text_scale":{"id":6,"id_scale":6,"metric":1,"values":[
                     {"id":26,"id_text_scale":6,"order":1,"name":"wenig"},{"id":44,"id_text_scale":6,"order":2,"name":"mittel "},{"id":28,"id_text_scale":6,"order":3,"name":"viel"}]},
                  "number_scale":{"id":6,"id_scale":6,"metric":1,"min":1,"max":10,"predefined_unit":1}},
                 {"id":7,"type":0,"name":"Kraft","color":20,"order":2,"state":1,"trend":2,
                  "text_scale":{"id":7,"id_scale":7,"metric":1,"values":[
                     {"id":31,"id_text_scale":7,"order":2,"name":"zwei"},{"id":32,"id_text_scale":7,"order":1,"name":"eins"}]},
                  "number_scale":{"id":7,"id_scale":7,"metric":2,"min":0,"max":20,"predefined_unit":3}},
                 {"id":9,"type":2,"name":"Dauer","color":30,"order":3,"created_at":1,"created_at_offset":0,"state":0,
                  "is_expanded_in_form":true,"is_expanded_in_stats":true,"is_expanded_in_stats_detail":true,"trend":2,
                  "time_scale":{"id":1,"id_scale":9,"metric":2}}],
               "dayEntries":[
                 ${values("""{"id_scale":6,"id_text_scale_value":44}""", """{"id_scale":7,"id_text_scale_value":32}""", """{"id_scale":9,"time_value":360}""")},
                 ${values("""{"id_scale":9,"time_value":0}""", """{"id_scale":6,"id_text_scale_value":28}""").replace("\"id\":1", "\"id\":2")},
                 ${values("""{"id_scale":9,"time_value":21600}""").replace("\"id\":1", "\"id\":3")}]""",
        )
        val (calm, power, time) = result.diary.scales
        assertEquals(listOf(ScaleType.TEXT, ScaleType.TEXT, ScaleType.DURATION), result.diary.scales.map { it.type })
        assertEquals("Ruhe", calm.name)
        assertEquals(listOf("wenig", "mittel", "viel"), calm.points.map { it.label })
        assertEquals(listOf(1, 2, 3), calm.points.map { it.position })
        // Punkte nach order sortiert, auch wenn sie anders in der Liste stehen.
        assertEquals(listOf("eins", "zwei"), power.points.map { it.label })
        // Text: 1 bis Anzahl der Punkte (nicht die Grenzen aus number_scale), Durchschnitt je Tag.
        assertEquals(listOf(1, 3, 1), listOf(calm.min, calm.max, calm.step))
        assertEquals(listOf(1, 2), listOf(power.min, power.max))
        assertEquals(ChartMode.DAILY_AVERAGE, calm.chartMode)
        // Zeit: ab 0 in Viertelstunden, Summe je Tag. Obergrenze ist ein Tag, ausser ein Wert liegt darueber.
        assertEquals(listOf(0, 21600, 15), listOf(time.min, time.max, time.step))
        assertEquals(ChartMode.DAILY_SUM, time.chartMode)
        // trend 1 = niedriger ist besser, 2 = hoeher ist besser.
        assertEquals(listOf(Trend.LOWER_IS_BETTER, Trend.HIGHER_IS_BETTER, Trend.HIGHER_IS_BETTER), result.diary.scales.map { it.trend })
        assertEquals(listOf(14, 15, 16), result.diary.scales.map { it.colorIndex })
        assertEquals(listOf(4, 2, 3), result.diary.scales.map { it.sortOrder })
        assertEquals(listOf(null, now, null), result.diary.scales.map { it.archivedAt })

        val (first, second, third) = result.diary.entries
        assertEquals(calm.points[1].id, first.scaleValues.single { it.scaleId == calm.id }.pointId)
        assertEquals(power.points[0].id, first.scaleValues.single { it.scaleId == power.id }.pointId)
        // time_value sind Minuten, auch wenn ein Wert wie Sekunden aussieht: nichts wird umgerechnet.
        assertEquals(360, first.scaleValues.single { it.scaleId == time.id }.minutes)
        assertEquals(listOf(0, null), second.scaleValues.map { it.minutes })
        assertEquals(calm.points[2].id, second.scaleValues[1].pointId)
        assertEquals(21600, third.scaleValues.single().minutes)
        assertEquals(6, result.diary.entries.sumOf { it.scaleValues.size })
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    @Test fun unknownTrendAndTypeAreNamed() {
        val result = import(
            """"scales":[{"id":1,"type":1,"name":"Zahl","color":3,"order":1,"state":0,"trend":0,
                          "number_scale":{"id":1,"id_scale":1,"metric":1,"min":2,"max":8,"predefined_unit":1}},
                         {"id":2,"type":2,"name":"Zeit","color":3,"order":2,"state":0,"trend":5,"time_scale":{"id":1,"id_scale":2,"metric":2}}],
               "dayEntries":[]""",
        )
        val (number, time) = result.diary.scales
        assertEquals(ScaleType.NUMBER, number.type)
        assertEquals(2 to 8, number.min to number.max)
        assertEquals(listOf(Trend.NEUTRAL, Trend.NEUTRAL), listOf(number.trend, time.trend))
        val text = result.warnings.joinToString("\n")
        assertTrue("Typ 1 als Zahlenskala angenommen" in text, text)
        assertTrue("Die Trend-Werte 0, 5 sind nicht bekannt" in text, text)
    }

    @Test fun repeatedScaleValueInOneEntryIsCounted() {
        val result = import(
            """"scales":[{"id":2,"type":2,"name":"Zeit","color":3,"order":2,"state":0,"trend":2,"time_scale":{"id":1,"id_scale":2,"metric":2}}],
               "dayEntries":[${entry(1, ""","scaleValues":[{"id_scale":2,"time_value":30},{"id_scale":2,"time_value":45},{"id_scale":8,"time_value":45}]""")}]""",
        )
        assertEquals(listOf(30), result.diary.entries.single().scaleValues.map { it.minutes })
        val text = result.warnings.joinToString("\n")
        assertTrue("1 Skalenwerte waren im selben Eintrag doppelt" in text, text)
        assertTrue("1 Skalenwerte konnten keiner Skala oder keinem Punkt zugeordnet werden" in text, text)
    }

    // ---------------------------------------------------------------- Farben

    @Test fun coloursAreMappedFromTheSourcePalette() {
        val known = mapOf(1 to 17, 3 to 1, 11 to 0, 14 to 14, 20 to 15, 24 to 7, 25 to 6, 30 to 16, 31 to 13, 39 to 12, 40 to 4)
        val groups = known.keys.mapIndexed { i, c -> """{"id":${i + 1},"name":"G$i","is_expanded":true,"order":${i + 1},"id_predefined":-1,"id_color":$c}""" }
        val result = import(
            """"tag_groups":[${groups.joinToString(",")},{"id":50,"name":"Ohne","is_expanded":true,"order":50,"id_predefined":-1}],
               "tags":[{"id":1,"name":"Erbt","icon":1,"order":1,"state":0,"id_tag_group":1,"createdAt":1},
                       {"id":2,"name":"Eigene","icon":1,"order":2,"state":0,"id_tag_group":1,"createdAt":1,"id_color":31},
                       {"id":3,"name":"Null","icon":1,"order":3,"state":0,"id_tag_group":1,"createdAt":1,"id_color":0}]""",
        )
        assertEquals(known.values.toList() + 10, result.diary.groups.map { it.colorIndex })
        assertEquals(listOf(null, 13, null), result.diary.tags.map { it.colorIndex })
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    @Test fun unknownColoursGetAStableFallbackAndOneWarning() {
        val result = import(
            """"tag_groups":[{"id":1,"name":"A","order":1,"id_color":29},{"id":2,"name":"B","order":2,"id_color":47},{"id":3,"name":"C","order":3,"id_color":29}],
               "tags":[{"id":1,"name":"T","order":1,"state":0,"id_tag_group":1,"id_color":27}],
               "scales":[{"id":1,"type":2,"name":"S","color":18,"order":4,"state":0,"trend":2,"time_scale":{"id":1,"id_scale":1,"metric":2}}],
               "prefs":[{"key":"CUSTOM_COLOR_GREAT","pref_name":"default","value":39},{"key":"CUSTOM_COLOR_GOOD","pref_name":"default","value":40},
                        {"key":"CUSTOM_COLOR_MEH","pref_name":"default","value":2},{"key":"CUSTOM_COLOR_FUGLY","pref_name":"default","value":1},
                        {"key":"CUSTOM_COLOR_AWFUL","pref_name":"default","value":11}]""",
        )
        // Rest bei Teilung durch 18: 29 -> 11, 47 -> 11, 27 -> 9, 18 -> 0, 2 -> 2.
        assertEquals(listOf(11, 11, 11), result.diary.groups.map { it.colorIndex })
        assertEquals(9, result.diary.tags.single().colorIndex)
        assertEquals(0, result.diary.scales.single().colorIndex)
        assertEquals("FFD9A62E", result.diary.settings.customMoodColors[2])
        assertEquals(
            listOf("Farben: Die Farbnummern 2, 18, 27, 29, 47 des Backups sind hier nicht bekannt. Dafür wurde eine Ersatzfarbe vergeben, bitte bei Bedarf anpassen."),
            result.warnings,
        )
    }

    @Test fun paletteMirrorsTheAppPalette() {
        // Die Werte muessen GroupPalette in Theme.kt der App entsprechen (dort gepflegt).
        assertEquals(
            listOf(
                "FFB9303F", "FFDD7F37", "FFD9A62E", "FF78A94E", "FF2D8A5F", "FF1F7A8C", "FF4F7FC0", "FF6A4C93", "FFB05690",
                "FF8A6E52", "FF5C6B78", "FF2D323C", "FF7DBE2B", "FF19B5B0", "FFE0558A", "FFA07BE0", "FF2F7BD9", "FFF2C230",
            ),
            DaylioBackup.PALETTE_ARGB_MIRRORS_GROUP_PALETTE,
        )
    }

    // ---------------------------------------------------------------- Einstellungen, wichtige Tage

    private fun pref(key: String, value: String) = """{"key":"$key","pref_name":"default","value":$value}"""

    @Test fun prefsInRealShape() {
        val result = import(
            """"prefs":[${pref("FIRST_DAY_OF_THE_WEEK", "1")},${pref("ARE_QUOTES_ENABLED", "false")},${pref("ARE_MEMORIES_VISIBLE_TO_USER", "false")},
                        ${pref("WEEKLY_REPORT_NOTIFICATION_ENABLED", "false")},${pref("MONTHLY_REPORT_NOTIFICATION_ENABLED", "false")},
                        ${pref("CUSTOM_COLOR_GREAT", "39")},${pref("CUSTOM_COLOR_GOOD", "40")},${pref("CUSTOM_COLOR_MEH", "25")},
                        ${pref("CUSTOM_COLOR_FUGLY", "1")},${pref("CUSTOM_COLOR_AWFUL", "11")},${pref("CUSTOM_COLOR_PRIMARY", "48")},
                        ${pref("COLOR_PALETTE_CODE", "15")},${pref("COLOR_PALETTE_DEFAULT_CODE", "1")},${pref("COLOR_PALETTE_PREVIOUS_NON_CUSTOM_CODE", "16")},
                        ${pref("COLOR_MODE", "2")},${pref("COLOR_MODE_SCHEDULED_LIGHT_MODE_START", "28800000")},${pref("palette_rev_14", "true")},
                        ${pref("PIN_LOCK_STATE", "1")},${pref("ENTRIES_AFTER_MIDNIGHT", "0")}]""",
        )
        val s = result.diary.settings
        assertEquals(7, s.weekStart) // 1 = Sonntag wie java.util.Calendar
        assertFalse(s.showQuote)
        assertFalse(s.showMemories)
        assertFalse(s.reportNotifications)
        assertEquals("custom", s.moodScheme)
        // GREAT, GOOD, MEH, FUGLY, AWFUL = Stufe 1 bis 5: Limette, Gruen, Blau, Gelb, Rot.
        assertEquals(listOf("FF7DBE2B", "FF2D8A5F", "FF4F7FC0", "FFF2C230", "FFB9303F"), s.customMoodColors)
        assertEquals(MidnightRule.ASK, s.midnightRule)
        assertEquals(now, s.updatedAt)
        // Nur die Zahl zur Mitternachtsregel bleibt offen; kein Farbschluessel wird als unverstanden genannt.
        assertEquals(
            listOf("Einstellungen: Diese Angaben ließen sich nicht sicher lesen und bleiben auf der Voreinstellung: ENTRIES_AFTER_MIDNIGHT."),
            result.warnings,
        )

        assertEquals(1, import(""""prefs":[${pref("FIRST_DAY_OF_THE_WEEK", "2")}]""").diary.settings.weekStart)
        // Einer der beiden Berichte genuegt.
        val oneReport = import(""""prefs":[${pref("WEEKLY_REPORT_NOTIFICATION_ENABLED", "false")},${pref("MONTHLY_REPORT_NOTIFICATION_ENABLED", "true")}]""")
        assertTrue(oneReport.diary.settings.reportNotifications)
        // Alles wie voreingestellt: die Einstellungen gelten als unveraendert.
        val defaults = import(""""prefs":[${pref("FIRST_DAY_OF_THE_WEEK", "2")},${pref("ARE_QUOTES_ENABLED", "true")},${pref("COLOR_MODE", "2")}]""")
        assertEquals(UserSettings(), defaults.diary.settings)
        assertTrue(defaults.warnings.isEmpty(), defaults.warnings.toString())
        // Nur ein Teil der fuenf Farben: nichts uebernehmen, aber nennen.
        val partial = import(""""prefs":[${pref("CUSTOM_COLOR_GREAT", "39")},${pref("CUSTOM_COLOR_AWFUL", "11")}]""")
        assertEquals("klar", partial.diary.settings.moodScheme)
        assertTrue(partial.diary.settings.customMoodColors.isEmpty())
        assertTrue(partial.warnings.single().endsWith("CUSTOM_COLOR_AWFUL, CUSTOM_COLOR_GREAT."), partial.warnings.toString())
    }

    @Test fun prefsOnlyWhereRecognisable() {
        val result = import(
            """"prefs":[{"key":"FIRST_DAY_OF_WEEK","pref_name":"default","value":2},
                        {"key":"PAST_MIDNIGHT_BEHAVIOUR","value":1},
                        {"key":"CUSTOM_MOOD_COLORS","value":"[\"#11aa22\",\"#000000\",\"#FFFFFF\",\"#123456\",\"#abcdef\"]"},
                        {"key":"COLOR_MODE","value":"dark"},{"key":"ACCENT_COLOR","value":"blau"},{"key":"PIN","value":"1234"}]""",
        )
        val s = result.diary.settings
        assertEquals(1, s.weekStart)
        assertEquals(MidnightRule.ASK, s.midnightRule)
        assertEquals(listOf("FF11AA22", "FF000000", "FFFFFFFF", "FF123456", "FFABCDEF"), s.customMoodColors)
        assertEquals("custom", s.moodScheme)
        val text = result.warnings.joinToString("\n")
        // Eine Farbangabe, die weder bekannt noch lesbar ist, und die unklare Mitternachtsregel werden genannt.
        assertTrue("ACCENT_COLOR, PAST_MIDNIGHT_BEHAVIOUR" in text, text)
        // COLOR_MODE (Hell/Dunkel des Vorbilds) hat hier keine Entsprechung und ist kein Grund fuer einen Hinweis.
        assertFalse("COLOR_MODE" in text, text)
        assertFalse("1234" in text || "PIN" in text || "blau" in text)

        assertEquals(7, import(""""prefs":[{"key":"WEEK_START","value":"1"}]""").diary.settings.weekStart)
        assertEquals(6, import(""""prefs":[{"key":"first_day_of_week","value":7}]""").diary.settings.weekStart)
        assertEquals(
            MidnightRule.PREVIOUS_DAY,
            import(""""prefs":[{"key":"MIDNIGHT_RULE","value":"PREVIOUS_DAY"}]""").diary.settings.midnightRule,
        )
    }

    @Test fun milestonesInRealShape() {
        fun day(id: Int, date: String, category: Int, asset: Int, yearly: Boolean, reminders: String): String {
            val (y, m, d) = date.split("-").map { it.toInt() }
            return """{"id":$id,"createdAt":1710103107877,"createdAtOffset":3600000,"day":$d,"month":$m,"year":$y,"categoryId":$category,
                "predefinedMilestoneId":0,"name":" Tag $id ","note":"<b>x</b>","assetId":$asset,"hasCustomPhoto":${asset > 0},
                "isAnniversary":$yearly,"remindersValue":"$reminders","stateValue":0}"""
        }
        val result = import(
            """"assets":[{"id":158,"checksum":"abc","type":1,"createdAt":1700000000000,"createdAtOffset":3600000,"android_metadata":"{}"}],
               "milestones":[${day(1, "2023-12-29", 0, 158, true, "2,4,3,1")},${day(2, "2022-01-31", 2, 158, false, "2,4")},${day(3, "1995-10-12", 1, -1, true, "")}]""",
        )
        val (a, b, c) = result.diary.importantDays
        assertEquals(listOf("2023-12-29", "2022-01-31", "1995-10-12"), result.diary.importantDays.map { it.date }) // Monat ab 1
        assertEquals("Tag 1", a.name)
        assertEquals("x", a.note)
        assertEquals(listOf(true, false, true), listOf(a.yearly, b.yearly, c.yearly))
        assertEquals(result.diary.assets.single().id, a.assetId)
        assertEquals(a.assetId, b.assetId)
        assertNull(c.assetId)
        // Kennzahlen und Kategorie-Nummern werden nicht gedeutet.
        assertTrue(result.diary.importantDays.all { !it.remindOnDay && it.remindDaysBefore.isEmpty() && it.category.isEmpty() })
        assertEquals(
            listOf(
                "3 wichtige Tage tragen im Backup nur eine Kategorie-Nummer ohne Namen. Die Kategorie bleibt leer und lässt sich hier neu wählen.",
                "2 wichtige Tage haben Erinnerungen in einer Form, die sich nicht sicher lesen lässt. Die Erinnerungen sind aus und lassen sich hier neu einstellen.",
            ),
            result.warnings,
        )
    }

    @Test fun milestonesMonthIsOneBased() {
        val stamp = java.time.LocalDateTime.of(2020, 5, 17, 0, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val result = import(
            """"milestoneCategories":[{"id":3,"name":"Familie "}],
               "milestones":[{"id":1,"title":"A","year":2020,"month":1,"day":31,"category_id":3,"note":"<b>x</b>","reminders":[0,3]},
                             {"id":2,"name":"B","date":$stamp},{"id":3,"name":"C","year":2020,"month":13,"day":1},
                             {"id":4,"name":"D","year":0,"month":2,"day":29}]""",
        )
        val (a, b, d) = result.diary.importantDays
        assertEquals("2020-01-31", a.date)
        assertEquals("Familie", a.category)
        assertEquals("x", a.note)
        assertTrue(a.remindOnDay)
        assertEquals(listOf(3), a.remindDaysBefore)
        assertEquals("2020-05-17", b.date)
        assertEquals("2000-02-29", d.date)
        assertTrue(result.warnings.any { "1 wichtige Tage ohne gültiges Datum" in it })
    }

    // ---------------------------------------------------------------- Archiv

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = Files.createTempFile("daylio", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9)

    @Test fun readsZipWithLeadingSlashAndBlocksTraversal() {
        val json = """{$moods,"assets":[{"id":1,"checksum":"aaa","createdAt":1700000000000},{"id":2,"checksum":"bbb"},{"id":3,"checksum":"passwd"}],
            "dayEntries":[${entry(1).replace("\"assets\":[]", "\"assets\":[1,2,3]")}]}"""
        // Base64 mit Zeilenumbruechen, wie es manche Kodierer schreiben.
        val encoded = Base64.getMimeEncoder().encode(json.toByteArray())
        val file = zip(
            "/backup.daylio" to encoded,
            "/assets/photos/2023/10/aaa" to jpeg,
            "assets/photos/../../../etc/passwd" to jpeg,
            "../assets/photos/2023/10/bbb" to jpeg,
        )
        try {
            val result = DaylioBackup.read(file, now)
            assertEquals(3, result.preview.assets)
            assertEquals(3, result.preview.assetLinks)
            val only = result.assetFiles.single()
            assertEquals("/assets/photos/2023/10/aaa", only.zipEntryName)
            assertEquals(DaylioBackup.deterministicId("asset", "1") + ".jpg", only.fileName)
            assertEquals(13L, result.diary.assets.first().sizeBytes)
            val text = result.warnings.joinToString("\n")
            assertTrue("2 Dateien im Archiv haben einen unzulässigen Pfad" in text, text)
            assertTrue("2 Dateien stehen im Backup, fehlen aber im Archiv" in text, text)

            val dir = Files.createTempDirectory("ziel").toFile()
            try {
                val copied = ImportArchive.copyAsset(file, only, dir)
                assertEquals(File(dir, only.fileName), copied)
                assertTrue(copied.readBytes().contentEquals(jpeg))
                // Ein boeser Zielname wird abgelehnt, auch wenn der Eintrag existiert.
                assertFailsWith<ImportException> { ImportArchive.copyAsset(file, only.copy(fileName = "../raus.jpg"), dir) }
                assertFailsWith<ImportException> { DaylioBackup.copyAsset(file, only.zipEntryName, dir, "../raus") }
                assertFailsWith<ImportException> { ImportArchive.copyAsset(file, only.copy(zipEntryName = "fehlt"), dir) }
                assertEquals(listOf(only.fileName), dir.list()!!.toList())
            } finally {
                dir.deleteRecursively()
            }
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsFilesThatAreNoBackup() {
        val text = Files.createTempFile("kein", ".daylio").toFile().apply { writeText("hallo") }
        val noJson = zip("anderes.txt" to "x".toByteArray())
        val broken = zip("backup.daylio" to "%%% kein base64 %%%".toByteArray())
        val plain = zip("backup.daylio" to "{$moods}".toByteArray())
        try {
            assertTrue("ZIP" in assertFailsWith<ImportException> { DaylioBackup.read(text, now) }.message!!)
            assertTrue("backup.daylio" in assertFailsWith<ImportException> { DaylioBackup.read(noJson, now) }.message!!)
            assertFailsWith<ImportException> { DaylioBackup.read(broken, now) }
            assertFailsWith<ImportException> { DaylioBackup.read(File(text.parentFile, "gibt-es-nicht.daylio"), now) }
            // Unkodiertes JSON wird zur Sicherheit auch gelesen.
            assertEquals(2, DaylioBackup.read(plain, now).preview.moods)
        } finally {
            listOf(text, noJson, broken, plain).forEach { it.delete() }
        }
    }

    @Test fun pathsAndFileTypes() {
        assertEquals("assets/photos/2024/1/abc", ImportArchive.safePath("/assets/photos/2024/1/abc"))
        assertEquals("assets/a", ImportArchive.safePath("\\assets\\.\\a"))
        assertNull(ImportArchive.safePath("../a"))
        assertNull(ImportArchive.safePath("assets/../../a"))
        assertNull(ImportArchive.safePath("C:/a"))
        assertNull(ImportArchive.safePath("/"))

        fun type(vararg bytes: Int) = ImportArchive.detectExtension(ByteArray(bytes.size) { bytes[it].toByte() })
        fun typeOf(text: String) = ImportArchive.detectExtension(text.toByteArray(Charsets.ISO_8859_1))
        assertEquals("jpg", type(0xFF, 0xD8, 0xFF, 0xE0))
        assertEquals("png", type(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A))
        assertEquals("webp", typeOf("RIFF\u0010\u0000\u0000\u0000WEBPVP8 "))
        assertEquals("wav", typeOf("RIFF\u0010\u0000\u0000\u0000WAVEfmt "))
        assertEquals("gif", typeOf("GIF89a"))
        assertEquals("heic", typeOf("\u0000\u0000\u0000\u0018ftypheic"))
        assertEquals("m4a", typeOf("\u0000\u0000\u0000\u0018ftypM4A "))
        assertEquals("mp3", typeOf("ID3\u0003"))
        assertEquals("bin", typeOf("irgendwas"))
        assertEquals("bin", type())
        assertTrue(ImportArchive.isAudioExtension("m4a"))
        assertFalse(ImportArchive.isAudioExtension("jpg"))
    }
}
