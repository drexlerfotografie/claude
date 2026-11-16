package de.mediclever.tagebuch.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import de.mediclever.tagebuch.core.model.Asset
import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Fotos und Sprachnotizen liegen im privaten App-Speicher unter files/assets/JJJJ/MM/.
 * Andere Apps und die Galerie sehen sie nicht.
 */
class AssetStore(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "assets"))

    fun file(asset: Asset): File = File(root, asset.fileName)
    fun exists(asset: Asset): Boolean = file(asset).isFile

    private fun newRelativeName(now: Long, ext: String): String {
        val d = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        return "%04d/%02d/%s.%s".format(d.year, d.monthValue, newId(), ext)
    }

    /** Legt eine leere Zieldatei an, z. B. fuer die Kamera oder die Tonaufnahme. */
    fun newFile(now: Long, ext: String): Pair<String, File> {
        val name = newRelativeName(now, ext)
        val f = File(root, name)
        f.parentFile?.mkdirs()
        return name to f
    }

    /**
     * Uebernimmt ein Bild. Es wird neu als JPEG gespeichert, dabei gedreht wie aufgenommen
     * und ohne Aufnahmeort und Geraetedaten (EXIF faellt beim Neu-Kodieren weg).
     */
    suspend fun importPhoto(context: Context, source: Uri, now: Long): Asset? = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(source)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext null
        importPhotoBytes(bytes, now)
    }

    fun importPhotoBytes(bytes: ByteArray, now: Long): Asset? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longEdge = maxOf(bmp.width, bmp.height)
        val scale = if (longEdge > MAX_EDGE) MAX_EDGE.toFloat() / longEdge else 1f
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val taken = runCatching { ExifInterface(bytes.inputStream()).dateTimeOriginal }.getOrNull() ?: now
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        m.postScale(scale, scale)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val (name, target) = newFile(now, "jpg")
        target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return Asset(type = AssetType.PHOTO, fileName = name, takenAt = taken, sizeBytes = target.length())
    }

    /** Macht aus einer bereits geschriebenen Datei (Kamera, Aufnahme, Import) einen Datensatz. */
    fun register(name: String, type: AssetType, takenAt: Long, durationMs: Long = 0, checksum: String = ""): Asset =
        Asset(type = type, fileName = name, takenAt = takenAt, sizeBytes = File(root, name).length(), durationMs = durationMs, checksum = checksum)

    fun delete(asset: Asset) { file(asset).delete() }
    fun wipe() { root.deleteRecursively() }
    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object { const val MAX_EDGE = 2560 }
}
