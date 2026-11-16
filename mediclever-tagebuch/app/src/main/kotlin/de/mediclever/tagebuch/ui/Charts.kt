package de.mediclever.tagebuch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ein Wert im Verlauf. value null heisst: an dieser Stelle gibt es keine Daten. */
data class ChartPoint(val label: String, val value: Double?, val color: Color? = null, val marked: Boolean = false)

/**
 * Verlauf als Linie oder Balken. min und max legen die Hoehe fest, Beschriftungen unten
 * zeigen jede n-te Stelle. Markierte Stellen (z. B. Tage mit einer Aktivitaet) bekommen
 * einen Punkt unter der Achse.
 */
@Composable
fun SeriesChart(
    points: List<ChartPoint>, min: Double, max: Double, description: String,
    modifier: Modifier = Modifier, bars: Boolean = false, color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 150.dp, labelEvery: Int = 5, markColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = points.size.coerceAtLeast(1)
            val bottom = size.height - 14.dp.toPx()
            val top = 6.dp.toPx()
            val slot = size.width / n
            fun x(i: Int) = slot * i + slot / 2
            fun y(v: Double) = (bottom - (v - min) / (max - min).coerceAtLeast(0.0001) * (bottom - top)).toFloat()
            for (g in 0..2) { val gy = top + (bottom - top) * g / 2; drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()) }
            if (bars) {
                val w = (slot * 0.62f).coerceAtMost(18.dp.toPx())
                points.forEachIndexed { i, p ->
                    val v = p.value ?: return@forEachIndexed
                    val yy = y(v).coerceAtMost(bottom - 2.dp.toPx())
                    drawRoundRect(p.color ?: color, Offset(x(i) - w / 2, yy), Size(w, bottom - yy), CornerRadius(w / 3))
                }
            } else {
                var path: Path? = null
                points.forEachIndexed { i, p ->
                    val v = p.value
                    if (v == null) { path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }; path = null }
                    else { if (path == null) path = Path().apply { moveTo(x(i), y(v)) } else path!!.lineTo(x(i), y(v)) }
                }
                path?.let { drawPath(it, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                val r = if (n > 40) 2.dp.toPx() else 3.5.dp.toPx()
                points.forEachIndexed { i, p -> p.value?.let { drawCircle(p.color ?: color, r, Offset(x(i), y(it))) } }
            }
            points.forEachIndexed { i, p -> if (p.marked) drawCircle(markColor, 3.dp.toPx(), Offset(x(i), size.height - 5.dp.toPx())) }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, p ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelEvery == 0) Text(p.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Anteile als Ring (sweep 360) oder Halbring (sweep 180), mit Text in der Mitte. */
@Composable
fun RingChart(parts: List<Pair<Color, Float>>, description: String, modifier: Modifier = Modifier, half: Boolean = false, size: Dp = 180.dp, center: @Composable () -> Unit = {}) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.width(size).height(if (half) size / 2 + 8.dp else size).semantics { contentDescription = description }, contentAlignment = if (half) Alignment.BottomCenter else Alignment.Center) {
        Canvas(Modifier.width(size).height(if (half) size / 2 + 8.dp else size)) {
            val stroke = this.size.width * 0.13f
            val d = this.size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val total = parts.sumOf { it.second.toDouble() }.toFloat()
            val full = if (half) 180f else 360f
            val start0 = if (half) 180f else -90f
            if (total <= 0f) drawArc(empty, start0, full, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
            else {
                var start = start0
                val gap = if (parts.count { it.second > 0 } > 1) 1.5f else 0f
                for ((c, v) in parts) {
                    if (v <= 0f) continue
                    val sweep = full * v / total
                    drawArc(c, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        center()
    }
}

/** Waagerechter Balken mit Anteil 0 bis 1. */
@Composable
fun ShareBar(share: Float, color: Color, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(color, size = Size((size.width * share.coerceIn(0f, 1f)).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}
