package app.fotosortierer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fotosortierer.core.MediaItem
import app.fotosortierer.core.Naming
import java.time.LocalDate

@Composable
fun SortScreen(vm: AppViewModel, modifier: Modifier, openFolders: () -> Unit) {
    if (vm.sources.isEmpty()) {
        Hint(modifier, "Noch keine Quellordner gewählt.", "Ordner wählen", openFolders)
        return
    }
    if (!vm.scanned) {
        Hint(modifier, "Lies deine Ordner ein, um loszulegen.", "Jetzt einlesen", vm::scan)
        return
    }

    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var onlyUncertain by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MediaItem?>(null) }
    var anlassDialog by remember { mutableStateOf(false) }

    val toSort = vm.toSort
    val years = remember(toSort) { toSort.map { it.taken.year }.distinct().sortedDescending() }
    val uncertainCount = remember(toSort) { toSort.count { !it.source.reliable } }
    val days = remember(toSort, year, onlyUncertain) {
        toSort.asSequence()
            .filter { year == null || it.taken.year == year }
            .filter { !onlyUncertain || !it.source.reliable }
            .groupBy { it.taken.toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (d, list) -> d to list.sortedBy { it.taken } }
    }
    val activeCount = toSort.count { it.path !in vm.excluded && it.taken.toLocalDate() !in vm.excludedDays }

    Column(modifier) {
        if (vm.stale) StaleBanner(vm::scan)
        Text(
            "${toSort.size} Dateien zu sortieren · ${vm.library.size - toSort.size} bereits sortiert" +
                if (uncertainCount > 0) " · $uncertainCount mit unsicherem Datum" else "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { FilterChip(year == null, { year = null }, { Text("Alle Jahre") }) }
            items(years) { y -> FilterChip(year == y, { year = if (year == y) null else y }, { Text("$y") }) }
            if (uncertainCount > 0) item {
                FilterChip(onlyUncertain, { onlyUncertain = !onlyUncertain }, { Text("Nur unsichere") })
            }
        }

        if (toSort.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Alles sortiert.") }
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(days.size, key = { days[it].first.toEpochDay() }) { index ->
                val (day, items) = days[index]
                val previousAnlass = if (index > 0) vm.dayAnlass[days[index - 1].first] else null
                DayCard(vm, day, items, previousAnlass, onDetail = { detail = it })
            }
        }

        if (vm.selection.isNotEmpty()) {
            SelectionBar(vm, onAnlass = { anlassDialog = true })
        } else {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = vm::buildPlan,
                    enabled = activeCount > 0,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { Text("Vorschau für $activeCount Dateien") }
            }
        }
    }

    detail?.let { item ->
        DetailDialog(item, vm.storageRoot, onDismiss = { detail = null })
    }
    if (anlassDialog) {
        AnlassDialog(
            count = vm.selection.size,
            onDismiss = { anlassDialog = false },
            onConfirm = {
                vm.setAnlassForSelection(it)
                anlassDialog = false
            },
        )
    }
}

@Composable
private fun DayCard(
    vm: AppViewModel,
    day: LocalDate,
    items: List<MediaItem>,
    previousAnlass: String?,
    onDetail: (MediaItem) -> Unit,
) {
    val included = day !in vm.excludedDays
    val anlass = vm.dayAnlass[day] ?: ""
    val videos = items.count { it.isVideo }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(included, { vm.setDayIncluded(day, it) })
                Column(Modifier.weight(1f)) {
                    Text(day.format(DAY_FORMAT), style = MaterialTheme.typography.titleSmall)
                    Text(
                        plural(items.size - videos, "Foto", "Fotos") + if (videos > 0) ", " + plural(videos, "Video", "Videos") else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { vm.selectAll(items.map { it.path }) }) { Text("Alle wählen") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = anlass,
                    onValueChange = { vm.setDayAnlass(day, it) },
                    label = { Text("Anlass (optional)") },
                    singleLine = true,
                    enabled = included,
                    modifier = Modifier.weight(1f),
                )
                if (!previousAnlass.isNullOrBlank() && previousAnlass != anlass) {
                    IconButton(onClick = { vm.setDayAnlass(day, previousAnlass) }, enabled = included) {
                        Icon(Icons.Default.ArrowUpward, "Anlass von oben übernehmen „$previousAnlass“")
                    }
                }
            }
            Text(
                "→ ${day.year}/" + Naming.fileName(Naming.prefix(day, anlass), 1, "jpg"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.path }) { item ->
                    val own = vm.itemAnlass[item.path]
                    Thumb(
                        item,
                        size = 76.dp,
                        selected = item.path in vm.selection,
                        dimmed = !included || item.path in vm.excluded,
                        badge = own?.let { Naming.sanitizeAnlass(it).ifEmpty { "–" } },
                        onClick = { vm.toggleSelect(item.path) },
                        onLongClick = { onDetail(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(vm: AppViewModel, onAnlass: () -> Unit) {
    val allExcluded = vm.selection.all { it in vm.excluded }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Auswahl aufheben") }
            Text("${vm.selection.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = vm::toggleExcludeSelection) { Text(if (allExcluded) "Einbeziehen" else "Auslassen") }
            Button(onClick = onAnlass) { Text("Anlass …") }
        }
    }
}

@Composable
private fun AnlassDialog(count: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anlass für $count Dateien") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Anlass") })
                Spacer(Modifier.height(8.dp))
                Text(
                    "Überschreibt den Anlass des Tages nur für diese Dateien. Leer lassen = wieder den Tages-Anlass verwenden.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun Hint(modifier: Modifier, text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun StaleBanner(onScan: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ordnerauswahl geändert.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onScan) { Text("Neu einlesen") }
        }
    }
}
