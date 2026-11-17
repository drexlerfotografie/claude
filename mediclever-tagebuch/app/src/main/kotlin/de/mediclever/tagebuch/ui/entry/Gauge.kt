package de.mediclever.tagebuch.ui.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Das Barometer: ein Halbbogen von "sehr schlecht" (links) bis "sehr gut" (rechts) mit fuenf
 * Rastpunkten. Tippen oder Ziehen setzt den Wert. Der Wert ist die Punktzahl 1 bis 5
 * (5 ist die beste), also 6 minus Stufe; null heisst: noch nichts gewaehlt.
 */
@Composable
fun MoodGauge(score: Int?, label: String, onScore: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDiaryColors.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val knob = MaterialTheme.colorScheme.surface
    val knobDot = MaterialTheme.colorScheme.onSurface
    val gap = MaterialTheme.colorScheme.surface
    val current by rememberUpdatedState(onScore)
    val active = score?.let { colors.level(6 - it) } ?: track

    // Position auf dem Bogen: Winkel 180 Grad (links) bis 0 Grad (rechts)
    fun scoreAt(pos: Offset, size: Size): Int {
        val cx = size.width / 2; val cy = size.height - 24.dp.value * (size.width / 320f)
        val angle = atan2((cy - pos.y).coerceAtLeast(0f), pos.x - cx)        // 0..PI
        val fraction = 1f - (angle / PI.toFloat())
        return (1 + (fraction * 4f).roundToInt()).coerceIn(1, 5)
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Canvas(
                Modifier.width(320.dp).height(184.dp)
                    .semantics {
                        contentDescription = "Stimmung"
                        stateDescription = if (score == null) "noch nicht gewählt" else "$score von 5, $label"
                        progressBarRangeInfo = ProgressBarRangeInfo((score ?: 0).toFloat(), 1f..5f, 3)
                        setProgress { v -> current(v.roundToInt().coerceIn(1, 5)); true }
                    }
                    .pointerInput(Unit) { detectTapGestures { current(scoreAt(it, Size(size.width.toFloat(), size.height.toFloat()))) } }
                    .pointerInput(Unit) { detectDragGestures { change, _ -> current(scoreAt(change.position, Size(size.width.toFloat(), size.height.toFloat()))) } },
            ) {
                val stroke = 24.dp.toPx()
                val radius = (size.width - stroke - 16.dp.toPx()) / 2
                val center = Offset(size.width / 2, size.height - 24.dp.toPx())
                val box = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)
                drawArc(track, 180f, 180f, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                fun point(s: Int): Offset {
                    val a = PI * (1 - (s - 1) / 4.0)
                    return Offset(center.x + radius * cos(a).toFloat(), center.y - radius * sin(a).toFloat())
                }
                if (score != null) {
                    drawArc(active, 180f, 180f * (score - 1) / 4f + 0.01f, false, topLeft, box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // Rastpunkte
                for (s in 1..5) drawCircle(gap, 3.dp.toPx(), point(s))
                if (score != null) {
                    drawCircle(knob, 19.dp.toPx(), point(score))
                    drawCircle(active, 19.dp.toPx(), point(score), style = Stroke(4.dp.toPx()))
                    drawCircle(knobDot, 6.dp.toPx(), point(score))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 10.dp)) {
                Text(score?.toString() ?: "–", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                Text(if (score == null) "Wie geht es dir?" else label, style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.width(320.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("sehr schlecht", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("sehr gut", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
