package app.fotosortierer.core

import java.time.LocalDateTime

/** Woher das Aufnahmedatum stammt. [reliable] = false bedeutet: nur geraten. */
enum class DateSource(val label: String, val reliable: Boolean) {
    EXIF("EXIF-Aufnahmedatum", true),
    VIDEO("Video-Metadaten", true),
    FILENAME("Dateiname", true),
    MODIFIED("Änderungsdatum der Datei (unsicher)", false),
}

data class MediaItem(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isVideo: Boolean,
    val taken: LocalDateTime,
    val source: DateSource,
) {
    val name: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/')
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

object MediaTypes {
    val PHOTO = setOf("jpg", "jpeg", "heic", "heif", "png", "webp", "dng")
    val VIDEO = setOf("mp4", "mov", "3gp", "mkv", "webm", "m4v")

    fun isPhoto(ext: String) = ext.lowercase() in PHOTO
    fun isVideo(ext: String) = ext.lowercase() in VIDEO
    fun isMedia(ext: String) = isPhoto(ext) || isVideo(ext)
}
