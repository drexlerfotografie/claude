package de.mediclever.tagebuch.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.goals.*
import de.mediclever.tagebuch.core.model.Goal
import de.mediclever.tagebuch.core.model.GoalRepeat
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.icons.iconFor
import de.mediclever.tagebuch.ui.setup.Choice
import de.mediclever.tagebuch.ui.setup.verticalScrollCompat
import de.mediclever.tagebuch.ui.theme.groupColor
import de.mediclever.tagebuch.ui.theme.onColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

fun seriesText(n: Int, unit: StreakUnit) = if (unit == StreakUnit.WEEKS) plural(n, "Woche", "Wochen") else plural(n, "Tag", "Tage")

fun repeatText(g: Goal): String = when (g.repeat) {
    GoalRepeat.DAILY -> "Täglich"
    GoalRepeat.TIMES_PER_WEEK -> "${g.timesPerWeek}-mal pro Woche"
    GoalRepeat.WEEKDAYS -> g.weekdays.sorted().joinToString(", ") { weekdayShort(it) }.ifEmpty { "Keine Wochentage" }
}

private fun pct(v: Double?) = v?.let { "${(it * 100).toInt()} %" } ?: "–"

@Composable
fun GoalsScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val all = remember(env.diary, env.today) { goalOverviews(env.diary, env.today, includeArchived = true) }
    val active = all.filter { it.goal.archivedAt == null }
    val archived = all.filter { it.goal.archivedAt != null }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    fun move(i: Int, by: Int) {
        val list = active.map { it.goal }.toMutableList(); val j = i + by
        if (j in list.indices) { list.add(j, list.removeAt(i)); env.act { save(list.mapIndexed { k, g -> g.copy(sortOrder = k) }) } }
    }
    SubScreen("Ziele", nav.back, floating = { ExtendedFloatingActionButton(onClick = { nav.go(Routes.goalEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Neues Ziel") }) }) {
        if (all.isEmpty()) EmptyState("Noch keine Ziele", "Ein Ziel hilft dir, etwas regelmäßig zu tun: täglich, ein paarmal pro Woche oder an festen Wochentagen.", actionLabel = "Erstes Ziel anlegen", onAction = { nav.go(Routes.goalEdit()) })
        active.forEachIndexed { i, o ->
            val c = groupColor(o.goal.colorIndex)
            val today = env.today.toString()
            Panel(onClick = { nav.go(Routes.goal(o.goal.id)) }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBubble(o.goal.icon, c, o.state == GoalState.DONE, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(o.goal.name, style = MaterialTheme.typography.titleMedium)
                        Text(repeatText(o.goal) + " · Serie: " + seriesText(o.streaks.current, o.streaks.unit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val checked = o.state == GoalState.DONE
                    Checkbox(checked, { env.act { setGoalCheck(o.goal.id, today, !checked) } }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Heute erledigt" })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("7 Tage: ${pct(o.successRate7)} · 30 Tage: ${pct(o.successRate30)} · Stufe ${o.level.level}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton({ move(i, -1) }, Modifier.size(40.dp), enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Nach oben") }
                    IconButton({ move(i, 1) }, Modifier.size(40.dp), enabled = i < active.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Nach unten") }
                }
            }
        }
        if (archived.isNotEmpty()) {
            TextButton({ showArchive = !showArchive }) { Text(if (showArchive) "Archiv ausblenden" else "Archiv anzeigen (${archived.size})") }
            if (showArchive) RowGroup { archived.forEachIndexed { i, o -> if (i > 0) RowDivider(); SettingRow(o.goal.name, "${o.totalCompletions}× erledigt · archiviert", iconFor(o.goal.icon), onClick = { nav.go(Routes.goal(o.goal.id)) }) } }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
fun GoalDetailScreen(id: String) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val tracker = remember(env.diary, id) { goalTracker(env.diary, id) }
    if (tracker == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val goal = tracker.goal
    val o = remember(tracker, env.today) { tracker.overview(env.today) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(env.today).toString()) }
    val month = YearMonth.parse(monthText)
    var confirmDelete by remember { mutableStateOf(false) }
    val c = groupColor(goal.colorIndex)

    SubScreen(goal.name, nav.back, actions = {
        IconButton({ nav.go(Routes.goalEdit(goal.id)) }) { Icon(Icons.Rounded.Edit, "Ziel bearbeiten") }
        IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Ziel löschen") }
    }) {
        Panel(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBubble(goal.icon, c, true, 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(repeatText(goal), style = MaterialTheme.typography.titleMedium)
                    val linked = env.diary.tagById[goal.tagId]
                    Text((linked?.let { "Verknüpft mit „${it.name}“ · " } ?: "") + "seit ${fmtDayShort(LocalDate.parse(goal.startDate))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (goal.note.isNotBlank()) Text(goal.note)
            if (goal.archivedAt != null) Text("Dieses Ziel ist archiviert.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Serie", seriesText(o.streaks.current, o.streaks.unit), Modifier.weight(1f))
            Stat("Längste Serie", seriesText(o.streaks.longest, o.streaks.unit), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Erfolg 7 Tage", pct(o.successRate7), Modifier.weight(1f))
            Stat("Erfolg 30 Tage", pct(o.successRate30), Modifier.weight(1f))
        }
        Panel(title = "Stufe ${o.level.level}", padding = 14.dp) {
            Text("${o.totalCompletions}-mal erledigt" + (o.level.remaining?.let { " · noch $it bis Stufe ${o.level.level + 1}" } ?: " · höchste Stufe erreicht"))
            val from = o.level.reachedThreshold ?: 0
            val to = o.level.nextThreshold
            ShareBar(if (to == null) 1f else (o.totalCompletions - from).toFloat() / (to - from).coerceAtLeast(1), c)
        }
        Panel(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ monthText = month.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft, "Vorheriger Monat") }
                Text(fmtMonth(month), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton({ monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(env.today)) { Icon(Icons.Rounded.ChevronRight, "Nächster Monat") }
            }
            Row { for (d in de.mediclever.tagebuch.core.stats.weekdayOrder(env.diary.settings.weekStart)) Text(weekdayShort(d), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for (week in tracker.calendar(month, env.today)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in week) {
                    if (day == null) { Spacer(Modifier.weight(1f).aspectRatio(1f)); continue }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .then(if (day.checked) Modifier.background(c) else if (day.due) Modifier.border(1.5.dp, c, CircleShape) else Modifier)
                            .clickable(enabled = !day.future, role = Role.Checkbox) { env.act { setGoalCheck(goal.id, day.date.toString(), !day.checked) } }
                            .semantics { contentDescription = fmtDayShort(day.date) + if (day.checked) ", erledigt" else ", offen" },
                        contentAlignment = Alignment.Center,
                    ) { Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, color = if (day.checked) onColor(c) else if (day.future) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface) }
                }
            }
            Text("Tippe einen Tag an, um ihn abzuhaken oder den Haken zu entfernen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel(title = "Abschlüsse je Monat ${month.year}", padding = 14.dp) {
            val per = tracker.completionsPerMonth(month.year)
            SeriesChart(per.map { ChartPoint(java.time.Month.of(it.month.monthValue).getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.GERMANY), it.count.toDouble()) }, 0.0, (per.maxOf { it.count }).coerceAtLeast(4).toDouble(),
                "Abschlüsse je Monat: " + per.joinToString { "${it.month.monthValue}.: ${it.count}" }, bars = true, color = c, labelEvery = 1, height = 110.dp)
        }
    }
    if (confirmDelete) ConfirmDialog("Ziel „${goal.name}“ löschen?", "Das Ziel und ${plural(o.totalCompletions, "abgehakter Tag", "abgehakte Tage")} werden gelöscht. Mit Archivieren bleibt der Verlauf erhalten.", "Löschen", destructive = true,
        onConfirm = { env.act { deleteGoal(goal) }; confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false },
        alternative = if (goal.archivedAt == null) "Archivieren" else null, onAlternative = { env.act { save(goal.copy(archivedAt = System.currentTimeMillis())) }; confirmDelete = false; nav.back() })
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Panel(modifier, padding = 14.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalEditScreen(id: String?) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val d = env.diary
    val existing = d.goals.firstOrNull { it.id == id }
    var g by remember(id) { mutableStateOf(existing ?: Goal(name = "", startDate = env.today.toString(), colorIndex = d.goals.size % 12, sortOrder = d.goals.size)) }
    var pickIcon by remember { mutableStateOf(false) }
    var pickTag by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    val c = groupColor(g.colorIndex)
    val valid = g.name.isNotBlank() && (g.repeat != GoalRepeat.WEEKDAYS || g.weekdays.isNotEmpty())

    SubScreen(if (existing == null) "Neues Ziel" else "Ziel bearbeiten", nav.back, actions = {
        TextButton({ env.act { save(g.copy(name = g.name.trim())) }; nav.back() }, enabled = valid) { Text("Speichern") }
    }) {
        Panel(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Symbol wählen") { pickIcon = true }) { IconBubble(g.icon, c, true, 52.dp) }
                OutlinedTextField(g.name, { g = g.copy(name = it) }, label = { Text("Was möchtest du erreichen?") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            ColorPicker(g.colorIndex, { g = g.copy(colorIndex = it) })
        }
        Panel(title = "Wie oft?", padding = 12.dp) {
            Choice(listOf(GoalRepeat.DAILY to "Täglich", GoalRepeat.TIMES_PER_WEEK to "Mehrmals pro Woche", GoalRepeat.WEEKDAYS to "Feste Wochentage"), g.repeat) { g = g.copy(repeat = it) }
            if (g.repeat == GoalRepeat.TIMES_PER_WEEK) Choice((1..7).map { it to "$it×" }, g.timesPerWeek) { g = g.copy(timesPerWeek = it) }
            if (g.repeat == GoalRepeat.WEEKDAYS) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in de.mediclever.tagebuch.core.stats.weekdayOrder(d.settings.weekStart)) FilterChip(w in g.weekdays, { g = g.copy(weekdays = if (w in g.weekdays) g.weekdays - w else (g.weekdays + w).sorted()) }, label = { Text(weekdayShort(w)) }, modifier = Modifier.height(48.dp))
            }
        }
        RowGroup {
            SettingRow("Verknüpfte Aktivität", d.tagById[g.tagId]?.let { "„${it.name}“ hakt das Ziel automatisch ab" } ?: "Keine. Du hakst selbst ab.", Icons.Rounded.Link, onClick = { pickTag = true })
            RowDivider()
            SettingRow("Erinnerung", g.reminderMinuteOfDay?.let { "Um ${fmtTime(it)} Uhr" } ?: "Aus", Icons.Rounded.Notifications, onClick = { pickTime = true }) {
                Switch(g.reminderMinuteOfDay != null, { on -> if (on) pickTime = true else g = g.copy(reminderMinuteOfDay = null) })
            }
            RowDivider()
            SettingRow("Startdatum", fmtDayShort(LocalDate.parse(g.startDate)), Icons.Rounded.CalendarMonth, onClick = { pickStart = true })
        }
        if (g.reminderMinuteOfDay != null) OutlinedTextField(g.reminderText, { g = g.copy(reminderText = it) }, label = { Text("Text der Erinnerung (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(g.note, { g = g.copy(note = it) }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        if (existing != null) {
            if (g.archivedAt == null) OutlinedButton({ env.act { save(g.copy(archivedAt = System.currentTimeMillis())) }; nav.back() }) { Text("Archivieren") }
            else OutlinedButton({ env.act { save(g.copy(archivedAt = null)) }; nav.back() }) { Text("Wiederherstellen") }
        }
    }
    if (pickIcon) IconPickerDialog(g.icon, c, { g = g.copy(icon = it); pickIcon = false }, { pickIcon = false })
    if (pickTag) AlertDialog(onDismissRequest = { pickTag = false }, title = { Text("Aktivität verknüpfen") }, confirmButton = { TextButton({ pickTag = false }) { Text("Schließen") } },
        text = { Column(Modifier.verticalScrollCompat()) {
            SettingRow("Keine Verknüpfung", onClick = { g = g.copy(tagId = null); pickTag = false })
            for (t in d.tags.filter { it.archivedAt == null }) SettingRow(t.name, d.groupById[t.groupId]?.name, onClick = { g = g.copy(tagId = t.id, icon = if (g.icon == "flag") t.icon else g.icon); pickTag = false })
        } })
    if (pickTime) {
        val m = g.reminderMinuteOfDay ?: (19 * 60)
        val state = rememberTimePickerState(m / 60, m % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { pickTime = false }, title = { Text("Erinnerung um") }, text = { TimePicker(state) },
            confirmButton = { TextButton({ g = g.copy(reminderMinuteOfDay = state.hour * 60 + state.minute); pickTime = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton({ pickTime = false }) { Text("Abbrechen") } })
    }
    if (pickStart) {
        val state = rememberDatePickerState(LocalDate.parse(g.startDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickStart = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { g = g.copy(startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; pickStart = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton({ pickStart = false }) { Text("Abbrechen") } }) { DatePicker(state) }
    }
}
