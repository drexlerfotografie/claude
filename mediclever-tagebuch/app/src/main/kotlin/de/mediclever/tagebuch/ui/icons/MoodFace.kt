package de.mediclever.tagebuch.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.onColor

/**
 * Eigene Stimmungssymbole: eine abgerundete Kachel mit Augen und Mund. Die Stufe bestimmt die
 * Mundform, die Variante (a bis d) Augen und Zusaetze. So ist jede Stufe an der Form erkennbar,
 * nicht nur an der Farbe.
 */
val MoodVariants = listOf("a", "b", "c", "d")

@Composable
fun MoodFace(level: Int, variant: String = "a", size: Dp = 40.dp, modifier: Modifier = Modifier, muted: Boolean = false) {
    val base = LocalDiaryColors.current.level(level)
    val bg = if (muted) base.copy(alpha = 0.18f) else base
    val fg = if (muted) base else onColor(base)
    Canvas(modifier.size(size).clearAndSetSemantics { }) { drawFace(level, variant, bg, fg) }
}

private fun DrawScope.drawFace(level: Int, variant: String, bg: Color, fg: Color) {
    val w = size.minDimension
    drawRoundRect(bg, size = Size(w, w), cornerRadius = CornerRadius(w * 0.32f))
    val stroke = Stroke(w * 0.07f, cap = StrokeCap.Round)
    val eyeY = w * 0.40f
    val lx = w * 0.33f
    val rx = w * 0.67f
    val r = w * 0.055f

    fun dotEyes() { drawCircle(fg, r, Offset(lx, eyeY)); drawCircle(fg, r, Offset(rx, eyeY)) }
    fun arcEyes(up: Boolean) {
        for (x in listOf(lx, rx)) {
            val p = Path().apply {
                moveTo(x - w * 0.07f, eyeY)
                quadraticTo(x, eyeY + (if (up) -1 else 1) * w * 0.08f, x + w * 0.07f, eyeY)
            }
            drawPath(p, fg, style = stroke)
        }
    }
    fun brows(angry: Boolean) {
        val d = w * 0.05f * if (angry) 1 else -1
        drawLine(fg, Offset(lx - w * 0.08f, eyeY - w * 0.13f - d), Offset(lx + w * 0.08f, eyeY - w * 0.13f + d), stroke.width, StrokeCap.Round)
        drawLine(fg, Offset(rx - w * 0.08f, eyeY - w * 0.13f + d), Offset(rx + w * 0.08f, eyeY - w * 0.13f - d), stroke.width, StrokeCap.Round)
    }

    when (variant) {
        "b" -> arcEyes(up = level <= 2)
        "c" -> { dotEyes(); brows(angry = level >= 4) }
        "d" -> { drawCircle(fg, r, Offset(lx, eyeY)); drawLine(fg, Offset(rx - w * 0.07f, eyeY), Offset(rx + w * 0.07f, eyeY), stroke.width, StrokeCap.Round) }
        else -> dotEyes()
    }

    // Mund: Kruemmung von breitem Laecheln (Stufe 1) bis deutlich nach unten (Stufe 5)
    val my = w * 0.66f
    val half = w * 0.20f
    val bend = when (level) { 1 -> 0.20f; 2 -> 0.11f; 3 -> 0f; 4 -> -0.11f; else -> -0.20f } * w
    if (level == 5) {
        // offener, nach unten gezogener Mund als gefuellte Form: Gegenstueck zu Stufe 1
        val p = Path().apply {
            moveTo(w * 0.30f, my + w * 0.14f)
            quadraticTo(w * 0.5f, my - w * 0.20f, w * 0.70f, my + w * 0.14f)
            close()
        }
        drawPath(p, fg)
    } else if (level == 1) {
        // offenes Laecheln als gefuellte Form
        val p = Path().apply {
            moveTo(w * 0.28f, my - w * 0.05f)
            quadraticTo(w * 0.5f, my + w * 0.26f, w * 0.72f, my - w * 0.05f)
            close()
        }
        drawPath(p, fg)
    } else {
        val y = my + if (bend < 0) w * 0.06f else 0f
        val p = Path().apply {
            moveTo(w * 0.5f - half, y)
            quadraticTo(w * 0.5f, y + bend * 1.6f, w * 0.5f + half, y)
        }
        drawPath(p, fg, style = stroke)
    }
    if (level == 5 && variant != "c") {
        // nach aussen abfallende Brauen als zweites Formmerkmal der schlechtesten Stufe
        drawLine(fg, Offset(lx - w * 0.09f, eyeY - w * 0.10f), Offset(lx + w * 0.07f, eyeY - w * 0.17f), stroke.width, StrokeCap.Round)
        drawLine(fg, Offset(rx + w * 0.09f, eyeY - w * 0.10f), Offset(rx - w * 0.07f, eyeY - w * 0.17f), stroke.width, StrokeCap.Round)
    }
}
