package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.text.NoteHtml
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Fehler, den die Oberflaeche dem Nutzer zeigen kann. Die Meldung ist deutsch und ohne Inhalte. */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Eine Datei im Archiv, die zu einem [de.mediclever.tagebuch.core.model.Asset] gehoert. */
data class AssetFile(
    val assetId: String,
    /** Name des Eintrags im ZIP, genau wie er dort steht (kann mit "/" beginnen). */
    val zipEntryName: String,
    /** Zieldateiname unter files/assets, gleich [de.mediclever.tagebuch.core.model.Asset.fileName]. */
    val fileName: String,
)

/** Zaehlwerte fuer die Vorschau vor dem Import und fuer den Abgleich mit den Pruefzahlen. */
data class ImportPreview(
    val moods: Int,
    val groups: Int,
    val tags: Int,
    val archivedTags: Int,
    val scales: Int,
    val entries: Int,
    /** Fuenf Werte, Stufe 1 (beste) zuerst. */
    val entriesPerLevel: List<Int>,
    val entriesWithNote: Int,
    val favorites: Int,
    val assets: Int,
    /** Zuordnungen von Dateien zu Eintraegen. */
    val assetLinks: Int,
    val goals: Int,
    val goalChecks: Int,
    val importantDays: Int,
    val templates: Int,
    val reminders: Int,
    /** Lokales Datum JJJJ-MM-TT des fruehesten und spaetesten Eintrags. */
    val earliestDate: String?,
    val latestDate: String?,
) {
    companion object {
        /** Zaehlt alles, was nicht als geloescht markiert ist. */
        fun of(diary: Diary): ImportPreview {
            val entries = diary.entries.filter { !it.deleted }
            val tags = diary.tags.filter { !it.deleted }
            val perLevel = IntArray(5)
            for (entry in entries) {
                val level = diary.levelOf(entry) ?: continue
                if (level in 1..5) perLevel[level - 1]++
            }
            return ImportPreview(
                moods = diary.moods.count { !it.deleted },
                groups = diary.groups.count { !it.deleted },
                tags = tags.size,
                archivedTags = tags.count { it.archivedAt != null },
                scales = diary.scales.count { !it.deleted },
                entries = entries.size,
                entriesPerLevel = perLevel.toList(),
                entriesWithNote = entries.count { NoteHtml.toPlainText(it.note).isNotEmpty() },
                favorites = entries.count { it.favorite },
                assets = diary.assets.count { !it.deleted },
                assetLinks = entries.sumOf { it.assetIds.size },
                goals = diary.goals.count { !it.deleted },
                goalChecks = diary.goalChecks.count { !it.deleted },
                importantDays = diary.importantDays.count { !it.deleted },
                templates = diary.templates.count { !it.deleted },
                reminders = diary.reminders.count { !it.deleted },
                earliestDate = entries.minOfOrNull { it.localDate },
                latestDate = entries.maxOfOrNull { it.localDate },
            )
        }
    }
}

/**
 * Ergebnis des Lesens einer Datei, egal ob Daylio-Backup oder eigene Sicherung.
 * Noch nichts ist gespeichert: die App zeigt [preview], fuehrt dann [DiaryMerge.merge]
 * aus und kopiert die [assetFiles] mit [ImportArchive.copyAsset].
 */
data class ImportResult(
    val diary: Diary,
    val assetFiles: List<AssetFile>,
    val preview: ImportPreview,
    /** Deutsche Hinweise ohne Tagebuch-Inhalte: nur Anzahlen und Feldnamen. */
    val warnings: List<String>,
)

typealias DaylioImport = ImportResult

/** Hilfen rund um das ZIP-Archiv: sichere Pfade, Dateityp am Inhalt, Kopieren ohne Zwischenpuffer im Speicher. */
object ImportArchive {

    /**
     * Normalisiert einen Eintragsnamen: Schraegstriche vereinheitlicht, fuehrende entfernt.
     * Liefert null, wenn der Pfad aus dem Zielordner ausbrechen wuerde ("..", Laufwerke) oder leer ist.
     */
    fun safePath(entryName: String): String? {
        val parts = entryName.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." || ':' in it || '\u0000' in it }) return null
        return parts.joinToString("/")
    }

    /** Ein Dateiname ohne Pfadanteile, der gefahrlos in einem Ordner angelegt werden kann. */
    fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' }

