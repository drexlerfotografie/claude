package de.mediclever.schichtclever

import de.mediclever.schichtclever.data.Freshness
import de.mediclever.schichtclever.data.Period
import de.mediclever.schichtclever.data.skillStatuses
import de.mediclever.schichtclever.data.stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private val data = SampleData.build()

    @Test fun statsZaehltEinsaetzeImZeitraum() {
        val all = data.stats(Period.ALLES)
        assertEquals(data.einsaetze.size, all.einsaetze)
        assertEquals(data.einsaetze.size, all.byHour.sum())
        assertTrue(data.stats(Period.WOCHE).einsaetze < all.einsaetze)
    }

    @Test fun frischeRichtetSichNachIntervall() {
        val st = data.skillStatuses().associateBy { it.skill.id }
        assertEquals(Freshness.LANGE_HER, st.getValue("thoraxentlastung").freshness)
        assertEquals(Freshness.FRISCH, st.getValue("iv").freshness)
        assertEquals(Freshness.NIE, st.getValue("koniotomie").freshness)
    }

    @Test fun zeitUndDauerWerdenTolerantGelesen() {
        assertEquals(22 * 60, de.mediclever.schichtclever.ui.screens.parseTime("22:00"))
        assertEquals(6 * 60 + 30, de.mediclever.schichtclever.ui.screens.parseTime("0630"))
        assertEquals(7 * 60, de.mediclever.schichtclever.ui.screens.parseTime("7"))
        assertEquals(null, de.mediclever.schichtclever.ui.screens.parseTime("25:00"))
        assertEquals(8 * 60 + 30, de.mediclever.schichtclever.ui.screens.parseDuration("8,5"))
        assertEquals(12 * 60 + 15, de.mediclever.schichtclever.ui.screens.parseDuration("12:15"))
        assertEquals(null, de.mediclever.schichtclever.ui.screens.parseDuration("0"))
    }

    @Test fun vorschlagNimmtNaechstenSchichtbeginn() {
        val t = de.mediclever.schichtclever.data.ShiftType
        assertEquals("N", t.guess(SampleData.CUSTOM, 18 * 60 + 40)?.code)   // 18:40 → Nacht ab 19 Uhr
        assertEquals("F2", t.guess(SampleData.CUSTOM, 6 * 60)?.code)       // 06:00 → Früh NEF 06:30
        assertEquals("N", t.guess(SampleData.CUSTOM, 23 * 60 + 50)?.code)  // über Mitternacht gerechnet
    }

    @Test fun assistiertZaehltNichtFuerFrische() {
        val st = data.skillStatuses().first { it.skill.id == "iv" }
        assertTrue(st.selfCount <= st.count)
    }
}
