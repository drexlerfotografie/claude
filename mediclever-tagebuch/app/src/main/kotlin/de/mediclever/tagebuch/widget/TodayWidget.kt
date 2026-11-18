package de.mediclever.tagebuch.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.mediclever.tagebuch.MainActivity
import de.mediclever.tagebuch.TagebuchApp
import de.mediclever.tagebuch.core.stats.dayStreak
import de.mediclever.tagebuch.ui.theme.moodColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate

/**
 * Homescreen-Widget: fuenf Stufen zum Antippen und die laufende Reihe. Ein Tipp oeffnet den
 * neuen Eintrag mit vorgewaehlter Stufe. Inhalte des Tagebuchs zeigt das Widget nicht.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TagebuchApp
        val diary = app.container.repo.diary.filterNotNull().first()
        val colors = moodColors(diary.settings)
        val streak = dayStreak(diary, LocalDate.now()).current
        val doneToday = diary.entries.any { it.localDate == LocalDate.now().toString() }
        val faces = (1..5).map { face(it, colors[it - 1].toArgb(), 96) }
        provideContent { GlanceTheme { Content(context, faces, streak, doneToday) } }
    }

    @Composable
    private fun Content(context: Context, faces: List<Bitmap>, streak: Int, doneToday: Boolean) {
        Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(22.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (doneToday) "Heute eingetragen" else "Wie geht es dir?", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Text(if (streak == 1) "1 Tag in Folge" else "$streak Tage in Folge", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                faces.forEachIndexed { i, bmp ->
                    Image(ImageProvider(bmp), "Eintrag mit Stufe ${i + 1} anlegen", GlanceModifier.size(44.dp).clickable(actionStartActivity(
                        Intent(context, MainActivity::class.java).putExtra("route", "entry/new?date=").putExtra("level", i + 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).setAction("widget${i + 1}"))))
                    if (i < 4) Spacer(GlanceModifier.width(6.dp))
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) { runCatching { TodayWidget().updateAll(context) } }

        /** Dasselbe Formprinzip wie MoodFace in der App, hier fuer ein Bitmap gezeichnet. */
        fun face(level: Int, color: Int, px: Int): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); val w = px.toFloat()
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            c.drawRoundRect(RectF(0f, 0f, w, w), w * 0.32f, w * 0.32f, bg)
            val lum = (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(color)) / 255
            val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (lum > 0.6) 0xFF11202C.toInt() else 0xFFFFFFFF.toInt(); strokeWidth = w * 0.07f; strokeCap = Paint.Cap.ROUND }
            c.drawCircle(w * 0.33f, w * 0.40f, w * 0.055f, fg); c.drawCircle(w * 0.67f, w * 0.40f, w * 0.055f, fg)
            val my = w * 0.66f
            if (level == 5) {
                c.drawPath(Path().apply { moveTo(w * 0.30f, my + w * 0.14f); quadTo(w * 0.5f, my - w * 0.20f, w * 0.70f, my + w * 0.14f); close() }, fg)
                c.drawLine(w * 0.24f, w * 0.30f, w * 0.40f, w * 0.23f, fg); c.drawLine(w * 0.76f, w * 0.30f, w * 0.60f, w * 0.23f, fg)
            } else if (level == 1) c.drawPath(Path().apply { moveTo(w * 0.28f, my - w * 0.05f); quadTo(w * 0.5f, my + w * 0.26f, w * 0.72f, my - w * 0.05f); close() }, fg)
            else {
                val bend = when (level) { 2 -> 0.11f; 3 -> 0f; 4 -> -0.11f; else -> -0.20f } * w
                val y = my + if (bend < 0) w * 0.06f else 0f
                fg.style = Paint.Style.STROKE
                c.drawPath(Path().apply { moveTo(w * 0.3f, y); quadTo(w * 0.5f, y + bend * 1.6f, w * 0.7f, y) }, fg)
            }
            return bmp
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
