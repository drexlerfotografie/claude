package de.mediclever.schichtclever.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Catalog
import de.mediclever.schichtclever.data.Einsatz
import de.mediclever.schichtclever.data.FilterSchedule
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.Role
import de.mediclever.schichtclever.data.Shift
import de.mediclever.schichtclever.data.ShiftType
import de.mediclever.schichtclever.data.hourOfDay
import de.mediclever.schichtclever.focus.FocusController
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.SectionLabel
import de.mediclever.schichtclever.ui.components.SettingRow
import de.mediclever.schichtclever.ui.components.StatTile
import de.mediclever.schichtclever.ui.date
import de.mediclever.schichtclever.ui.dateLong
import de.mediclever.schichtclever.ui.durationHm
import de.mediclever.schichtclever.ui.greeting
import de.mediclever.schichtclever.ui.theme.LocalExtra
import de.mediclever.schichtclever.ui.time
import kotlinx.coroutines.delay

/** Bezeichnung für "Einsatz" je nach Rolle – in der Klinik heißt es eher Notfall/Aufnahme. */
fun Role.einsatzWord(plural: Boolean = false): String = when (this) {
    Role.PFLEGE -> if (plural) "Notfälle" else "Eintrag"
    else -> if (plural) "Einsätze" else "Einsatz"
}

@Composable
fun rememberNow(intervalMs: Long = 30_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) { now = System.currentTimeMillis(); delay(intervalMs) }
    }
    return now
}

