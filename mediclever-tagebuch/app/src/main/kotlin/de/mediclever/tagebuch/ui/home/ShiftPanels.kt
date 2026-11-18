package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.shift.BlockCourse
import de.mediclever.tagebuch.core.shift.ShiftAverage
import de.mediclever.tagebuch.core.shift.ShiftStats
import de.mediclever.tagebuch.core.shift.Shifts
import de.mediclever.tagebuch.core.stats.DateRange
import de.mediclever.tagebuch.core.stats.PeriodStats
import de.mediclever.tagebuch.core.stats.roundedLevel
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.theme.Brand
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import java.util.Locale

private fun num(v: Double) = String.format(Locale.GERMANY, "%.1f", v)

/** Der eine Satz oben: schwerste Dienstart gegen freie Tage, sonst gegen den Schnitt. */
fun shiftHeadline(averages: List<ShiftAverage>, overall: Double?): String? {
    val work = averages.filter { !it.free && it.entries >= 2 }.minByOrNull { it.average } ?: return null
    val free = averages.filter { it.free && it.entries >= 2 }.maxByOrNull { it.average }
    val first = "An Tagen mit ${work.tag.name} liegt dein Wert im Schnitt bei ${num(work.average)}."
    return when {
        free != null -> "$first An freien Tagen bei ${num(free.average)}."
        overall != null -> "$first Über alle Tage bei ${num(overall)}."
        else -> first
    }
}

/** Oben in der Auswertung, wenn Schichtdienst eingeschaltet ist: das Wichtigste, Wert nach Dienstart, Dienstblock. */
@Composable
fun ShiftPanels(s: PeriodStats, period: String) {
    val env = LocalEnv.current
    val diary = env.diary
    val colors = LocalDiaryColors.current
    val averages = remember(diary, s.range) { ShiftStats.averages(diary, s.range) }
    if (averages.isEmpty()) return

    shiftHeadline(averages, s.averageScore)?.let { line ->
        Surface(color = if (colors.dark) Color(0xFF2A3947) else Brand.RichBlack, contentColor = Color.White, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DAS WICHTIGSTE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFFEF8A95))
                Text(line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    Panel(title = "Dein Wert nach Dienstart", padding = 14.dp) {
        for (a in averages) Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "${a.tag.name}: ${num(a.average)} von 5 aus ${plural(a.entries, "Eintrag", "Einträgen")}" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Shifts.short(diary, a.tag), Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth((a.average / 5.0).toFloat().coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(colors.level(roundedLevel(a.average))))
            }
            Text(num(a.average), Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        }
        Text("Schnitt $period, 5 ist der beste Wert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    BlockPanel(diary, DateRange(s.range.endInclusive.minusDays(179), s.range.endInclusive))
}

/** Verlauf ueber einen Dienstblock, mit Auswahl der Dienstart. Gerechnet ueber die letzten sechs Monate. */
@Composable
private fun BlockPanel(diary: Diary, range: DateRange) {
    val colors = LocalDiaryColors.current
    val work = remember(diary) { Shifts.all(diary).filter { !Shifts.isFree(diary, it) } }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val course: BlockCourse = remember(diary, range, chosen) {
        work.firstOrNull { it.id == chosen }?.let { ShiftStats.blockCourse(diary, it, range) } ?: ShiftStats.strongestBlock(diary, range)
    } ?: return
    var menu by remember { mutableStateOf(false) }

    Panel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Im Dienstblock", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Box {
                AssistChip({ menu = true }, { Text("${Shifts.short(diary, course.tag)} · ${plural(course.blocks, "Block", "Blöcke")}", maxLines = 1) }, trailingIcon = { Icon(Icons.Rounded.ExpandMore, "Dienstart wählen", Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
                DropdownMenu(menu, { menu = false }) { for (t in work) DropdownMenuItem({ Text(t.name) }, { chosen = t.id; menu = false }) }
            }
        }
        if (!course.enough) {
            Text("Noch zu wenig Blöcke. Ab drei Blöcken mit mindestens zwei Diensten in Folge erscheint hier der Verlauf.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Panel
        }
        val values = course.during + course.after
        val description = "Verlauf über einen Block ${course.tag.name}: " + course.during.mapIndexed { i, v -> "${i + 1}. Dienst ${num(v)}" }.joinToString(", ") +
            course.after.mapIndexed { i, v -> ", ${i + 1}. freier Tag ${num(v)}" }.joinToString("")
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Saeulen mit dem Wert darueber; eine Luecke trennt Dienste und freie Tage
            Row(Modifier.height(100.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEachIndexed { i, v ->
                    if (i == course.during.size) Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(num(v), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                        Box(Modifier.fillMaxWidth().height((14 + (v - 1) / 4.0 * 60).dp).clip(RoundedCornerShape(8.dp)).background(colors.level(roundedLevel(v))))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.indices.forEach { i ->
                    if (i == course.during.size) Spacer(Modifier.width(10.dp))
                    Text("${(if (i < course.during.size) i else i - course.during.size) + 1}.", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Shifts.short(diary, course.tag), Modifier.weight(course.during.size.toFloat()), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (course.after.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text("frei danach", Modifier.weight(course.after.size.toFloat()), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        blockSentence(course)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Aus den letzten sechs Monaten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Wann der Wert nach dem Block wieder beim ersten Diensttag liegt. */
fun blockSentence(c: BlockCourse): String? {
    if (c.during.isEmpty() || c.after.isEmpty()) return null
    val usual = c.during.first() - 0.25
    val back = c.after.indexOfFirst { it >= usual }
    return when (back) {
        0 -> "Schon am ersten freien Tag bist du wieder bei deinem Wert vom Blockanfang."
        1 -> "Am zweiten freien Tag bist du wieder bei deinem Wert vom Blockanfang."
        else -> "Nach zwei freien Tagen liegt dein Wert noch unter dem vom Blockanfang."
    }
}
