package app.fotosortierer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fotosortierer.data.OpType

@Composable
fun PreviewScreen(vm: AppViewModel, modifier: Modifier) {
    val plan = vm.plan ?: return
    BackHandler(onBack = vm::closePlan)
    var confirm by remember { mutableStateOf<OpType?>(null) }

    val byDir = remember(plan) { plan.groupBy { it.targetDir }.toSortedMap(compareByDescending { it }) }
    val uncertain = remember(plan) { plan.count { !it.item.source.reliable } }
    val totalSize = remember(plan) { plan.sumOf { it.item.size } }

    Column(modifier) {
        Text(
            "${plural(plan.size, "Datei", "Dateien")} → ${plural(byDir.size, "Ordner", "Ordner")} unter ${shortPath(vm.targetRoot, vm.storageRoot)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (uncertain > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(
                    (if (uncertain == 1) "1 Datei hat kein Aufnahmedatum. Dafür" else "$uncertain Dateien haben kein Aufnahmedatum. Für sie") +
                        " wird das Änderungsdatum der Datei verwendet, das kann falsch sein (z. B. Download- oder Kopierdatum). Du kannst sie unter " +
                        "„Nur unsichere“ prüfen und bei Bedarf auslassen.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            byDir.forEach { (dir, moves) ->
                item(key = "h$dir") {
                    Text(
                        "${shortPath(dir, vm.storageRoot)}  (${moves.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(moves.sortedBy { it.targetName }, key = { it.item.path }) { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Thumb(m.item, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.targetName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "aus ${shortPath(m.item.folder, vm.storageRoot)}/${m.item.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::closePlan) { Text("Zurück") }
                OutlinedButton(onClick = { confirm = OpType.COPY }, modifier = Modifier.weight(1f)) { Text("Kopieren") }
                Button(onClick = { confirm = OpType.MOVE }, modifier = Modifier.weight(1f)) { Text("Verschieben") }
            }
        }
    }

    confirm?.let { type ->
        val move = type == OpType.MOVE
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (move) "${plan.size} Dateien verschieben?" else "${plan.size} Dateien kopieren?") },
            text = {
                Text(
                    if (move) "Die Originale werden umbenannt und verschoben. Unter „Verlauf“ kannst du den Lauf komplett rückgängig machen."
                    else "Es werden Kopien angelegt (zusätzlich ${formatSize(totalSize)}). Die Originale bleiben unverändert."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    vm.executePlan(type)
                }) { Text(if (move) "Verschieben" else "Kopieren") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } },
        )
    }
}
