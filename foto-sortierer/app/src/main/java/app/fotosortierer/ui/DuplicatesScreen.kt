package app.fotosortierer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.fotosortierer.core.MediaItem
import app.fotosortierer.data.DUPLICATE_DIR

@Composable
fun DuplicatesScreen(vm: AppViewModel, modifier: Modifier) {
    if (!vm.scanned) {
        Hint(modifier, "Lies zuerst deine Ordner ein.", "Jetzt einlesen", vm::scan)
        return
    }
    val groups = vm.dupGroups
    if (groups == null) {
        Hint(
            modifier,
            "Findet Dateien mit exakt identischem Inhalt in ${vm.library.size} Dateien – egal wie sie heißen. " +
                "Ähnliche, aber bearbeitete Fotos werden nicht erkannt.",
            "Duplikate suchen",
            vm::findDuplicates,
        )
        return
    }
    var detail by remember { mutableStateOf<MediaItem?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val marked = vm.dupMarked
    val markedSize = groups.flatten().filter { it.path in marked }.sumOf { it.size }

    Column(modifier) {
        Text(
            if (groups.isEmpty()) "Keine Duplikate gefunden."
            else "${groups.size} ${if (groups.size == 1) "Gruppe" else "Gruppen"} · ${marked.size} zum Aussortieren markiert (${formatSize(markedSize)}). " +
                "Tippen = markieren/behalten, lange drücken = Details.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TextButton(onClick = vm::findDuplicates) { Text("Erneut suchen") }
            }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { it.first().path }) { group ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${group.size}× · je ${formatSize(group.first().size)}", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            items(group, key = { it.path }) { item ->
                                val isMarked = item.path in marked
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Thumb(
                                        item, size = 88.dp,
                                        dimmed = isMarked,
                                        selected = !isMarked,
                                        onClick = { vm.toggleDupMark(group, item) },
                                        onLongClick = { detail = item },
                                    )
                                    Text(
                                        if (isMarked) "aussortieren" else "behalten",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        group.forEach {
                            Text(
                                (if (it.path in marked) "✗ " else "✓ ") + shortPath(it.path, vm.storageRoot),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = { confirm = true },
                enabled = marked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text("${marked.size} Dateien nach $DUPLICATE_DIR verschieben") }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Duplikate aussortieren?") },
            text = {
                Text(
                    "${marked.size} Dateien werden nach ${shortPath(vm.targetRoot, vm.storageRoot)}/$DUPLICATE_DIR verschoben, " +
                        "nicht gelöscht. Schau sie dort durch und lösche den Ordner danach selbst. Rückgängig geht über „Verlauf“."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; vm.moveDuplicates() }) { Text("Verschieben") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
    detail?.let { DetailDialog(it, vm.storageRoot, onDismiss = { detail = null }) }
}
