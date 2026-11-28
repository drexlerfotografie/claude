package de.mediclever.schichtclever

import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Category
import de.mediclever.schichtclever.data.Einsatz
import de.mediclever.schichtclever.data.Involvement
import de.mediclever.schichtclever.data.MeasureEntry
import de.mediclever.schichtclever.data.Outcome
import de.mediclever.schichtclever.data.Practice
import de.mediclever.schichtclever.data.Settings
import de.mediclever.schichtclever.data.Shift
import de.mediclever.schichtclever.data.ShiftType
import de.mediclever.schichtclever.data.SkillGoal
import de.mediclever.schichtclever.data.Urgency

object SampleData {
    private const val H = 3_600_000L
    private const val D = 24 * H
    val FRUEH = ShiftType.DEFAULTS.first { it.code == "F" }
    val NACHT = ShiftType.DEFAULTS.first { it.code == "N" }
    /** Eigene Vorlagen wie aus einem echten Dienstplan. */
    val CUSTOM = listOf(
        ShiftType("t", "T", "Tag RTW", 7 * 60, 12 * 60, false),
        ShiftType("n", "N", "Nacht RTW", 19 * 60, 12 * 60, true),
        ShiftType("f2", "F2", "Früh NEF", 6 * 60 + 30, 8 * 60 + 30, false),
        ShiftType("bd", "BD", "Bereitschaft", 17 * 60, 15 * 60, true),
        ShiftType("ka", "KA", "Kurs / Ausbildung", 8 * 60, 8 * 60, false),
    )

    fun build(now: Long = System.currentTimeMillis(), active: Boolean = true, activeType: ShiftType = NACHT): AppData {
        val shifts = mutableListOf<Shift>()
        val einsaetze = mutableListOf<Einsatz>()
        val cats = Category.entries
        val measureSets = listOf(
            listOf("iv", "ekg12", "bz"), listOf("iv", "analgesie"), listOf("neuro", "bz", "iv"),
            listOf("ekg12", "niv"), listOf("hws", "vakuum", "schienung"), listOf("rea", "defi", "larynx", "io"),
        )
        // 14 vergangene Schichten, abwechselnd Tag und Nacht
        for (i in 1..14) {
            val type = if (i % 3 == 0) FRUEH else NACHT
            val start = now - i * 2 * D - 5 * H
            val s = Shift(id = "s$i", type = type, startAt = start, plannedEndAt = start + type.durationMinutes * 60_000L, endAt = start + type.durationMinutes * 60_000L, unit = "RTW 1/83-1", rating = (i % 5) + 1)
            shifts += s
            repeat(3 + i % 4) { k ->
                einsaetze += Einsatz(
                    shiftId = s.id, alarmAt = start + (k * 2 + 1) * H + (k * 7) * 60_000L,
                    category = cats[(i + k) % 6], urgency = Urgency.entries[(i + k) % 3], outcome = Outcome.entries[(i * k) % 3],
                    measures = measureSets[(i + k) % measureSets.size].map { MeasureEntry(it, if ((i + k) % 5 == 0) Involvement.ASSISTIERT else Involvement.SELBST) },
                )
            }
        }
        if (active) {
            val start = now - (4 * H + 47 * 60_000)
            val s = Shift(id = "aktiv", type = activeType, startAt = start, plannedEndAt = start + activeType.durationMinutes * 60_000L, unit = if (activeType.night) "RTW 1/83-1" else "Intensiv 2")
            shifts += s
            einsaetze += Einsatz(shiftId = s.id, alarmAt = start + 1 * H + 23 * 60_000, category = Category.TRAUMA, outcome = Outcome.TRANSPORT, measures = listOf(MeasureEntry("hws"), MeasureEntry("vakuum")))
            einsaetze += Einsatz(shiftId = s.id, alarmAt = start + 2 * H + 58 * 60_000, category = Category.NEURO, outcome = Outcome.TRANSPORT, measures = listOf(MeasureEntry("iv"), MeasureEntry("neuro"), MeasureEntry("bz")))
            einsaetze += Einsatz(shiftId = s.id, alarmAt = start + 4 * H + 1 * 60_000, category = Category.INTERNISTISCH, urgency = Urgency.HOCH, outcome = Outcome.TRANSPORT, measures = listOf(MeasureEntry("ekg12"), MeasureEntry("iv"), MeasureEntry("analgesie")), note = "ACS-Verdacht")
            einsaetze += Einsatz(shiftId = s.id, alarmAt = now - 9 * 60_000, quick = true)
        }
        return AppData(
            settings = Settings(onboardingDone = true, blockedApps = setOf("com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube", "com.netflix.mediaclient")),
            shifts = shifts,
            einsaetze = einsaetze,
            practices = listOf(Practice(skillId = "io", at = now - 94 * D), Practice(skillId = "thoraxentlastung", at = now - 150 * D)),
            goals = mapOf(
                "thoraxentlastung" to SkillGoal(intervalDays = 120, onPracticeList = true),
                "io" to SkillGoal(intervalDays = 60, onPracticeList = true),
                "larynx" to SkillGoal(intervalDays = 60, onPracticeList = true),
            ),
        )
    }
}
