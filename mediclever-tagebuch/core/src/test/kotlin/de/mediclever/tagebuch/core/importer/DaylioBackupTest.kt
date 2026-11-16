package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.MidnightRule
import de.mediclever.tagebuch.core.model.ScaleType
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

    // ---------------------------------------------------------------- Ziele

    private fun goalImport(checks: String) = import(
        """"goals":[{"id":1,"goal_id":1,"name":"Ziel","created_at":1690000000000,"state":0,"repeat_type":1}],"goalEntries":[$checks]""",
    )

    @Test fun goalMonthBaseIsDetected() {
        // Monat 0 kommt vor -> ab 0 gezaehlt.
        val zero = goalImport("""{"id":1,"goalId":1,"day":5,"month":0,"year":2024},{"id":2,"goalId":1,"day":6,"month":11,"year":2024}""")
        assertEquals(listOf("2024-01-05", "2024-12-06"), zero.diary.goalChecks.map { it.date })
        assertTrue(zero.warnings.any { "ab 0 gezählt" in it && "Monat 0 kommt vor" in it })

        // Monat 12 kommt vor -> ab 1 gezaehlt.
        val twelve = goalImport("""{"id":1,"goalId":1,"day":5,"month":12,"year":2024},{"id":2,"goalId":1,"day":6,"month":1,"year":2024}""")
        assertEquals(listOf("2024-12-05", "2024-01-06"), twelve.diary.goalChecks.map { it.date })
        assertTrue(twelve.warnings.any { "ab 1 gezählt" in it && "Monat 12 kommt vor" in it })

        // Weder noch: der Zeitpunkt des Abhakens entscheidet. 15.6.2024 12:00 UTC.
        val june = java.time.LocalDateTime.of(2024, 6, 15, 12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        val byStampZero = goalImport("""{"id":1,"goalId":1,"createdAt":$june,"day":15,"month":5,"year":2024}""")
        assertEquals("2024-06-15", byStampZero.diary.goalChecks.single().date)
        assertTrue(byStampZero.warnings.any { "ab 0 gezählt" in it && "Vergleich" in it })
        val byStampOne = goalImport("""{"id":1,"goalId":1,"createdAt":$june,"day":15,"month":6,"year":2024}""")
        assertEquals("2024-06-15", byStampOne.diary.goalChecks.single().date)
        assertTrue(byStampOne.warnings.any { "ab 1 gezählt" in it && "Vergleich" in it })

        // Kein Anhaltspunkt: Vermutung ab 1, mit Hinweis.
        val unknown = goalImport("""{"id":1,"goalId":1,"day":15,"month":6,"year":2024}""")
        assertEquals("2024-06-15", unknown.diary.goalChecks.single().date)
        assertTrue(unknown.warnings.any { "ab 1 gezählt" in it && "kein Anhaltspunkt" in it })
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
        val viaGoalId = import(json.format("51"))
        assertEquals("B", viaGoalId.diary.goals.single { it.id == viaGoalId.diary.goalChecks.single().goalId }.name)
        val viaId = import(json.format("2"))
        assertEquals("B", viaId.diary.goals.single { it.id == viaId.diary.goalChecks.single().goalId }.name)
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

    @Test fun prefsOnlyWhereRecognisable() {
        val result = import(
            """"prefs":[{"key":"FIRST_DAY_OF_WEEK","pref_name":"default","value":2},
                        {"key":"PAST_MIDNIGHT_BEHAVIOUR","value":1},
                        {"key":"CUSTOM_MOOD_COLORS","value":"[\"#11aa22\",\"#000000\",\"#FFFFFF\",\"#123456\",\"#abcdef\"]"},
                        {"key":"COLOR_MODE","value":"dark"},{"key":"PIN","value":"1234"}]""",
        )
        val s = result.diary.settings
        assertEquals(1, s.weekStart)
        assertEquals(MidnightRule.ASK, s.midnightRule)
        assertEquals(listOf("FF11AA22", "FF000000", "FFFFFFFF", "FF123456", "FFABCDEF"), s.customMoodColors)
        assertEquals("custom", s.moodScheme)
        val text = result.warnings.joinToString("\n")
        assertTrue("COLOR_MODE, PAST_MIDNIGHT_BEHAVIOUR" in text, text)
        assertFalse("1234" in text || "PIN" in text)

        assertEquals(7, import(""""prefs":[{"key":"WEEK_START","value":"1"}]""").diary.settings.weekStart)
        assertEquals(6, import(""""prefs":[{"key":"first_day_of_week","value":7}]""").diary.settings.weekStart)
        assertEquals(
            MidnightRule.PREVIOUS_DAY,
            import(""""prefs":[{"key":"MIDNIGHT_RULE","value":"PREVIOUS_DAY"}]""").diary.settings.midnightRule,
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
