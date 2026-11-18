package de.mediclever.tagebuch.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.ShiftTime
import de.mediclever.tagebuch.core.model.Tag
import de.mediclever.tagebuch.core.model.TagGroup
import de.mediclever.tagebuch.core.shift.Shifts
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.theme.groupColor

private fun parseTime(text: String): Int? {
    val m = Regex("""^\s*(\d{1,2})(?:[:.](\d{2}))?\s*$""").find(text) ?: return null
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

fun shiftSubtitle(d: Diary, tag: Tag): String {
    val t = Shifts.time(d, tag.id)
    val time = if (t?.startMinute != null && t.endMinute != null) "${fmtTime(t.startMinute!!)} bis ${fmtTime(t.endMinute!!)}" else "ohne Zeiten"
    val short = Shifts.short(d, tag)
    return time + if (short != tag.name) " · im Eintrag: $short" else ""
}

/** Dienstarten ein- und ausschalten, umbenennen, eigene anlegen. Nur eingeschaltete stehen im Eintrag. */
@Composable
fun ShiftsScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val diary = env.diary
    val group = Shifts.group(diary)
    var edit by remember { mutableStateOf<Tag?>(null) }
    var create by remember { mutableStateOf(false) }

    fun applyTemplate(key: String) {
        val list = Shifts.templates.getValue(key)
        val g = group ?: TagGroup(name = "Dienst", colorIndex = 0, sortOrder = (diary.groups.minOfOrNull { it.sortOrder } ?: 0) - 1)
        val existing = diary.tags.filter { it.groupId == g.id }
        var order = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val tags = list.map { t ->
            existing.firstOrNull { it.name.equals(t.name, ignoreCase = true) }?.copy(archivedAt = null)
                ?: Tag(groupId = g.id, name = t.name, icon = t.icon, colorIndex = t.colorIndex, sortOrder = order++)
        }
        val times = list.zip(tags).map { (t, tag) -> ShiftTime(tag.id, t.short, t.start, t.end, t.free) }
        val keep = tags.map { it.id }.toSet()
        // was nicht zur Vorlage gehoert, wird ausgeschaltet, bleibt aber in alten Eintraegen erhalten
        val off = existing.filter { it.id !in keep && it.archivedAt == null }.map { it.copy(archivedAt = System.currentTimeMillis()) }
        env.act {
            if (group == null) save(g)
            save(tags + off)
            updateSettings { s -> s.copy(shiftGroupId = g.id, shiftTimes = s.shiftTimes.filter { it.tagId !in keep } + times) }
        }
    }

    SubScreen("Deine Dienstarten", nav.back) {
        Text("Schalte ein, was es bei dir gibt. Nur das steht später im Eintrag. Namen, Zeiten und Farbe änderst du durch Antippen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("Mit einer Vorlage starten")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (key in Shifts.templates.keys) OutlinedButton({ applyTemplate(key) }, Modifier.heightIn(min = 48.dp)) { Text(key) }
        }
        val all = Shifts.all(diary)
        if (all.isNotEmpty()) RowGroup {
            all.forEachIndexed { i, tag ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "${tag.name} bearbeiten") { edit = tag }.heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(groupColor(tag.colorIndex ?: group?.colorIndex)))
                    Column(Modifier.weight(1f)) {
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                        Text(shiftSubtitle(diary, tag), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(tag.archivedAt == null, { on -> env.act { save(tag.copy(archivedAt = if (on) null else System.currentTimeMillis())) } }, Modifier.semantics { contentDescription = "${tag.name} im Eintrag anzeigen" })
                }
            }
        }
        if (group == null && diary.groups.any { it.archivedAt == null }) {
            SectionLabel("Oder eine vorhandene Gruppe verwenden")
            RowGroup {
                diary.groups.filter { it.archivedAt == null }.sortedBy { it.sortOrder }.forEachIndexed { i, g ->
                    if (i > 0) RowDivider()
                    SettingRow(g.name, plural(diary.tags.count { it.groupId == g.id && it.archivedAt == null }, "Aktivität", "Aktivitäten"), onClick = { env.act { updateSettings { it.copy(shiftGroupId = g.id) } } })
                }
            }
        }
        if (group != null) OutlinedButton({ create = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Eigene Dienstart anlegen") }
        Text("Ausgeschaltete Dienstarten bleiben in alten Einträgen erhalten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val target = edit
    if (target != null || create) ShiftDialog(diary, target, onDismiss = { edit = null; create = false }) { name, short, start, end, free, color ->
        val g = group ?: return@ShiftDialog
        val tag = (target ?: Tag(groupId = g.id, name = name, icon = "schedule", sortOrder = (Shifts.all(diary).maxOfOrNull { it.sortOrder } ?: -1) + 1)).copy(name = name, colorIndex = color)
        env.act {
            save(tag)
            updateSettings { s -> s.copy(shiftGroupId = g.id, shiftTimes = s.shiftTimes.filter { it.tagId != tag.id } + ShiftTime(tag.id, short, start, end, free)) }
        }
        edit = null; create = false
    }
}

@Composable
private fun ShiftDialog(diary: Diary, tag: Tag?, onDismiss: () -> Unit, onSave: (String, String, Int?, Int?, Boolean, Int?) -> Unit) {
    val t = tag?.let { Shifts.time(diary, it.id) }
    var name by remember { mutableStateOf(tag?.name ?: "") }
    var short by remember { mutableStateOf(t?.short ?: "") }
    var start by remember { mutableStateOf(t?.startMinute?.let { fmtTime(it) } ?: "") }
    var end by remember { mutableStateOf(t?.endMinute?.let { fmtTime(it) } ?: "") }
    var free by remember { mutableStateOf(tag?.let { Shifts.isFree(diary, it) } ?: false) }
    var color by remember { mutableStateOf(tag?.colorIndex) }
    val timesOk = (start.isBlank() || parseTime(start) != null) && (end.isBlank() || parseTime(end) != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "Eigene Dienstart" else "Dienstart bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(short, { short = it.take(12) }, label = { Text("Kurzname im Eintrag (optional)") }, placeholder = { Text(Shifts.shorten(name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Beginn") }, placeholder = { Text("7:00") }, singleLine = true, isError = start.isNotBlank() && parseTime(start) == null, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Ende") }, placeholder = { Text("19:00") }, singleLine = true, isError = end.isNotBlank() && parseTime(end) == null, modifier = Modifier.weight(1f))
                }
                Text("Zeiten sind freiwillig.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Zählt als freier Tag", checked = free, onChange = { free = it })
                ColorPicker(color, { color = it })
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), short.trim(), parseTime(start), parseTime(end), free, color) }, enabled = name.isNotBlank() && timesOk) { Text("Speichern") } },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}
