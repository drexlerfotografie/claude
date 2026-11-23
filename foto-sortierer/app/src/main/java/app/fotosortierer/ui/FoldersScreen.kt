package app.fotosortierer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate

private data class Suggestion(val label: String, val relative: String, val note: String? = null)

private val SUGGESTIONS = listOf(
    Suggestion("Kamera", "DCIM/Camera"),
    Suggestion("Screenshots", "Pictures/Screenshots"),
    Suggestion("Screenshots (DCIM)", "DCIM/Screenshots"),
    Suggestion("Downloads", "Download"),
    Suggestion(
        "WhatsApp Bilder", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
        "Verschobene Bilder fehlen danach in den WhatsApp-Chats. Lieber „Kopieren“ verwenden.",
    ),
    Suggestion(
        "WhatsApp Videos", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
        "Verschobene Videos fehlen danach in den WhatsApp-Chats. Lieber „Kopieren“ verwenden.",
    ),
    Suggestion("Telegram", "Pictures/Telegram"),
    Suggestion("Signal", "Pictures/Signal"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoldersScreen(vm: AppViewModel, modifier: Modifier) {
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    val root = vm.storageRoot

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Quellordner", style = MaterialTheme.typography.titleMedium)
        Text("Aus diesen Ordnern werden Fotos und Videos eingelesen.", style = MaterialTheme.typography.bodySmall)
        if (vm.sources.isEmpty()) Text("Noch keine gewählt.", style = MaterialTheme.typography.bodyMedium)
        vm.sources.forEach { path ->
            Card {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null)
                    Text(
                        shortPath(path, root),
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { vm.removeSource(path) }) { Icon(Icons.Default.Delete, "Entfernen") }
                }
            }
        }
        Button(onClick = { picker = PickerMode.SOURCE }) {
            Icon(Icons.Default.Add, null)
            Text("  Ordner hinzufügen")
        }

        val available = SUGGESTIONS.filter { s -> File(root, s.relative).isDirectory && "$root/${s.relative}" !in vm.sources }
        if (available.isNotEmpty()) {
            Text("Gefunden auf deinem Handy:", style = MaterialTheme.typography.bodySmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                available.forEach { s ->
                    AssistChip(
                        onClick = {
                            vm.addSource("$root/${s.relative}")
                            if (s.note != null) warning = s.note
                        },
                        label = { Text("+ ${s.label}") },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Unterordner einbeziehen")
                Text("Durchsucht auch alle Ordner innerhalb der Quellordner.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(vm.recursive, vm::changeRecursive)
        }

        HorizontalDivider()

        Text("Zielordner", style = MaterialTheme.typography.titleMedium)
        Card {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null)
                Text(shortPath(vm.targetRoot, root), Modifier.weight(1f).padding(horizontal = 8.dp))
                TextButton(onClick = { picker = PickerMode.TARGET }) { Text("Ändern") }
            }
        }
        val today = LocalDate.now()
        Text(
            "Beispiel: ${shortPath(vm.targetRoot, root)}/${today.year}/" +
                String.format("%04d_%02d_%02d", today.year, today.monthValue, today.dayOfMonth) + "_Rom_001.jpg",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Tipp: Ein Ordner unter „Pictures“ oder „DCIM“ wird von allen Galerie-Apps als Album angezeigt. " +
                "Bereits sortierte Dateien im Zielordner werden erkannt und nicht noch einmal angefasst.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (vm.sources.isNotEmpty()) {
            OutlinedButton(onClick = vm::scan, modifier = Modifier.fillMaxWidth()) { Text("Jetzt einlesen") }
        }
    }

    picker?.let { mode ->
        FolderPicker(
            start = if (mode == PickerMode.TARGET && File(vm.targetRoot).isDirectory) vm.targetRoot else root,
            root = root,
            title = if (mode == PickerMode.SOURCE) "Quellordner wählen" else "Zielordner wählen",
            allowCreate = mode == PickerMode.TARGET,
            onDismiss = { picker = null },
            onPick = { path ->
                if (mode == PickerMode.SOURCE) vm.addSource(path) else vm.changeTarget(path)
                picker = null
            },
        )
    }
    warning?.let { w ->
        AlertDialog(
            onDismissRequest = { warning = null },
            confirmButton = { TextButton(onClick = { warning = null }) { Text("Verstanden") } },
            title = { Text("Hinweis") },
            text = { Text(w) },
        )
    }
}

private enum class PickerMode { SOURCE, TARGET }

@Composable
private fun FolderPicker(
    start: String,
    root: String,
    title: String,
    allowCreate: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var current by remember { mutableStateOf(start) }
    var newName by remember { mutableStateOf<String?>(null) }
    val dirs = remember(current) {
        File(current).listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            ?.filter { it.path != "$root/Android/data" && it.path != "$root/Android/obb" }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current != root) {
                        IconButton(onClick = { current = File(current).parent ?: root }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                    }
                    Text(shortPath(current, root), style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(dirs, key = { it.path }) { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { current = d.path }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Folder, null)
                            Text(d.name, Modifier.padding(start = 8.dp))
                        }
                    }
                    if (dirs.isEmpty()) item { Text("Keine Unterordner", Modifier.padding(vertical = 10.dp)) }
                }
                if (allowCreate) {
                    TextButton(onClick = { newName = "" }) { Text("+ Neuer Ordner hier") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Diesen Ordner wählen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )

    newName?.let { name ->
        AlertDialog(
            onDismissRequest = { newName = null },
            title = { Text("Neuer Ordner") },
            text = {
                androidx.compose.material3.OutlinedTextField(name, { newName = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = name.trim().replace("/", "")
                    if (clean.isNotEmpty()) {
                        val f = File(current, clean)
                        f.mkdirs()
                        current = f.path
                    }
                    newName = null
                }) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { newName = null }) { Text("Abbrechen") } },
        )
    }
}
