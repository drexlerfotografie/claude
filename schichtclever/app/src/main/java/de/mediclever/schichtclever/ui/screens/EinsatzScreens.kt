package de.mediclever.schichtclever.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Catalog
import de.mediclever.schichtclever.data.Category
import de.mediclever.schichtclever.data.Einsatz
import de.mediclever.schichtclever.data.Involvement
import de.mediclever.schichtclever.data.MeasureEntry
import de.mediclever.schichtclever.data.Outcome
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.Urgency
import de.mediclever.schichtclever.data.toLocalDate
import de.mediclever.schichtclever.ui.components.Cleverle
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.McChip
import de.mediclever.schichtclever.ui.components.SectionLabel
import de.mediclever.schichtclever.ui.dateLong
import de.mediclever.schichtclever.ui.theme.LocalExtra
import de.mediclever.schichtclever.ui.time
import java.time.ZoneId

@Composable
fun EinsatzEditorScreen(data: AppData, einsatzId: String?, onClose: () -> Unit) {
    val existing = data.einsaetze.firstOrNull { it.id == einsatzId }
    var draft by remember { mutableStateOf(existing ?: Einsatz(alarmAt = System.currentTimeMillis())) }
    var involvement by remember { mutableStateOf(Involvement.SELBST) }
    var query by remember { mutableStateOf("") }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val role = data.settings.role
    val skills = Catalog.forRole(role, data.settings.customSkills)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Neuer ${role.einsatzWord()}" else "${role.einsatzWord()} bearbeiten", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Löschen") }
                    TextButton(onClick = { Repository.saveEinsatz(draft.copy(quick = false)); onClose() }) {
                        Text("SPEICHERN", style = MaterialTheme.typography.titleMedium, color = LocalExtra.current.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(
                Modifier.fillMaxWidth().border(1.dp, LocalExtra.current.accent).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Warning, null, tint = LocalExtra.current.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Keine Patientendaten eintragen: keine Namen, Geburtsdaten oder Adressen. Nur, was du gemacht hast.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionLabel("Alarmierung")
            Row(verticalAlignment = Alignment.CenterVertically) {
                McCard(Modifier.weight(1f), onClick = { showTime = true }, padding = 12.dp) {
                    Text("Uhrzeit", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                    Text("${draft.alarmAt.time()}  ·  ${draft.alarmAt.dateLong()}", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { draft = draft.copy(alarmAt = System.currentTimeMillis()) }, shape = MaterialTheme.shapes.small) { Text("Jetzt") }
            }

            SectionLabel("Kategorie")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c -> McChip(c.label, draft.category == c, { draft = draft.copy(category = c) }) }
            }

            SectionLabel("Dringlichkeit")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Urgency.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = draft.urgency == u, onClick = { draft = draft.copy(urgency = u) },
                        shape = SegmentedButtonDefaults.itemShape(i, Urgency.entries.size, MaterialTheme.shapes.small),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = androidx.compose.ui.graphics.Color.White),
                        icon = {},
                    ) { Text(u.label, maxLines = 1) }
                }
            }

            SectionLabel("Ausgang")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Outcome.entries.forEach { o -> McChip(o.label, draft.outcome == o, { draft = draft.copy(outcome = if (draft.outcome == o) null else o) }) }
            }

            SectionLabel("Maßnahmen", trailing = if (draft.measures.isNotEmpty()) "${draft.measures.size} gewählt" else null)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Involvement.entries.forEachIndexed { i, inv ->
                    SegmentedButton(
                        selected = involvement == inv, onClick = { involvement = inv },
                        shape = SegmentedButtonDefaults.itemShape(i, 2, MaterialTheme.shapes.small),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = androidx.compose.ui.graphics.Color.White),
                        icon = {},
                    ) { Text(if (inv == Involvement.SELBST) "Selbst durchgeführt" else "Assistiert") }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Maßnahme suchen …") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.small,
            )
            val filtered = skills.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            filtered.groupBy { it.group }.forEach { (group, list) ->
                Text(group.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { s ->
                        val entry = draft.measures.firstOrNull { it.skillId == s.id }
                        val label = if (entry?.involvement == Involvement.ASSISTIERT) "${s.name} (A)" else s.name
                        McChip(label, entry != null, {
                            draft = draft.copy(
                                measures = if (entry != null) draft.measures - entry
                                else draft.measures + MeasureEntry(s.id, involvement)
                            )
                        })
                    }
                }
            }
            if (filtered.isEmpty() && query.isNotBlank()) {
                OutlinedButton(
                    onClick = { Repository.addCustomSkill(query, "Sonstiges"); query = "" },
                    modifier = Modifier.padding(top = 8.dp), shape = MaterialTheme.shapes.small,
                ) { Icon(Icons.Outlined.Add, null); Text(" „${query.trim()}“ als eigene Maßnahme anlegen") }
            }

            SectionLabel("Stichwort / Notiz (optional)")
            OutlinedTextField(
                value = draft.note, onValueChange = { draft = draft.copy(note = it.take(300)) },
                placeholder = { Text("z. B. ACS-Verdacht, was du nachlesen willst") },
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, minLines = 2,
            )

            Button(
                onClick = { Repository.saveEinsatz(draft.copy(quick = false)); onClose() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp), shape = MaterialTheme.shapes.small,
            ) { Text("Speichern".uppercase(), style = MaterialTheme.typography.titleLarge) }
        }
    }

    if (showTime) {
        val z = java.time.Instant.ofEpochMilli(draft.alarmAt).atZone(ZoneId.systemDefault())
        val state = rememberTimePickerState(z.hour, z.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    var t = z.withHour(state.hour).withMinute(state.minute).withSecond(0)
                    // Uhrzeit in der Zukunft = vermutlich gestern (z. B. nach Mitternacht nachgetragen)
                    if (t.toInstant().toEpochMilli() > System.currentTimeMillis() + 5 * 60_000) t = t.minusDays(1)
                    draft = draft.copy(alarmAt = t.toInstant().toEpochMilli())
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Abbrechen") } },
            text = { TimePicker(state) },
            shape = MaterialTheme.shapes.medium,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${role.einsatzWord()} löschen?") },
            text = { Text("Das lässt sich nicht rückgängig machen.") },
            confirmButton = { TextButton(onClick = { existing?.let { Repository.deleteEinsatz(it.id) }; confirmDelete = false; onClose() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun EinsaetzeScreen(data: AppData, dark: Boolean, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val role = data.settings.role
    val byDay = data.einsaetze.sortedByDescending { it.alarmAt }.groupBy { it.alarmAt.toLocalDate() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(role.einsatzWord(true), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onNew) { Icon(Icons.Outlined.Add, "Neu", tint = LocalExtra.current.accent) }
        }
        if (byDay.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Cleverle(Modifier.size(72.dp, 84.dp), dark)
                Spacer(Modifier.height(16.dp))
                Text("Noch nichts erfasst.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Starte eine Schicht und tippe auf „${role.einsatzWord()}“ oder nutze die Kachel in den Schnelleinstellungen.",
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            byDay.forEach { (day, list) ->
                item(key = day.toString()) {
                    SectionLabel(list.first().alarmAt.dateLong(), trailing = "${list.size}")
                }
                item(key = day.toString() + "-list") {
                    McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                        list.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(color = LocalExtra.current.line)
                            EinsatzRow(e, data, onClick = { onOpen(e.id) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp).background(MaterialTheme.colorScheme.background)) }
        }
    }
}
