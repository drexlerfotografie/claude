package de.mediclever.schichtclever.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Catalog
import de.mediclever.schichtclever.data.Freshness
import de.mediclever.schichtclever.data.Involvement
import de.mediclever.schichtclever.data.Practice
import de.mediclever.schichtclever.data.PracticeKind
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.SkillStatus
import de.mediclever.schichtclever.data.skillStatuses
import de.mediclever.schichtclever.ui.components.BarChart
import de.mediclever.schichtclever.ui.components.Cleverle
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.McChip
import de.mediclever.schichtclever.ui.components.SectionLabel
import de.mediclever.schichtclever.ui.components.StatTile
import de.mediclever.schichtclever.ui.components.freshnessColor
import de.mediclever.schichtclever.ui.dateShort
import de.mediclever.schichtclever.ui.daysAgoText
import de.mediclever.schichtclever.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val FILTER_ALL = "Alle"
private const val FILTER_LIST = "Übungsliste"
private const val FILTER_DUE = "Fällig"

@Composable
fun SkillsScreen(data: AppData, dark: Boolean, onOpen: (String) -> Unit) {
    val statuses = remember(data) { data.skillStatuses() }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    val listed = statuses.filter { it.goal.onPracticeList }
    // Der am längsten überfällige Skill auf der Übungsliste bekommt die Erinnerung von Cleverle.
    val reminder = listed.filter { it.freshness == Freshness.LANGE_HER || it.freshness == Freshness.NIE }
        .maxByOrNull { it.daysAgo ?: Long.MAX_VALUE }

    val shown = when (filter) {
        FILTER_ALL -> statuses
        FILTER_LIST -> listed
        FILTER_DUE -> statuses.filter { it.freshness != Freshness.FRISCH }
        else -> statuses.filter { it.skill.group == filter }
    }.sortedWith(compareByDescending<SkillStatus> { it.count }.thenBy { it.skill.name })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Skills", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(statuses.count { it.freshness == Freshness.FRISCH }.toString(), "frisch", Modifier.weight(1f))
                StatTile(statuses.count { it.freshness == Freshness.BALD }.toString(), "bald üben", Modifier.weight(1f))
                StatTile(statuses.count { it.freshness == Freshness.LANGE_HER }.toString(), "lange her", Modifier.weight(1f))
            }
            if (reminder != null) {
                McCard(Modifier.fillMaxWidth().padding(top = 12.dp), accent = true, onClick = { onOpen(reminder.skill.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cleverle(Modifier.size(40.dp, 46.dp), dark)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${reminder.skill.name} ist ${daysAgoText(reminder.daysAgo)} zuletzt drangekommen. Zeit für ein Skillstraining?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(FILTER_ALL, FILTER_LIST, FILTER_DUE) + Catalog.groups).forEach { f ->
                    McChip(if (f == FILTER_LIST) "$f (${listed.size})" else f, filter == f, { filter = f })
                }
            }
        }
        if (shown.isEmpty()) item {
            Text(
                if (filter == FILTER_LIST) "Deine Übungsliste ist leer. Öffne einen Skill und tippe auf „Auf Übungsliste“." else "Hier ist nichts.",
                color = LocalExtra.current.muted, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        items(shown, key = { it.skill.id }) { st ->
            SkillRow(st, onClick = { onOpen(st.skill.id) })
            HorizontalDivider(color = LocalExtra.current.line)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SkillRow(st: SkillStatus, onClick: () -> Unit) {
    de.mediclever.schichtclever.ui.components.SettingRow(
        title = st.skill.name + if (st.goal.onPracticeList) "  ★" else "",
        subtitle = "${st.count}× gesamt · zuletzt ${daysAgoText(st.daysAgo)}",
        onClick = onClick,
        trailing = {
            Box(Modifier.size(8.dp).clip(CircleShape).background(freshnessColor(st.freshness)))
            Spacer(Modifier.width(6.dp))
            Text(st.freshness.label, style = MaterialTheme.typography.bodySmall, color = freshnessColor(st.freshness))
            Icon(Icons.Outlined.ChevronRight, null, tint = LocalExtra.current.muted)
        },
    )
}

@Composable
fun SkillDetailScreen(data: AppData, skillId: String, onBack: () -> Unit) {
    val st = remember(data, skillId) { data.skillStatuses().firstOrNull { it.skill.id == skillId } }
    var addPractice by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(st?.skill?.name ?: "Skill", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (st == null) { Text("Skill nicht gefunden.", Modifier.padding(pad).padding(16.dp)); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "${st.skill.group.uppercase()}  ·  ${st.freshness.label.uppercase()}",
                style = MaterialTheme.typography.labelSmall, color = freshnessColor(st.freshness),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${st.count}×", "gesamt, ${st.selfCount}× selbst", Modifier.weight(1f))
                StatTile(st.daysAgo?.toString() ?: "–", "Tage seit dem letzten Mal", Modifier.weight(1f), accent = st.freshness != Freshness.FRISCH)
            }

            val now = LocalDate.now()
            SectionLabel("Letzte 12 Monate", trailing = "Ziel: alle ${st.intervalDays} Tage")
            McCard(Modifier.fillMaxWidth()) {
                BarChart(
                    values = st.monthly.toList(),
                    labels = (11 downTo 0).map { now.minusMonths(it.toLong()).month.getDisplayName(TextStyle.NARROW, Locale.GERMANY) },
                    height = 90.dp,
                    highlight = st.monthly.indices.filter { st.monthly[it] > 0 }.toSet(),
                )
            }

            SectionLabel("Übungsintervall")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 180).forEach { d ->
                    McChip("$d Tage", st.intervalDays == d, { Repository.setGoal(st.skill.id, st.goal.copy(intervalDays = d)) })
                }
            }

            Button(
                onClick = { Repository.setGoal(st.skill.id, st.goal.copy(onPracticeList = !st.goal.onPracticeList)) },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp), shape = MaterialTheme.shapes.small,
            ) {
                Icon(if (st.goal.onPracticeList) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd, null)
                Spacer(Modifier.width(8.dp))
                Text((if (st.goal.onPracticeList) "Von Übungsliste nehmen" else "Auf Übungsliste").uppercase(), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(
                onClick = { addPractice = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp), shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Übung eintragen")
            }

            SectionLabel("Verlauf")
            val history = (
                data.einsaetze.flatMap { e -> e.measures.filter { it.skillId == st.skill.id }.map { Triple(e.alarmAt, "${e.category.label} (${it.involvement.label.lowercase()})", it.involvement == Involvement.SELBST) } } +
                    data.practices.filter { it.skillId == st.skill.id }.map { Triple(it.at, it.kind.label, true) }
                ).sortedByDescending { it.first }.take(20)
            if (history.isEmpty()) Text("Noch keine Einträge.", color = LocalExtra.current.muted)
            history.forEach { (t, label, self) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(t.dateShort(), style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.width(100.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (self) MaterialTheme.colorScheme.onSurface else LocalExtra.current.muted)
                }
            }
        }
    }

    if (addPractice && st != null) {
        AlertDialog(
            onDismissRequest = { addPractice = false },
            title = { Text("Übung eintragen") },
            text = { Text("Wo hast du „${st.skill.name}“ heute geübt?") },
            confirmButton = {
                Column {
                    PracticeKind.entries.forEach { k ->
                        TextButton(onClick = { Repository.addPractice(Practice(skillId = st.skill.id, at = System.currentTimeMillis(), kind = k)); addPractice = false }) { Text(k.label) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { addPractice = false }) { Text("Abbrechen") } },
        )
    }
}
