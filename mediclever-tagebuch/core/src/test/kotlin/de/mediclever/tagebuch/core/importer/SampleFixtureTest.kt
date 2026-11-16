package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.ChartMode
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.EntrySource
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import de.mediclever.tagebuch.core.model.Trend
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Schreibt das kuenstliche Backup nach tests/fixtures/sample.daylio und prueft den
 * ganzen Weg daran: Lesen, Zaehlwerte, Zeitzonen, zweiter Import, Zusammenfuehren, CSV.
 */
class SampleFixtureTest {

    private val now = 1_780_000_000_000L
    private val sample = SampleBackup.generate()
    private val expected = sample.expected

    /** Gradle startet Tests im Modulordner core/. */
    private val fixture: File by lazy {
        val file = File("../tests/fixtures/sample.daylio").absoluteFile.normalize()
        val bytes = SampleBackup.zipBytes(sample)
        if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        file
    }

    private val imported: DaylioImport by lazy { DaylioBackup.read(fixture, now) }

    @Test fun generatorIsDeterministic() {
        assertContentEquals(SampleBackup.zipBytes(), SampleBackup.zipBytes())
        assertTrue(fixture.isFile)
        assertContentEquals(SampleBackup.zipBytes(sample), fixture.readBytes())
    }

    @Test fun previewMatchesGenerator() {
        val p = imported.preview
        assertEquals(expected.moods, p.moods)
        assertEquals(expected.groups, p.groups)
        assertEquals(expected.tags, p.tags)
        assertEquals(expected.archivedTags, p.archivedTags)
        assertEquals(expected.scales, p.scales)
        assertEquals(120, expected.entries)
        assertEquals(expected.entries, p.entries)
        assertEquals(expected.entriesPerLevel, p.entriesPerLevel)
        assertEquals(expected.entries, p.entriesPerLevel.sum())
        assertEquals(expected.entriesWithNote, p.entriesWithNote)
        assertEquals(expected.favorites, p.favorites)
        assertEquals(expected.assets, p.assets)
        assertEquals(expected.assetLinks, p.assetLinks)
        assertEquals(expected.goals, p.goals)
        assertEquals(expected.goalChecks, p.goalChecks)
        assertEquals(expected.importantDays, p.importantDays)
        assertEquals(expected.templates, p.templates)
        assertEquals(expected.reminders, p.reminders)
        assertEquals(expected.earliestDate, p.earliestDate)
        assertEquals(expected.latestDate, p.latestDate)
        assertEquals("2023-01-05", p.earliestDate)
        assertEquals(ImportPreview.of(imported.diary), p)
    }

    @Test fun setupIsMapped() {
        val d = imported.diary
        // Standardnamen auf Deutsch, eigener Name ohne Leerzeichen am Ende.
        assertEquals(listOf("Super", "Gut", "Okay", "Schlecht", "Mies", "Erschöpft"), d.moods.map { it.name })
        assertEquals(listOf(1, 2, 3, 4, 5, 4), d.moods.map { it.level })
        assertEquals(listOf("Alltag", "Bewegung", "Menschen", "Dienst"), d.groups.map { it.name })
        assertEquals(listOf(3, 8, 1, 6), d.groups.map { it.colorIndex }) // 3, 8, 13, 18 modulo 12
        assertEquals(listOf(false, false, true, false), d.groups.map { it.collapsed })

        val tags = d.tags.associateBy { it.name }
        assertTrue("Putzen" in tags, "Leerzeichen am Ende werden entfernt")
        assertEquals("bed", tags.getValue("Schlaf").icon)
        assertEquals("fitness_center", tags.getValue("Sport").icon)
        assertEquals("emergency", tags.getValue("Nachtschicht").icon)
        assertEquals("emergency", tags.getValue("Frühdienst").icon)
        assertEquals("circle", tags.getValue("Sonstiges").icon)
        assertNull(tags.getValue("Sonstiges").groupId)
        assertEquals(d.groups[3].id, tags.getValue("Fortbildung").groupId)
        assertEquals(setOf("Radfahren", "Verabredung", "Fortbildung"), d.tags.filter { it.archivedAt != null }.map { it.name }.toSet())
        assertTrue(d.tags.all { it.icon in IconGuess.ALLOWED })

        val (stress, energy, sleep) = d.scales
        assertEquals(ScaleType.TEXT, stress.type)
        assertEquals(listOf("Kaum", "Etwas", "Deutlich", "Sehr hoch"), stress.points.map { it.label })
        assertEquals(listOf(1, 2, 3, 4), stress.points.map { it.position })
        assertEquals(Trend.LOWER_IS_BETTER, stress.trend)
        assertEquals("Energie", energy.name)
        assertEquals(5, energy.points.size)
        assertEquals(5, energy.max)
        assertEquals(Trend.HIGHER_IS_BETTER, energy.trend)
        assertEquals(ScaleType.DURATION, sleep.type)
        assertEquals(Trend.NEUTRAL, sleep.trend)
        assertEquals(ChartMode.DAILY_SUM, sleep.chartMode)
        assertTrue(sleep.points.isEmpty())

        assertEquals(7, d.settings.weekStart) // Wert 1 des Vorbilds ist Sonntag
    }