    /**
     * Dateiendung anhand der ersten Bytes (mindestens 12 uebergeben).
     * Die Pruefsumme im Daylio-Archiv ist nur ein Name und sagt nichts ueber den Typ.
     */
    fun detectExtension(head: ByteArray): String {
        fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "jpg"
            at(0) == 0x89 && ascii(1, "PNG") -> "png"
            ascii(0, "GIF8") -> "gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "webp"
            ascii(0, "RIFF") && ascii(8, "WAVE") -> "wav"
            ascii(4, "ftyp") -> when {
                ascii(8, "heic") || ascii(8, "heix") || ascii(8, "hevc") || ascii(8, "mif1") || ascii(8, "msf1") -> "heic"
                ascii(8, "avif") -> "avif"
                ascii(8, "3gp") -> "3gp"
                else -> "m4a"
            }
            ascii(0, "OggS") -> "ogg"
            ascii(0, "ID3") || (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) -> "mp3"
            ascii(0, "#!AMR") -> "amr"
            ascii(0, "BM") -> "bmp"
            else -> "bin"
        }
    }

    fun isAudioExtension(extension: String): Boolean = extension in setOf("wav", "3gp", "m4a", "ogg", "mp3", "amr")

    /**
     * Kopiert eine Datei aus dem Archiv nach [targetDir]/[AssetFile.fileName] und liefert die Zieldatei.
     * Geschrieben wird erst in eine Nebendatei und dann umbenannt, damit ein abgebrochener
     * Lauf keine halben Dateien hinterlaesst. Eine schon vorhandene Datei gleicher Groesse bleibt stehen.
     */
    fun copyAsset(zip: ZipFile, asset: AssetFile, targetDir: File): File {
        if (!isSafeFileName(asset.fileName)) throw ImportException("Ungültiger Dateiname in der Sicherung.")
        val entry = zip.getEntry(asset.zipEntryName) ?: throw ImportException("Eine Datei fehlt im Archiv.")
        if (entry.isDirectory) throw ImportException("Eine Datei fehlt im Archiv.")
        val target = File(targetDir, asset.fileName)
        if (target.isFile && entry.size >= 0 && target.length() == entry.size) return target
        zip.getInputStream(entry).use { copyTo(it, target) }
        return target
    }

    /** Wie oben, oeffnet das Archiv selbst. Fuer viele Dateien besser einmal oeffnen und [copyAsset] mit ZipFile nutzen. */
    fun copyAsset(zipFile: File, asset: AssetFile, targetDir: File): File =
        ZipFile(zipFile).use { copyAsset(it, asset, targetDir) }

    /**
     * Kopiert alle Dateien. Fehler einzelner Dateien brechen nicht ab, sondern werden gezaehlt.
     * [onProgress] bekommt (erledigt, gesamt) nach jeder Datei.
     */
    fun copyAssets(
        zipFile: File,
        assets: List<AssetFile>,
        targetDir: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): CopyReport {
        var copied = 0
        var failed = 0
        ZipFile(zipFile).use { zip ->
            assets.forEachIndexed { index, asset ->
                try {
                    copyAsset(zip, asset, targetDir)
                    copied++
                } catch (e: Exception) {
                    failed++
                }
                onProgress(index + 1, assets.size)
            }
        }
        return CopyReport(copied, failed)
    }

    /**
     * Kopiert einen Eintrag, dessen Typ noch unbekannt ist: die Endung wird am Inhalt erkannt,
     * die Zieldatei heisst [baseName].<endung>.
     */
    fun copyDetectingType(zipFile: File, zipEntryName: String, targetDir: File, baseName: String): File {
        if (!isSafeFileName(baseName)) throw ImportException("Ungültiger Dateiname in der Sicherung.")
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(zipEntryName) ?: throw ImportException("Eine Datei fehlt im Archiv.")
            val extension = detectExtension(readHead(zip, entry))
            return copyAsset(zip, AssetFile(baseName, zipEntryName, "$baseName.$extension"), targetDir)
        }
    }

    internal fun readHead(zip: ZipFile, entry: ZipEntry, bytes: Int = 16): ByteArray =
        zip.getInputStream(entry).use { it.readNBytes(bytes) }

    private fun copyTo(input: InputStream, target: File) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".teil")
        try {
            part.outputStream().use { input.copyTo(it, 64 * 1024) }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw ImportException("Eine Datei konnte nicht gespeichert werden.")
        } finally {
            part.delete()
        }
    }
}

data class CopyReport(val copied: Int, val failed: Int)
