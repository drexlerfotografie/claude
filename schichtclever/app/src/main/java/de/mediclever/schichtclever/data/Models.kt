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

@Serializable
enum class ShiftType(val label: String, val startHour: Int, val hours: Int, val night: Boolean) {
    FRUEH("Früh", 6, 8, false),
    SPAET("Spät", 14, 8, false),
    NACHT("Nacht", 22, 8, true),
    ZWOELF_TAG("12 h Tag", 7, 12, false),
    ZWOELF_NACHT("12 h Nacht", 19, 12, true),
    VIERUNDZWANZIG("24 h", 7, 24, true);

    val rangeLabel: String
        get() = "%02d–%02d Uhr".format(startHour, (startHour + hours) % 24)

    companion object {
        /** Schätzt die passende Schicht für die aktuelle Uhrzeit (für Schnellstart per Kachel). */
        fun guess(hour: Int): ShiftType = when (hour) {
            in 5..11 -> FRUEH
            in 12..18 -> SPAET
            else -> NACHT
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
