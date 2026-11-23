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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fotosortierer.core.Event
import app.fotosortierer.core.MediaItem
import app.fotosortierer.core.Naming
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val SHORT_DAY = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN)

fun eventTitle(e: Event): String {
    val s = e.start
    val t = e.end
    return if (s.toLocalDate() == t.toLocalDate()) s.format(DAY_FORMAT)
    else "${s.format(SHORT_DAY)} – ${t.format(DAY_FORMAT)}"
}

fun eventTime(e: Event): String? {
    if (e.items.all { it.taken.toLocalTime().toSecondOfDay() == 0 }) return null
    val a = e.start.format(TIME)
    val b = e.end.format(TIME)
    return if (a == b) "$a Uhr" else "$a–$b Uhr"
}

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
    var categoryDialog by remember { mutableStateOf(false) }
    val analysis by vm.analysis.collectAsState()

    val toSort = vm.toSort
    val allEvents = vm.events
    val years = remember(allEvents) { allEvents.map { it.start.year }.distinct().sortedDescending() }
    val uncertainCount = remember(toSort) { toSort.count { !it.source.reliable } }
    val events = remember(allEvents, year, onlyUncertain) {
        allEvents.asSequence()
            .filter { year == null || it.start.year == year }
            .filter { !onlyUncertain || it.items.any { i -> !i.source.reliable } }
            .sortedByDescending { it.start }
            .toList()
    }
    val activeCount = allEvents.filter { it.id !in vm.excludedEvents }.sumOf { e -> e.items.count { it.path !in vm.excluded } }

    Column(modifier) {
        if (vm.stale) StaleBanner(vm::scan)
        analysis?.let { (done, total) ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Vorschläge werden berechnet … $done von $total Ereignissen",
                    style = MaterialTheme.typography.labelSmall,
                )
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            "${plural(toSort.size, "Datei", "Dateien")} in ${plural(allEvents.size, "Ereignis", "Ereignissen")} · " +
                "${vm.library.size - toSort.size} bereits sortiert" +
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
            items(events.size, key = { events[it].id }) { index ->
                EventCard(vm, events[index], events.getOrNull(index - 1), onDetail = { detail = it })
            }
        }

        if (vm.selection.isNotEmpty()) {
            SelectionBar(vm, onAnlass = { anlassDialog = true }, onCategory = { categoryDialog = true })
        } else {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = vm::buildPlan,
                    enabled = activeCount > 0,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { Text("Vorschau für ${plural(activeCount, "Datei", "Dateien")}") }
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
    if (categoryDialog) {
        CategoryDialog(
            title = "Kategorie für ${plural(vm.selection.size, "Datei", "Dateien")}",
            categories = vm.categories,
            current = null,
            allowInherit = true,
            onDismiss = { categoryDialog = false },
            onPick = {
                vm.setCategoryForSelection(it)
                categoryDialog = false
            },
        )
    }
}

