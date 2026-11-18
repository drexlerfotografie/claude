package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScalePoint
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.model.Tag
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvFormatTest {

    private val now = 1_780_000_000_000L
    private val berlin = ZoneId.of("Europe/Berlin")

    private val diary = Diary(
        moods = listOf(
            Mood(id = "m1", level = 1, name = "Super", icon = "mood1"),
            Mood(id = "m2", level = 2, name = "Gut", icon = "mood2"),
            Mood(id = "m4", level = 4, name = "Schlecht, aber \"okay\"", icon = "mood4"),
        ),
        tags = listOf(Tag(id = "t1", name = "Lesen"), Tag(id = "t2", name = "Sport, draußen"), Tag(id = "t3", name = "Familie ")),
        scales = listOf(
            Scale(
                id = "s1", type = ScaleType.TEXT, name = "Stress", sortOrder = 1,
                points = listOf(ScalePoint("p1", 1, "Kaum"), ScalePoint("p2", 2, "Etwas"), ScalePoint("p3", 3, "Sehr: hoch 2/3")),
            ),
            Scale(id = "s2", type = ScaleType.DURATION, name = "Schlaf", sortOrder = 2, min = 0, max = 1440),
            Scale(id = "s3", type = ScaleType.NUMBER, name = "Schritte: Tausend", sortOrder = 3, min = 0, max = 50),
        ),
        entries = listOf(
            Entry(
                id = "e1", timestamp = 1, offsetMinutes = 60, localDate = "2025-03-07", minuteOfDay = 20 * 60 + 5, moodId = "m2",
                title = "Titel, mit Komma", note = "<p>Erste Zeile</p><p>Zweite \"Zeile\", mit Komma</p>",
                tagIds = listOf("t2", "t1"),
                scaleValues = listOf(ScaleValue("s2", minutes = 6 * 60), ScaleValue("s1", pointId = "p2"), ScaleValue("s3", number = 12)),
            ),
            Entry(id = "e2", timestamp = 2, offsetMinutes = 60, localDate = "2025-03-07", minuteOfDay = 7 * 60, moodId = "m1"),
            Entry(
                id = "e3", timestamp = 3, offsetMinutes = 120, localDate = "2025-08-01", minuteOfDay = 0, moodId = "m4",
                tagIds = listOf("t3"), scaleValues = listOf(ScaleValue("s1", pointId = "p3"), ScaleValue("s2", minutes = 25 * 60 + 5)),
                note = "a &lt; b &amp; c",
            ),
            Entry(id = "e4", timestamp = 4, offsetMinutes = 60, localDate = "2026-01-01", minuteOfDay = 1, moodId = "m1", deleted = true),
        ),
    )

    @Test fun exportFormat() {
        val expected = """
            full_date,date,weekday,time,mood,activities,scales,note_title,note
            2025-08-01,1. August,Freitag,00:00,"Schlecht, aber ""okay""${'"'},Familie,Stress: Sehr: hoch 2/3 3/3 | Schlaf: 25:05,,a < b & c
            2025-03-07,7. März,Freitag,20:05,Gut,"Sport, draußen | Lesen",Stress: Etwas 2/3 | Schlaf: 6:00 | Schritte: Tausend: 12,"Titel, mit Komma","Erste Zeile
            Zweite ""Zeile"", mit Komma"
            2025-03-07,7. März,Freitag,07:00,Super,,,,
        """.trimIndent() + "\n"
        assertEquals(expected, CsvFormat.export(diary))
        assertEquals("﻿" + expected, CsvFormat.export(diary, withBom = true))
    }

    @Test fun roundTrip() {
        for (bom in listOf(false, true)) {
            val csv = CsvFormat.export(diary, withBom = bom)
            val parsed = CsvFormat.parse(csv.replace("\n", "\r\n"), diary)
            assertTrue(parsed.warnings.isEmpty(), parsed.warnings.toString())
            assertTrue(parsed.unknownMoods.isEmpty() && parsed.unknownActivities.isEmpty() && parsed.unknownScales.isEmpty())
            assertEquals(listOf(2, 3, 5), parsed.rows.map { it.line })
            val back = CsvFormat.resolve(parsed, diary, zone = berlin, now = now).entries
            val original = listOf("e3", "e1", "e2").map { id -> diary.entries.single { it.id == id } }
            assertEquals(3, back.size)
            original.zip(back).forEach { (a, b) ->
                assertEquals(a.localDate, b.localDate)
                assertEquals(a.minuteOfDay, b.minuteOfDay)
                assertEquals(a.moodId, b.moodId)
                assertEquals(a.tagIds, b.tagIds)
                assertEquals(a.scaleValues.toSet(), b.scaleValues.toSet())
                assertEquals(a.title, b.title)
                assertEquals(a.offsetMinutes, b.offsetMinutes) // Berlin: Winter +1, Sommer +2
            }
            assertEquals("Erste Zeile<br>Zweite \"Zeile\", mit Komma", back[1].note)
            assertEquals("a &lt; b &amp; c", back[0].note)
            // Zeitpunkt = Ortszeit in der angegebenen Zone.
            assertEquals(java.time.LocalDateTime.of(2025, 3, 7, 19, 5).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(), back[1].timestamp)
            // Noch einmal exportiert ergibt dieselbe Datei.
            assertEquals(CsvFormat.export(diary), CsvFormat.export(diary.copy(entries = back)))
        }
    }

    @Test fun rfc4180Parsing() {
        val records = CsvFormat.parseRecords("﻿a,b,c\r\n\"x, y\",\"Zeile 1\nZeile 2\",\"sagt \"\"hallo\"\"\"\n\n,,\n\"\"\nletzte,ohne,umbruch")
        assertEquals(
            listOf(
                1 to listOf("a", "b", "c"),
                2 to listOf("x, y", "Zeile 1\nZeile 2", "sagt \"hallo\""),
                5 to listOf("", "", ""),
                6 to listOf(""),
                7 to listOf("letzte", "ohne", "umbruch"),
            ),
            records,
        )
        assertTrue(CsvFormat.parseRecords("").isEmpty())
        assertEquals(listOf(1 to listOf("a", "")), CsvFormat.parseRecords("a,"))
    }

    @Test fun unknownItemsAreReportedAndCreatedOnResolve() {
        val csv = """
            full_date,date,weekday,time,mood,activities,scales,note_title,note
            2024-05-02,2 May,Thursday,8:15 PM,rad,"Lesen | Gassi gehen | gassi gehen ","Stress: Etwas 2/3 | Laune: Top 4/4 | Wach: 16:30 | Wasser: 3",,"Hallo <b>Welt</b>"
            2024-05-01,1 May,Wednesday,12:05 AM,Mau,Klettern,"Laune: Flop 1/4 | Stress: Mittel 2/3",Titel,
            2024-05-01,1 May,Wednesday,7:00,GUT ,,Stress: Unbekannt 4/5,,
        """.trimIndent()
        val parsed = CsvFormat.parse(csv, diary)
        assertEquals(listOf("rad", "Mau"), parsed.unknownMoods)
        assertEquals(listOf("Gassi gehen", "Klettern"), parsed.unknownActivities)
        assertEquals(
            listOf(
                CsvFormat.UnknownScale("Laune", ScaleType.TEXT, listOf("Flop", "2", "3", "Top")),
                CsvFormat.UnknownScale("Wach", ScaleType.DURATION),
                CsvFormat.UnknownScale("Wasser", ScaleType.NUMBER),
            ),
            parsed.unknownScales,
        )
        assertEquals(listOf(20 * 60 + 15, 5, 7 * 60), parsed.rows.map { it.minuteOfDay })
        // "Stress: Unbekannt 4/5" passt weder nach Bezeichnung noch nach Punktzahl.
        assertEquals(listOf("1 Skalenwerte passen nicht zu den Punkten der vorhandenen Skala und werden nicht übernommen."), parsed.warnings)
        assertEquals(1, CsvFormat.suggestedMoodLevel("rad"))
        assertEquals(5, CsvFormat.suggestedMoodLevel(" Awful"))
        assertEquals(3, CsvFormat.suggestedMoodLevel("Mau"))

        val incoming = CsvFormat.resolve(parsed, diary, moodLevels = mapOf("mau" to 4), zone = berlin, now = now)
        assertEquals(listOf(1 to "rad", 2 to "Gut", 4 to "Mau"), incoming.moods.sortedBy { it.level }.map { it.level to it.name })
        assertEquals(setOf("Lesen", "Gassi gehen", "Klettern"), incoming.tags.map { it.name }.toSet())
        assertEquals("pets", incoming.tags.single { it.name == "Gassi gehen" }.icon)
        val laune = incoming.scales.single { it.name == "Laune" }
        assertEquals(listOf("Flop", "2", "3", "Top"), laune.points.map { it.label })
        assertEquals(4, laune.max)

        val (first, second, third) = incoming.entries
        assertEquals(listOf("t1", incoming.tags.single { it.name == "Gassi gehen" }.id), first.tagIds)
        assertEquals("Hallo &lt;b&gt;Welt&lt;/b&gt;", first.note) // Text bleibt Text, kein HTML aus der Datei
        assertEquals(
            setOf(
                ScaleValue("s1", pointId = "p2"),
                ScaleValue(laune.id, pointId = laune.points[3].id),
                ScaleValue(incoming.scales.single { it.name == "Wach" }.id, minutes = 16 * 60 + 30),
                ScaleValue(incoming.scales.single { it.name == "Wasser" }.id, number = 3),
            ),
            first.scaleValues.toSet(),
        )
        // "Mittel 2/3": Bezeichnung unbekannt, Punktzahl stimmt -> Position 2 der vorhandenen Skala.
        assertEquals(setOf(ScaleValue(laune.id, pointId = laune.points[0].id), ScaleValue("s1", pointId = "p2")), second.scaleValues.toSet())
        assertEquals("Titel", second.title)
        assertEquals("m2", third.moodId)
        assertTrue(third.scaleValues.isEmpty())

        // Zusammenfuehren legt nur das Neue an; ein zweiter Lauf nichts mehr.
        val merged = DiaryMerge.merge(diary, incoming, MergeMode.MERGE, now)
        assertEquals(KindReport(taken = 2, skipped = 1), merged.report[ImportKind.MOODS])
        assertEquals(KindReport(taken = 2, skipped = 1), merged.report[ImportKind.TAGS])
        assertEquals(KindReport(taken = 3, skipped = 1), merged.report[ImportKind.SCALES])
        assertEquals(KindReport(taken = 3), merged.report[ImportKind.ENTRIES])
        val reparsed = CsvFormat.parse(csv, merged.diary)
        assertTrue(reparsed.unknownMoods.isEmpty() && reparsed.unknownActivities.isEmpty() && reparsed.unknownScales.isEmpty())
        val again = DiaryMerge.merge(merged.diary, CsvFormat.resolve(reparsed, merged.diary, zone = berlin, now = now), MergeMode.MERGE, now)
        assertEquals(0, again.report.taken)
        assertEquals(merged.diary, again.diary)
    }

    @Test fun identicalRowsAreKept() {
        val csv = "full_date,time,mood\n2024-01-01,10:00,Gut\n2024-01-01,10:00,Gut\n"
        val entries = CsvFormat.resolve(CsvFormat.parse(csv, diary), diary, zone = berlin, now = now).entries
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.id }.toSet().size)
    }

    @Test fun badRowsAreSkippedWithLineNumbers() {
        val csv = "mood,full_date,time\nGut,kein Datum,10:00\n,2024-01-01,10:00\nGut,01.02.2024,abends\n\nGut,2024-02-30,10:00\n"
        val parsed = CsvFormat.parse(csv, diary)
        assertEquals(listOf("2024-02-01"), parsed.rows.map { it.localDate })
        assertEquals(12 * 60, parsed.rows.single().minuteOfDay)
        assertEquals(
            listOf(
                "2 Zeilen ohne lesbares Datum wurden übersprungen (Zeilen 2, 6).",
                "1 Zeilen ohne Stimmung wurden übersprungen (Zeilen 3).",
                "1 Zeilen ohne lesbare Uhrzeit: 12:00 angenommen (Zeilen 4).",
            ),
            parsed.warnings,
        )
        assertFailsWith<ImportException> { CsvFormat.parse("", diary) }
        assertFailsWith<ImportException> { CsvFormat.parse("a,b,c\n1,2,3", diary) }
    }
}
