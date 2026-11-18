package de.mediclever.tagebuch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import de.mediclever.tagebuch.core.markup.NoteMarkup

private fun styleOf(s: NoteMarkup.Style): SpanStyle = when (s) {
    NoteMarkup.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    NoteMarkup.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    NoteMarkup.Style.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    NoteMarkup.Style.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
}

/** Gespeichertes Notiz-HTML als formatierter Text, ohne sichtbare Marker. */
fun noteToAnnotated(html: String): AnnotatedString {
    val markup = NoteMarkup.fromHtml(html).lines().joinToString("\n") { if (it.startsWith("- ")) "•  " + it.drop(2) else it }
    val (styles, markers) = NoteMarkup.spans(markup)
    val hidden = BooleanArray(markup.length)
    markers.forEach { r -> for (i in r) hidden[i] = true }
    // Position im Text ohne Marker fuer jede Position im Markup
    val map = IntArray(markup.length + 1)
    var out = 0
    for (i in markup.indices) { map[i] = out; if (!hidden[i]) out++ }
    map[markup.length] = out
    return buildAnnotatedString {
        append(markup.filterIndexed { i, _ -> !hidden[i] })
        styles.forEach { addStyle(styleOf(it.style), map[it.start], map[it.end]) }
    }
}

@Composable
fun NoteText(html: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val text = remember(html) { noteToAnnotated(html) }
    Text(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Im Editor bleiben die Marker sichtbar, aber blass; der Inhalt zeigt schon seine Formatierung. */
class MarkupTransformation(private val markerColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val (styles, markers) = NoteMarkup.spans(text.text)
        val styled = buildAnnotatedString {
            append(text.text)
            styles.forEach { addStyle(styleOf(it.style), it.start, it.end) }
            markers.forEach { addStyle(SpanStyle(color = markerColor), it.first, it.last + 1) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
