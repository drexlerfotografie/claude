package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import java.time.LocalDate
import java.util.TreeMap
import java.util.TreeSet

/**
 * Ein gueltiger Eintrag mit vorberechneten Werten.
 * level ist null, wenn die Stimmung fehlt, geloescht ist oder keine Stufe 1..5 hat.
 * tagIds enthaelt nur vorhandene, nicht geloeschte Aktivitaeten, jede hoechstens einmal.
 * photoIds enthaelt nur vorhandene, nicht geloeschte Fotos in Anzeige-Reihenfolge.
 */
internal class IndexedEntry(
    val entry: Entry,
    val date: LocalDate,
    val level: Int?,
    val tagIds: List<String>,
    val photoIds: List<String>,
) {
    /** Punktwert 6 - Stufe (5 = beste Stimmung), null ohne bekannte Stufe. */
    val score: Int? = level?.let { 6 - it }
}

/**
 * Vorbereitete Sicht auf ein [Diary]: geloeschte Zeilen sind entfernt, Eintraege nach Tag sortiert.
 * Archivierte Stimmungen, Aktivitaeten und Skalen bleiben enthalten.
 * Eintraege mit ungueltigem localDate werden uebergangen.
 */
internal class DiaryIndex(val diary: Diary) {
    val moods: Map<String, Mood> = diary.moods.filter { !it.deleted }.associateBy { it.id }
    val tags: Map<String, Tag> = diary.tags.filter { !it.deleted }.associateBy { it.id }
    val groups: Map<String, TagGroup> = diary.groups.filter { !it.deleted }.associateBy { it.id }
    val scales: Map<String, Scale> = diary.scales.filter { !it.deleted }.associateBy { it.id }
    val photoIds: Set<String> =
        diary.assets.filter { !it.deleted && it.type == AssetType.PHOTO }.mapTo(HashSet()) { it.id }

    /** Aufsteigend nach Tag, Uhrzeit, Zeitstempel, id. */
    val entries: List<IndexedEntry>

    /** Eintraege je Tag, innerhalb des Tages aufsteigend nach Uhrzeit. */
    val byDate: TreeMap<LocalDate, List<IndexedEntry>>

    init {
        val list = ArrayList<IndexedEntry>(diary.entries.size)
        for (e in diary.entries) {
            if (e.deleted) continue
            val date = parseDateOrNull(e.localDate) ?: continue
            val level = moods[e.moodId]?.level?.takeIf { it in 1..5 }
            val tagIds = if (e.tagIds.isEmpty()) emptyList() else e.tagIds.filter { it in tags }.distinct()
            val photos = if (e.assetIds.isEmpty()) emptyList() else e.assetIds.filter { it in photoIds }.distinct()
            list.add(IndexedEntry(e, date, level, tagIds, photos))
        }
        list.sortWith(
            compareBy<IndexedEntry> { it.date }.thenBy { it.entry.minuteOfDay }
                .thenBy { it.entry.timestamp }.thenBy { it.entry.id },
        )
        entries = list
        val map = TreeMap<LocalDate, List<IndexedEntry>>()
        for (e in list) (map.getOrPut(e.date) { ArrayList<IndexedEntry>(2) } as MutableList<IndexedEntry>).add(e)
        byDate = map
    }

    /** Mittlerer Punktwert je Tag ueber die Eintraege mit bekannter Stufe; Tage ohne solche Eintraege fehlen. */
    val dailyScore: TreeMap<LocalDate, Double> by lazy {
        val m = TreeMap<LocalDate, Double>()
        for ((d, list) in byDate) averageScore(list)?.let { m[d] = it }
        m
    }

    /** Tage, an denen eine Aktivitaet in mindestens einem Eintrag vorkommt. */
    val datesByTag: Map<String, TreeSet<LocalDate>> by lazy {
        val m = HashMap<String, TreeSet<LocalDate>>()
        for (e in entries) for (t in e.tagIds) m.getOrPut(t) { TreeSet() }.add(e.date)
        m
    }

    fun entriesIn(range: DateRange): List<IndexedEntry> {
        val out = ArrayList<IndexedEntry>()
        for (l in byDate.subMap(range.start, true, range.endInclusive, true).values) out.addAll(l)
        return out
    }

    companion object {
        @Volatile private var last: DiaryIndex? = null

        /** Liefert den Index zu [diary]; fuer dasselbe Objekt wird er wiederverwendet. */
        fun of(diary: Diary): DiaryIndex {
            val l = last
            if (l != null && l.diary === diary) return l
            return DiaryIndex(diary).also { last = it }
        }
    }
}

/** Mittelwert der Punktwerte der Eintraege mit bekannter Stufe, null wenn es keine gibt. */
internal fun averageScore(entries: Collection<IndexedEntry>): Double? {
    var sum = 0
    var n = 0
    for (e in entries) {
        val s = e.score ?: continue
        sum += s
        n++
    }
    return if (n == 0) null else sum.toDouble() / n
}
