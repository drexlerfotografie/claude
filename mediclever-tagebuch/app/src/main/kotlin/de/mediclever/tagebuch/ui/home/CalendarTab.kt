package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.stats.CalendarFilter
import de.mediclever.tagebuch.core.stats.calendarMonth
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.setup.verticalScrollCompat
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.onColor
import de.mediclever.tagebuch.ui.theme.groupColor
import java.time.LocalDate
import java.time.YearMonth

fun levelName(level: Int): String = listOf("beste Stufe", "gute Stufe", "mittlere Stufe", "schlechte Stufe", "schlechteste Stufe").getOrElse(level - 1) { "" }

/** Monatsraster: jeder Tag in der Farbe seiner Stimmung, leere Tage mit Plus zum Nachtragen. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalendarTab(month: YearMonth) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val diary = env.diary
    val colors = LocalDiaryColors.current
    var moodIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tagIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pick by remember { mutableStateOf(false) }
    var dayOpen by remember { mutableStateOf<LocalDate?>(null) }
    val filter = CalendarFilter(moodIds = moodIds.toSet(), tagIds = tagIds.toSet())
    val cal = remember(diary, month, env.today, moodIds, tagIds) { calendarMonth(diary, month, env.today, filter = filter) }
    val filtering = moodIds.isNotEmpty() || tagIds.isNotEmpty()
    val levels = remember(diary, month) { de.mediclever.tagebuch.core.stats.dayLevels(diary, de.mediclever.tagebuch.core.stats.DateRange(month.atDay(1), month.atEndOfMonth())) }
    val shifts = remember(diary, month) { (1..month.lengthOfMonth()).mapNotNull { d -> month.atDay(d).let { day -> de.mediclever.tagebuch.core.shift.Shifts.ofDay(diary, day)?.let { day to it } } }.toMap() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in cal.counters) InputChip(true, onClick = { moodIds = moodIds - c.id; tagIds = tagIds - c.id }, label = { Text("${c.label} · ${c.entryCount}") },
                trailingIcon = { Icon(Icons.Rounded.Close, "Filter ${c.label} entfernen", Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
            AssistChip({ pick = true }, { Text(if (filtering) "hinzu" else "Filtern nach Stimmung oder Aktivität") }, leadingIcon = { Icon(if (filtering) Icons.Rounded.Add else Icons.Rounded.FilterList, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        Panel(padding = 10.dp) {
            Row { for (d in cal.weekdays) Text(weekdayShort(d), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for (week in cal.weeks) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in week) {
                    if (day == null) { Spacer(Modifier.weight(1f).aspectRatio(0.85f)); continue }
                    val c = day.level?.let { colors.level(it) }
                    val dimmed = filtering && day.matchCount == 0
                    val label = fmtDayShort(day.date) + when { day.entryCount == 0 -> ", kein Eintrag"; else -> ", ${plural(day.entryCount, "Eintrag", "Einträge")}" + (day.level?.let { ", " + levelName(it) } ?: "") }
                    Column(
                        Modifier.weight(1f).aspectRatio(0.85f).clip(RoundedCornerShape(12.dp))
                            .then(if (c != null) (dayBrush(levels[day.date].orEmpty()).takeIf { !filtering }?.let { Modifier.background(it) } ?: Modifier.background(c.copy(alpha = if (dimmed) 0.2f else 1f))) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)))
                            .then(if (day.isToday) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable(enabled = !day.isFuture) { if (day.entryCount == 0) nav.go(Routes.newEntry(day.date.toString())) else dayOpen = day.date }
                            .semantics(mergeDescendants = true) { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        val fg = if (c != null && !dimmed) onColor(c) else MaterialTheme.colorScheme.onSurface
                        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = if (day.isToday) FontWeight.Black else FontWeight.SemiBold, color = fg, modifier = Modifier.alpha(if (day.isFuture) 0.4f else 1f))
                        if (day.entryCount == 0 && !day.isFuture) Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Symbol der Stufe, damit der Tag nicht nur an der Farbe erkennbar ist
                        else shifts[day.date]?.let { t ->
                            // Streifen in der Farbe der Dienstart
                            Box(Modifier.padding(top = 4.dp).width(22.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)).padding(1.dp).clip(RoundedCornerShape(2.dp)).background(groupColor(t.colorIndex ?: diary.groupById[t.groupId]?.colorIndex)).alpha(if (dimmed) 0.3f else 1f))
                        }
                    }
                }
            }
        }
        val legend = shifts.values.distinctBy { it.id }.sortedBy { it.sortOrder }
        if (legend.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (t in legend) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(16.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(groupColor(t.colorIndex ?: diary.groupById[t.groupId]?.colorIndex)))
                Text(de.mediclever.tagebuch.core.shift.Shifts.short(diary, t), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (cal.hint.isNotBlank()) Text(cal.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Panel(title = if (filtering) "Werte der passenden Einträge" else "Werte im ${fmtMonth(month)}", padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (l in cal.moodCounter.byLevel) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    colors.level(l.level).let { c -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (l.count == 0) MaterialTheme.colorScheme.surfaceVariant else c), contentAlignment = Alignment.Center) { Text((6 - l.level).toString(), color = if (l.count == 0) MaterialTheme.colorScheme.onSurfaceVariant else onColor(c), fontWeight = FontWeight.Black) } }
                    Text(l.count.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(diary.moods.firstOrNull { it.level == l.level }?.name ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (pick) AlertDialog(
        onDismissRequest = { pick = false },
        title = { Text("Filter hinzufügen") },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                SectionLabel("Stimmungen")
                for (m in diary.moods.filter { it.id !in moodIds }) SettingRow(m.name, onClick = { moodIds = moodIds + m.id; pick = false })
                SectionLabel("Aktivitäten")
                for (t in diary.tags.filter { it.id !in tagIds && it.archivedAt == null }) SettingRow(t.name, diary.groupById[t.groupId]?.name, onClick = { tagIds = tagIds + t.id; pick = false })
            }
        },
        confirmButton = { TextButton({ pick = false }) { Text("Schließen") } },
    )

    dayOpen?.let { date ->
        val list = diary.entries.filter { it.localDate == date.toString() }
        ModalBottomSheet({ dayOpen = null }) {
            Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(fmtDay(date, env.today), style = MaterialTheme.typography.titleLarge)
                for (e in list) EntryCard(e, onOpen = { dayOpen = null; nav.go(Routes.editEntry(e.id)) }, showDate = false)
                OutlinedButton({ dayOpen = null; nav.go(Routes.newEntry(date.toString())) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text("Weiterer Eintrag an diesem Tag") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
