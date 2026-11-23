package app.fotosortierer.core

import java.time.Duration
import java.time.LocalDateTime

/** Zusammenhängende Aufnahmen. [id] ist stabil, solange die erste Datei gleich bleibt. */
data class Event(val id: String, val items: List<MediaItem>) {
    val start: LocalDateTime get() = items.first().taken
    val end: LocalDateTime get() = items.last().taken
}

object Events {
    /**
     * Gruppiert chronologisch: Eine Pause von mehr als [gapHours] Stunden beginnt ein neues Ereignis.
     * Dateien nur mit Datum (z. B. WhatsApp, 00:00 Uhr) bilden je Tag ein eigenes Ereignis.
     */
    fun group(items: List<MediaItem>, gapHours: Int): List<Event> {
        val sorted = items.sortedWith(compareBy({ it.taken }, { it.name }))
        val result = ArrayList<Event>()
        var current = ArrayList<MediaItem>()
        val gap = Duration.ofHours(gapHours.toLong())
        for (item in sorted) {
            val last = current.lastOrNull()
            val split = last != null && (
                Duration.between(last.taken, item.taken) > gap ||
                    dateOnly(last) != dateOnly(item) ||
                    (dateOnly(item) && last.taken.toLocalDate() != item.taken.toLocalDate())
                )
            if (split) {
                result += Event(current.first().path, current)
                current = ArrayList()
            }
            current += item
        }
        if (current.isNotEmpty()) result += Event(current.first().path, current)
        return result
    }

    private fun dateOnly(item: MediaItem) =
        item.source == DateSource.FILENAME && item.taken.toLocalTime().toSecondOfDay() == 0
}
