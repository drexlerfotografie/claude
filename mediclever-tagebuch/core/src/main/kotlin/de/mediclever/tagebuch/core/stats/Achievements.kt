package de.mediclever.tagebuch.core.stats

import de.mediclever.tagebuch.core.goals.effectiveGoalDates
import de.mediclever.tagebuch.core.model.Diary
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Art eines Erfolgs. */
enum class AchievementCategory { ENTRIES, STREAK, GOALS, YEARS, PHOTOS }

/**
 * Ein Erfolg aus dem festen Katalog. progress ist der aktuelle Wert, hoechstens target;
 * unlocked ist wahr, sobald der Wert target erreicht.
 */
data class Achievement(
    val key: String,
    val category: AchievementCategory,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val unlocked: Boolean,
)

/**
 * Erfolge eines Zeitraums. unlockedCount und totalCount gelten zum Stichtag (Ende des Zeitraums, hoechstens heute).
 * unlockedInPeriod: Erfolge, die am Tag vor dem Zeitraum noch nicht erreicht waren und am Stichtag erreicht sind.
 * next: der noch nicht erreichte Erfolg mit dem groessten Anteil progress / target (bei Gleichstand der erste
 * im Katalog), null wenn alle erreicht sind.
 */
data class AchievementSummary(
    val unlockedCount: Int,
    val totalCount: Int,
    val unlockedInPeriod: List<Achievement>,
    val next: Achievement?,
)

private class Def(val category: AchievementCategory, val target: Int, val title: String, val description: String) {
    val key: String = category.name.lowercase() + "_" + target
}

private val CATALOGUE: List<Def> = buildList {
    fun add(c: AchievementCategory, target: Int, title: String, description: String) = add(Def(c, target, title, description))
    val e = AchievementCategory.ENTRIES
    add(e, 1, "Anfang gemacht", "Du hast Deinen ersten Eintrag geschrieben.")
    add(e, 10, "Zehn Seiten", "Du hast 10 Einträge geschrieben.")
    add(e, 50, "Fünfzig Seiten", "Du hast 50 Einträge geschrieben.")
    add(e, 100, "Hundert Seiten", "Du hast 100 Einträge geschrieben.")
    add(e, 250, "Ein dickes Heft", "Du hast 250 Einträge geschrieben.")
    add(e, 500, "Halbes Tausend", "Du hast 500 Einträge geschrieben.")
    add(e, 1000, "Tausend Seiten", "Du hast 1000 Einträge geschrieben.")
    add(e, 2000, "Zwei Bände", "Du hast 2000 Einträge geschrieben.")
    val s = AchievementCategory.STREAK
    add(s, 3, "Drei Tage am Stück", "Du hast an 3 Tagen in Folge etwas eingetragen.")
    add(s, 7, "Eine volle Woche", "Du hast an 7 Tagen in Folge etwas eingetragen.")
    add(s, 14, "Zwei Wochen ohne Lücke", "Du hast an 14 Tagen in Folge etwas eingetragen.")
    add(s, 30, "Ein Monat ohne Lücke", "Du hast an 30 Tagen in Folge etwas eingetragen.")
    add(s, 60, "Zwei Monate ohne Lücke", "Du hast an 60 Tagen in Folge etwas eingetragen.")
    add(s, 100, "Hundert Tage in Folge", "Du hast an 100 Tagen in Folge etwas eingetragen.")
    add(s, 365, "Ein Jahr ohne Lücke", "Du hast an 365 Tagen in Folge etwas eingetragen.")
    val g = AchievementCategory.GOALS
    add(g, 1, "Erstes Häkchen", "Du hast zum ersten Mal ein Ziel abgehakt.")
    add(g, 10, "Zehn Häkchen", "Du hast Deine Ziele 10-mal abgehakt.")
    add(g, 50, "Fünfzig Häkchen", "Du hast Deine Ziele 50-mal abgehakt.")
    add(g, 100, "Hundert Häkchen", "Du hast Deine Ziele 100-mal abgehakt.")
    add(g, 500, "Fünfhundert Häkchen", "Du hast Deine Ziele 500-mal abgehakt.")
    add(g, 1000, "Tausend Häkchen", "Du hast Deine Ziele 1000-mal abgehakt.")
    val y = AchievementCategory.YEARS
    add(y, 1, "Ein Jahr Tagebuch", "Dein erster Eintrag liegt 1 Jahr zurück.")
    val words = listOf("Zwei", "Drei", "Vier", "Fünf", "Sechs", "Sieben", "Acht", "Neun", "Zehn")
    for (n in 2..10) add(y, n, "${words[n - 2]} Jahre Tagebuch", "Dein erster Eintrag liegt $n Jahre zurück.")
    val p = AchievementCategory.PHOTOS
    add(p, 1, "Erstes Foto", "Du hast einem Eintrag das erste Foto hinzugefügt.")
    add(p, 50, "Kleines Album", "Deine Einträge enthalten 50 Fotos.")
    add(p, 250, "Großes Album", "Deine Einträge enthalten 250 Fotos.")
    add(p, 1000, "Bildarchiv", "Deine Einträge enthalten 1000 Fotos.")
}

