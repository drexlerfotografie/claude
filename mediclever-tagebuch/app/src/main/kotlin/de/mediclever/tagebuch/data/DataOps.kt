package de.mediclever.tagebuch.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import de.mediclever.tagebuch.core.importer.*
import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.text.NoteHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Eine zum Import geoeffnete Datei mit Vorschau. */
sealed interface Opened {
    val file: File
    data class Archive(override val file: File, val kind: String, val result: ImportResult) : Opened
    data class Csv(override val file: File, val import: CsvFormat.CsvImport) : Opened
}

data class ImportOutcome(val report: MergeReport, val filesCopied: Int, val filesFailed: Int)

/** Sichern, Wiederherstellen, Import und Export. Arbeitet auf Dateien, die Oberflaeche liefert nur die Ziele. */
class DataOps(private val context: Context, private val repo: DiaryRepository) {

    private fun live(d: Diary) = d.copy(
        moods = d.moods.filter { !it.deleted }, groups = d.groups.filter { !it.deleted }, tags = d.tags.filter { !it.deleted },
        scales = d.scales.filter { !it.deleted }, entries = d.entries.filter { !it.deleted }.sortedByDescending { it.timestamp }, assets = d.assets.filter { !it.deleted },
        goals = d.goals.filter { !it.deleted }, goalChecks = d.goalChecks.filter { !it.deleted }, importantDays = d.importantDays.filter { !it.deleted },
        templates = d.templates.filter { !it.deleted }, reminders = d.reminders.filter { !it.deleted },
    )

    // ---- Export ------------------------------------------------------------------------

    suspend fun writeBackup(target: Uri): BackupFormat.WriteReport = withContext(Dispatchers.IO) {
        val d = live(repo.snapshot())
        context.contentResolver.openOutputStream(target)!!.use { out -> BackupFormat.write(out, d) { a -> repo.assets.file(a).takeIf { it.isFile } } }
    }

    suspend fun writeCsv(out: OutputStream) = withContext(Dispatchers.IO) {
        out.write(CsvFormat.export(live(repo.snapshot()), withBom = true).toByteArray())
    }

