package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.ThemeMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupFormatTest {

    private val now = 1_780_000_000_000L

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("sicherung").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Das Beispiel-Tagebuch mit echten Dateien in [dir]/quelle. */
    private fun sampleDiary(dir: File): Pair<Diary, File> {
        val sample = SampleBackup.generate()
        val zip = File(dir, "sample.daylio").apply { writeBytes(SampleBackup.zipBytes(sample)) }
        val imported = DaylioBackup.read(zip, now)
        val source = File(dir, "quelle")
        ImportArchive.copyAssets(zip, imported.assetFiles, source)
        val diary = DiaryMerge.merge(Diary(), imported.diary, MergeMode.EMPTY_ONLY, now).diary
        return diary.copy(settings = diary.settings.copy(themeMode = ThemeMode.DARK, fontChoice = "serif")) to source
    }

    @Test fun roundTrip() = withTempDir { dir ->
        val (diary, source) = sampleDiary(dir)
        // Zeilen, die schon hochgeladen waren, eine geloeschte Datei und eine fehlende Datei.
        // Die geloeschte Datei haengt, wie in der App, an keinem Eintrag mehr.
        val gone = diary.assets.first().id
        val entries = diary.entries.map { it.copy(assetIds = it.assetIds - gone) }
        val synced = diary.copy(
            entries = entries.map { it.copy(dirty = false) },
            assets = diary.assets.mapIndexed { i, a -> if (i == 0) a.copy(deleted = true) else a } +
                Asset(id = "ohne-datei", fileName = "ohne-datei.jpg"),
        )
        val backup = File(dir, "sicherung.tagebuch")
        val report = backup.outputStream().use { out ->
            BackupFormat.write(out, synced, createdAt = now) { asset -> File(source, asset.fileName) }
        }
        assertEquals(BackupFormat.WriteReport(assetsWritten = 8, assetsMissing = 1), report)

        ZipFile(backup).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(BackupFormat.JSON_NAME, names.first())
            assertEquals(9, names.size)
            assertTrue(names.drop(1).all { it.startsWith("assets/") })
            val json = zip.getInputStream(zip.getEntry("tagebuch.json")).readBytes().toString(Charsets.UTF_8)
            assertTrue(json.startsWith("{\"formatVersion\":1,"), json.take(40))
        }

        val read = BackupFormat.read(backup)
        // Inhaltlich gleich; nur die Marke "noch nicht hochgeladen" ist ueberall gesetzt.
        assertEquals(synced.copy(entries = entries), read.diary)
        assertTrue(read.diary.entries.all { it.dirty })
        assertEquals(ImportPreview.of(synced), read.preview)
        assertEquals(8, read.assetFiles.size)
        assertEquals(listOf("1 Dateien stehen in der Sicherung, fehlen aber im Archiv."), read.warnings)

        val target = File(dir, "ziel")
        assertEquals(CopyReport(8, 0), ImportArchive.copyAssets(backup, read.assetFiles, target))
        for (file in read.assetFiles) {
            assertTrue(File(source, file.fileName).readBytes().contentEquals(File(target, file.fileName).readBytes()))
        }

        // Wiederherstellen in ein leeres Tagebuch ergibt denselben Bestand, ein zweites Mal aendert nichts.
        val restored = DiaryMerge.merge(Diary(), read.diary, MergeMode.EMPTY_ONLY, now)
        assertEquals(ImportPreview.of(synced), ImportPreview.of(restored.diary))
        assertEquals(ThemeMode.DARK, restored.diary.settings.themeMode)
        assertEquals("serif", restored.diary.settings.fontChoice)
        assertEquals(7, restored.diary.settings.weekStart)
        val again = DiaryMerge.merge(restored.diary, read.diary, MergeMode.MERGE, now)
        assertEquals(0, again.report.taken)
        assertEquals(restored.diary, again.diary)
    }

    @Test fun emptyDiary() = withTempDir { dir ->
        val out = ByteArrayOutputStream()
        assertEquals(BackupFormat.WriteReport(0, 0), BackupFormat.write(out, Diary()) { null })
        val file = File(dir, "leer.tagebuch").apply { writeBytes(out.toByteArray()) }
        val read = BackupFormat.read(file)
        assertEquals(Diary(), read.diary)
        assertTrue(read.assetFiles.isEmpty() && read.warnings.isEmpty())
    }

    private fun zip(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test fun zipSlipAndBadNamesAreBlocked() = withTempDir { dir ->
        val json = """{"formatVersion":1,"zukunft":true,"diary":{"assets":[
            {"id":"a","fileName":"../../boese.jpg"},{"id":"b","fileName":"gut.jpg"},{"id":"c","fileName":"unter/ordner.jpg"}]}}"""
        val file = File(dir, "boese.tagebuch")
        zip(file, "tagebuch.json" to json, "assets/../../boese.jpg" to "x", "/assets/gut.jpg" to "gut", "assets/unter/ordner.jpg" to "x")
        val read = BackupFormat.read(file)
        assertEquals(listOf(AssetFile("b", "/assets/gut.jpg", "gut.jpg")), read.assetFiles)
        val text = read.warnings.joinToString("\n")
        assertTrue("1 Dateien im Archiv haben einen unzulässigen Pfad" in text, text)
        assertTrue("2 Dateien haben einen unzulässigen Namen" in text, text)

        val target = File(dir, "ziel")
        ImportArchive.copyAssets(file, read.assetFiles, target)
        assertEquals(listOf("gut.jpg"), target.list()!!.toList())
        assertEquals(setOf("boese.tagebuch", "ziel"), dir.list()!!.toSet())

        // Beim Schreiben werden unzulaessige Namen ebenfalls nicht angefasst.
        var asked = 0
        val out = ByteArrayOutputStream()
        val report = BackupFormat.write(out, read.diary) { asked++; File(target, "gut.jpg") }
        assertEquals(BackupFormat.WriteReport(assetsWritten = 1, assetsMissing = 2), report)
        assertEquals(1, asked)
    }

    @Test fun rejectsForeignAndNewerFiles() = withTempDir { dir ->
        val text = File(dir, "text.tagebuch").apply { writeText("kein zip") }
        val other = File(dir, "anderes.zip").also { zip(it, "backup.daylio" to "x") }
        val broken = File(dir, "kaputt.tagebuch").also { zip(it, "tagebuch.json" to "{kaputt") }
        val newer = File(dir, "neuer.tagebuch").also { zip(it, "tagebuch.json" to """{"formatVersion":99,"diary":{}}""") }
        assertFailsWith<ImportException> { BackupFormat.read(text) }
        assertTrue("tagebuch.json" in assertFailsWith<ImportException> { BackupFormat.read(other) }.message!!)
        assertTrue("beschädigt" in assertFailsWith<ImportException> { BackupFormat.read(broken) }.message!!)
        assertTrue("neueren Version" in assertFailsWith<ImportException> { BackupFormat.read(newer) }.message!!)
    }
}
