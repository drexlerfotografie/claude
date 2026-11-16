package de.mediclever.tagebuch.core.text

/**
 * Notizen sind HTML mit einer kleinen Positivliste. Alles, was von aussen kommt
 * (Import, Zwischenablage, Abgleich), laeuft vor dem Speichern durch [sanitize].
 *
 * Bewusst ohne HTML-Bibliothek: ein kleiner, nachsichtiger Zerleger, der nie Text
 * ungeprueft durchreicht. Ausgegeben werden nur Tags ohne Attribute und maskierter Text.
 */
object NoteHtml {

    private val ALLOWED = setOf("b", "strong", "i", "em", "u", "s", "br", "p", "ul", "ol", "li")

    /** Inhalt dieser Elemente wird komplett verworfen, nicht nur das Tag. */
    private val DROP_WITH_CONTENT = setOf("script", "style")

    /** Elemente, die in der Textfassung einen Zeilenumbruch bedeuten. */
    private val BLOCKS = setOf(
        "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "blockquote", "pre",
    )

    private const val NBSP = ' '

    private sealed interface Token
    private data class Text(val text: String) : Token
    private data class Open(val name: String) : Token
    private data class Close(val name: String) : Token

    /** Bereinigtes HTML: nur erlaubte Tags, keine Attribute, sauber geschachtelt. */
    fun sanitize(html: String): String {
        val out = StringBuilder(html.length + 16)
        val open = ArrayDeque<String>()
        for (token in tokenize(html)) {
            when (token) {
                is Text -> out.append(escape(token.text))
                is Open -> {
                    val name = if (token.name == "div") "p" else token.name
                    if (name !in ALLOWED) continue
                    if (name == "br") {
                        out.append("<br>")
                    } else {
                        out.append('<').append(name).append('>')
                        open.addLast(name)
                    }
                }
                is Close -> {
                    val name = if (token.name == "div") "p" else token.name
                    // Schliessende Tags ohne passendes oeffnendes Tag fallen weg.
                    if (name !in ALLOWED || name == "br" || name !in open) continue
                    while (true) {
                        val top = open.removeLast()
                        out.append("</").append(top).append('>')
                        if (top == name) break
                    }
                }
            }
        }
        while (open.isNotEmpty()) out.append("</").append(open.removeLast()).append('>')
        return out.toString()
    }

    /** Reiner Text: Absaetze und Listenpunkte als Zeilen, Entities aufgeloest. */
    fun toPlainText(html: String): String {
        val sb = StringBuilder(html.length)
        fun newline() {
            if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
        }
        for (token in tokenize(html)) {
            when (token) {
                is Text -> sb.append(token.text.replace(NBSP, ' '))
                is Open -> when (token.name) {
                    "br" -> sb.append('\n')
                    in BLOCKS -> newline()
                }
                is Close -> if (token.name in BLOCKS) newline()
            }
        }
        return sb.lines().joinToString("\n") { it.trimEnd() }.trim()
    }

    /** Anzahl der Woerter (Zeichenfolgen mit mindestens einem Buchstaben oder einer Ziffer). */
    fun wordCount(html: String): Int =
        toPlainText(html).split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    /** Reiner Text als Notiz-HTML: maskiert, Zeilenumbrueche als br. */
    fun fromPlainText(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').joinToString("<br>") { escape(it) }

    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                NBSP -> sb.append("&nbsp;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- Zerleger

    private fun tokenize(html: String): List<Token> {
        val tokens = ArrayList<Token>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                tokens.add(Text(decodeEntities(text.toString())))
                text.setLength(0)
            }
        }
        var i = 0
        val n = html.length
        while (i < n) {
            val c = html[i]
            if (c == '\u0000') { i++; continue }
            if (c != '<') { text.append(c); i++; continue }

            // Kommentare, Doctype, Verarbeitungsanweisungen: komplett weg.
            if (html.startsWith("<!--", i)) {
                val end = html.indexOf("-->", i + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            if (i + 1 < n && (html[i + 1] == '!' || html[i + 1] == '?')) {
                val end = html.indexOf('>', i)
                i = if (end < 0) n else end + 1
                continue
            }

            var j = i + 1
            val closing = j < n && html[j] == '/'
            if (closing) j++
            val nameStart = j
            while (j < n && (html[j].isLetterOrDigit() || html[j] == ':' || html[j] == '-')) j++
            if (j == nameStart || !html[nameStart].isLetter()) {
                // Kein Tag, z. B. "a < b": das Zeichen ist Text.
                text.append('<')
                i++
                continue
            }
            val name = html.substring(nameStart, j).lowercase()
            val end = findTagEnd(html, j)
            flush()
            if (end < 0) {
                // Angefangenes Tag ohne Ende: der Rest wird verworfen, wie im Browser.
                i = n
                continue
            }
            i = end + 1
            if (closing) {
                tokens.add(Close(name))
            } else if (name in DROP_WITH_CONTENT) {
                i = skipElement(html, i, name)
            } else {
                tokens.add(Open(name))
            }
        }
        flush()
        return tokens
    }

    /** Position des schliessenden '>' eines Tags; Anfuehrungszeichen in Attributen werden beachtet. */
    private fun findTagEnd(html: String, from: Int): Int {
        var quote = '\u0000'
        var i = from
        while (i < html.length) {
            val c = html[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        return -1
    }

    /** Ueberspringt alles bis hinter das schliessende Tag von [name]; ohne Ende bis zum Schluss. */
    private fun skipElement(html: String, from: Int, name: String): Int {
        var i = from
        while (true) {
            val at = html.indexOf("</", i)
            if (at < 0) return html.length
            if (html.regionMatches(at + 2, name, 0, name.length, ignoreCase = true)) {
                val end = html.indexOf('>', at)
                return if (end < 0) html.length else end + 1
            }
            i = at + 2
        }
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to NBSP.toString(),
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü",
        "szlig" to "ß", "euro" to "€", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "laquo" to "«", "raquo" to "»", "bdquo" to "„", "ldquo" to "“", "rdquo" to "”",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "copy" to "©", "deg" to "°", "shy" to "",
        "eacute" to "é", "egrave" to "è", "agrave" to "à", "ccedil" to "ç",
    )

    /** Loest Entities auf. Unbekanntes bleibt woertlich stehen und wird spaeter maskiert. */
    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 12)) {
                    val decoded = decodeEntity(s.substring(i + 1, semi))
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun decodeEntity(body: String): String? {
        if (!body.startsWith("#")) return NAMED[body]
        val code = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
            body.substring(2).toIntOrNull(16)
        } else {
            body.substring(1).toIntOrNull()
        } ?: return null
        // Steuerzeichen und ungueltige Codepunkte nicht uebernehmen.
        if (code == 9 || code == 10 || code == 13) return code.toChar().toString()
        if (code < 32 || code in 0xD800..0xDFFF || code > 0x10FFFF || code == 0x7F) return ""
        return String(Character.toChars(code))
    }
}
