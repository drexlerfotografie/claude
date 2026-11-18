package de.mediclever.tagebuch.core.markup

/**
 * Notizen werden als bereinigtes HTML gespeichert, im Editor aber als einfacher Text mit
 * Auszeichnungen bearbeitet: **fett**, _kursiv_, ++unterstrichen++, ~~durchgestrichen~~,
 * Zeilen mit "- " (Liste) oder "1. " (nummerierte Liste).
 */
object NoteMarkup {
    enum class Style { BOLD, ITALIC, UNDERLINE, STRIKE }

    /** Ein ausgezeichneter Bereich im Markup-Text; die Marker selbst liegen in den Marker-Bereichen. */
    data class Span(val style: Style, val start: Int, val end: Int)

    private val inline = listOf(
        Triple(Style.BOLD, "**", Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")),
        Triple(Style.STRIKE, "~~", Regex("""~~(?=\S)(.+?)(?<=\S)~~""")),
        Triple(Style.UNDERLINE, "++", Regex("""\+\+(?=\S)(.+?)(?<=\S)\+\+""")),
        Triple(Style.ITALIC, "_", Regex("""(?<![\p{L}\p{N}_])_(?=\S)(.+?)(?<=\S)_(?![\p{L}\p{N}_])""")),
    )
    private val bullet = Regex("""^- (.*)$""")
    private val numbered = Regex("""^\d+\. (.*)$""")

    /** Bereiche fuer die Darstellung im Editor: Stil ueber den Inhalt, Marker separat zum Abdunkeln. */
    fun spans(markup: String): Pair<List<Span>, List<IntRange>> {
        val styles = mutableListOf<Span>()
        val markers = mutableListOf<IntRange>()
        for ((style, mark, rx) in inline) for (m in rx.findAll(markup)) {
            if ('\n' in m.value) continue
            styles += Span(style, m.range.first + mark.length, m.range.last + 1 - mark.length)
            markers += m.range.first until m.range.first + mark.length
            markers += (m.range.last + 1 - mark.length)..m.range.last
        }
        return styles to markers
    }

    fun toHtml(markup: String): String {
        val out = StringBuilder()
        var list: String? = null
        val para = mutableListOf<String>()
        fun flushPara() {
            if (para.isNotEmpty()) { out.append("<p>").append(para.joinToString("<br>")).append("</p>"); para.clear() }
        }
        fun closeList() { list?.let { out.append("</$it>") }; list = null }
        for (line in markup.trimEnd().lines()) {
            val b = bullet.matchEntire(line)
            val n = numbered.matchEntire(line)
            val kind = if (b != null) "ul" else if (n != null) "ol" else null
            if (line.isBlank()) {
                // Leerzeile als eigener leerer Absatz, damit sie den Hin- und Rueckweg uebersteht
                flushPara(); closeList(); out.append("<p><br></p>")
            } else if (kind != null) {
                flushPara()
                if (list != kind) { closeList(); out.append("<$kind>"); list = kind }
                out.append("<li>").append(inlineToHtml((b ?: n)!!.groupValues[1])).append("</li>")
            } else {
                closeList()
                para += inlineToHtml(line)
            }
        }
        flushPara(); closeList()
        return if (markup.isBlank()) "" else out.toString()
    }

    private fun inlineToHtml(text: String): String {
        var s = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val tags = mapOf(Style.BOLD to "b", Style.STRIKE to "s", Style.UNDERLINE to "u", Style.ITALIC to "i")
        for ((style, _, rx) in inline) s = rx.replace(s) { "<${tags[style]}>${it.groupValues[1]}</${tags[style]}>" }
        return s
    }

    private val token = Regex("""<(/?)([a-zA-Z0-9]+)[^>]*>""")

    fun fromHtml(html: String): String {
        if (html.isBlank()) return ""
        val out = StringBuilder()
        val lists = ArrayDeque<Pair<String, Int>>()
        fun newline() { if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n') }
        var pos = 0
        fun text(raw: String) {
            val t = decode(raw)
            // Zeilenumbrueche im Quelltext sind in HTML nur Leerraum
            out.append(if (out.isEmpty() || out.endsWith("\n")) t.replace('\n', ' ').trimStart() else t.replace('\n', ' '))
        }
        for (m in token.findAll(html)) {
            if (m.range.first > pos) text(html.substring(pos, m.range.first))
            pos = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            when (m.groupValues[2].lowercase()) {
                "b", "strong" -> out.append("**")
                "i", "em" -> out.append("_")
                "u" -> out.append("++")
                "s", "strike", "del" -> out.append("~~")
                "br" -> out.append('\n')
                "p", "div" -> newline()
                "ul", "ol" -> { newline(); if (closing) lists.removeLastOrNull() else lists.addLast(m.groupValues[2].lowercase() to 0) }
                "li" -> if (!closing) {
                    newline()
                    val cur = lists.removeLastOrNull() ?: ("ul" to 0)
                    lists.addLast(cur.first to cur.second + 1)
                    out.append(if (cur.first == "ol") "${cur.second + 1}. " else "- ")
                } else newline()
            }
        }
        if (pos < html.length) text(html.substring(pos))
        return out.toString().lines().joinToString("\n") { it.trimEnd() }.trim('\n')
    }

    private fun decode(s: String): String = s
        .replace("&nbsp;", " ").replace("&#160;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

    /** Setzt oder entfernt einen Marker um die Auswahl. Liefert neuen Text und neue Auswahl. */
    fun toggleInline(text: String, start: Int, end: Int, style: Style): Triple<String, Int, Int> {
        val mark = inline.first { it.first == style }.second
        val a = minOf(start, end); val b = maxOf(start, end)
        val wrapped = a >= mark.length && text.startsWith(mark, a - mark.length) && text.startsWith(mark, b)
        return if (wrapped) Triple(text.removeRange(b, b + mark.length).removeRange(a - mark.length, a), a - mark.length, b - mark.length)
        else Triple(text.substring(0, a) + mark + text.substring(a, b) + mark + text.substring(b), a + mark.length, b + mark.length)
    }

    /** Macht die Zeilen der Auswahl zu Listenzeilen oder nimmt die Liste wieder weg. */
    fun toggleList(text: String, start: Int, end: Int, ordered: Boolean): Pair<String, Int> {
        val from = text.lastIndexOf('\n', (minOf(start, end) - 1).coerceAtLeast(0)).let { if (minOf(start, end) == 0) 0 else it + 1 }
        val to = text.indexOf('\n', maxOf(start, end)).let { if (it < 0) text.length else it }
        val lines = text.substring(from, to).split('\n')
        val isList = lines.all { (if (ordered) numbered else bullet).matches(it) }
        val changed = lines.mapIndexed { i, l ->
            val plain = bullet.matchEntire(l)?.groupValues?.get(1) ?: numbered.matchEntire(l)?.groupValues?.get(1) ?: l
            if (isList) plain else if (ordered) "${i + 1}. $plain" else "- $plain"
        }.joinToString("\n")
        return (text.substring(0, from) + changed + text.substring(to)) to (from + changed.length)
    }
}