@Composable
fun ShiftScreen(data: AppData, onNewEinsatz: () -> Unit, onOpenEinsatz: (String) -> Unit, onOpenFocus: () -> Unit, onOpenFilter: () -> Unit) {
    val active = data.activeShift
    var showStart by rememberSaveable { mutableStateOf<ShiftType?>(null) }
    var showEnd by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp)) {
            Header()
            if (active == null) IdleContent(data, onPick = { showStart = it })
            else ActiveContent(data, active, onOpenEinsatz, onOpenFocus, onOpenFilter, onEnd = { showEnd = true })
        }
        if (active != null) {
            ExtendedFloatingActionButton(
                onClick = onNewEinsatz,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = MaterialTheme.shapes.small,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(data.settings.role.einsatzWord().uppercase(), style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    showStart?.let { type -> StartShiftSheet(data, type, onDismiss = { showStart = null }) }
    if (showEnd && active != null) EndShiftSheet(data, active, onDismiss = { showEnd = false })
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Schicht", style = MaterialTheme.typography.headlineMedium)
        Text("Clever", style = MaterialTheme.typography.headlineMedium, color = LocalExtra.current.accent)
    }
}

private fun ShiftType.icon() = when (this) {
    ShiftType.FRUEH -> Icons.Outlined.WbTwilight
    ShiftType.SPAET, ShiftType.ZWOELF_TAG -> Icons.Outlined.LightMode
    ShiftType.NACHT, ShiftType.ZWOELF_NACHT -> Icons.Outlined.Bedtime
    ShiftType.VIERUNDZWANZIG -> Icons.Outlined.Schedule
}

@Composable
private fun IdleContent(data: AppData, onPick: (ShiftType) -> Unit) {
    val now = rememberNow()
    val suggested = ShiftType.guess(now.hourOfDay())
    Text(now.dateLong().uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 12.dp))
    Text("${greeting(now.hourOfDay())}\nWelche Schicht steht an?", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

    ShiftType.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { t ->
                McCard(Modifier.weight(1f), accent = t == suggested, onClick = { onPick(t) }, padding = 12.dp) {
                    Icon(t.icon(), null, tint = if (t == suggested) LocalExtra.current.accent else LocalExtra.current.muted)
                    Spacer(Modifier.height(10.dp))
                    Text(t.label, style = MaterialTheme.typography.titleLarge)
                    Text(t.rangeLabel, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                }
            }
        }
    }
    Button(
        onClick = { onPick(suggested) },
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Icon(Icons.Outlined.PlayArrow, null)
        Spacer(Modifier.width(8.dp))
        Text("${suggested.label}schicht starten".uppercase(), style = MaterialTheme.typography.titleLarge)
    }
    Text(
        "Fokus-Modus und Blaufilter starten mit, je nach Einstellung.",
        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
    )

    data.shifts.filter { !it.active }.maxByOrNull { it.startAt }?.let { last ->
        SectionLabel("Letzte Schicht")
        McCard(Modifier.fillMaxWidth()) {
            val count = data.einsaetze.count { it.shiftId == last.id }
            Text("${last.type.label} · ${last.startAt.date()}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${durationHm((last.endAt ?: last.startAt) - last.startAt)} h · $count ${data.settings.role.einsatzWord(true)}" +
                    (last.unit.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }
    }
}

@Composable
private fun ActiveContent(
    data: AppData, shift: Shift,
    onOpenEinsatz: (String) -> Unit, onOpenFocus: () -> Unit, onOpenFilter: () -> Unit, onEnd: () -> Unit,
) {
    val now = rememberNow(15_000)
    val elapsed = now - shift.startAt
    val total = (shift.plannedEndAt - shift.startAt).coerceAtLeast(1)
    val remaining = shift.plannedEndAt - now
    val einsaetze = data.einsaetze.filter { it.shiftId == shift.id }.sortedByDescending { it.alarmAt }
    val measures = einsaetze.sumOf { it.measures.size }
    val role = data.settings.role
    val filterOn = FocusController.filterActive(data, now)

    McCard(Modifier.fillMaxWidth().padding(top = 12.dp), accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● ${shift.type.label}schicht läuft".uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.accent, modifier = Modifier.weight(1f))
            if (shift.unit.isNotBlank()) Text(shift.unit, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(durationHm(elapsed), style = MaterialTheme.typography.displayLarge)
            Text(" h", style = MaterialTheme.typography.titleLarge, color = LocalExtra.current.muted, modifier = Modifier.padding(bottom = 10.dp).weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(if (remaining >= 0) "noch" else "Überstunden", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                Text("${durationHm(kotlin.math.abs(remaining))} h", style = MaterialTheme.typography.titleLarge, color = if (remaining < 0) LocalExtra.current.warning else MaterialTheme.colorScheme.onSurface)
            }
        }
        LinearProgressIndicator(
            progress = { (elapsed.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = LocalExtra.current.line,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(shift.startAt.time(), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.weight(1f))
            Text(shift.plannedEndAt.time(), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(einsaetze.size.toString(), role.einsatzWord(true), Modifier.weight(1f))
        StatTile(measures.toString(), "Maßnahmen", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        McCard(Modifier.weight(1f), onClick = onOpenFocus, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, tint = LocalExtra.current.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                StatusPill(if (data.focusActive) "aktiv" else "aus", data.focusActive)
            }
            Spacer(Modifier.height(8.dp))
            Text("Fokus", style = MaterialTheme.typography.titleMedium)
            Text("${data.settings.blockedApps.size} Apps pausiert", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
        McCard(Modifier.weight(1f), onClick = onOpenFilter, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Contrast, null, tint = LocalExtra.current.warning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                StatusPill(if (filterOn) "${(data.settings.filterIntensity * 100).toInt()} %" else "aus", filterOn)
            }
            Spacer(Modifier.height(8.dp))
            Text("Blaufilter", style = MaterialTheme.typography.titleMedium)
            Text(
                when (data.settings.filterSchedule) {
                    FilterSchedule.MIT_SCHICHT -> "mit Nachtschicht"
                    FilterSchedule.ZEITRAUM -> "22–06 Uhr"
                    FilterSchedule.MANUELL -> "manuell"
                },
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }
    }

    SectionLabel("In dieser Schicht")
    if (einsaetze.isEmpty()) {
        Text("Noch ruhig. Tipp: Über die Schnelleinstellungen kannst du einen ${role.einsatzWord()} mit einem Tipp erfassen.", style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
    } else {
        McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            einsaetze.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = LocalExtra.current.line)
                EinsatzRow(e, data, onClick = { onOpenEinsatz(e.id) })
            }
        }
    }

    OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp), shape = MaterialTheme.shapes.small) {
        Text("Schicht beenden".uppercase(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun StatusPill(text: String, on: Boolean) {
    val c = if (on) LocalExtra.current.accent else LocalExtra.current.muted
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (on) Color.White else c,
        modifier = Modifier.background(if (on) c else Color.Transparent).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun EinsatzRow(e: Einsatz, data: AppData, onClick: () -> Unit) {
    SettingRow(
        title = if (e.quick) "Schnellerfassung · Details ergänzen" else e.category.label + (e.outcome?.let { " · ${it.label}" } ?: ""),
        subtitle = e.measures.takeIf { it.isNotEmpty() }?.joinToString(" · ") { Catalog.byId(it.skillId, data.settings.customSkills)?.name ?: "?" }
            ?: if (e.quick) "Tippen, um Kategorie und Maßnahmen nachzutragen" else "Keine Maßnahmen eingetragen",
        onClick = onClick,
        trailing = {
            Text(e.alarmAt.time(), style = MaterialTheme.typography.titleLarge, color = if (e.quick) LocalExtra.current.warning else LocalExtra.current.accent)
            Icon(Icons.Outlined.ChevronRight, null, tint = LocalExtra.current.muted)
        },
    )
}

@Composable
private fun StartShiftSheet(data: AppData, initial: ShiftType, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initial) }
    var unit by remember { mutableStateOf(data.shifts.lastOrNull()?.unit ?: "") }
    var focus by remember { mutableStateOf(data.settings.focusAutoWithShift) }
    val recentUnits = data.shifts.map { it.unit }.filter { it.isNotBlank() }.distinct().takeLast(3)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("${type.label}schicht starten", style = MaterialTheme.typography.headlineSmall)
            SectionLabel("Schichttyp")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftType.entries.forEach { t ->
                    de.mediclever.schichtclever.ui.components.McChip(t.label, t == type, { type = t })
                }
            }
            SectionLabel("Fahrzeug / Station (optional)")
            OutlinedTextField(
                value = unit, onValueChange = { unit = it.take(40) }, singleLine = true,
                placeholder = { Text(data.settings.role.unitHint) },
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            )
            if (recentUnits.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    recentUnits.forEach { u -> de.mediclever.schichtclever.ui.components.McChip(u, u == unit, { unit = u }) }
                }
            }
            SectionLabel("Automatisch mitstarten")
            SettingRow("Fokus-Modus", "${data.settings.blockedApps.size} Apps pausiert" + if (data.settings.dndEnabled) ", Bitte nicht stören" else "", Icons.Outlined.Block, focus, { focus = it })
            Text(
                "Blaufilter: " + when (data.settings.filterSchedule) {
                    FilterSchedule.MIT_SCHICHT -> if (type.night) "startet mit dieser Schicht" else "bleibt aus (keine Nachtschicht)"
                    FilterSchedule.ZEITRAUM -> "läuft von 22 bis 06 Uhr"
                    FilterSchedule.MANUELL -> "nur manuell"
                },
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            Button(
                onClick = { Repository.startShift(type, unit = unit.trim(), focus = focus); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.small,
            ) { Text("Schicht starten".uppercase(), style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun EndShiftSheet(data: AppData, shift: Shift, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rating by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val es = data.einsaetze.filter { it.shiftId == shift.id }
    val top = es.flatMap { it.measures }.groupingBy { it.skillId }.eachCount().toList().sortedByDescending { it.second }.take(3)
    val peak = es.groupingBy { it.alarmAt.hourOfDay() }.eachCount().maxByOrNull { it.value }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, shape = MaterialTheme.shapes.medium, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Schicht beenden", style = MaterialTheme.typography.headlineSmall)
            Text("${shift.type.label} · ${shift.startAt.date()} ${shift.unit}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(durationHm(System.currentTimeMillis() - shift.startAt), "Stunden", Modifier.weight(1f))
                StatTile(es.size.toString(), data.settings.role.einsatzWord(true), Modifier.weight(1f))
                StatTile(es.sumOf { it.measures.size }.toString(), "Maßnahmen", Modifier.weight(1f))
            }
            if (top.isNotEmpty()) {
                SectionLabel("Top-Maßnahmen", trailing = peak?.let { "Peak %02d–%02d Uhr".format(it.key, (it.key + 1) % 24) })
                top.forEach { (id, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(Catalog.byId(id, data.settings.customSkills)?.name ?: id, Modifier.weight(1f))
                        Text("${n}×", fontWeight = FontWeight.Bold)
                    }
                }
            }
            SectionLabel("Wie war die Schicht?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hart", "Zäh", "Okay", "Gut", "Top").forEachIndexed { i, label ->
                    val v = i + 1
                    McCard(Modifier.weight(1f), accent = rating == v, onClick = { rating = v }, padding = 8.dp) {
                        Text("$v", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally), color = if (rating == v) LocalExtra.current.accent else MaterialTheme.colorScheme.onSurface)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(500) },
                placeholder = { Text("Notiz für dich, z. B. was du nachlesen willst") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = MaterialTheme.shapes.small, minLines = 2,
            )
            Text(
                "Fokus-Modus und Blaufilter werden beendet.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(
                onClick = { Repository.endShift(rating, note.trim()); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("Speichern & Feierabend".uppercase(), style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Weiterarbeiten") }
        }
    }
}
