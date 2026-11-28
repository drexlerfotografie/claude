package de.mediclever.schichtclever.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.hourOfDay(): Int = Instant.ofEpochMilli(this).atZone(zone).hour
fun Long.minuteOfDay(): Int = Instant.ofEpochMilli(this).atZone(zone).let { it.hour * 60 + it.minute }
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun daysSince(time: Long, now: Long = System.currentTimeMillis()): Long =
    ChronoUnit.DAYS.between(time.toLocalDate(), now.toLocalDate())

enum class Period(val label: String, val days: Long) { WOCHE("Woche", 7), MONAT("Monat", 30), JAHR("Jahr", 365), ALLES("Alles", 36_500) }

data class Stats(
    val einsaetze: Int,
    val measures: Int,
    val shifts: Int,
    val hours: Double,
    val byHour: IntArray,
    val byCategory: List<Pair<Category, Int>>,
    val avgPerShiftType: List<Pair<String, Double>>,
    val topMeasures: List<Pair<String, Int>>,
    val peakHour: Int?,
    val nightStreak: Int,
)

fun AppData.stats(period: Period, now: Long = System.currentTimeMillis()): Stats {
    val from = now - period.days * 86_400_000L
    val es = einsaetze.filter { it.alarmAt >= from }
    val ss = shifts.filter { it.startAt >= from }
    val byHour = IntArray(24).also { arr -> es.forEach { arr[it.alarmAt.hourOfDay()]++ } }
    val byCat = es.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }
    val avg = ss.groupBy { it.type.code }.map { (type, list) ->
        val count = list.sumOf { s -> es.count { it.shiftId == s.id } }
        type to count.toDouble() / list.size
    }.sortedByDescending { it.second }
    val top = es.flatMap { it.measures }.groupingBy { it.skillId }.eachCount().toList().sortedByDescending { it.second }.take(5)
    val hours = ss.sumOf { ((it.endAt ?: now) - it.startAt) / 3_600_000.0 }
    // Wie viele Nachtschichten in Folge zuletzt (ohne Tagschicht dazwischen)?
    val streak = shifts.sortedByDescending { it.startAt }.takeWhile { it.type.night }.size
    return Stats(
        einsaetze = es.size,
        measures = es.sumOf { it.measures.size },
        shifts = ss.size,
        hours = hours,
        byHour = byHour,
        byCategory = byCat,
        avgPerShiftType = avg,
        topMeasures = top,
        peakHour = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 },
        nightStreak = streak,
    )
}

enum class Freshness(val label: String) { FRISCH("Frisch"), BALD("Bald üben"), LANGE_HER("Lange her"), NIE("Noch nie") }

data class SkillStatus(
    val skill: Skill,
    val count: Int,
    val selfCount: Int,
    val lastAt: Long?,
    val intervalDays: Int,
    val goal: SkillGoal,
    /** Anzahl je Monat, die letzten 12 Monate, ältester zuerst. */
    val monthly: IntArray,
) {
    val daysAgo: Long? get() = lastAt?.let { daysSince(it) }
    val freshness: Freshness
        get() {
            val d = daysAgo ?: return Freshness.NIE
            return when {
                d < intervalDays * 0.6 -> Freshness.FRISCH
                d < intervalDays -> Freshness.BALD
                else -> Freshness.LANGE_HER
            }
        }
}

fun AppData.skillStatuses(now: Long = System.currentTimeMillis()): List<SkillStatus> {
    val skills = Catalog.forRole(settings.role, settings.customSkills)
    val nowMonth = now.toLocalDate().withDayOfMonth(1)
    return skills.map { skill ->
        val fromEinsatz = einsaetze.flatMap { e -> e.measures.filter { it.skillId == skill.id }.map { e.alarmAt to it.involvement } }
        val fromPractice = practices.filter { it.skillId == skill.id }.map { it.at to Involvement.SELBST }
        val all = fromEinsatz + fromPractice
        val goal = goals[skill.id] ?: SkillGoal(intervalDays = skill.defaultIntervalDays)
        val monthly = IntArray(12)
        all.forEach { (t, _) ->
            val months = ChronoUnit.MONTHS.between(t.toLocalDate().withDayOfMonth(1), nowMonth).toInt()
            if (months in 0..11) monthly[11 - months]++
        }
        SkillStatus(
            skill = skill,
            count = all.size,
            selfCount = all.count { it.second == Involvement.SELBST },
            // Für die Frische zählt nur, was man selbst gemacht hat.
            lastAt = all.filter { it.second == Involvement.SELBST }.maxOfOrNull { it.first },
            intervalDays = goal.intervalDays,
            goal = goal,
            monthly = monthly,
        )
    }
}
