package de.mediclever.tagebuch.ui.home

import java.time.LocalDate

/** Eigene, ruhige Gedanken fuer den Tag. Keine Zitate Dritter, damit keine Rechte beruehrt sind. */
object Quotes {
    private val all = listOf(
        "Ein kurzer Eintrag ist besser als keiner.",
        "Du musst heute nichts bewerten. Aufschreiben reicht.",
        "Auch ein durchschnittlicher Tag gehört zu deiner Geschichte.",
        "Was hat dir heute gutgetan?",
        "Kleine Schritte zählen, besonders nach langen Diensten.",
        "Schlaf ist keine Belohnung, sondern Grundlage.",
        "Wer aufschreibt, was war, sieht leichter, was hilft.",
        "Ein schlechter Tag ist ein Datenpunkt, kein Urteil.",
        "Pausen sind Teil der Arbeit.",
        "Was würdest du einer Kollegin an deiner Stelle raten?",
        "Heute darf einfach heute sein.",
        "Muster erkennt man erst mit etwas Abstand.",
        "Du darfst stolz auf das sein, was du geschafft hast.",
        "Ein Glas Wasser und fünf Minuten Ruhe sind ein Anfang.",
        "Ehrlich notiert ist besser als schön notiert.",
        "Nicht jeder Tag braucht eine Erkenntnis.",
        "Was nimmst du aus dieser Woche mit?",
        "Erholung lässt sich planen wie ein Dienst.",
        "Gute Tage haben oft unscheinbare Gründe.",
        "Du kennst dich besser, als du denkst. Das Tagebuch hilft beim Erinnern.",
        "Einmal durchatmen, dann eintragen.",
        "Was hat dich heute zum Lächeln gebracht?",
        "Routine entsteht aus Wiederholung, nicht aus Perfektion.",
        "Wenig Zeit? Die Stimmung allein genügt.",
        "Achte heute auf einen Moment, der gut war.",
        "Nach der Schicht ist vor der Erholung.",
        "Dein Tempo ist das richtige Tempo.",
        "Schwere Tage werden leichter, wenn man sie ablegt.",
        "Was brauchst du gerade am meisten?",
        "Zurückblättern zeigt, wie weit du gekommen bist.",
        "Freundlich zu dir selbst sein ist erlaubt.",
    )

    fun forDay(date: LocalDate): String = all[Math.floorMod(date.toEpochDay().toInt(), all.size)]
}
