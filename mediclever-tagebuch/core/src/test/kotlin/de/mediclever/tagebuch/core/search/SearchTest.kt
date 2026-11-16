package de.mediclever.tagebuch.core.search

import de.mediclever.tagebuch.core.stats.DIARY
import de.mediclever.tagebuch.core.stats.plainText
import de.mediclever.tagebuch.core.stats.wordCount
import kotlin.test.Test
import kotlin.test.assertEquals

/** Eintraege siehe Tabelle an ENTRIES in Fixture.kt. */
class SearchTest {
    private fun ids(q: SearchQuery) = searchEntries(DIARY, q).map { it.id }

    @Test fun emptyQueryReturnsEverythingNewestFirst() {
        // am 04.03. steht e4 (20:00) vor e3 (08:00); der geloeschte Eintrag fehlt
        assertEquals(listOf("e8", "e7", "e6", "e5", "e4", "e3", "e2", "e1", "f2", "f1"), ids(SearchQuery()))
    }

    @Test fun textSearchIgnoresCaseAndHtml() {
        assertEquals(listOf("e1"), ids(SearchQuery(text = "LAUFEN im")))
        assertEquals(listOf("e1"), ids(SearchQuery(text = "  start ")))
        assertEquals(emptyList(), ids(SearchQuery(text = "b>")))
        assertEquals(emptyList(), ids(SearchQuery(text = "<p>")))
        assertEquals(listOf("e5"), ids(SearchQuery(text = "kaffee & kuchen")))
        assertEquals(emptyList(), ids(SearchQuery(text = "gibt es nicht")))
    }

    @Test fun byMoodOrLevel() {
        assertEquals(listOf("e6", "e1"), ids(SearchQuery(levels = setOf(1))))
        assertEquals(listOf("e3", "e2", "f2"), ids(SearchQuery(moodIds = setOf("m2"))))
        // Stufe 2 umfasst auch die Stimmung m2b
        assertEquals(listOf("e7", "e3", "e2", "f2"), ids(SearchQuery(levels = setOf(2))))
        assertEquals(listOf("e7", "e6", "e1"), ids(SearchQuery(moodIds = setOf("m2b"), levels = setOf(1))))
    }

    @Test fun byActivities() {
        assertEquals(listOf("e6", "e5", "e4", "e3", "f1"), ids(SearchQuery(tagIds = setOf("lesen", "arbeit"))))
        assertEquals(listOf("e6"), ids(SearchQuery(tagIds = setOf("lesen", "sport"), matchAllTags = true)))
        // geloeschte Aktivitaet findet nichts
        assertEquals(emptyList(), ids(SearchQuery(tagIds = setOf("old"))))
    }

    @Test fun criteriaAreCombinedWithAnd() {
        assertEquals(listOf("e7", "e2", "f2"), ids(SearchQuery(levels = setOf(2), tagIds = setOf("sport"))))
        assertEquals(listOf("e1"), ids(SearchQuery(text = "park", levels = setOf(1), tagIds = setOf("sport"))))
        assertEquals(emptyList(), ids(SearchQuery(text = "park", levels = setOf(2))))
        assertEquals(listOf("e1"), ids(SearchQuery(favoritesOnly = true)))
    }

    @Test fun plainTextAndWords() {
        assertEquals("Heute war ich laufen im Park.", plainText("<p>Heute war ich <b>laufen</b> im Park.</p>"))
        assertEquals("eins zwei drei", plainText("<ul><li>eins</li><li>zwei</li></ul>drei"))
        assertEquals("a < b & c", plainText("a &lt; b &amp;&nbsp;c"))
        assertEquals("Zeile Zeile", plainText("Zeile<br>Zeile"))
        assertEquals(6, wordCount("<p>Heute war ich <b>laufen</b> im Park.</p>"))
        assertEquals(0, wordCount(""))
        assertEquals(0, wordCount("<p> </p>"))
        assertEquals(1, wordCount("Wort"))
    }
}