    @Test fun entriesKeepLocalTimeAcrossTimeZones() {
        val d = imported.diary
        assertEquals(setOf(60, 120, 180), d.entries.map { it.offsetMinutes }.toSet())
        for ((sourceId, want) in expected.special) {
            val entry = d.entries.single { it.id == DaylioBackup.deterministicId("entry", sourceId.toString()) }
            assertEquals(want.first, entry.localDate)
            assertEquals(want.second, entry.minuteOfDay)
            assertEquals(want.third, entry.offsetMinutes)
            // UTC liegt am Vortag: wer nur den Zeitstempel nimmt, landet im falschen Monat oder Jahr.
            val utcDate = java.time.Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.of("UTC")).toLocalDate().toString()
            assertTrue(utcDate < entry.localDate)
        }
        for (entry in d.entries) {
            assertEquals(EntrySource.IMPORT, entry.source)
            // Ortszeit = UTC + Versatz, unabhaengig von der Zeitzone des Geraets.
            val local = java.time.Instant.ofEpochMilli(entry.timestamp).atOffset(java.time.ZoneOffset.ofTotalSeconds(entry.offsetMinutes * 60))
            assertEquals(local.toLocalDate().toString(), entry.localDate)
            assertEquals(local.hour * 60 + local.minute, entry.minuteOfDay)
            val month = entry.localDate.substring(5, 7).toInt()
            if (entry.offsetMinutes == 60) assertTrue(month !in 4..10)
        }
        // Mehrere Eintraege pro Tag kommen vor.
        assertTrue(d.entries.groupBy { it.localDate }.values.count { it.size >= 2 } >= 20)
    }

    @Test fun entryDetails() {
        val d = imported.diary
        assertEquals(expected.entriesWithTextValue, d.entries.count { e -> e.scaleValues.any { it.pointId != null } })
        assertEquals(expected.entriesWithTimeValue, d.entries.count { e -> e.scaleValues.any { it.minutes != null } })
        val pointIds = d.scales.flatMap { it.points }.map { it.id }.toSet()
        for (entry in d.entries) {
            assertTrue(entry.moodId in d.moodById)
            assertTrue(entry.tagIds.all { it in d.tagById })
            for (value in entry.scaleValues) {
                assertEquals(1, listOfNotNull(value.pointId, value.number, value.minutes).size)
                value.pointId?.let { assertTrue(it in pointIds) }
                value.minutes?.let { assertTrue(it in 240..600) }
            }
            assertFalse("<script" in entry.note || "<span" in entry.note || "<div" in entry.note || "style=" in entry.note)
        }
        assertTrue(d.entries.any { "<b>Viel geschafft</b>" in it.note })
        assertTrue(d.entries.any { "<p>Spaziergang am Fluss</p><p>Danach Kaffee&nbsp;und Kuchen</p>" == it.note })
        assertTrue(d.entries.any { "<ul><li>Brot</li>" in it.note })
        assertTrue(d.entries.any { it.title == "Guter Start" })
        assertEquals(3, d.entries.maxOf { it.assetIds.size })
    }

    @Test fun assetsAreMatchedAndCopied() {
        val d = imported.diary
        assertEquals(9, imported.assetFiles.size)
        assertEquals(1, imported.assetFiles.count { it.zipEntryName.startsWith("/") })
        assertEquals(mapOf("jpg" to 7, "png" to 1, "webp" to 1), d.assets.groupingBy { it.fileName.substringAfterLast('.') }.eachCount())
        assertTrue(d.assets.all { it.type == AssetType.PHOTO && it.sizeBytes > 0 && it.checksum.length == 32 })

        val dir = Files.createTempDirectory("assets").toFile()
        try {
            val report = ImportArchive.copyAssets(fixture, imported.assetFiles, dir)
            assertEquals(CopyReport(9, 0), report)
            for (asset in d.assets) {
                val file = File(dir, asset.fileName)
                assertEquals(asset.sizeBytes, file.length())
                assertEquals(asset.fileName.substringAfterLast('.'), ImportArchive.detectExtension(file.readBytes().copyOf(16)))
            }
            assertEquals(9, dir.listFiles()!!.size)
            // Einzeln, mit Erkennung des Typs am Inhalt.
            val slash = imported.assetFiles.single { it.zipEntryName.startsWith("/") }
            val single = DaylioBackup.copyAsset(fixture, slash.zipEntryName, dir, "einzeln")
            assertEquals("einzeln.jpg", single.name)
            assertEquals(dir.canonicalFile, single.canonicalFile.parentFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun goalsAndOtherLists() {
        val d = imported.diary
        assertEquals(listOf("Jeden Tag lesen", "Sport", "Radfahren", "Früh ins Bett"), d.goals.map { it.name })
        assertEquals(listOf(false, false, true, true), d.goals.map { it.archivedAt != null })
        assertEquals(listOf(GoalRepeat.DAILY, GoalRepeat.TIMES_PER_WEEK, GoalRepeat.TIMES_PER_WEEK, GoalRepeat.DAILY), d.goals.map { it.repeat })
        assertEquals(3, d.goals[1].timesPerWeek)
        assertEquals(20 * 60 + 30, d.goals[0].reminderMinuteOfDay)
        assertNull(d.goals[1].reminderMinuteOfDay)
        assertEquals(d.tags.single { it.name == "Lesen" }.id, d.goals[0].tagId)
        assertNull(d.goals[3].tagId)
        assertEquals("2023-10-01", d.goals[0].startDate)

        // Monat ab 1: der erste Haken ist der 2.11.2023, der Dezember bleibt Dezember.
        val first = d.goalChecks.filter { it.goalId == d.goals[0].id }.map { it.date }.sorted()
        assertEquals(20, first.size)
        assertEquals("2023-11-02", first.first())
        assertEquals("2023-12-29", first.last())
        assertTrue(d.goalChecks.all { it.id == "${it.goalId}|${it.date}" })
        assertTrue(imported.warnings.any { "ab 1 gezählt" in it && "Monat 12" in it })

        val (wedding, birthday, move) = d.importantDays
        assertEquals("2015-06-20", wedding.date)
        assertTrue(wedding.remindOnDay)
        assertEquals(listOf(7), wedding.remindDaysBefore)
        assertNotNull(wedding.assetId)
        assertTrue(wedding.assetId in d.assets.map { it.id })
        assertEquals("Geburtstag Oma", birthday.name)
        assertEquals("1941-12-03", birthday.date)
        assertEquals(listOf(1), birthday.remindDaysBefore)
        assertEquals("2022-01-31", move.date)
        assertFalse(move.yearly)

        assertEquals(listOf("Dankbarkeit", "Dienst-Rückblick", "Abend"), d.templates.map { it.title })
        assertEquals("<p>Heute war …</p>", d.templates[2].body)
        assertEquals(listOf(20 * 60, 7 * 60 + 45), d.reminders.map { it.minuteOfDay })
        assertEquals(listOf(true, false), d.reminders.map { it.active })
        assertEquals(listOf("", "Wie hast Du geschlafen?"), d.reminders.map { it.text })
    }

    @Test fun warningsNameAssumptionsButNoContent() {
        val text = imported.warnings.joinToString("\n")
        assertTrue("Skalen: Der Aufbau im Backup ist nicht sicher bekannt" in text, text)
        assertTrue("„text_values“" in text, text)
        assertTrue("Minuten" in text, text)
        assertTrue("Wichtige Tage: Der Aufbau" in text, text)
        // Unbekannte Felder werden mit Namen und Art genannt.
        assertTrue("Liste „assets“: unbekannte Felder, nicht übernommen: android_metadata (Text)." in text, text)
        assertTrue("createdAt (Zahl)" in text, text)
        // Nichts aus dem Tagebuch selbst.
        for (secret in listOf("Schicht", "Jonas", "Hochzeit", "Oma", "Stress", "Lesen", "Dankbar", "geschlafen")) {
            assertFalse(secret in text, "$secret in: $text")
        }
        assertFalse("achievements" in text || "goalSuccessWeeks" in text, text)
        assertFalse("übersprungen" in text, text)
    }

    @Test fun secondImportCreatesNoDuplicates() {
        val again = DaylioBackup.read(fixture, now)
        assertEquals(imported.diary, again.diary)

        val first = DiaryMerge.merge(Diary(), imported.diary, MergeMode.EMPTY_ONLY, now)
        assertEquals(expected.entries, first.report[ImportKind.ENTRIES].taken)
        assertEquals(expected.tags, first.report[ImportKind.TAGS].taken)
        assertEquals(expected.goalChecks, first.report[ImportKind.GOAL_CHECKS].taken)
        assertEquals(0, first.report.skipped + first.report.errors)
        assertEquals(imported.preview, ImportPreview.of(first.diary))

        for (mode in MergeMode.entries) {
            val second = DiaryMerge.merge(first.diary, DaylioBackup.read(fixture, now + 5000).diary, mode, now + 5000)
            assertEquals(first.diary, second.diary, "$mode")
            assertEquals(0, second.report.taken + second.report.errors, "$mode")
            assertEquals(expected.entries, second.report[ImportKind.ENTRIES].skipped, "$mode")
            assertEquals(expected.assets, second.report[ImportKind.ASSETS].skipped, "$mode")
        }
    }

    @Test fun interruptedImportCanBeContinued() {
        val half = imported.diary.copy(entries = imported.diary.entries.take(50), goalChecks = emptyList())
        val partial = DiaryMerge.merge(Diary(), half, MergeMode.EMPTY_ONLY, now).diary
        val full = DiaryMerge.merge(partial, imported.diary, MergeMode.EMPTY_ONLY, now)
        assertEquals(expected.entries - 50, full.report[ImportKind.ENTRIES].taken)
        assertEquals(50, full.report[ImportKind.ENTRIES].skipped)
        assertEquals(imported.preview, ImportPreview.of(full.diary))
    }

    @Test fun emptyOnlyReplacesStarterSetupAndRefusesForeignEntries() {
        val starter = Diary(
            moods = (1..5).map { Mood(id = "start-mood-$it", level = it, name = "Stufe $it", icon = "mood$it") },
            groups = listOf(TagGroup(id = "start-group", name = "Start")),
            tags = listOf(Tag(id = "start-tag", name = "Schlaf", groupId = "start-group")),
        )
        val result = DiaryMerge.merge(starter, imported.diary, MergeMode.EMPTY_ONLY, now)
        assertEquals(imported.preview, ImportPreview.of(result.diary))
        assertTrue(result.diary.moods.filter { it.id.startsWith("start-") }.all { it.deleted && it.dirty && it.updatedAt == now })
        assertTrue(result.diary.tags.single { it.id == "start-tag" }.deleted)
        assertTrue(result.diary.groups.single { it.id == "start-group" }.deleted)
        assertEquals(7, result.diary.settings.weekStart)

        val foreign = result.diary.copy(entries = result.diary.entries + result.diary.entries.first().copy(id = "eigener-eintrag"))
        val error = assertFailsWith<ImportException> { DiaryMerge.merge(foreign, imported.diary, MergeMode.EMPTY_ONLY, now) }
        assertTrue("Zusammenführen" in error.message!!)
    }

    @Test fun mergeMapsSetupByName() {
        val existing = Diary(
            moods = listOf(
                Mood(id = "m-gut", level = 2, name = " GUT ", icon = "mood2"),
                // Gleicher Name, andere Stufe: wird nicht zusammengelegt.
                Mood(id = "m-mies", level = 4, name = "Mies", icon = "mood4"),
            ),
            groups = listOf(TagGroup(id = "g-alltag", name = "alltag")),
            tags = listOf(Tag(id = "t-lesen", name = "lesen ", groupId = "g-alltag"), Tag(id = "t-sport", name = "SPORT")),
            scales = listOf(
                de.mediclever.tagebuch.core.model.Scale(
                    id = "s-stress", type = ScaleType.TEXT, name = "stress",
                    points = listOf("Kaum", "Etwas", "Deutlich", "Sehr hoch", "Extrem").mapIndexed { i, l ->
                        de.mediclever.tagebuch.core.model.ScalePoint(id = "p-$i", position = i + 1, label = l)
                    },
                ),
                // Gleicher Name, aber Zahl statt Zeit: bleibt getrennt.
                de.mediclever.tagebuch.core.model.Scale(id = "s-schlaf", type = ScaleType.NUMBER, name = "Schlafdauer"),
            ),
            settings = de.mediclever.tagebuch.core.model.UserSettings(weekStart = 1),
        )
        val incoming = imported.diary
        val result = DiaryMerge.merge(existing, incoming, MergeMode.MERGE, now)
        val d = result.diary

        assertEquals(KindReport(taken = 5, skipped = 1), result.report[ImportKind.MOODS])
        assertEquals(KindReport(taken = 3, skipped = 1), result.report[ImportKind.GROUPS])
        assertEquals(KindReport(taken = 12, skipped = 2), result.report[ImportKind.TAGS])
        assertEquals(KindReport(taken = 2, skipped = 1), result.report[ImportKind.SCALES])
        assertEquals(expected.entries, result.report[ImportKind.ENTRIES].taken)
        assertEquals(0, result.report.errors)

        assertEquals(1, d.moods.count { it.name.trim().equals("gut", ignoreCase = true) })
        assertEquals(2, d.moods.count { it.name == "Mies" })
        assertEquals(1, d.tags.count { it.name.trim().equals("lesen", ignoreCase = true) })
        assertEquals(1, d.groups.count { it.name.equals("alltag", ignoreCase = true) })
        assertEquals(2, d.scales.count { it.name == "Schlafdauer" })
        assertEquals(1, d.settings.weekStart, "Zusammenführen ändert keine Einstellungen")

        // Verweise zeigen auf die vorhandenen Zeilen.
        val oldGut = incoming.moods.single { it.name == "Gut" }.id
        val oldLesen = incoming.tags.single { it.name == "Lesen" }.id
        val oldSport = incoming.tags.single { it.name == "Sport" }.id
        val oldStress = incoming.scales.single { it.name == "Stress" }
        val oldAlltag = incoming.groups.single { it.name == "Alltag" }.id
        val byId = d.entries.associateBy { it.id }
        for (before in incoming.entries) {
            val after = byId.getValue(before.id)
            assertEquals(if (before.moodId == oldGut) "m-gut" else before.moodId, after.moodId)
            assertEquals(before.tagIds.map { if (it == oldLesen) "t-lesen" else if (it == oldSport) "t-sport" else it }, after.tagIds)
            assertEquals(before.scaleValues.size, after.scaleValues.size)
            before.scaleValues.zip(after.scaleValues).forEach { (b, a) ->
                if (b.scaleId == oldStress.id) {
                    assertEquals("s-stress", a.scaleId)
                    assertEquals("p-${oldStress.points.single { it.id == b.pointId }.position - 1}", a.pointId)
                } else {
                    assertEquals(b, a)
                }
            }
        }
        assertTrue(d.entries.none { e -> e.moodId == oldGut || oldLesen in e.tagIds || e.scaleValues.any { it.scaleId == oldStress.id } })
        assertTrue(incoming.entries.any { it.moodId == oldGut } && incoming.entries.any { oldLesen in it.tagIds })
        assertEquals("t-lesen", d.goals.single { it.name == "Jeden Tag lesen" }.tagId)
        assertEquals("t-sport", d.goals.single { it.name == "Sport" }.tagId)
        assertEquals("g-alltag", d.tags.single { it.name == "Einkaufen" }.groupId)
        assertTrue(d.tags.none { it.groupId == oldAlltag } && d.groups.none { it.id == oldAlltag })
        assertTrue(d.tags.none { it.id == oldLesen } && d.moods.none { it.id == oldGut } && d.scales.none { it.id == oldStress.id })

        // Und noch einmal: nichts Neues.
        val again = DiaryMerge.merge(d, incoming, MergeMode.MERGE, now)
        assertEquals(d, again.diary)
        assertEquals(0, again.report.taken)
    }

    @Test fun csvRoundTripOfImportedDiary() {
        val d = imported.diary
        val csv = CsvFormat.export(d)
        val lines = CsvFormat.parseRecords(csv)
        assertEquals(CsvFormat.HEADER, lines.first().second)
        assertEquals(expected.entries + 1, lines.size)

        val parsed = CsvFormat.parse(csv, d)
        assertTrue(parsed.warnings.isEmpty(), parsed.warnings.toString())
        assertTrue(parsed.unknownMoods.isEmpty() && parsed.unknownActivities.isEmpty() && parsed.unknownScales.isEmpty())
        val back = CsvFormat.resolve(parsed, d, zone = ZoneId.of("Europe/Berlin"), now = now)

        val sorted = d.entries.sortedWith(compareByDescending<de.mediclever.tagebuch.core.model.Entry> { it.localDate }.thenByDescending { it.minuteOfDay })
        assertEquals(expected.latestDate, parsed.rows.first().localDate)
        assertEquals(sorted.size, back.entries.size)
        sorted.zip(back.entries).forEach { (a, b) ->
            assertEquals(a.localDate, b.localDate)
            assertEquals(a.minuteOfDay, b.minuteOfDay)
            assertEquals(a.moodId, b.moodId)
            assertEquals(a.tagIds, b.tagIds)
            assertEquals(a.scaleValues.toSet(), b.scaleValues.toSet())
            assertEquals(a.title, b.title)
            assertEquals(de.mediclever.tagebuch.core.text.NoteHtml.toPlainText(a.note), de.mediclever.tagebuch.core.text.NoteHtml.toPlainText(b.note))
        }

        // In ein leeres Tagebuch: alles Fehlende wird gemeldet und nach Rueckfrage angelegt.
        val fresh = CsvFormat.parse(csv, Diary())
        assertEquals(setOf("Super", "Gut", "Okay", "Schlecht", "Mies", "Erschöpft"), fresh.unknownMoods.toSet())
        assertEquals(setOf("Stress", "Energie", "Schlafdauer"), fresh.unknownScales.map { it.name }.toSet())
        val created = CsvFormat.resolve(fresh, Diary(), moodLevels = mapOf("Erschöpft" to 4), zone = ZoneId.of("Europe/Berlin"), now = now)
        assertEquals(imported.preview.entriesPerLevel, ImportPreview.of(created).entriesPerLevel)
        // Zeilengleich bis auf die Reihenfolge der Skalen innerhalb einer Zelle (die folgt der Reihenfolge im Tagebuch).
        assertEquals(normalizeScales(csv), normalizeScales(CsvFormat.export(created)))
        val merged = DiaryMerge.merge(Diary(), created, MergeMode.MERGE, now)
        val twice = DiaryMerge.merge(merged.diary, CsvFormat.resolve(fresh, merged.diary, mapOf("Erschöpft" to 4), ZoneId.of("Europe/Berlin"), now), MergeMode.MERGE, now)
        assertEquals(0, twice.report.taken)
        assertEquals(merged.diary, twice.diary)
    }

    private fun normalizeScales(csv: String): List<List<String>> = CsvFormat.parseRecords(csv).map { (_, cells) ->
        cells.mapIndexed { i, cell -> if (i == 6) cell.split(" | ").sorted().joinToString(" | ") else cell }
    }
}
