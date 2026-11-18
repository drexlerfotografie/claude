package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.model.StartBlocks
import de.mediclever.tagebuch.core.stats.DateRange
import de.mediclever.tagebuch.core.stats.dayStreak
import de.mediclever.tagebuch.core.tone.Lines
import de.mediclever.tagebuch.core.model.Tone
import de.mediclever.tagebuch.core.goals.GoalState
import de.mediclever.tagebuch.core.goals.goalsToday
import de.mediclever.tagebuch.R
import de.mediclever.tagebuch.ui.theme.Brand
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import de.mediclever.tagebuch.core.stats.dayLevels
import de.mediclever.tagebuch.core.stats.weekOf
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.entry.MoodGauge
import de.mediclever.tagebuch.ui.entry.RingDay
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.groupColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Startseite: eine Frage, das Barometer mit dem Ring der laufenden Woche, darunter die Bloecke,
 * die der Nutzer sehen will (Skalen, Monat, ein Satz). Ein Tipp auf den Bogen oeffnet den Eintrag
 * mit diesem Wert.
 */
@Composable
fun StartScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val diary = env.diary
    val today = env.today
    var newSheet by rememberSaveable { mutableStateOf(false) }

    val week = remember(today, diary.settings.weekStart) { weekOf(today, diary.settings.weekStart) }
    val month = YearMonth.from(today)
    val levels = remember(diary, today) { dayLevels(diary, DateRange(minOf(week.first(), month.atDay(1)), maxOf(week.last(), month.atEndOfMonth()))) }
    val ring = week.map { RingDay(levels[it].orEmpty(), it == today) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { PillBar(PillTab.START) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("Wie war dein Tag?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.semantics { heading() })
                }
                IconButton({ newSheet = true }, Modifier.size(48.dp)) { Icon(Icons.Rounded.EditCalendar, "Anderen Tag, Ziel oder wichtigen Tag anlegen") }
            }
            Panel(padding = 10.dp) {
                MoodGauge(null, "", onScore = { nav.go(Routes.newEntry(score = it)) }, ring = ring, emptyLabel = "",
                    ends = weekdayShort(week.first().dayOfWeek.value) to weekdayShort(week.last().dayOfWeek.value))
            }
            for (block in diary.settings.startBlocks) when (block) {
                StartBlocks.SCALES -> ScaleTiles(diary)
                StartBlocks.MATRIX -> MonthMatrix(month, today, levels, diary.settings.weekStart, diary.entries.map { it.localDate }.toSet()) { nav.go(Routes.section(2)) }
                StartBlocks.LINE -> startLine(diary, today, levels)?.let { line ->
                    if (diary.settings.tone == Tone.CLEVERLE) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Image(painterResource(R.drawable.cleverle), null, Modifier.size(52.dp))
                        Surface(color = if (LocalDiaryColors.current.dark) Color(0xFF2A3947) else Brand.RichBlack, contentColor = Color.White, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp), modifier = Modifier.weight(1f)) {
                            Text(line, Modifier.padding(horizontal = 14.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else Text(line, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                StartBlocks.GOALS -> {
                    val goals = remember(diary, today) { goalsToday(diary, today) }
                    if (goals.isNotEmpty()) Panel(onClick = { nav.go(Routes.GOALS) }, padding = 12.dp) {
                        val done = goals.count { it.state == GoalState.DONE }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ziele heute", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            Text("$done von ${goals.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        }
                        goals.firstOrNull { it.state == GoalState.OPEN }?.let { Text("Offen: ${it.goal.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                StartBlocks.LAST -> diary.entries.firstOrNull()?.let { e -> EntryCard(e, onOpen = { nav.go(Routes.editEntry(e.id)) }) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (newSheet) NewSheet { newSheet = false }
}

/** Der eine Satz der Startseite im gewaehlten Tonfall. */
fun startLine(diary: Diary, today: LocalDate, levels: Map<LocalDate, List<Int>>): String? {
    val week = weekOf(today, diary.settings.weekStart)
    fun avg(days: List<LocalDate>, source: Map<LocalDate, List<Int>>) = days.flatMap { source[it].orEmpty() }.map { 6 - it }.takeIf { it.isNotEmpty() }?.average()
    val before = week.map { it.minusDays(7) }
    val latest = diary.entries.firstOrNull()
    val input = Lines.Input(
        week = avg(week, levels), lastWeek = avg(before, dayLevels(diary, DateRange(before.first(), before.last()))),
        daysSinceEntry = latest?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it.localDate), today).toInt().coerceAtLeast(0) },
        streak = dayStreak(diary, today).current, lastScore = latest?.let { diary.levelOf(it) }?.let { 6 - it }, entryCount = diary.entries.size,
    )
    return Lines.start(input, diary.settings.tone, diary.settings.cleverleHints)
}

/** Die Skalen der Startseite: die gewaehlten, sonst die ersten drei. */
fun startScales(diary: Diary): List<Scale> {
    val active = diary.scales.filter { it.archivedAt == null }.sortedBy { it.sortOrder }
    val chosen = diary.settings.startScaleIds.mapNotNull { id -> active.firstOrNull { it.id == id } }
    return chosen.ifEmpty { active }.take(3)
}

/** Anteil 0 bis 1 eines Skalenwerts in seinem Bereich und der Text dazu. */
fun scaleReading(scale: Scale, v: ScaleValue?): Pair<Float, String>? {
    if (v == null) return null
    return when (scale.type) {
        ScaleType.TEXT -> {
            val pts = scale.points.sortedBy { it.position }
            val i = pts.indexOfFirst { it.id == v.pointId }.takeIf { it >= 0 } ?: return null
            (if (pts.size < 2) 1f else i / (pts.size - 1f)) to pts[i].label.replaceFirstChar { it.uppercase() }
        }
        ScaleType.NUMBER -> v.number?.let { n -> ((n - scale.min).toFloat() / (scale.max - scale.min).coerceAtLeast(1)) to n.toString() }
        ScaleType.DURATION -> v.minutes?.let { m -> ((m - scale.min).toFloat() / (scale.max - scale.min).coerceAtLeast(1)) to fmtDuration(m) }
    }
}

@Composable
private fun ScaleTiles(diary: Diary) {
    val nav = LocalNav.current
    val scales = startScales(diary)
    if (scales.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in scales) {
            val last = remember(diary.entries, s.id) { diary.entries.firstNotNullOfOrNull { e -> e.scaleValues.firstOrNull { it.scaleId == s.id } } }
            val reading = scaleReading(s, last)
            Panel(Modifier.weight(1f), onClick = { nav.go(Routes.section(1)) }, padding = 10.dp) {
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(reading?.second ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SegmentBar(reading?.first, groupColor(s.colorIndex), Modifier.padding(top = 4.dp))
                }
            }
        }
        repeat(3 - scales.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Fuenf kleine Segmente; gefuellt bis zum Anteil. */
@Composable
fun SegmentBar(fraction: Float?, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val filled = fraction?.let { 1 + Math.round(it.coerceIn(0f, 1f) * 4) } ?: 0
    Row(modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) Box(Modifier.weight(1f).height(height).clip(RoundedCornerShape(3.dp)).background(if (i <= filled) color else MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Flaeche einer Tageskachel: einfarbig, bei zwei Eintraegen geteilt, ab drei als Verlauf. */
@Composable
fun dayBrush(levels: List<Int>): Brush? {
    val colors = LocalDiaryColors.current
    val cs = levels.map { colors.level(it) }
    return when (cs.size) {
        0 -> null
        1 -> Brush.horizontalGradient(listOf(cs[0], cs[0]))
        2 -> Brush.horizontalGradient(0f to cs[0], 0.5f to cs[0], 0.5f to cs[1], 1f to cs[1])
        else -> Brush.horizontalGradient(cs)
    }
}

@Composable
private fun MonthMatrix(month: YearMonth, today: LocalDate, levels: Map<LocalDate, List<Int>>, weekStart: Int, entryDates: Set<String>, onClick: () -> Unit) {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val lead = Math.floorMod(days.first().dayOfWeek.value - weekStart, 7)
    val done = days.count { !it.isAfter(today) && it.toString() in entryDates }
    val passed = days.count { !it.isAfter(today) }
    Panel(onClick = onClick, padding = 12.dp) {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${month.format(DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY))}: $done von $passed Tagen eingetragen. Öffnet den Kalender." }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(month.format(DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Text("$done von $passed Tagen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0..6) Text(weekdayShort(Math.floorMod(weekStart - 1 + i, 7) + 1), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val cells: List<LocalDate?> = List(lead) { null } + days
            for (row in cells.chunked(7)) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (d in row) {
                    val shape = RoundedCornerShape(7.dp)
                    val brush = d?.let { dayBrush(levels[it].orEmpty()) }
                    Box(Modifier.weight(1f).height(22.dp).clip(shape).then(when {
                        d == null -> Modifier
                        brush != null -> Modifier.background(brush)
                        d == today -> Modifier.background(MaterialTheme.colorScheme.surface).border(3.dp, MaterialTheme.colorScheme.onSurface, shape)
                        else -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    }))
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
