package de.mediclever.tagebuch.core.tone

import de.mediclever.tagebuch.core.model.Detail
import de.mediclever.tagebuch.core.model.EntryBlocks
import de.mediclever.tagebuch.core.model.StartBlocks
import de.mediclever.tagebuch.core.model.Tone
import de.mediclever.tagebuch.core.model.UserSettings
import de.mediclever.tagebuch.core.model.UserType
import java.util.Locale

/** Was ein Typ voreinstellt. Mit Plus laesst sich danach jeder Punkt einzeln aendern. */
data class Preset(val detail: Detail, val tone: Tone, val entryOrder: List<String>, val startBlocks: List<String>)

object Presets {
    fun of(type: UserType): Preset = when (type) {
        UserType.SCHNELL -> Preset(Detail.KOMPAKT, Tone.KNAPP, EntryBlocks.DEFAULT, listOf(StartBlocks.MATRIX))
        UserType.AUSGEWOGEN -> Preset(Detail.NORMAL, Tone.FREUNDLICH, EntryBlocks.DEFAULT, StartBlocks.DEFAULT)
        UserType.ANALYTISCH -> Preset(Detail.ALLES, Tone.SACHLICH, EntryBlocks.DEFAULT, listOf(StartBlocks.SCALES, StartBlocks.MATRIX, StartBlocks.LINE, StartBlocks.GOALS))
        UserType.ERZAEHLEND -> Preset(Detail.NORMAL, Tone.FREUNDLICH, listOf(EntryBlocks.NOTE, EntryBlocks.SHIFT, EntryBlocks.SCALES, EntryBlocks.TAGS), listOf(StartBlocks.LINE, StartBlocks.LAST, StartBlocks.MATRIX))
    }

    fun apply(s: UserSettings, type: UserType): UserSettings = of(type).let { p ->
        s.copy(userType = type, detail = p.detail, tone = p.tone, entryOrder = p.entryOrder, startBlocks = p.startBlocks)
    }

    fun title(type: UserType) = when (type) { UserType.SCHNELL -> "Schnell"; UserType.AUSGEWOGEN -> "Ausgewogen"; UserType.ANALYTISCH -> "Analytisch"; UserType.ERZAEHLEND -> "Erzählend" }
    fun hint(type: UserType) = when (type) {
        UserType.SCHNELL -> "Ein Wert, fertig. So wenig wie möglich auf dem Bildschirm."
        UserType.AUSGEWOGEN -> "Wert, Skalen und ein paar Aktivitäten. Der übliche Weg."
        UserType.ANALYTISCH -> "Alle Skalen, alle Auswertungen, Zahlen statt Worte."
        UserType.ERZAEHLEND -> "Die Notiz steht vorn, Zahlen kommen danach."
    }
}

/**
 * Der eine Satz auf der Startseite, je nach Tonfall. Cleverle ist trocken und ironisch, aber nur
 * ueber Alltag, Technik und sich selbst. Nach einem schweren Tag (Wert 1 oder 2) spricht er ohne
 * Sprueche.
 */
object Lines {
    data class Input(
        /** Schnitt der laufenden Woche und der Vorwoche, 1 bis 5 (5 ist der beste). */
        val week: Double?, val lastWeek: Double?,
        /** Tage seit dem letzten Eintrag; 0 = heute, null = noch nie. */
        val daysSinceEntry: Int?,
        /** Tage in Folge bis heute. */
        val streak: Int,
        /** Tageswert des juengsten Eintrags. */
        val lastScore: Int?,
        val entryCount: Int,
    )

    private fun n(v: Double) = String.format(Locale.GERMANY, "%.1f", v)
    private fun signed(v: Double) = (if (v >= 0) "+" else "−") + n(kotlin.math.abs(v))
    private fun days(n: Int) = when (n) { 2 -> "zwei"; 3 -> "drei"; 4 -> "vier"; 5 -> "fünf"; 6 -> "sechs"; 7 -> "sieben"; else -> n.toString() }
    private val milestones = setOf(7, 30, 50, 100, 200, 365)

