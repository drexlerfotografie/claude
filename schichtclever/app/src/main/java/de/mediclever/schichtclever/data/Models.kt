package de.mediclever.schichtclever.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class Role(val label: String, val unitHint: String) {
    RETTUNGSDIENST("Rettungsdienst", "z. B. RTW 1/83-1"),
    PFLEGE("Pflege (Klinik)", "z. B. Intensiv 2 oder ZNA"),
    ARZT("Ärztin / Arzt", "z. B. NEF oder Notaufnahme"),
    AUSBILDUNG("Ausbildung & Studium", "z. B. Praktikum Lehrrettungswache"),
}

/**
 * Schichtvorlage. Standardvorlagen werden beim ersten Start angelegt, danach kann man sie
 * frei ändern, löschen und eigene mit Kürzel anlegen (z. B. "N", "F2", "BD").
 * Eine laufende Schicht speichert eine Kopie der Vorlage, damit spätere Änderungen alte Schichten nicht verfälschen.
 */
@Serializable
data class ShiftType(
    val id: String = newId(),
    val code: String,
    val label: String,
    /** Beginn in Minuten nach Mitternacht. */
    val startMinute: Int,
    val durationMinutes: Int,
    val night: Boolean,
) {
    val hours: Double get() = durationMinutes / 60.0
    val startLabel: String get() = "%02d:%02d".format(startMinute / 60, startMinute % 60)
    val endLabel: String get() = ((startMinute + durationMinutes) % 1440).let { "%02d:%02d".format(it / 60, it % 60) }
    val rangeLabel: String get() = "$startLabel–$endLabel"

    companion object {
        val DEFAULTS = listOf(
            ShiftType("frueh", "F", "Früh", 6 * 60, 8 * 60, false),
            ShiftType("spaet", "S", "Spät", 14 * 60, 8 * 60, false),
            ShiftType("nacht", "N", "Nacht", 22 * 60, 8 * 60, true),
            ShiftType("tag12", "T12", "12 h Tag", 7 * 60, 12 * 60, false),
            ShiftType("nacht12", "N12", "12 h Nacht", 19 * 60, 12 * 60, true),
            ShiftType("dienst24", "24", "24 h", 7 * 60, 24 * 60, true),
        )

        /** Vorlage, deren Beginn der aktuellen Uhrzeit am nächsten liegt (für Schnellstart per Kachel). */
        fun guess(templates: List<ShiftType>, minuteOfDay: Int): ShiftType? = templates.minByOrNull {
            val d = kotlin.math.abs(it.startMinute - minuteOfDay)
            minOf(d, 1440 - d)
        }
    }
}

@Serializable
data class Shift(
    val id: String = newId(),
    val type: ShiftType,
    val startAt: Long,
    val plannedEndAt: Long,
    val endAt: Long? = null,
    val unit: String = "",
    val focus: Boolean = true,
    val rating: Int? = null,
    val note: String = "",
) {
    val active: Boolean get() = endAt == null
}

@Serializable
enum class Category(val label: String) {
    INTERNISTISCH("Internistisch"),
    TRAUMA("Trauma"),
    NEURO("Neuro"),
    PAEDIATRIE("Pädiatrie"),
    PSYCH("Psych"),
    REANIMATION("Reanimation"),
    GYN("Geburt / Gyn"),
    SONSTIGES("Sonstiges"),
}

@Serializable
enum class Urgency(val label: String) { NIEDRIG("Niedrig"), MITTEL("Mittel"), HOCH("Hoch / Notarzt") }

@Serializable
enum class Outcome(val label: String) {
    TRANSPORT("Transport"),
    AMBULANT("Ambulant versorgt"),
    UEBERGABE("Übergabe / Verlegung"),
    EXITUS("Exitus"),
    FEHLEINSATZ("Fehleinsatz"),
}

@Serializable
enum class Involvement(val label: String) { SELBST("Selbst"), ASSISTIERT("Assistiert") }

@Serializable
data class MeasureEntry(val skillId: String, val involvement: Involvement = Involvement.SELBST)

@Serializable
data class Einsatz(
    val id: String = newId(),
    val shiftId: String? = null,
    val alarmAt: Long,
    val category: Category = Category.SONSTIGES,
    val urgency: Urgency = Urgency.MITTEL,
    val outcome: Outcome? = null,
    val measures: List<MeasureEntry> = emptyList(),
    val note: String = "",
    /** Per Schnellkachel angelegt, Details fehlen noch. */
    val quick: Boolean = false,
)

@Serializable
enum class PracticeKind(val label: String) { SIMULATION("Simulation"), TRAINING("Skillstraining"), REAL("Echteinsatz") }

@Serializable
data class Practice(
    val id: String = newId(),
    val skillId: String,
    val at: Long,
    val kind: PracticeKind = PracticeKind.TRAINING,
)

@Serializable
data class SkillGoal(
    val intervalDays: Int = 90,
    val onPracticeList: Boolean = false,
    val targetDate: Long? = null,
)

@Serializable
enum class ThemeMode(val label: String) { DUNKEL("Dunkel"), HELL("Hell"), AUTO_SCHICHT("Automatisch nach Schicht"), SYSTEM("Wie System") }

@Serializable
enum class FilterSchedule(val label: String) { MIT_SCHICHT("Mit Nachtschicht"), ZEITRAUM("22–06 Uhr"), MANUELL("Nur manuell") }

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    val role: Role = Role.RETTUNGSDIENST,
    val theme: ThemeMode = ThemeMode.AUTO_SCHICHT,
    val focusAutoWithShift: Boolean = true,
    val focusManual: Boolean = false,
    val dndEnabled: Boolean = true,
    val blockedApps: Set<String> = emptySet(),
    val filterSchedule: FilterSchedule = FilterSchedule.MIT_SCHICHT,
    val filterManualOn: Boolean = false,
    val filterIntensity: Float = 0.4f,
    val filterDim: Float = 0.0f,
    val customSkills: List<Skill> = emptyList(),
    val shiftTemplates: List<ShiftType> = ShiftType.DEFAULTS,
    /** Merkt sich, ob SchichtClever "Bitte nicht stören" eingeschaltet hat, damit wir nur das wieder ausschalten. */
    val dndSetByApp: Boolean = false,
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val shifts: List<Shift> = emptyList(),
    val einsaetze: List<Einsatz> = emptyList(),
    val practices: List<Practice> = emptyList(),
    val goals: Map<String, SkillGoal> = emptyMap(),
) {
    val activeShift: Shift? get() = shifts.firstOrNull { it.active }
    val focusActive: Boolean
        get() = settings.focusManual || activeShift?.focus == true
}
