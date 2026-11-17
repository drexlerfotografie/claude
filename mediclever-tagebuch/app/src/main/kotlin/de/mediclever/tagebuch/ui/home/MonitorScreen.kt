package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.R
import de.mediclever.tagebuch.core.goals.GoalState
import de.mediclever.tagebuch.core.goals.goalsToday
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.stats.DateRange
import de.mediclever.tagebuch.core.stats.Granularity
import de.mediclever.tagebuch.core.stats.dayStreak
import de.mediclever.tagebuch.core.stats.monthStats
import de.mediclever.tagebuch.core.stats.moodCourse
import de.mediclever.tagebuch.core.stats.roundedLevel
import de.mediclever.tagebuch.core.stats.scaleStats
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.groupColor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fragen von Cleverle, nach Tag wechselnd. Kurz, ruhig, aus dem Dienstalltag. */
private val cleverleFragen = listOf(
    "Wie war dein Dienst?", "Wie geht es dir nach der Schicht?", "Was nimmst du von heute mit?",
    "Wie viel Kraft ist noch da?", "Was hat dir heute gutgetan?", "Wie hast du geschlafen?", "Was liegt dir noch im Kopf?",
)

/**
 * Startseite "Monitor": der eigene Verlauf auf einen Blick, wie Werte auf einem
 * Ueberwachungsmonitor. Oben "Messen" fuer den Eintrag von heute, darunter Kacheln, die in die
 * Bereiche fuehren, unten die Lasche mit dem Menue.
 */
@Composable
fun MonitorScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val diary = env.diary
    val colors = LocalDiaryColors.current
    val today = env.today
    var newSheet by rememberSaveable { mutableStateOf(false) }

    val range = remember(today) { DateRange(today.minusDays(13), today) }
    val course = remember(diary, today) { moodCourse(diary, range, Granularity.DAY) }
    val streak = remember(diary, today) { dayStreak(diary, today) }
    val goals = remember(diary, today) { goalsToday(diary, today) }
    val scales = remember(diary, today) { scaleStats(diary, range, DateRange(today.minusDays(27), today.minusDays(14)), Granularity.DAY).filter { it.dayCount > 0 }.take(2) }
    val insights = remember(diary, today) { monthStats(diary, YearMonth.from(today), today).stats.insights.drop(2).take(2) }
    val last = course.lastOrNull { it.averageScore != null }
    val average = course.mapNotNull { it.averageScore }.takeIf { it.isNotEmpty() }?.average()
    val todayDone = diary.entries.any { it.localDate == today.toString() }
    fun num(v: Double) = String.format(Locale.GERMANY, "%.1f", v)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Die Lasche: die wichtigsten Bereiche direkt, alles Weitere dahinter
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).then(Modifier).clickable(onClickLabel = "Menü öffnen") { nav.go(Routes.MENU) }) { Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxSize()) {} }
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((label, route) in listOf("Einträge" to Routes.section(0), "Auswertung" to Routes.section(1), "Kalender" to Routes.section(2), "Alles" to Routes.MENU))
                            FilledTonalButton({ nav.go(route) }, Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("Dein Verlauf", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.semantics { heading() })
                }
                IconButton({ nav.go(Routes.SEARCH) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Search, "Suchen") }
                IconButton({ newSheet = true }, Modifier.size(48.dp)) { Icon(Icons.Rounded.EditCalendar, "Anderen Tag, Ziel oder wichtigen Tag anlegen") }
            }

            // Cleverle fragt, solange heute noch nichts gemessen ist
            Panel(onClick = { nav.go(Routes.newEntry()) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.cleverle), null, Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (todayDone) "Heute gemessen" else cleverleFragen[Math.floorMod(today.toEpochDay().toInt(), cleverleFragen.size)], style = MaterialTheme.typography.titleMedium)
                        Text(if (todayDone) "Du kannst jederzeit eine weitere Messung anlegen." else "Eine Messung dauert eine halbe Minute.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button({ nav.go(Routes.newEntry()) }, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Messen") }
                }
            }

            // Stimmung der letzten 14 Tage
            Panel(onClick = { nav.go(Routes.section(1)) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STIMMUNG · 14 TAGE", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (average != null) Text("Ø ${num(average)} von 5", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (last == null) Text("Noch keine Messung in den letzten 14 Tagen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val level = roundedLevel(last.averageScore!!)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(num(last.averageScore!!).removeSuffix(",0"), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                        Surface(color = colors.level(level), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 10.dp).size(14.dp)) {}
                        Text((if (last.date == today) "heute" else fmtDayShort(last.date)) + ": " + (diary.moods.firstOrNull { it.level == level }?.name ?: levelName(level)), Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SeriesChart(course.map { ChartPoint(it.date.dayOfMonth.toString(), it.averageScore, it.averageScore?.let { v -> colors.level(roundedLevel(v)) }) }, 1.0, 5.0,
                        "Stimmung der letzten 14 Tage" + (average?.let { ", Durchschnitt ${num(it)} von 5" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant, labelEvery = 2, height = 96.dp)
                }
            }

            // Kacheln: Werte der eigenen Skalen, Tage in Folge, Ziele
            val tiles = buildList<@Composable (Modifier) -> Unit> {
                for (sc in scales) add { m ->
                    val v = sc.series.lastOrNull { it.value != null }?.value
                    val text = when {
                        v == null -> "–"
                        sc.scale.type == ScaleType.DURATION -> fmtTime(v.toInt())
                        sc.scale.type == ScaleType.TEXT -> sc.scale.points.minByOrNull { p -> kotlin.math.abs(p.position - v) }?.label ?: num(v)
                        else -> num(v).removeSuffix(",0")
                    }
                    Tile(sc.scale.name, text, sc.average?.let { "Ø " + if (sc.scale.type == ScaleType.DURATION) fmtDuration(it.toInt()) else num(it) } ?: "", groupColor(sc.scale.colorIndex), m) { nav.go(Routes.section(1)) }
                }
                add { m -> Tile("Tage in Folge", streak.current.toString(), "längste Reihe: ${streak.longest}", MaterialTheme.colorScheme.primary, m) { nav.go(Routes.ACHIEVEMENTS) } }
                add { m ->
                    val done = goals.count { it.state == GoalState.DONE }
                    val open = goals.firstOrNull { it.state == GoalState.OPEN }
                    Tile("Ziele heute", if (goals.isEmpty()) "–" else "$done / ${goals.size}", when { goals.isEmpty() -> "noch kein Ziel"; open != null -> "offen: ${open.goal.name}"; else -> "alles erledigt" }, MaterialTheme.colorScheme.tertiary, m) { nav.go(Routes.GOALS) }
                }
            }
            for (row in tiles.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in row) t(Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }

            if (insights.isNotEmpty()) Panel(onClick = { nav.go(Routes.section(1)) }, padding = 14.dp) {
                Text("AUFFÄLLIG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (line in insights) Text(line, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (newSheet) NewSheet { newSheet = false }
}

@Composable
private fun Tile(label: String, value: String, hint: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier, onClick = onClick, padding = 14.dp) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = color, shape = RoundedCornerShape(3.dp), modifier = Modifier.width(6.dp).height(28.dp)) {}
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
