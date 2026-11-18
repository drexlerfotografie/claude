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
        // Farbnummern 39, 25, 11 der Quell-Palette; die Gruppe ohne id_color wird neutral.
        assertEquals(listOf(12, 6, 10, 0), d.groups.map { it.colorIndex })
        assertEquals(listOf(false, false, true, false), d.groups.map { it.collapsed })
        // Eigene Farbe nur, wo id_color steht; sonst erbt die Aktivitaet (null).
        assertEquals(expected.tagColors, d.tags.map { it.colorIndex })
        assertEquals(listOf<Int?>(17, null, 1, null, 0, null, 14, null, 7, null, 16, 13, null, 12), d.tags.map { it.colorIndex })

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
        // Reihenfolge nach dem Feld order, nicht nach den Kennungen der Punkte.
        assertEquals(listOf("Kaum", "Etwas", "Deutlich", "Sehr hoch"), stress.points.map { it.label })
        assertEquals(listOf(1, 2, 3, 4), stress.points.map { it.position })
        assertEquals(1, stress.min)
        assertEquals(4, stress.max)
        assertEquals(Trend.LOWER_IS_BETTER, stress.trend) // trend 1
        assertEquals(ChartMode.DAILY_AVERAGE, stress.chartMode)
        assertEquals("Energie", energy.name)
        assertEquals(ScaleType.TEXT, energy.type)
        assertEquals(5, energy.points.size)
        assertEquals(5, energy.max)
        assertEquals(Trend.HIGHER_IS_BETTER, energy.trend) // trend 2
        assertEquals(ScaleType.DURATION, sleep.type)
        assertEquals(Trend.HIGHER_IS_BETTER, sleep.trend)
        assertEquals(ChartMode.DAILY_SUM, sleep.chartMode)
        assertEquals(listOf(0, 24 * 60, 15), listOf(sleep.min, sleep.max, sleep.step))
        assertTrue(sleep.points.isEmpty())
        // Farbnummern 3 und 14 sind bekannt, 27 nicht: fester Ersatz 27 mod 18.
        assertEquals(listOf(1, 14, 9), d.scales.map { it.colorIndex })
        // Gruppen und Skalen teilen sich eine Reihenfolge.
        assertEquals(listOf(1, 2, 4, 5), d.groups.map { it.sortOrder })
        assertEquals(listOf(3, 6, 7), d.scales.map { it.sortOrder })

        val s = d.settings
        assertEquals(7, s.weekStart) // Wert 1 des Vorbilds ist Sonntag
        assertEquals("custom", s.moodScheme)
        // Farbnummern 39, 40, 25, 1, 11 -> Palette 12, 4, 6, 17, 0; Stufe 1 zuerst.
        assertEquals(listOf("FF7DBE2B", "FF2D8A5F", "FF4F7FC0", "FFF2C230", "FFB9303F"), s.customMoodColors)
        assertFalse(s.showQuote)
        assertTrue(s.showMemories)
        assertTrue(s.reportNotifications, "Wochen- oder Monatsbericht ist an")
        assertEquals(de.mediclever.tagebuch.core.model.MidnightRule.ASK, s.midnightRule)
        assertEquals(now, s.updatedAt)
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
        assertEquals(expected.scaleValues, d.entries.sumOf { it.scaleValues.size })
        assertTrue(expected.scaleValues > 100)
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
        // Ziele mit Aktivitaet haben im Backup keinen Namen und heissen wie die Aktivitaet.
        assertEquals(listOf("Lesen", "Sport", "Radfahren", "Früh ins Bett", "Spaziergang", "Großeinkauf"), d.goals.map { it.name })
        assertEquals(listOf(false, false, true, true, false, true), d.goals.map { it.archivedAt != null })
        assertEquals(
            listOf(GoalRepeat.DAILY, GoalRepeat.TIMES_PER_WEEK, GoalRepeat.TIMES_PER_WEEK, GoalRepeat.WEEKDAYS, GoalRepeat.WEEKDAYS, GoalRepeat.TIMES_PER_WEEK),
            d.goals.map { it.repeat },
        )
        assertEquals(3, d.goals[1].timesPerWeek)
        assertEquals(2, d.goals[2].timesPerWeek)
        assertTrue(d.goals[0].weekdays.isEmpty())
        assertEquals(listOf(1, 2, 3, 4, 5), d.goals[3].weekdays) // Maske 62: Montag bis Freitag
        assertEquals(listOf(7), d.goals[4].weekdays)             // Maske 1: Sonntag
        assertEquals(1, d.goals[5].timesPerWeek)                 // monatlich gibt es hier nicht
        assertEquals(listOf(20 * 60 + 30, null, null, null, 9 * 60 + 30, null), d.goals.map { it.reminderMinuteOfDay })
        assertEquals(d.tags.single { it.name == "Lesen" }.id, d.goals[0].tagId)
        assertNull(d.goals[3].tagId)
        assertNull(d.goals[5].tagId)
        // created_at ist Mitternacht Ortszeit (in UTC der 30.9.): es gilt der Tag am Ort.
        assertEquals("2023-10-01", d.goals[0].startDate)
        assertEquals("2023-10-02", d.goals[1].startDate)
        assertEquals(listOf(null, null, "2025-08-23", "2025-08-23", null, "2025-08-23"), d.goals.map { it.endDate })
        assertEquals("Mindestens zehn Seiten", d.goals[0].note)
        assertEquals(DaylioBackup.deterministicId("goal", "101"), d.goals[0].id)

        // goalEntries verweist auf goal_id. Monat ab 1: der erste Haken ist der 2.11.2023, der Dezember bleibt Dezember.
        assertEquals(listOf(20, 12, 5, 3, 4, 2), d.goals.map { g -> d.goalChecks.count { it.goalId == g.id } })
        val first = d.goalChecks.filter { it.goalId == d.goals[0].id }.map { it.date }.sorted()
        assertEquals("2023-11-02", first.first())
        assertEquals("2023-12-29", first.last())
        val sundays = d.goalChecks.filter { it.goalId == d.goals[4].id }.map { java.time.LocalDate.parse(it.date).dayOfWeek.value }
        assertEquals(listOf(7, 7, 7, 7), sundays)
        assertTrue(d.goalChecks.all { it.id == "${it.goalId}|${it.date}" })

        val (wedding, birthday, move) = d.importantDays
        assertEquals("2015-06-20", wedding.date)
        assertEquals("Tisch reservieren", wedding.note)
        assertNotNull(wedding.assetId)
        assertTrue(wedding.assetId in d.assets.map { it.id })
        assertTrue(wedding.yearly)
        assertEquals("Geburtstag Oma", birthday.name)
        assertEquals("1941-12-03", birthday.date)
        assertEquals(wedding.assetId, birthday.assetId)
        assertEquals("2022-01-31", move.date)
        assertFalse(move.yearly)
        assertNull(move.assetId)
        // Kennzahlen der Erinnerungen und Kategorie-Nummern sind nicht entschluesselt: nichts wird geraten.
        assertTrue(d.importantDays.all { !it.remindOnDay && it.remindDaysBefore.isEmpty() && it.category.isEmpty() })

        assertEquals(listOf("Dankbarkeit", "Dienst-Rückblick", "Abend"), d.templates.map { it.title })
        assertEquals("<p>Heute war …</p>", d.templates[2].body)
        assertEquals(listOf(20 * 60, 7 * 60 + 45), d.reminders.map { it.minuteOfDay })
        assertEquals(listOf(true, false), d.reminders.map { it.active })
        assertEquals(listOf("", "Wie hast Du geschlafen?"), d.reminders.map { it.text })
    }

    @Test fun warningsAreFewAndWithoutContent() {
        // Der Aufbau ist belegt: es bleiben nur die Hinweise zu dem, was sich wirklich nicht uebernehmen laesst.
        assertEquals(
            listOf(
                "1 monatliche Ziele wurden als „einmal pro Woche“ übernommen, weil es monatliche Ziele hier nicht gibt.",
                "3 wichtige Tage tragen im Backup nur eine Kategorie-Nummer ohne Namen. Die Kategorie bleibt leer und lässt sich hier neu wählen.",
                "2 wichtige Tage haben Erinnerungen in einer Form, die sich nicht sicher lesen lässt. Die Erinnerungen sind aus und lassen sich hier neu einstellen.",
                "Einstellungen: Diese Angaben ließen sich nicht sicher lesen und bleiben auf der Voreinstellung: ENTRIES_AFTER_MIDNIGHT.",
                "Farben: Die Farbnummern 27 des Backups sind hier nicht bekannt. Dafür wurde eine Ersatzfarbe vergeben, bitte bei Bedarf anpassen.",
            ),
            imported.warnings,
        )
        val text = imported.warnings.joinToString("\n")
        // Nichts aus dem Tagebuch selbst.
        for (secret in listOf("Schicht", "Jonas", "Hochzeit", "Oma", "Stress", "Lesen", "Dankbar", "geschlafen", "Einkauf", "See")) {
            assertFalse(secret in text, "$secret in: $text")
        }
    }

    @Test fun dataFileAloneGivesTheSameDiaryWithoutPhotos() {
        val dir = Files.createTempDirectory("datendatei").toFile()
        try {
            // Nur der innere Teil des Archivs: Base64-kodiertes JSON, nicht gepackt.
            val data = File(dir, "backup.daylio").apply { writeBytes(java.util.Base64.getEncoder().encode(sample.json.toByteArray())) }
            assertFalse(DaylioBackup.looksLikeZip(data))
            assertTrue(DaylioBackup.looksLikeZip(fixture))
            val alone = DaylioBackup.readDataFile(data, now)
            assertTrue(alone.assetFiles.isEmpty())
            assertEquals(imported.preview, alone.preview)
            assertEquals(imported.diary.entries, alone.diary.entries)
            assertEquals(imported.diary.goals, alone.diary.goals)
            assertEquals(imported.diary.assets.map { it.id }, alone.diary.assets.map { it.id })
            assertEquals(imported.warnings, alone.warnings.dropLast(1))
            assertTrue("nur die Datendatei" in alone.warnings.last() && "9 Fotos" in alone.warnings.last(), alone.warnings.last())
            assertEquals(alone.diary, DaylioBackup.readData(data.readBytes(), now).diary)
            // Ein Archiv ist keine Datendatei, eine fehlende Datei auch nicht.
            assertFailsWith<ImportException> { DaylioBackup.readDataFile(fixture, now) }
            assertFailsWith<ImportException> { DaylioBackup.readDataFile(File(dir, "fehlt.daylio"), now) }
        } finally {
            dir.deleteRecursively()
        }
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
        assertEquals("t-lesen", d.goals.single { it.name == "Lesen" }.tagId)
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