/** Laengste Reihe aufeinanderfolgender Kalendertage in einer aufsteigend sortierten Folge von Tagen. */
internal fun longestRun(days: Iterable<LocalDate>): Int {
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in days) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        if (run > longest) longest = run
        prev = d
    }
    return longest
}

private fun achievementsAsOf(idx: DiaryIndex, goalDates: Map<String, Set<LocalDate>>, asOf: LocalDate): List<Achievement> {
    val days = idx.byDate.headMap(asOf, true)
    var entryCount = 0
    val photos = HashSet<String>()
    for (list in days.values) {
        entryCount += list.size
        for (e in list) photos.addAll(e.photoIds)
    }
    val streak = longestRun(days.keys)
    val goals = goalDates.values.sumOf { set -> set.count { !it.isAfter(asOf) } }
    val years = if (days.isEmpty()) 0 else ChronoUnit.YEARS.between(days.firstKey(), asOf).toInt()
    return CATALOGUE.map { def ->
        val value = when (def.category) {
            AchievementCategory.ENTRIES -> entryCount
            AchievementCategory.STREAK -> streak
            AchievementCategory.GOALS -> goals
            AchievementCategory.YEARS -> years
            AchievementCategory.PHOTOS -> photos.size
        }
        Achievement(def.key, def.category, def.title, def.description, minOf(value, def.target), def.target, value >= def.target)
    }
}

/**
 * Alle Erfolge des festen Katalogs mit Stand [today], neu berechnet aus den Daten. Es zaehlt nur, was an
 * oder vor [today] liegt. Schluessel: "<kategorie>_<ziel>", z. B. "entries_10".
 *
 *  - ENTRIES (1, 10, 50, 100, 250, 500, 1000, 2000): Anzahl der nicht geloeschten Eintraege.
 *  - STREAK (3, 7, 14, 30, 60, 100, 365): laengste Reihe aufeinanderfolgender Tage mit mindestens einem Eintrag.
 *  - GOALS (1, 10, 50, 100, 500, 1000): abgehakte Ziel-Tage aller nicht geloeschten Ziele nach
 *    [effectiveGoalDates].
 *  - YEARS (1 bis 10): volle Jahre zwischen dem Tag des ersten Eintrags und [today].
 *  - PHOTOS (1, 50, 250, 1000): verschiedene nicht geloeschte Fotos in nicht geloeschten Eintraegen.
 */
fun achievements(diary: Diary, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Achievement> =
    achievementsAsOf(DiaryIndex.of(diary), effectiveGoalDates(diary, zone), today)

/**
 * Erfolge fuer den Zeitraum [range], siehe [AchievementSummary]. Stichtag ist das Ende des Zeitraums,
 * hoechstens [today]. Beginnt der Zeitraum nach [today], ist unlockedInPeriod leer und der Stichtag [today].
 */
fun achievementSummary(
    diary: Diary,
    range: DateRange,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): AchievementSummary {
    val idx = DiaryIndex.of(diary)
    val goalDates = effectiveGoalDates(diary, zone)
    val asOf = if (range.endInclusive.isAfter(today)) today else range.endInclusive
    val now = achievementsAsOf(idx, goalDates, asOf)
    val newly = if (range.start.isAfter(today)) emptyList() else {
        val before = achievementsAsOf(idx, goalDates, range.start.minusDays(1)).filter { it.unlocked }.mapTo(HashSet()) { it.key }
        now.filter { it.unlocked && it.key !in before }
    }
    val next = now.filter { !it.unlocked }.maxByOrNull { it.progress.toDouble() / it.target }
    return AchievementSummary(now.count { it.unlocked }, now.size, newly, next)
}
