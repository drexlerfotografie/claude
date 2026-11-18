package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.Diary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Eigenes Sicherungsformat: ein ZIP mit tagebuch.json (der ganze Datenbestand samt
 * Formatversion) und den Dateien unter assets/<fileName>.
 *
 * Lesen liefert dieselbe Form wie der Daylio-Import ([ImportResult]), damit die App
 * beide Wege gleich behandelt: Vorschau zeigen, [DiaryMerge.merge], Dateien kopieren.
 */
object BackupFormat {

    const val FORMAT_VERSION = 1
    const val JSON_NAME = "tagebuch.json"
    const val ASSET_DIR = "assets/"
    /** Vorschlag fuer die Dateiendung der Sicherung. */
    const val FILE_EXTENSION = "tagebuch"

    @Serializable
    data class Document(
        val formatVersion: Int = FORMAT_VERSION,
        val app: String = "mediclever-tagebuch",
        /** Millisekunden UTC, wann die Sicherung geschrieben wurde. */
        val createdAt: Long = 0,
        val diary: Diary = Diary(),
    )

    data class WriteReport(val assetsWritten: Int, val assetsMissing: Int)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Schreibt die Sicherung nach [out] (wird nicht geschlossen). [assetFile] liefert zu
     * einem Asset die Datei auf dem Geraet oder null, wenn sie fehlt; fehlende Dateien
     * werden gezaehlt, die Angaben zum Asset bleiben trotzdem in der Sicherung.
     */
    fun write(
        out: OutputStream,
        diary: Diary,
        createdAt: Long = System.currentTimeMillis(),
        assetFile: (Asset) -> File?,
    ): WriteReport {
        var written = 0
        var missing = 0
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(JSON_NAME))
        zip.write(json.encodeToString(Document.serializer(), Document(createdAt = createdAt, diary = diary)).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        val done = HashSet<String>()
        for (asset in diary.assets) {
            if (asset.deleted) continue
            val file = if (ImportArchive.isSafeFileName(asset.fileName)) assetFile(asset)?.takeIf { it.isFile } else null
            if (file == null) { missing++; continue }
            if (!done.add(asset.fileName)) continue
            zip.putNextEntry(ZipEntry(ASSET_DIR + asset.fileName))
            file.inputStream().use { it.copyTo(zip, 64 * 1024) }
            zip.closeEntry()
            written++
        }
        zip.finish()
        zip.flush()
        return WriteReport(written, missing)
    }

    /**
     * Liest eine Sicherung. Dateien werden nicht geladen, nur zugeordnet.
     * Alle Zeilen kommen als "noch nicht hochgeladen" zurueck, damit eine Wiederherstellung
     * auch ein leeres Konto fuellt.
     * @throws ImportException wenn die Datei keine lesbare Sicherung ist.
     */
    fun read(zipFile: File): ImportResult {
        val zip = try {
            ZipFile(zipFile)
        } catch (e: ZipException) {
            throw ImportException("Die Datei ist keine Tagebuch-Sicherung (kein ZIP-Archiv).", e)
        } catch (e: java.io.IOException) {
            throw ImportException("Die Datei konnte nicht gelesen werden.", e)
        }
        zip.use {
            // Namen im Archiv absichern: kein Eintrag darf aus dem Zielordner zeigen.
            val safeNames = HashMap<String, String>() // sicherer Pfad -> Originalname
            var unsafe = 0
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val safe = ImportArchive.safePath(entry.name)
                if (safe == null) unsafe++ else safeNames.putIfAbsent(safe, entry.name)
            }
            val jsonEntry = safeNames[JSON_NAME]?.let { zip.getEntry(it) }
                ?: throw ImportException("In der Datei fehlt $JSON_NAME. Ist es wirklich eine Tagebuch-Sicherung?")
            val document = try {
                json.decodeFromString(Document.serializer(), zip.getInputStream(jsonEntry).use { it.readBytes() }.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                throw ImportException("Die Sicherung ist beschädigt und kann nicht gelesen werden.", e)
            }
            if (document.formatVersion > FORMAT_VERSION) {
                throw ImportException("Die Sicherung stammt aus einer neueren Version der App. Bitte aktualisiere die App.")
            }

            val warnings = ArrayList<String>()
            if (unsafe > 0) warnings.add("$unsafe Dateien im Archiv haben einen unzulässigen Pfad und wurden nicht übernommen.")
            val diary = markDirty(document.diary)
            val files = ArrayList<AssetFile>()
            var missing = 0
            var badName = 0
            for (asset in diary.assets) {
                if (asset.deleted) continue
                if (!ImportArchive.isSafeFileName(asset.fileName)) { badName++; continue }
                val name = safeNames[ASSET_DIR + asset.fileName]
                if (name == null) missing++ else files.add(AssetFile(asset.id, name, asset.fileName))
            }
            if (badName > 0) warnings.add("$badName Dateien haben einen unzulässigen Namen und wurden nicht übernommen.")
            if (missing > 0) warnings.add("$missing Dateien stehen in der Sicherung, fehlen aber im Archiv.")
            return ImportResult(diary, files, ImportPreview.of(diary), warnings)
        }
    }

    private fun markDirty(d: Diary): Diary = d.copy(
        moods = d.moods.map { it.copy(dirty = true) },
        groups = d.groups.map { it.copy(dirty = true) },
        tags = d.tags.map { it.copy(dirty = true) },
        scales = d.scales.map { it.copy(dirty = true) },
        entries = d.entries.map { it.copy(dirty = true) },
        assets = d.assets.map { it.copy(dirty = true) },
        goals = d.goals.map { it.copy(dirty = true) },
        goalChecks = d.goalChecks.map { it.copy(dirty = true) },
        importantDays = d.importantDays.map { it.copy(dirty = true) },
        templates = d.templates.map { it.copy(dirty = true) },
        reminders = d.reminders.map { it.copy(dirty = true) },
    )
}
