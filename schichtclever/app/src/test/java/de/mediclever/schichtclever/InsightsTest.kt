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

    @Test fun assistiertZaehltNichtFuerFrische() {
        val st = data.skillStatuses().first { it.skill.id == "iv" }
        assertTrue(st.selfCount <= st.count)
    }
}