    fun start(i: Input, tone: Tone, hints: Boolean = true): String? {
        // Zurueckhaltung nach einem schweren Tag: kein Spruch, keine Aufforderung
        val low = i.lastScore != null && i.lastScore <= 2 && i.daysSinceEntry != null && i.daysSinceEntry <= 1
        val t = if (tone == Tone.CLEVERLE && low) Tone.FREUNDLICH else tone
        if (i.entryCount == 0) return when (t) {
            Tone.SACHLICH -> "Noch kein Eintrag. Tippe auf den Bogen."
            Tone.FREUNDLICH -> "Schön, dass du da bist. Tippe auf den Bogen für deinen ersten Eintrag."
            Tone.KNAPP -> "Noch kein Eintrag"
            Tone.CLEVERLE -> "Moin. Noch ist hier nichts los. Tipp auf den Bogen, ich schreib mit."
        }
        if (low && tone == Tone.CLEVERLE) return "Das war ein schwerer Tag. Dein Eintrag ist gespeichert. Wenn du magst, schau morgen noch mal rein."
        val gap = i.daysSinceEntry
        if (gap != null && gap in 2..30 && (hints || t != Tone.CLEVERLE)) return when (t) {
            Tone.SACHLICH -> "Letzter Eintrag vor $gap Tagen."
            Tone.FREUNDLICH -> "Dein letzter Eintrag ist ${days(gap)} Tage her. Magst du nachtragen?"
            Tone.KNAPP -> "$gap Tage ohne Eintrag"
            Tone.CLEVERLE -> "${days(gap).replaceFirstChar { it.uppercase() }} Tage Funkstille. Ich hab nichts gesagt. Bis jetzt. Nachtragen dauert eine Kaffeelänge."
        }
        if (i.streak in milestones && gap == 0) return when (t) {
            Tone.SACHLICH -> "${i.streak} Tage in Folge eingetragen."
            Tone.FREUNDLICH -> "${i.streak} Tage in Folge. Stark, dass du dranbleibst."
            Tone.KNAPP -> "${i.streak} Tage in Folge"
            Tone.CLEVERLE -> "${i.streak} Einträge in Folge. Ich bin beeindruckt. Innerlich. Aber immerhin."
        }
        val now = i.week
        val last = i.lastWeek
        if (now == null) return null
        if (last == null) return when (t) {
            Tone.SACHLICH -> "Wochenschnitt ${n(now)}."
            Tone.FREUNDLICH -> "Dein Schnitt diese Woche liegt bei ${n(now)}."
            Tone.KNAPP -> n(now)
            Tone.CLEVERLE -> "Sachstand: ${n(now)}. Für einen Vergleich fehlt mir die Vorwoche. Ja, selbst mir."
        }
        val diff = now - last
        return when {
            diff >= 0.25 -> when (t) {
                Tone.SACHLICH -> "Wochenschnitt ${n(now)}. Das sind ${n(diff)} mehr als in der Vorwoche."
                Tone.FREUNDLICH -> "Diese Woche läuft besser als die letzte. Dein Schnitt liegt bei ${n(now)}."
                Tone.KNAPP -> "${n(now)} · ${signed(diff)}"
                Tone.CLEVERLE -> "Sachstand: ${n(now)} statt ${n(last)}. Läuft. Sag ich doch."
            }
            diff <= -0.25 -> when (t) {
                Tone.SACHLICH -> "Wochenschnitt ${n(now)}. Das sind ${n(-diff)} weniger als in der Vorwoche."
                Tone.FREUNDLICH -> "Diese Woche ist bisher schwerer als die letzte. Dein Schnitt liegt bei ${n(now)}."
                Tone.KNAPP -> "${n(now)} · ${signed(diff)}"
                Tone.CLEVERLE -> if (hints) "Lagemeldung: ${n(now)} statt ${n(last)}. Ich urteile nicht, ich schreib nur mit. Eine Pause wäre trotzdem keine Schande."
                else "Lagemeldung: ${n(now)} statt ${n(last)}. Ich urteile nicht, ich schreib nur mit."
            }
            else -> when (t) {
                Tone.SACHLICH -> "Wochenschnitt ${n(now)}, wie in der Vorwoche."
                Tone.FREUNDLICH -> "Diese Woche läuft ähnlich wie die letzte. Dein Schnitt liegt bei ${n(now)}."
                Tone.KNAPP -> "${n(now)} · ±0"
                Tone.CLEVERLE -> "${n(now)}, wie letzte Woche. Beständig. Fast schon unheimlich."
            }
        }
    }

    /** Beispielsatz fuer die Auswahl des Tonfalls: dieselbe Auskunft in jedem Ton. */
    fun sample(tone: Tone): String = start(Input(3.6, 3.2, 0, 4, 4, 20), tone)!!

    fun title(tone: Tone) = when (tone) { Tone.SACHLICH -> "Sachlich"; Tone.FREUNDLICH -> "Freundlich"; Tone.KNAPP -> "Knapp"; Tone.CLEVERLE -> "Cleverle" }
}
