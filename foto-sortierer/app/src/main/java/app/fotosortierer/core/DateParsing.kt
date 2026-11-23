package app.fotosortierer.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val MIN_YEAR = 1980

/** EXIF-Datum im Format "2023:11:05 14:35:12". */
object ExifDate {
    private val re = Regex("""^\s*(\d{4})[:\-](\d{2})[:\-](\d{2})[ T](\d{2}):(\d{2}):(\d{2})""")

    fun parse(value: String?): LocalDateTime? {
        val m = re.find(value ?: return null) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        if (v[0] < MIN_YEAR) return null
        return runCatching { LocalDateTime.of(v[0], v[1], v[2], v[3], v[4], v[5]) }.getOrNull()
    }
}

/** Video-Datum von MediaMetadataRetriever, z. B. "20231105T143512.000Z" (UTC). */
object VideoDate {
    private val re = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(?:\.\d+)?(Z)?""")

    fun parse(value: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        val m = re.find(value?.trim() ?: return null) ?: return null
        val v = m.groupValues.subList(1, 7).map { it.toInt() }
        if (v[0] < MIN_YEAR) return null // viele Handys schreiben 1904-01-01 bei fehlendem Datum
        val dt = runCatching { LocalDateTime.of(v[0], v[1], v[2], v[3], v[4], v[5]) }.getOrNull() ?: return null
        return if (m.groupValues[7] == "Z") {
            dt.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
        } else dt
    }
}

/**
 * Datum aus typischen Dateinamen:
 * IMG_20231105_143512.jpg, PXL_20231105_143512345.jpg, VID_20231105_143512.mp4,
 * IMG-20231105-WA0001.jpg (WhatsApp, nur Datum), Screenshot_2023-11-05-14-35-12.png,
 * 2023_11_05_Rom_001.jpg (bereits sortiert).
 */
object FilenameDate {
    private val re = Regex(
        """(?<!\d)(19[89]\d|20\d\d)[-_.]?(\d\d)[-_.]?(\d\d)(?:[-_ T.]?(\d\d)[-_.]?(\d\d)[-_.]?(\d\d))?"""
    )

    fun parse(name: String, today: LocalDate = LocalDate.now()): LocalDateTime? {
        for (m in re.findAll(name)) {
            val g = m.groupValues
            val date = runCatching { LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull() ?: continue
            if (date.isAfter(today.plusDays(1))) continue
            val time = if (g[4].isNotEmpty()) {
                runCatching { LocalTime.of(g[4].toInt(), g[5].toInt(), g[6].toInt()) }.getOrNull()
            } else null
            return date.atTime(time ?: LocalTime.MIDNIGHT)
        }
        return null
    }
}
