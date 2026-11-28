package de.mediclever.schichtclever.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.ShiftType
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.SettingRow
import de.mediclever.schichtclever.ui.theme.LocalExtra

@Composable
fun TemplatesScreen(data: AppData, onBack: () -> Unit) {
    val templates = data.settings.shiftTemplates
    var editing by remember { mutableStateOf<ShiftType?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    SubScreen("Meine Schichten", onBack) {
        Text(
            "Lege deine Schichten so an, wie sie im Dienstplan stehen, mit eigenem Kürzel, Beginn und Dauer. " +
                "Die Reihenfolge hier ist auch die Reihenfolge auf dem Startbildschirm.",
            style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.padding(bottom = 12.dp),
        )
        if (templates.isNotEmpty()) McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            templates.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = LocalExtra.current.line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.code, style = MaterialTheme.typography.headlineSmall, color = LocalExtra.current.accent,
                        modifier = Modifier.padding(start = 16.dp).width(56.dp), maxLines = 1,
                    )
                    Column(Modifier.weight(1f)) {
                        SettingRow(
                            t.label,
                            "${t.rangeLabel} · ${durationText(t.durationMinutes)}" + if (t.night) " · Nacht" else "",
                            onClick = { editing = t },
                        )
                    }
                    IconButton(onClick = { Repository.moveTemplate(t.id, -1) }, enabled = i > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Nach oben") }
                    IconButton(onClick = { Repository.moveTemplate(t.id, 1) }, enabled = i < templates.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Nach unten") }
                }
            }
        }
        Button(
            onClick = { editing = ShiftType(code = "", label = "", startMinute = 7 * 60, durationMinutes = 8 * 60, night = false) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Schicht anlegen".uppercase(), style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Auf Standard zurücksetzen", color = LocalExtra.current.muted)
        }
    }

    editing?.let { t ->
        TemplateDialog(
            initial = t,
            isNew = templates.none { it.id == t.id },
            existingCodes = templates.filter { it.id != t.id }.map { it.code.lowercase() }.toSet(),
            onDismiss = { editing = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Standard wiederherstellen?") },
            text = { Text("Deine eigenen Schichten werden durch Früh, Spät, Nacht, 12 h und 24 h ersetzt. Bereits erfasste Schichten bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { Repository.resetTemplates(); confirmReset = false }) { Text("Zurücksetzen") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
}

fun durationText(min: Int): String = if (min % 60 == 0) "${min / 60} h" else "%d:%02d h".format(min / 60, min % 60)

/** "22:00", "22.00", "2200" oder "22" → Minuten nach Mitternacht. */
fun parseTime(text: String): Int? {
    val digits = text.trim().replace('.', ':').replace(',', ':')
    val (h, m) = when {
        ':' in digits -> digits.split(':').let { it[0].toIntOrNull() to (it.getOrNull(1)?.ifBlank { "0" }?.toIntOrNull()) }
        digits.length in 3..4 -> digits.dropLast(2).toIntOrNull() to digits.takeLast(2).toIntOrNull()
        else -> digits.toIntOrNull() to 0
    }
    if (h == null || m == null || h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** "8", "8,5", "8:30" → Minuten. */
fun parseDuration(text: String): Int? {
    val t = text.trim().replace(',', '.')
    val min = if (':' in t) t.split(':').let { (it[0].toIntOrNull() ?: return null) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    else ((t.toDoubleOrNull() ?: return null) * 60).toInt()
    return min.takeIf { it in 30..48 * 60 }
}

@Composable
private fun TemplateDialog(initial: ShiftType, isNew: Boolean, existingCodes: Set<String>, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf(initial.code) }
    var label by remember { mutableStateOf(initial.label) }
    var start by remember { mutableStateOf(initial.startLabel) }
    var duration by remember { mutableStateOf(if (isNew && initial.code.isEmpty()) "" else durationText(initial.durationMinutes).removeSuffix(" h")) }
    var night by remember { mutableStateOf(initial.night) }
    var nightTouched by remember { mutableStateOf(!isNew) }
    var confirmDelete by remember { mutableStateOf(false) }

    val startMin = parseTime(start)
    val durMin = parseDuration(duration)
    // Vorschlag: Nacht, wenn die Schicht über Mitternacht geht oder spät beginnt
    val suggestNight = startMin != null && durMin != null && (startMin + durMin > 24 * 60 || startMin >= 19 * 60)
    val effectiveNight = if (nightTouched) night else suggestNight
    val codeTaken = code.trim().lowercase() in existingCodes
    val valid = code.isNotBlank() && !codeTaken && startMin != null && durMin != null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
        title = { Text(if (isNew) "Neue Schicht" else "Schicht bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.take(5) }, singleLine = true,
                        label = { Text("Kürzel") }, placeholder = { Text("N2") },
                        isError = codeTaken, supportingText = if (codeTaken) { { Text("vergeben") } } else null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    )
                    OutlinedTextField(
                        value = label, onValueChange = { label = it.take(24) }, singleLine = true,
                        label = { Text("Name") }, placeholder = { Text("Nacht RTW") },
                        modifier = Modifier.weight(2f), shape = MaterialTheme.shapes.small,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start, onValueChange = { start = it.take(5) }, singleLine = true,
                        label = { Text("Beginn") }, placeholder = { Text("22:00") },
                        isError = start.isNotBlank() && startMin == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    )
                    OutlinedTextField(
                        value = duration, onValueChange = { duration = it.take(5) }, singleLine = true,
                        label = { Text("Dauer (h)") }, placeholder = { Text("8,5") },
                        isError = duration.isNotBlank() && durMin == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small,
                    )
                }
                if (startMin != null && durMin != null) {
                    Text(
                        "Ende um " + ((startMin + durMin) % 1440).let { "%02d:%02d".format(it / 60, it % 60) } + " Uhr" +
                            if (startMin + durMin > 1440) " am Folgetag" else "",
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                    )
                }
                SettingRow(
                    "Nachtschicht", "Dunkles Design, Blaufilter und Nacht-Statistik",
                    checked = effectiveNight, onCheckedChange = { night = it; nightTouched = true },
                )
                if (!isNew) OutlinedButton(onClick = { confirmDelete = true }, shape = MaterialTheme.shapes.small) { Text("Löschen") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                Repository.saveTemplate(
                    initial.copy(
                        code = code.trim(),
                        label = label.trim().ifBlank { code.trim() },
                        startMinute = startMin!!, durationMinutes = durMin!!, night = effectiveNight,
                    )
                )
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${initial.code}“ löschen?") },
            text = { Text("Bereits erfasste Schichten bleiben in der Statistik erhalten.") },
            confirmButton = { TextButton(onClick = { Repository.deleteTemplate(initial.id); confirmDelete = false; onDismiss() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

