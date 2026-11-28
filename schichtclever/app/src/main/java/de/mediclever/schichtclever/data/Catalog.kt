package de.mediclever.schichtclever.data

import kotlinx.serialization.Serializable

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val group: String,
    /** Leer = für alle Rollen sichtbar. */
    val roles: Set<Role> = emptySet(),
    /** Seltene, kritische Maßnahmen bekommen ein kürzeres Standard-Intervall. */
    val defaultIntervalDays: Int = 90,
)

object Catalog {
    private val RD = Role.RETTUNGSDIENST
    private val PF = Role.PFLEGE
    private val AR = Role.ARZT
    private val AZ = Role.AUSBILDUNG

    val groups = listOf("Zugang", "Atemweg", "Kreislauf", "Diagnostik", "Trauma", "Medikamente", "Pflege", "Sonstiges")

    val skills: List<Skill> = listOf(
        // Zugang
        Skill("iv", "i.v.-Zugang", "Zugang", defaultIntervalDays = 30),
        Skill("io", "i.o.-Zugang", "Zugang", setOf(RD, AR, AZ), 60),
        Skill("zvk", "ZVK-Anlage", "Zugang", setOf(AR), 90),
        Skill("arterie", "Arterielle Kanüle", "Zugang", setOf(AR, PF), 90),
        Skill("bbk", "Blutentnahme", "Zugang", setOf(PF, AR, AZ), 30),
        Skill("dk", "Blasenkatheter (DK)", "Zugang", setOf(PF, AR, AZ), 90),
        Skill("magensonde", "Magensonde", "Zugang", setOf(PF, AR), 120),
        // Atemweg
        Skill("bmv", "Beutel-Masken-Beatmung", "Atemweg", defaultIntervalDays = 60),
        Skill("larynx", "Larynxtubus / SGA", "Atemweg", setOf(RD, AR, AZ), 60),
        Skill("intubation", "Endotracheale Intubation", "Atemweg", setOf(RD, AR), 60),
        Skill("niv", "NIV / CPAP", "Atemweg", setOf(RD, AR, PF), 90),
        Skill("absaugen", "Absaugen", "Atemweg", defaultIntervalDays = 90),
        Skill("koniotomie", "Koniotomie", "Atemweg", setOf(RD, AR), 120),
        Skill("tracheo", "Trachealkanülen-Wechsel", "Atemweg", setOf(PF, AR), 120),
        // Kreislauf
        Skill("rea", "Reanimation (CPR)", "Kreislauf", defaultIntervalDays = 60),
        Skill("defi", "Defibrillation", "Kreislauf", defaultIntervalDays = 60),
        Skill("kardioversion", "Kardioversion", "Kreislauf", setOf(RD, AR), 120),
        Skill("pacing", "Externer Schrittmacher", "Kreislauf", setOf(RD, AR), 120),
        Skill("katecholamine", "Katecholamin-Perfusor", "Kreislauf", setOf(RD, AR, PF), 90),
        // Diagnostik
        Skill("ekg12", "12-Kanal-EKG", "Diagnostik", defaultIntervalDays = 30),
        Skill("bga", "Blutgasanalyse", "Diagnostik", setOf(PF, AR), 60),
        Skill("sono", "Sonografie (eFAST)", "Diagnostik", setOf(AR), 60),
        Skill("bz", "Blutzucker", "Diagnostik", defaultIntervalDays = 60),
        Skill("neuro", "Neuro-Status / BE-FAST", "Diagnostik", defaultIntervalDays = 60),
        // Trauma
        Skill("tourniquet", "Tourniquet", "Trauma", setOf(RD, AR, AZ), 90),
        Skill("beckenschlinge", "Beckenschlinge", "Trauma", setOf(RD, AR, AZ), 90),
        Skill("hws", "HWS-Immobilisation", "Trauma", setOf(RD, AZ), 90),
        Skill("vakuum", "Vakuummatratze", "Trauma", setOf(RD, AZ), 90),
        Skill("thoraxentlastung", "Thoraxentlastung", "Trauma", setOf(RD, AR), 120),
        Skill("thoraxdrainage", "Thoraxdrainage", "Trauma", setOf(AR), 120),
        Skill("schienung", "Schienung / Reposition", "Trauma", defaultIntervalDays = 90),
        // Medikamente
        Skill("analgesie", "Analgesie", "Medikamente", defaultIntervalDays = 60),
        Skill("narkose", "Narkoseeinleitung (RSI)", "Medikamente", setOf(AR, RD), 60),
        Skill("vernebler", "Vernebler / Inhalation", "Medikamente", defaultIntervalDays = 90),
        // Pflege
        Skill("lagerung", "Lagerung / Mobilisation", "Pflege", setOf(PF, AZ), 60),
        Skill("wunde", "Wundversorgung", "Pflege", defaultIntervalDays = 90),
        Skill("aufnahme", "Patientenaufnahme", "Pflege", setOf(PF, AZ), 60),
        // Sonstiges
        Skill("geburt", "Geburtsbegleitung", "Sonstiges", setOf(RD, AR, AZ), 120),
        Skill("uebergabe", "Strukturierte Übergabe", "Sonstiges", defaultIntervalDays = 60),
        Skill("anleitung", "Praxisanleitung", "Sonstiges", setOf(RD, PF), 90),
    )

    fun forRole(role: Role, custom: List<Skill>): List<Skill> =
        skills.filter { it.roles.isEmpty() || role in it.roles } + custom

    fun all(custom: List<Skill>): List<Skill> = skills + custom

    fun byId(id: String, custom: List<Skill>): Skill? = all(custom).firstOrNull { it.id == id }
}
