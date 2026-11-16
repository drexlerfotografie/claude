package de.mediclever.tagebuch.core.markup

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteMarkupTest {
    @Test fun htmlBecomesMarkup() = assertEquals(
        "Heute **gut** geschlafen\nund _lange_ gelesen\n- Brot\n- Milch\n1. erst\n2. dann",
        NoteMarkup.fromHtml("<p>Heute <b>gut</b> geschlafen<br>und <em>lange</em> gelesen</p><ul><li>Brot</li><li>Milch</li></ul><ol><li>erst</li><li>dann</li></ol>"),
    )

    @Test fun markupBecomesHtml() = assertEquals(
        "<p>Heute <b>gut</b><br>und <i>lange</i></p><ul><li>Brot</li><li>Milch</li></ul><p>Ende</p>",
        NoteMarkup.toHtml("Heute **gut**\nund _lange_\n- Brot\n- Milch\nEnde"),
    )

    @Test fun roundTripIsStable() {
        val m = "Ein ++wichtiger++ Tag mit ~~Fehler~~ **Erfolg**\n\n1. Dienst\n2. Sport\nschnell & gut <3"
        assertEquals(m, NoteMarkup.fromHtml(NoteMarkup.toHtml(m)))
    }

    @Test fun htmlInMarkupIsEscaped() =
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", NoteMarkup.toHtml("<script>alert(1)</script>"))

    @Test fun underscoreInsideWordIsNotItalic() = assertEquals("<p>datei_name_neu</p>", NoteMarkup.toHtml("datei_name_neu"))

    @Test fun entitiesAndNbsp() = assertEquals("a b & c", NoteMarkup.fromHtml("a&nbsp;b &amp; c"))

    @Test fun emptyStaysEmpty() { assertEquals("", NoteMarkup.toHtml("  ")); assertEquals("", NoteMarkup.fromHtml("")) }

    @Test fun toggleBoldWrapsAndUnwraps() {
        val (t, a, b) = NoteMarkup.toggleInline("ein Wort hier", 4, 8, NoteMarkup.Style.BOLD)
        assertEquals("ein **Wort** hier", t); assertEquals(6 to 10, a to b)
        assertEquals("ein Wort hier", NoteMarkup.toggleInline(t, a, b, NoteMarkup.Style.BOLD).first)
    }

    @Test fun toggleListOnSelectedLines() {
        val (t, _) = NoteMarkup.toggleList("eins\nzwei\ndrei", 2, 7, ordered = false)
        assertEquals("- eins\n- zwei\ndrei", t)
        assertEquals("eins\nzwei\ndrei", NoteMarkup.toggleList(t, 0, 10, ordered = false).first)
        assertEquals("1. eins\n2. zwei\ndrei", NoteMarkup.toggleList(t, 0, 10, ordered = true).first)
    }

    @Test fun spansCoverContentNotMarkers() {
        val (styles, markers) = NoteMarkup.spans("a **b** c")
        assertEquals(listOf(NoteMarkup.Span(NoteMarkup.Style.BOLD, 4, 5)), styles)
        assertEquals(listOf(2..3, 5..6), markers)
    }
}
