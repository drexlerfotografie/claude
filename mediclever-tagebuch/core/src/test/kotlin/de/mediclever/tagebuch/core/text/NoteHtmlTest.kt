package de.mediclever.tagebuch.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteHtmlTest {

    @Test fun keepsAllowedTags() {
        val html = "<p>Heute <b>fett</b>, <strong>stark</strong>, <i>schräg</i>, <em>betont</em>, " +
            "<u>unter</u>, <s>durch</s><br></p><ul><li>eins</li></ul><ol><li>zwei</li></ol>"
        assertEquals(html, NoteHtml.sanitize(html))
    }

    @Test fun dropsAttributes() {
        assertEquals("<p>Text</p>", NoteHtml.sanitize("<p style=\"color:red\" onclick='x()' class=a>Text</p>"))
        assertEquals("<b>x</b>", NoteHtml.sanitize("<b title=\"a > b\">x</b>"))
    }

    @Test fun divBecomesParagraph() {
        assertEquals("<p>eins</p><p>zwei</p>", NoteHtml.sanitize("<div>eins</div><DIV class='x'>zwei</DIV>"))
    }

    @Test fun brVariants() {
        assertEquals("a<br>b<br>c<br>d", NoteHtml.sanitize("a<br>b<br/>c<BR />d</br>"))
    }

    @Test fun removesScriptAndStyleWithContent() {
        assertEquals("ab", NoteHtml.sanitize("a<script>alert('x')</script>b"))
        assertEquals("ab", NoteHtml.sanitize("a<STYLE type=\"text/css\">p{color:red}</style >b"))
        assertEquals("a", NoteHtml.sanitize("a<script>alert(1)"))
        assertEquals("ab", NoteHtml.sanitize("a<script>if (a</b) x()</script>b"))
    }

    @Test fun stripsUnknownTagsToText() {
        assertEquals("Titel Link Bild", NoteHtml.sanitize("<h1>Titel</h1> <a href=\"javascript:alert(1)\">Link</a> <img src=x onerror=alert(1)>Bild"))
        assertEquals("x", NoteHtml.sanitize("<iframe src=\"https://example.org\"></iframe>x"))
        assertEquals("", NoteHtml.sanitize("<svg onload=alert(1)>"))
    }

    @Test fun xssCases() {
        val cases = listOf(
            "<scr<script>ipt>alert(1)</scr</script>ipt>",
            "<img src=\"x\" onerror=\"alert(1)\">",
            "<b onmouseover=alert(1)>x</b>",
            "<p><script>alert(1)</p>",
            "<<script>alert(1);//<</script>",
            "<a href='javascript:alert(1)'>x</a>",
            "<body onload=alert(1)>",
            "<math><mi xlink:href=\"data:x,<script>alert(1)</script>\">",
            "<!--<script>-->alert(1)<!--</script>-->",
            "<p title=\"</p><script>alert(1)</script>\">x</p>",
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            "&#60;script&#62;alert(1)&#60;/script&#62;",
            "&#x3c;img src=x onerror=alert(1)&#x3e;",
            "<b",
            "<b style=\"",
            "<\u0000script>alert(1)</script>",
            "<ScRiPt\n>alert(1)</sCrIpT\n>",
        )
        val tag = Regex("<(/?)([a-zA-Z0-9]*)([^>]*)>")
        for (case in cases) {
            val out = NoteHtml.sanitize(case)
            // Jedes '<' in der Ausgabe gehoert zu einem erlaubten Tag ohne Attribute.
            var rest = out
            for (m in tag.findAll(out)) {
                assertTrue(m.groupValues[2] in setOf("b", "strong", "i", "em", "u", "s", "br", "p", "ul", "ol", "li"), "$case -> $out")
                assertEquals("", m.groupValues[3], "$case -> $out")
                rest = rest.replace(m.value, "")
            }
            assertFalse('<' in rest || '>' in rest, "$case -> $out")
            assertFalse(out.contains("onerror=\"") || out.contains("<script", ignoreCase = true), "$case -> $out")
            // Nochmaliges Bereinigen aendert nichts mehr.
            assertEquals(out, NoteHtml.sanitize(out), case)
        }
    }

    @Test fun escapedMarkupStaysEscaped() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", NoteHtml.sanitize("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertEquals("&lt;script&gt;", NoteHtml.sanitize("&#60;script&#x3E;"))
        assertEquals("a &lt; b &amp; c &gt; d", NoteHtml.sanitize("a < b & c > d"))
        assertEquals("&amp;unbekannt; &amp;", NoteHtml.sanitize("&unbekannt; &amp;"))
    }

    @Test fun nbspIsKeptInHtmlAndBecomesSpaceInText() {
        assertEquals("a&nbsp;b&nbsp;c", NoteHtml.sanitize("a&nbsp;b c"))
        assertEquals("a b c", NoteHtml.toPlainText("a&nbsp;b c"))
    }

    @Test fun fixesNesting() {
        assertEquals("<p><b>x</b></p>", NoteHtml.sanitize("<p><b>x</p>"))
        assertEquals("x<b>y</b>", NoteHtml.sanitize("</i>x<b>y"))
    }

    @Test fun plainText() {
        assertEquals("Erste Zeile\nZweite Zeile", NoteHtml.toPlainText("<p>Erste Zeile</p><p>Zweite Zeile</p>"))
        assertEquals("a\nb", NoteHtml.toPlainText("a<br>b"))
        assertEquals("Liste:\neins\nzwei", NoteHtml.toPlainText("Liste:<ul><li>eins</li><li>zwei</li></ul>"))
        assertEquals("Schöne Grüße & <mehr>", NoteHtml.toPlainText("Sch&ouml;ne Gr&uuml;&szlig;e &amp; &lt;mehr&gt;<script>x</script>"))
        assertEquals("", NoteHtml.toPlainText(""))
    }

    @Test fun counting() {
        assertEquals(0, NoteHtml.wordCount(""))
        assertEquals(0, NoteHtml.wordCount("<p> – </p>"))
        assertEquals(5, NoteHtml.wordCount("<p>Heute war&nbsp;ein <b>guter</b> Tag.</p>"))
        assertEquals(4, NoteHtml.wordCount("<ul><li>eins</li><li>zwei</li></ul>drei<br>vier"))
    }

    @Test fun fromPlainTextRoundTrip() {
        val text = "Zeile 1 <b> & mehr\nZeile 2\n\nZeile 4"
        val html = NoteHtml.fromPlainText(text)
        assertEquals("Zeile 1 &lt;b&gt; &amp; mehr<br>Zeile 2<br><br>Zeile 4", html)
        assertEquals(html, NoteHtml.sanitize(html))
        assertEquals(text, NoteHtml.toPlainText(html))
        assertEquals("a<br>b", NoteHtml.fromPlainText("a\r\nb"))
    }
}