    suspend fun writePhotos(target: Uri): Int = withContext(Dispatchers.IO) {
        val d = live(repo.snapshot())
        val dateOf = d.entries.flatMap { e -> e.assetIds.map { it to e.localDate } }.toMap()
        var n = 0
        ZipOutputStream(context.contentResolver.openOutputStream(target)!!).use { zip ->
            for (a in d.assets.filter { it.type == AssetType.PHOTO }) {
                val f = repo.assets.file(a).takeIf { it.isFile } ?: continue
                zip.putNextEntry(ZipEntry("${dateOf[a.id] ?: "ohne-datum"}_${a.id.take(8)}.${f.extension.ifEmpty { "jpg" }}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry(); n++
            }
        }
        n
    }

    /** Alle Eintraege als PDF im Format A4, neueste zuerst. */
    suspend fun writePdf(out: OutputStream, from: LocalDate? = null, to: LocalDate? = null): Int = withContext(Dispatchers.IO) {
        val d = live(repo.snapshot())
        val entries = d.entries.filter { (from == null || it.localDate >= from.toString()) && (to == null || it.localDate <= to.toString()) }
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 48f; val width = (w - 2 * margin).toInt()
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = 0xFF11202C.toInt() }
        val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF66717B.toInt() }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF2D323C.toInt() }
        val line = Paint().apply { color = 0xFFDDE1E5.toInt(); strokeWidth = 1f }
        val fmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            y = margin
            page!!.canvas.drawText("MediClever Tagebuch · Seite $pageNo", margin, h - 24f, meta)
        }
        fun block(text: String, paint: TextPaint, gap: Float) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setLineSpacing(2f, 1f).build()
            var first = 0
            // lange Notizen ueber Seiten umbrechen
            while (first < layout.lineCount) {
                var last = first
                while (last < layout.lineCount && y + (layout.getLineBottom(last) - layout.getLineTop(first)) < h - margin - 16) last++
                if (last == first) { newPage(); continue }
                val c = page!!.canvas
                c.save(); c.translate(margin, y - layout.getLineTop(first)); c.clipRect(0, layout.getLineTop(first), width, layout.getLineBottom(last - 1)); layout.draw(c); c.restore()
                y += layout.getLineBottom(last - 1) - layout.getLineTop(first)
                first = last
                if (first < layout.lineCount) newPage()
            }
            y += gap
        }
        newPage()
        for (e in entries) {
            if (y > h - margin - 90) newPage()
            val mood = d.moodById[e.moodId]
            block(LocalDate.parse(e.localDate).format(fmt) + " · %d:%02d Uhr".format(e.minuteOfDay / 60, e.minuteOfDay % 60), head, 2f)
            block("Stimmung: ${mood?.name ?: "unbekannt"}" + "", meta, 2f)
            block(e.tagIds.mapNotNull { d.tagById[it]?.name }.joinToString(", "), meta, 2f)
            block(e.scaleValues.mapNotNull { v -> d.scaleById[v.scaleId]?.let { s -> s.name + ": " + when (s.type) {
                ScaleType.TEXT -> s.points.firstOrNull { it.id == v.pointId }?.label ?: "–"
                ScaleType.NUMBER -> v.number?.toString() ?: "–"
                ScaleType.DURATION -> v.minutes?.let { "%d:%02d Std.".format(it / 60, it % 60) } ?: "–"
            } } }.joinToString(" · "), meta, 4f)
            if (e.title.isNotBlank()) block(e.title, head, 2f)
            block(NoteHtml.toPlainText(e.note), body, 4f)
            page!!.canvas.drawLine(margin, y + 4, w - margin, y + 4, line)
            y += 16f
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out); doc.close()
        entries.size
    }

    // ---- Import ------------------------------------------------------------------------

    /** Kopiert die gewaehlte Datei in den Cache und liest sie. Erkennt eigene Sicherung, Daylio-Backup und CSV am Inhalt. */
    suspend fun open(source: Uri): Opened = withContext(Dispatchers.IO) {
        val file = File(File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }, "datei")
        context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: throw ImportException("Die Datei konnte nicht geöffnet werden.")
        val isZip = file.inputStream().use { s -> val b = ByteArray(2); s.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
        if (!isZip) {
            // Auch die einzelne Datei "backup.daylio" aus dem Archiv wird angenommen (Base64 oder JSON), dann ohne Fotos
            val head = file.inputStream().use { s -> ByteArray(64).let { b -> String(b, 0, s.read(b).coerceAtLeast(0)) } }.trimStart()
            val looksLikeCsv = head.contains(',') || head.contains(';')
            if (!looksLikeCsv || head.startsWith("{")) {
                val result = runCatching { DaylioBackup.fromJson(DaylioBackup.decodeBackupText(file.readBytes())) }.getOrNull()
                if (result != null && result.preview.entries + result.preview.moods > 0) return@withContext Opened.Archive(file, "Backup im Daylio-Format (nur Daten, ohne Fotos)", result.copy(assetFiles = emptyList()))
            }
            return@withContext Opened.Csv(file, CsvFormat.parse(file.readText(), live(repo.snapshot())))
        }
        val names = runCatching { ZipFile(file).use { z -> z.entries().asSequence().map { it.name.trimStart('/') }.toList() } }.getOrElse { throw ImportException("Die Datei ist beschädigt oder kein gültiges Archiv.") }
        when {
            BackupFormat.JSON_NAME in names -> Opened.Archive(file, "Sicherung von MediClever Tagebuch", BackupFormat.read(file))
            names.any { it.endsWith("backup.daylio") } -> Opened.Archive(file, "Backup im Daylio-Format", DaylioBackup.read(file))
            else -> throw ImportException("In dieser Datei wurde kein Tagebuch gefunden.")
        }
    }

    /** Strukturbericht eines Daylio-Backups: Feldnamen und Zahlen, keine Texte. Fuer die Fehlersuche beim Import. */
    suspend fun structureReport(opened: Opened.Archive): File = withContext(Dispatchers.IO) {
        val json = ZipFile(opened.file).use { z ->
            val entry = z.entries().asSequence().firstOrNull { it.name.endsWith("backup.daylio") } ?: throw ImportException("Für diese Datei gibt es keinen Strukturbericht.")
            DaylioBackup.decodeBackupText(z.getInputStream(entry).readBytes())
        }
        val text = DaylioDiagnostics.describe(json) + "\nHinweise des Imports:\n" + opened.result.warnings.joinToString("\n") { "- $it" }
        File(File(context.cacheDir, "share").apply { mkdirs() }, "strukturbericht.txt").apply { writeText(text) }
    }

    /**
     * replace = true leert das Tagebuch vorher. Das ist der Weg, um einen frueheren, fehlerhaften
     * Import (z. B. aus einer CSV-Datei) durch das vollstaendige Backup zu ersetzen.
     */
    suspend fun importArchive(opened: Opened.Archive, mode: MergeMode, replace: Boolean = false, onProgress: (Int, Int) -> Unit): ImportOutcome = withContext(Dispatchers.IO) {
        if (replace) repo.clearDiary()
        val existing = repo.snapshot()
        val merged = DiaryMerge.merge(existing, opened.result.diary, mode)
        saveChanged(existing, merged.diary, withSettings = mode == MergeMode.EMPTY_ONLY)
        // Nur Dateien kopieren, deren Datensatz uebernommen wurde und die noch fehlen
        val wanted = merged.diary.assets.filter { !it.deleted }.associateBy { it.id }
        val todo = opened.result.assetFiles.filter { f -> wanted[f.assetId]?.let { !repo.assets.exists(it) } == true }
        val copy = if (todo.isEmpty()) CopyReport(0, 0) else ImportArchive.copyAssets(opened.file, todo, repo.assets.file(Asset(fileName = "x")).parentFile!!, onProgress)
        opened.file.delete()
        ImportOutcome(merged.report, copy.copied, copy.failed)
    }

    /** moodLevels: Stufe 1 bis 5 je unbekannter Stimmung, vom Nutzer bestaetigt. */
    suspend fun importCsv(opened: Opened.Csv, moodLevels: Map<String, Int> = emptyMap()): ImportOutcome = withContext(Dispatchers.IO) {
        val existing = repo.snapshot()
        val incoming = CsvFormat.resolve(opened.import, live(existing), moodLevels)
        val merged = DiaryMerge.merge(existing, incoming, MergeMode.MERGE)
        saveChanged(existing, merged.diary, withSettings = false)
        opened.file.delete()
        ImportOutcome(merged.report, 0, 0)
    }

    /** Schreibt nur Zeilen, die neu sind oder sich geaendert haben. */
    private suspend fun saveChanged(old: Diary, new: Diary, withSettings: Boolean) {
        fun <T : SyncRow> diff(a: List<T>, b: List<T>): List<T> { val have = a.associateBy { it.id }; return b.filter { have[it.id] != it } }
        repo.save(diff(old.moods, new.moods) + diff(old.groups, new.groups) + diff(old.tags, new.tags) + diff(old.scales, new.scales) +
            diff(old.entries, new.entries) + diff(old.assets, new.assets) + diff(old.goals, new.goals) + diff(old.goalChecks, new.goalChecks) +
            diff(old.importantDays, new.importantDays) + diff(old.templates, new.templates) + diff(old.reminders, new.reminders))
        if (withSettings && new.settings != old.settings) repo.updateSettings { new.settings.copy(themeMode = it.themeMode, fontChoice = it.fontChoice) }
    }
}
