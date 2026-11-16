package de.mediclever.tagebuch.core.stats

private val BLOCK_TAG =
    Regex("</?(p|div|br|li|ul|ol|h[1-6]|blockquote|tr|td|th|table|hr)\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")

/**
 * Reiner Text einer Notiz: Absatz-, Zeilen- und Listen-Tags werden zu einem Leerzeichen,
 * alle uebrigen Tags entfallen ersatzlos, die Zeichen &nbsp; &amp; &lt; &gt; &quot; &#39; werden
 * zurueckgewandelt, Leerraum wird zu einem Leerzeichen zusammengefasst und am Rand entfernt.
 */
fun plainText(html: String): String {
    if (html.isEmpty()) return ""
    val noTags = if (html.indexOf('<') < 0) html else ANY_TAG.replace(BLOCK_TAG.replace(html, " "), "")
    val decoded = if (noTags.indexOf('&') < 0) noTags else noTags
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
    return WHITESPACE.replace(decoded, " ").trim()
}

/** Anzahl der Woerter: durch Leerraum getrennte Teile von [plainText]; leerer Text hat 0 Woerter. */
fun wordCount(html: String): Int {
    val text = plainText(html)
    if (text.isEmpty()) return 0
    var n = 1
    for (c in text) if (c == ' ') n++
    return n
}
