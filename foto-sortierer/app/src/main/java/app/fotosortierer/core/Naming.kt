package app.fotosortierer.core

import java.time.LocalDate
import java.util.Locale

/** Namensschema: <Jahr>/<JJJJ>_<MM>_<TT>[_<Anlass>]_<NNN>.<ext> */
object Naming {
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val spaces = Regex("""\s+""")
    private val underscores = Regex("_+")

    /** Erkennt Dateien, die schon nach dem Schema benannt sind. */
    val sortedName = Regex("""^\d{4}_\d{2}_\d{2}_(?:.+_)?\d{3,}\.[A-Za-z0-9]+$""")

    fun sanitizeAnlass(raw: String): String =
        raw.trim()
            .replace(illegal, "")
            .replace(spaces, "_")
            .replace(underscores, "_")
            .trim('_', '.', ' ')
            .take(60)

    fun prefix(date: LocalDate, anlass: String): String {
        val base = String.format(Locale.ROOT, "%04d_%02d_%02d", date.year, date.monthValue, date.dayOfMonth)
        val clean = sanitizeAnlass(anlass)
        return if (clean.isEmpty()) "${base}_" else "${base}_${clean}_"
    }

    fun normalizedExt(ext: String): String = when (val e = ext.lowercase()) {
        "jpeg" -> "jpg"
        else -> e
    }

    fun fileName(prefix: String, number: Int, ext: String): String =
        prefix + number.toString().padStart(3, '0') + "." + ext

    /** Laufende Nummer, wenn [name] genau zu [prefix] passt, sonst null. */
    fun numberFor(prefix: String, name: String): Int? {
        if (!name.startsWith(prefix, ignoreCase = true)) return null
        val rest = name.substring(prefix.length).substringBeforeLast('.')
        return if (rest.length >= 3 && rest.all { it.isDigit() }) rest.toIntOrNull() else null
    }
}
