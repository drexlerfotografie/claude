package de.mediclever.tagebuch.core.shift

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.stats.DateRange
import java.time.LocalDate

/** Durchschnittlicher Tageswert (1 bis 5, 5 ist der beste) der Eintraege mit einer Dienstart. */
data class ShiftAverage(val tag: Tag, val average: Double, val entries: Int, val free: Boolean)

/**
 * Verlauf ueber einen Dienstblock: mehrere Dienste derselben Art an aufeinanderfolgenden Tagen,
 * danach die freien Tage. during[i] ist der Schnitt des (i+1)-ten Diensttages ueber alle Bloecke,
 * after[j] der des (j+1)-ten freien Tages danach.
 */
data class BlockCourse(val tag: Tag, val blocks: Int, val during: List<Double>, val after: List<Double>) {
    val enough: Boolean get() = blocks >= MIN_BLOCKS && during.size >= 2
    /** Wie stark der Wert im Block absackt: erster Diensttag minus tiefster Punkt. */
    val drop: Double get() = if (during.isEmpty()) 0.0 else during.first() - (during + after).min()
    companion object { const val MIN_BLOCKS = 3 }
}

object ShiftStats {
    private class Day(val shift: Tag?, val score: Double?)

    private fun days(d: Diary, range: DateRange): Map<LocalDate, Day> {
        val from = range.start.toString(); val to = range.endInclusive.toString()
        return d.entries.filter { !it.deleted && it.localDate >= from && it.localDate <= to }.groupBy { it.localDate }.map { (date, list) ->
            val shift = list.sortedBy { it.minuteOfDay }.firstNotNullOfOrNull { Shifts.of(d, it) }
            val scores = list.mapNotNull { e -> d.levelOf(e)?.let { 6.0 - it } }
            LocalDate.parse(date) to Day(shift, scores.takeIf { it.isNotEmpty() }?.average())
        }.toMap()
    }

    /** Schnitt je Dienstart im Zeitraum, bester zuerst. Nur Dienstarten mit mindestens einem Eintrag. */
    fun averages(d: Diary, range: DateRange): List<ShiftAverage> {
        val from = range.start.toString(); val to = range.endInclusive.toString()
        val byTag = HashMap<String, MutableList<Double>>()
        for (e in d.entries) {
            if (e.deleted || e.localDate < from || e.localDate > to) continue
            val tag = Shifts.of(d, e) ?: continue
            val level = d.levelOf(e) ?: continue
            byTag.getOrPut(tag.id) { mutableListOf() } += 6.0 - level
        }
        return byTag.mapNotNull { (id, v) -> d.tagById[id]?.let { ShiftAverage(it, v.average(), v.size, Shifts.isFree(d, it)) } }
            .sortedWith(compareByDescending<ShiftAverage> { it.average }.thenBy { it.tag.sortOrder })
    }

    fun blockCourse(d: Diary, tag: Tag, range: DateRange): BlockCourse {
        val days = days(d, range)
        val during = List(MAX_DURING) { mutableListOf<Double>() }
        val after = List(MAX_AFTER) { mutableListOf<Double>() }
        var blocks = 0
        var day = range.start
        while (!day.isAfter(range.endInclusive)) {
            if (days[day]?.shift?.id != tag.id) { day = day.plusDays(1); continue }
            var end = day
            while (days[end.plusDays(1)]?.shift?.id == tag.id) end = end.plusDays(1)
            val length = (end.toEpochDay() - day.toEpochDay()).toInt() + 1
            if (length >= 2) {
                blocks++
                for (i in 0 until minOf(length, MAX_DURING)) days[day.plusDays(i.toLong())]?.score?.let { during[i] += it }
                for (j in 0 until MAX_AFTER) {
                    val next = days[end.plusDays(j + 1L)] ?: continue
                    // nur freie Tage zaehlen; der naechste Dienst beendet die Erholung
                    if (next.shift != null && !Shifts.isFree(d, next.shift)) break
                    next.score?.let { after[j] += it }
                }
            }
            day = end.plusDays(1)
        }
        // Positionen, die nur in wenigen Bloecken vorkommen, wuerden den Verlauf verzerren
        val need = maxOf(2, (blocks + 1) / 2)
        fun cut(lists: List<List<Double>>) = lists.takeWhile { it.size >= need }.map { it.average() }
        return BlockCourse(tag, blocks, cut(during), cut(after))
    }

    /** Die Dienstart, deren Block am staerksten absackt; ohne ausreichende Daten die mit den meisten Bloecken. */
    fun strongestBlock(d: Diary, range: DateRange): BlockCourse? {
        val all = Shifts.all(d).filter { !Shifts.isFree(d, it) }.map { blockCourse(d, it, range) }.filter { it.blocks > 0 }
        return all.filter { it.enough }.maxByOrNull { it.drop } ?: all.maxByOrNull { it.blocks }
    }

    const val MAX_DURING = 5
    const val MAX_AFTER = 2
}
