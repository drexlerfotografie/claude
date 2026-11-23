package app.fotosortierer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class Kind(val label: String) { ALL("Alle"), PHOTO("Fotos"), VIDEO("Videos") }

private sealed interface Cell {
    data class Header(val title: String, val count: Int) : Cell
    data class Media(val item: MediaItem) : Cell
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: AppViewModel, modifier: Modifier) {
    if (!vm.scanned) {
        Hint(modifier, "Lies zuerst deine Ordner ein.", "Jetzt einlesen", vm::scan)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.ALL) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var fromEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var toEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var onlyUncertain by rememberSaveable { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MediaItem?>(null) }

    val library = vm.library
    val folders = remember(library) { library.map { it.folder }.distinct().sorted() }

    val cells = remember(library, query, kind, folder, fromEpoch, toEpoch, newestFirst, onlyUncertain) {
        val q = query.trim().lowercase()
        val from = fromEpoch?.let { LocalDate.ofEpochDay(it) }
        val to = toEpoch?.let { LocalDate.ofEpochDay(it) }
        val filtered = library.filter { item ->
            val d = item.taken.toLocalDate()
            (q.isEmpty() || item.path.lowercase().contains(q)) &&
                (kind == Kind.ALL || (kind == Kind.VIDEO) == item.isVideo) &&
                (folder == null || item.folder == folder) &&
                (from == null || !d.isBefore(from)) &&
                (to == null || !d.isAfter(to)) &&
                (!onlyUncertain || !item.source.reliable)
        }.let { list -> if (newestFirst) list.sortedByDescending { it.taken } else list.sortedBy { it.taken } }

        buildList<Cell> {
            filtered.groupBy { it.taken.withDayOfMonth(1).toLocalDate() }.forEach { (month, items) ->
                add(Cell.Header(month.format(MONTH_FORMAT), items.size))
                items.forEach { add(Cell.Media(it)) }
            }
        }
    }
    val resultCount = cells.count { it is Cell.Media }

    Column(modifier) {
        if (vm.stale) StaleBanner(vm::scan)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Dateiname, Ordner, Anlass …") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Leeren") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Kind.entries.forEach { k ->
                item { FilterChip(kind == k, { kind = k }, { Text(k.label) }) }
            }
            item {
                val label = if (fromEpoch == null) "Zeitraum" else {
                    val f = DateTimeFormatter.ofPattern("dd.MM.yy")
                    "${LocalDate.ofEpochDay(fromEpoch!!).format(f)}–${toEpoch?.let { LocalDate.ofEpochDay(it).format(f) } ?: "…"}"
                }
                FilterChip(fromEpoch != null, { if (fromEpoch != null) { fromEpoch = null; toEpoch = null } else showRange = true }, { Text(label) })
            }
            item {
                Box {
                    FilterChip(folder != null, { if (folder != null) folder = null else folderMenu = true }, {
                        Text(folder?.substringAfterLast('/') ?: "Ordner", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    })
                    DropdownMenu(folderMenu, { folderMenu = false }) {
                        folders.forEach { f ->
                            DropdownMenuItem(text = { Text(shortPath(f, vm.storageRoot)) }, onClick = { folder = f; folderMenu = false })
                        }
                    }
                }
            }
            item { FilterChip(onlyUncertain, { onlyUncertain = !onlyUncertain }, { Text("Datum unsicher") }) }
            item { FilterChip(false, { newestFirst = !newestFirst }, { Text(if (newestFirst) "Neueste zuerst" else "Älteste zuerst") }) }
        }
        Text(
            "$resultCount Treffer",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (resultCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Treffer") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(cells, key = { c -> if (c is Cell.Header) "h" + c.title else (c as Cell.Media).item.path },
                    span = { c -> GridItemSpan(if (c is Cell.Header) maxLineSpan else 1) }) { c ->
                    when (c) {
                        is Cell.Header -> Text(
                            "${c.title}  ·  ${c.count}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
                        )
                        is Cell.Media -> Thumb(c.item, onClick = { detail = c.item })
                    }
                }
            }
        }
    }

    if (showRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    fromEpoch = state.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    toEpoch = state.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showRange = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Abbrechen") } },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f))
        }
    }

    detail?.let { item ->
        DetailDialog(item, vm.storageRoot, onDismiss = { detail = null })
    }
}
