package app.fotosortierer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.fotosortierer.data.Journal
import java.time.Instant
import java.time.ZoneId

@Composable
fun HistoryScreen(vm: AppViewModel, modifier: Modifier) {
    val journals = vm.journals
    var undoTarget by remember { mutableStateOf<Journal?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    if (journals.isEmpty()) {
        Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Noch keine Läufe. Jeder Sortier- und Duplikat-Lauf wird hier protokolliert und lässt sich rückgängig machen.")
        }
        return
    }

    LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(journals, key = { it.id }) { j ->
            val time = Instant.ofEpochMilli(j.createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val open = j.id in expanded
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(j.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        time.format(DATETIME_FORMAT) + if (j.undone) " · rückgängig gemacht" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (open) {
                        j.entries.take(200).forEach { e ->
                            Text(
                                "${e.from.substringAfterLast('/')} → ${shortPath(e.to, vm.storageRoot)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (j.entries.size > 200) Text("… und ${j.entries.size - 200} weitere", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { expanded = if (open) expanded - j.id else expanded + j.id }) {
                            Text(if (open) "Weniger" else "${j.entries.size} Einträge zeigen")
                        }
                        if (!j.undone) OutlinedButton(onClick = { undoTarget = j }) { Text("Rückgängig") }
                        else TextButton(onClick = { vm.deleteJournal(j) }) { Text("Entfernen") }
                    }
                }
            }
        }
    }

    undoTarget?.let { j ->
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text("Lauf rückgängig machen?") },
            text = {
                Text(
                    "${j.entries.size} Dateien werden an ihren ursprünglichen Ort mit ursprünglichem Namen zurückgelegt" +
                        " (Kopien werden gelöscht). Dateien, die du seitdem selbst verschoben hast, werden übersprungen."
                )
            },
            confirmButton = { TextButton(onClick = { undoTarget = null; vm.undo(j) }) { Text("Rückgängig") } },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text("Abbrechen") } },
        )
    }
}
