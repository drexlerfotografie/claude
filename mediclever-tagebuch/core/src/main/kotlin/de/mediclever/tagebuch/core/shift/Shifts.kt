package de.mediclever.tagebuch.core.shift

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.ShiftTime
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import java.time.LocalDate

/**
 * Dienstarten. Eine Dienstart ist eine Aktivitaet aus der Dienstgruppe; so laufen Abgleich,
 * Sicherung, Import und Farben ohne eigenes Datenformat mit. Welche Gruppe das ist, steht in
 * den Einstellungen. Zeiten und Kurznamen liegen als ShiftTime ebenfalls dort.
 */
object Shifts {
    private val groupNames = setOf("dienst", "dienstart", "dienstarten", "schicht", "schichten", "schichtdienst")

    data class Template(val name: String, val short: String, val icon: String, val colorIndex: Int, val start: Int?, val end: Int?, val free: Boolean = false)

    /** Vorlagen als Startpunkt. Jede laesst sich danach aendern. */
    val templates: Map<String, List<Template>> = mapOf(
        "12 Stunden" to listOf(
            Template("Tagschicht", "Tag", "wb_sunny", 3, 7 * 60, 19 * 60),
            Template("Nachtschicht", "Nacht", "nightlight", 9, 19 * 60, 7 * 60),
            Template("Zwischendienst", "Zwischen", "schedule", 11, 10 * 60, 18 * 60),
            Template("Frei", "Frei", "weekend", 4, null, null, free = true),
        ),
        "Drei Schichten" to listOf(
            Template("Frühdienst", "Früh", "wb_sunny", 3, 6 * 60, 14 * 60),
            Template("Spätdienst", "Spät", "schedule", 0, 14 * 60, 22 * 60),
            Template("Nachtdienst", "Nacht", "nightlight", 9, 22 * 60, 6 * 60),
            Template("Frei", "Frei", "weekend", 4, null, null, free = true),
        ),
        "24 Stunden" to listOf(
            Template("24-Stunden-Dienst", "24 h", "emergency", 0, 7 * 60, 7 * 60),
            Template("Bereitschaft", "Bereit", "access_time", 11, null, null),
            Template("Frei", "Frei", "weekend", 4, null, null, free = true),
        ),
    )

    fun group(d: Diary): TagGroup? =
        d.groupById[d.settings.shiftGroupId]?.takeIf { !it.deleted }
            ?: d.groups.firstOrNull { !it.deleted && it.archivedAt == null && it.name.trim().lowercase() in groupNames }

    /** Alle Dienstarten, auch ausgeschaltete (archivierte), in der Reihenfolge des Nutzers. */
    fun all(d: Diary): List<Tag> {
        val g = group(d) ?: return emptyList()
        return d.tags.filter { it.groupId == g.id && !it.deleted }.sortedBy { it.sortOrder }
    }

    fun active(d: Diary): List<Tag> = all(d).filter { it.archivedAt == null }

    fun isShift(d: Diary, tagId: String): Boolean = group(d)?.let { g -> d.tagById[tagId]?.groupId == g.id } == true

    /** Dienstart eines Eintrags: die erste gewaehlte Aktivitaet der Dienstgruppe. */
    fun of(d: Diary, e: Entry): Tag? {
        val g = group(d) ?: return null
        return e.tagIds.mapNotNull { d.tagById[it] }.filter { it.groupId == g.id }.minByOrNull { it.sortOrder }
    }

    /** Dienstart eines Tages: die des fruehesten Eintrags, der eine hat. */
    fun ofDay(d: Diary, date: LocalDate): Tag? =
        d.entries.filter { it.localDate == date.toString() && !it.deleted }.sortedBy { it.minuteOfDay }.firstNotNullOfOrNull { of(d, it) }

    fun time(d: Diary, tagId: String): ShiftTime? = d.settings.shiftTimes.firstOrNull { it.tagId == tagId }

    fun isFree(d: Diary, tag: Tag): Boolean = time(d, tag.id)?.free ?: tag.name.lowercase().let { it == "frei" || it.startsWith("frei ") || it == "urlaub" }

    /** Kurzname fuer Knoepfe und Marken: "Nachtschicht" wird "Nacht". */
    fun short(d: Diary, tag: Tag): String = time(d, tag.id)?.short?.takeIf { it.isNotBlank() } ?: shorten(tag.name)

    fun shorten(name: String): String {
        val n = name.trim()
        for (suffix in listOf("schicht", "dienst")) {
            if (n.lowercase().endsWith(suffix)) {
                val head = n.dropLast(suffix.length).trimEnd('-', ' ')
                if (head.length >= 3) return head
            }
        }
        return n
    }

    /** Entwurf eines Eintrags: genau eine Dienstart, erneutes Waehlen nimmt sie zurueck. */
    fun select(d: Diary, tagIds: List<String>, tagId: String): List<String> {
        val had = tagId in tagIds
        val without = tagIds.filterNot { isShift(d, it) }
        return if (had) without else without + tagId
    }
}
