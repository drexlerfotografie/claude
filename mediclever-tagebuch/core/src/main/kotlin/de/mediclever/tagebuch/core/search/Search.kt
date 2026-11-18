package de.mediclever.tagebuch.core.search

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.stats.DiaryIndex
import de.mediclever.tagebuch.core.stats.plainText
import java.util.Locale

/**
 * Suchanfrage. Jedes gesetzte Kriterium muss zutreffen (UND zwischen den Kriterien):
 *  - [text]: der Text ohne Leerraum am Rand muss als Ganzes im Titel oder im reinen Text der Notiz
 *    (ohne HTML-Tags) vorkommen, ohne Unterscheidung von Gross- und Kleinschreibung. Leer = kein Kriterium.
 *  - Stimmung: die Stimmung des Eintrags ist in [moodIds] ODER seine Stufe ist in [levels].
 *    Beide leer = kein Kriterium.
 *  - Aktivitaet: der Eintrag enthaelt mindestens eine der [tagIds], mit [matchAllTags] alle.
 *    Leer = kein Kriterium.
 *  - [favoritesOnly]: nur als Favorit markierte Eintraege.
 */
data class SearchQuery(
    val text: String = "",
    val moodIds: Set<String> = emptySet(),
    val levels: Set<Int> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val matchAllTags: Boolean = false,
    val favoritesOnly: Boolean = false,
)

/**
 * Sucht in den nicht geloeschten Eintraegen nach [query] (Kriterien siehe [SearchQuery]).
 * Ergebnis von neu nach alt: nach Tag, dann Uhrzeit, dann Zeitstempel absteigend.
 * Eine leere Anfrage liefert alle Eintraege.
 */
fun searchEntries(diary: Diary, query: SearchQuery): List<Entry> {
    val idx = DiaryIndex.of(diary)
    val needle = query.text.trim().lowercase(Locale.ROOT)
    val byMood = query.moodIds.isNotEmpty() || query.levels.isNotEmpty()
    val out = ArrayList<Entry>()
    for (e in idx.entries.asReversed()) {
        if (query.favoritesOnly && !e.entry.favorite) continue
        if (byMood && !(e.entry.moodId in query.moodIds || (e.level != null && e.level in query.levels))) continue
        if (query.tagIds.isNotEmpty()) {
            val ok = if (query.matchAllTags) e.tagIds.containsAll(query.tagIds) else e.tagIds.any { it in query.tagIds }
            if (!ok) continue
        }
        if (needle.isNotEmpty() &&
            !e.entry.title.lowercase(Locale.ROOT).contains(needle) &&
            !plainText(e.entry.note).lowercase(Locale.ROOT).contains(needle)
        ) continue
        out += e.entry
    }
    return out
}
