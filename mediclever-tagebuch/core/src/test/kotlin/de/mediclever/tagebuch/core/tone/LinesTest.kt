package de.mediclever.tagebuch.core.tone

import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.tone.Lines.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinesTest {
    private val good = Input(week = 3.6, lastWeek = 3.2, daysSinceEntry = 0, streak = 4, lastScore = 4, entryCount = 20)

    @Test fun sameFactInFourTones() {
        assertEquals("Wochenschnitt 3,6. Das sind 0,4 mehr als in der Vorwoche.", Lines.start(good, Tone.SACHLICH))
        assertEquals("Diese Woche läuft besser als die letzte. Dein Schnitt liegt bei 3,6.", Lines.start(good, Tone.FREUNDLICH))
        assertEquals("3,6 · +0,4", Lines.start(good, Tone.KNAPP))
        assertEquals("Sachstand: 3,6 statt 3,2. Läuft. Sag ich doch.", Lines.start(good, Tone.CLEVERLE))
    }

    @Test fun cleverleHoldsBackAfterAHardDay() {
        val hard = good.copy(week = 2.4, lastScore = 2)
        val line = Lines.start(hard, Tone.CLEVERLE)!!
        assertEquals("Das war ein schwerer Tag. Dein Eintrag ist gespeichert. Wenn du magst, schau morgen noch mal rein.", line)
        // auch gestern zaehlt noch, danach nicht mehr
        assertEquals(line, Lines.start(hard.copy(daysSinceEntry = 1), Tone.CLEVERLE))
        assertTrue(Lines.start(hard.copy(daysSinceEntry = 3), Tone.CLEVERLE)!!.contains("Funkstille"))
    }

    @Test fun gapComesBeforeTheWeek() {
        val gap = good.copy(daysSinceEntry = 2)
        assertEquals("Letzter Eintrag vor 2 Tagen.", Lines.start(gap, Tone.SACHLICH))
        assertEquals("Zwei Tage Funkstille. Ich hab nichts gesagt. Bis jetzt. Nachtragen dauert eine Kaffeelänge.", Lines.start(gap, Tone.CLEVERLE))
        // ohne Hinweise nennt Cleverle nur Zahlen
        assertEquals("Sachstand: 3,6 statt 3,2. Läuft. Sag ich doch.", Lines.start(gap, Tone.CLEVERLE, hints = false))
    }

    @Test fun worseWeekAndHintsSwitch() {
        val worse = good.copy(week = 3.0, lastWeek = 3.6)
        assertEquals("Wochenschnitt 3,0. Das sind 0,6 weniger als in der Vorwoche.", Lines.start(worse, Tone.SACHLICH))
        assertEquals("3,0 · −0,6", Lines.start(worse, Tone.KNAPP))
        assertTrue(Lines.start(worse, Tone.CLEVERLE)!!.endsWith("Eine Pause wäre trotzdem keine Schande."))
        assertEquals("Lagemeldung: 3,0 statt 3,6. Ich urteile nicht, ich schreib nur mit.", Lines.start(worse, Tone.CLEVERLE, hints = false))
    }

    @Test fun milestonesEmptyAndMissingData() {
        assertEquals("30 Einträge in Folge. Ich bin beeindruckt. Innerlich. Aber immerhin.", Lines.start(good.copy(streak = 30), Tone.CLEVERLE))
        assertEquals("Noch kein Eintrag", Lines.start(Input(null, null, null, 0, null, 0), Tone.KNAPP))
        assertNull(Lines.start(good.copy(week = null, lastWeek = null), Tone.SACHLICH))
        assertEquals("Wochenschnitt 3,6.", Lines.start(good.copy(lastWeek = null), Tone.SACHLICH))
        assertEquals("Wochenschnitt 3,6, wie in der Vorwoche.", Lines.start(good.copy(lastWeek = 3.5), Tone.SACHLICH))
    }

    @Test fun presetsSetDetailToneAndOrder() {
        val s = Presets.apply(UserSettings(), UserType.ANALYTISCH)
        assertEquals(Detail.ALLES, s.detail); assertEquals(Tone.SACHLICH, s.tone); assertEquals(UserType.ANALYTISCH, s.userType)
        assertTrue(StartBlocks.GOALS in s.startBlocks)
        assertEquals(EntryBlocks.NOTE, Presets.apply(UserSettings(), UserType.ERZAEHLEND).entryOrder.first())
        assertEquals(listOf(StartBlocks.MATRIX), Presets.of(UserType.SCHNELL).startBlocks)
        // der Standard entspricht dem Typ Ausgewogen
        assertEquals(UserSettings().copy(), Presets.apply(UserSettings(), UserType.AUSGEWOGEN))
    }
}