@Composable
private fun EventCard(vm: AppViewModel, event: Event, above: Event?, onDetail: (MediaItem) -> Unit) {
    val included = event.id !in vm.excludedEvents
    val anlass = vm.anlassFor(event)
    val category = vm.categoryFor(event)
    val videos = event.items.count { it.isVideo }
    val info = vm.eventInfo[event.id]
    var pickCategory by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(included, { vm.setEventIncluded(event, it) })
                Column(Modifier.weight(1f)) {
                    Text(eventTitle(event), style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            eventTime(event),
                            plural(event.items.size - videos, "Foto", "Fotos") + if (videos > 0) ", " + plural(videos, "Video", "Videos") else "",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { vm.selectAll(event.items.map { it.path }) }) { Text("Alle wählen") }
            }

            // Kategorie
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { pickCategory = true }, enabled = included, modifier = Modifier.fillMaxWidth()) {
                        if (vm.isSuggestedCategory(event)) Icon(Icons.Default.AutoAwesome, "Vorschlag", Modifier.padding(end = 6.dp))
                        Text(category.ifEmpty { "Keine Kategorie" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(pickCategory, { pickCategory = false }) {
                        vm.categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { vm.setEventCategory(event, c); pickCategory = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Keine Kategorie") }, onClick = { vm.setEventCategory(event, ""); pickCategory = false })
                    }
                }
                if (above != null && included) {
                    val aboveAnlass = vm.anlassFor(above)
                    val aboveCat = vm.categoryFor(above)
                    if ((aboveAnlass.isNotEmpty() || aboveCat.isNotEmpty()) && (aboveAnlass != anlass || aboveCat != category)) {
                        IconButton(onClick = {
                            vm.setEventAnlass(event, aboveAnlass)
                            vm.setEventCategory(event, aboveCat)
                        }) {
                            Icon(Icons.Default.ArrowUpward, "Kategorie und Anlass von oben übernehmen")
                        }
                    }
                }
            }

            // Anlass
            OutlinedTextField(
                value = anlass,
                onValueChange = { vm.setEventAnlass(event, it) },
                label = { Text(if (vm.isSuggestedAnlass(event)) "Anlass (Vorschlag aus Ort)" else "Anlass (optional)") },
                leadingIcon = if (vm.isSuggestedAnlass(event)) ({ Icon(Icons.Default.AutoAwesome, null) }) else null,
                trailingIcon = if (anlass.isNotEmpty()) ({
                    IconButton(onClick = { vm.setEventAnlass(event, "") }) { Icon(Icons.Default.Close, "Leeren") }
                }) else null,
                singleLine = true,
                enabled = included,
                modifier = Modifier.fillMaxWidth(),
            )
            val ideas = vm.anlassIdeas(event)
            if (included && ideas.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ideas) { idea -> AssistChip(onClick = { vm.setEventAnlass(event, idea) }, label = { Text(idea) }) }
                }
            }

            val first = event.items.first().taken.toLocalDate()
            val target = Naming.dir("", first, category).trimStart('/') + "/" + Naming.fileName(Naming.prefix(first, anlass), 1, "jpg")
            Text(
                "→ $target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (info != null && info.labels.isNotEmpty()) {
                Text(
                    "Erkannt: " + info.labels.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(event.items, key = { it.path }) { item ->
                    val ownA = vm.itemAnlass[item.path]
                    val ownC = vm.itemCategory[item.path]
                    val badge = listOfNotNull(ownC?.ifEmpty { "–" }, ownA?.let { Naming.sanitizeAnlass(it).ifEmpty { "–" } })
                        .joinToString(" · ").ifEmpty { null }
                    Thumb(
                        item,
                        size = 76.dp,
                        selected = item.path in vm.selection,
                        dimmed = !included || item.path in vm.excluded,
                        badge = badge,
                        onClick = { vm.toggleSelect(item.path) },
                        onLongClick = { onDetail(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(vm: AppViewModel, onAnlass: () -> Unit, onCategory: () -> Unit) {
    val allExcluded = vm.selection.all { it in vm.excluded }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = vm::clearSelection) { Icon(Icons.Default.Close, "Auswahl aufheben") }
            Text("${vm.selection.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::toggleExcludeSelection) { Text(if (allExcluded) "Einbeziehen" else "Auslassen") }
            OutlinedButton(onClick = onCategory) { Text("Kategorie") }
            Button(onClick = onAnlass) { Text("Anlass") }
        }
    }
}

/** [onPick] mit null = Wert des Ereignisses verwenden, "" = keine Kategorie. */
@Composable
fun CategoryDialog(
    title: String,
    categories: List<String>,
    current: String?,
    allowInherit: Boolean,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val options = categories.map { it to it } + ("" to "Keine Kategorie") +
                    if (allowInherit) listOf(null to "Wie das Ereignis") else emptyList()
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(current == value, role = Role.RadioButton) { onPick(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current == value, null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun AnlassDialog(count: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anlass für ${plural(count, "Datei", "Dateien")}") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Anlass") })
                Spacer(Modifier.height(8.dp))
                Text(
                    "Überschreibt den Anlass des Ereignisses nur für diese Dateien. Leer lassen = wieder den Anlass des Ereignisses verwenden.",
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
