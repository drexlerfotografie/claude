package de.mediclever.tagebuch

import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.data.StarterPacks
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/** Erfundene Beispieldaten fuer Screenshots und Tests. Fester Zufall, festes "Heute". */
object SampleData {
    val now: LocalDateTime = LocalDateTime.of(2026, 11, 16, 19, 30)
    val today: LocalDate = now.toLocalDate()

    private val notes = listOf(
        "" to "<p>Ruhiger Tag. Nach dem Dienst noch eine Runde um den See.</p>",
        "Guter Start" to "<p>Früh raus, <b>Sport</b> geschafft und danach lange gefrühstückt.</p><ul><li>Wäsche</li><li>Einkauf</li></ul>",
        "" to "<p>Viel los gewesen, abends müde, aber zufrieden.</p>",
        "" to "", "" to "", "" to "",
        "Schwieriger Tag" to "<p>Schlecht geschlafen und im Dienst kaum Pause. <i>Morgen früher ins Bett.</i></p>",
    )

    val diary: Diary by lazy { build() }

    private fun build(): Diary {
        val rnd = Random(7)
        val base = StarterPacks.build("schichtdienst").let { d ->
            // feste IDs, damit Screenshots stabil bleiben
            d.copy(moods = d.moods + Mood(level = 2, name = "Zufrieden", icon = "b", sortOrder = 1))
        }
        val moodsByLevel = base.moods.groupBy { it.level }
        val entries = mutableListOf<Entry>()
        val checks = mutableListOf<GoalCheck>()
        val sport = base.tags.first { it.name == "Sport" }
        val goalSport = Goal(name = "Dreimal pro Woche Sport", tagId = sport.id, icon = "fitness_center", colorIndex = 4,
            repeat = GoalRepeat.TIMES_PER_WEEK, timesPerWeek = 3, startDate = "2026-01-01", sortOrder = 0)
        val goalWater = Goal(name = "Zwei Liter trinken", icon = "water_drop", colorIndex = 6, startDate = "2026-06-01", sortOrder = 1,
            reminderMinuteOfDay = 18 * 60, reminderText = "Schon genug getrunken?")
        var day = today
        var i = 0
        while (day >= today.minusDays(420)) {
            val skip = rnd.nextInt(100) < 12 && day != today && day != today.minusDays(1)
            if (!skip) {
                val count = if (rnd.nextInt(100) < 12) 2 else 1
                repeat(count) { k ->
                    val level = listOf(1, 2, 2, 2, 3, 3, 4, 2, 1, 5, 3, 2)[rnd.nextInt(12)]
                    val mood = moodsByLevel.getValue(level).let { it[rnd.nextInt(it.size)] }
                    val tags = base.tags.shuffled(rnd).take(2 + rnd.nextInt(4)).map { it.id }
                    val values = base.scales.mapNotNull { s ->
                        if (rnd.nextInt(100) < 20) null else when (s.type) {
                            ScaleType.TEXT -> ScaleValue(s.id, pointId = s.points[(s.points.size - level + rnd.nextInt(2)).coerceIn(0, s.points.size - 1)].id)
                            ScaleType.DURATION -> ScaleValue(s.id, minutes = 300 + rnd.nextInt(14) * 15)
                            ScaleType.NUMBER -> ScaleValue(s.id, number = 1 + rnd.nextInt(5))
                        }
                    }
                    val (title, note) = notes[rnd.nextInt(notes.size)]
                    val minute = if (k == 0) 19 * 60 + rnd.nextInt(180) else 8 * 60 + rnd.nextInt(120)
                    val offset = if (day.monthValue in 4..10) 120 else 60
                    entries += Entry(
                        id = "e$i", timestamp = day.atTime(minute / 60, minute % 60).toEpochSecond(java.time.ZoneOffset.ofTotalSeconds(offset * 60)) * 1000,
                        offsetMinutes = offset, localDate = day.toString(), minuteOfDay = minute, moodId = mood.id,
                        title = title, note = note, favorite = rnd.nextInt(100) < 4, tagIds = tags, scaleValues = values,
                    )
                    i++
                    if (sport.id in tags) checks += GoalCheck(GoalCheck.idFor(goalSport.id, day.toString()), goalSport.id, day.toString())
                }
                if (rnd.nextInt(100) < 75 && day >= LocalDate.of(2026, 6, 1)) checks += GoalCheck(GoalCheck.idFor(goalWater.id, day.toString()), goalWater.id, day.toString())
            }
            day = day.minusDays(1)
        }
        return base.copy(
            entries = entries.sortedByDescending { it.timestamp },
            goals = listOf(goalSport, goalWater),
            goalChecks = checks.distinctBy { it.id },
            importantDays = listOf(
                ImportantDay(name = "Examen bestanden", date = "2021-09-24", category = "Beruf", note = "Der lange Weg hat sich gelohnt.", remindOnDay = true),
                ImportantDay(name = "Umzug", date = "2024-12-01", category = "Privat", yearly = true),
                ImportantDay(name = "Marathon", date = "2027-04-18", category = "Sport", yearly = false, remindDaysBefore = listOf(7)),
            ),
            reminders = listOf(Reminder(minuteOfDay = 20 * 60, text = "Wie war dein Tag?"), Reminder(minuteOfDay = 7 * 60 + 30, text = "", active = false)),
        )
    }
}
