package de.mediclever.tagebuch.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.data.StarterPacks
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.icons.MoodVariants
import de.mediclever.tagebuch.ui.icons.iconFor
import de.mediclever.tagebuch.ui.theme.groupColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Einrichtung: Stimmungen, Gruppen mit Aktivitaeten, Skalen und Schreibvorlagen.
 * Unterseiten laufen ueber einen einfachen Seitenschluessel: "", "moods", "activities",
 * "group:<id>", "scale:<id>", "templates", "template:<id>".
 */
@Composable
fun SetupScreen(section: String, onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(section) }
    val root = section
    fun up() {
        page = when {
            page == root -> { onBack(); page }
            page.startsWith("group:") || page.startsWith("scale:") -> "activities"
            page.startsWith("template:") -> "templates"
            else -> if (root.isEmpty()) "" else { onBack(); page }
        }
    }
    BackHandler(enabled = page != root) { up() }
    when {
        page == "moods" -> MoodsPage(::up)
        page == "activities" -> BlocksPage(::up) { page = it }
        page.startsWith("group:") -> GroupPage(page.removePrefix("group:"), ::up)
        page.startsWith("scale:") -> ScalePage(page.removePrefix("scale:"), ::up)
        page == "templates" -> TemplatesPage(::up) { page = it }
        page.startsWith("template:") -> TemplatePage(page.removePrefix("template:"), ::up)
        else -> SubScreen("Einrichtung", onBack) {
            val d = LocalEnv.current.diary
            Text("Du bestimmst, was dein Tagebuch erfasst. Fest sind nur die fünf Stimmungsstufen, damit Auswertungen vergleichbar bleiben.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            RowGroup {
                SettingRow("Stimmungen", plural(d.moods.count { it.archivedAt == null }, "Stimmung", "Stimmungen") + " in fünf Stufen", Icons.Rounded.Mood, onClick = { page = "moods" })
                RowDivider()
                SettingRow("Aktivitäten und Skalen", "${plural(d.groups.count { it.archivedAt == null }, "Gruppe", "Gruppen")}, ${plural(d.tags.count { it.archivedAt == null }, "Aktivität", "Aktivitäten")}, ${plural(d.scales.count { it.archivedAt == null }, "Skala", "Skalen")}", Icons.Rounded.Category, onClick = { page = "activities" })
                RowDivider()
                SettingRow("Schreibvorlagen", plural(d.templates.size, "Vorlage", "Vorlagen"), Icons.Rounded.Description, onClick = { page = "templates" })
            }
        }
    }
}

// ---- Stimmungen --------------------------------------------------------------------------

@Composable
private fun MoodsPage(onBack: () -> Unit) {
    val env = LocalEnv.current
    val d = env.diary
    var editing by remember { mutableStateOf<Mood?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val levelNames = listOf("Stufe 1 · beste", "Stufe 2", "Stufe 3 · mittlere", "Stufe 4", "Stufe 5 · schlechteste")
    SubScreen("Stimmungen", onBack) {
        Text("Jede Stufe kann mehrere Stimmungen haben, zum Beispiel „Gut“ und „Zufrieden“ auf Stufe 2.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (level in 1..5) {
            val list = d.moods.filter { it.level == level && (showArchived || it.archivedAt == null) }
            Panel(title = levelNames[level - 1], padding = 12.dp, action = {
                TextButton({ editing = Mood(level = level, name = "", icon = "a", sortOrder = (list.maxOfOrNull { it.sortOrder } ?: -1) + 1) }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Neu") }
            }) {
                for (m in list) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editing = m }.heightIn(min = 52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MoodFace(m.level, m.icon, 36.dp, muted = m.archivedAt != null)
                    Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (m.archivedAt != null) Text("archiviert", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (d.moods.any { it.archivedAt != null }) TextButton({ showArchived = !showArchived }) { Text(if (showArchived) "Archivierte ausblenden" else "Archivierte anzeigen") }
    }
    editing?.let { m -> MoodDialog(m, isNew = d.moods.none { it.id == m.id }) { editing = null } }
}

@Composable
private fun MoodDialog(mood: Mood, isNew: Boolean, onClose: () -> Unit) {
    val env = LocalEnv.current
    val d = env.diary
    var name by remember { mutableStateOf(mood.name) }
    var icon by remember { mutableStateOf(mood.icon) }
    var confirmDelete by remember { mutableStateOf(false) }
    val others = d.moods.filter { it.level == mood.level && it.id != mood.id && it.archivedAt == null }
    val affected = d.entries.count { it.moodId == mood.id }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neue Stimmung" else "Stimmung bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in MoodVariants) Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (v == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(role = Role.RadioButton) { icon = v }.semantics { contentDescription = "Symbol ${MoodVariants.indexOf(v) + 1}"; selected = v == icon },
                        contentAlignment = Alignment.Center,
                    ) { MoodFace(mood.level, v, 40.dp) }
                }
                if (!isNew) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mood.archivedAt == null) OutlinedButton({ env.act { save(mood.copy(archivedAt = System.currentTimeMillis())) }; onClose() }, enabled = others.isNotEmpty()) { Text("Archivieren") }
                    else OutlinedButton({ env.act { save(mood.copy(archivedAt = null)) }; onClose() }) { Text("Wiederherstellen") }
                    OutlinedButton({ confirmDelete = true }, enabled = others.isNotEmpty(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") }
                }
                if (!isNew && others.isEmpty()) Text("Jede Stufe braucht mindestens eine aktive Stimmung. Lege erst eine zweite an.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ env.act { save(mood.copy(name = name.trim(), icon = icon)) }; onClose() }, enabled = name.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClose) { Text("Abbrechen") } },
    )
    if (confirmDelete) ConfirmDialog(
        "„${mood.name}“ löschen?",
        if (affected == 0) "Diese Stimmung wird in keinem Eintrag verwendet."
        else "${plural(affected, "Eintrag verwendet", "Einträge verwenden")} diese Stimmung. Sie wechseln zu „${others.first().name}“ auf derselben Stufe. Mit Archivieren bleibt alles, wie es ist.",
        "Löschen", destructive = true,
        onConfirm = { env.act { deleteMood(mood, others.first()) }; confirmDelete = false; onClose() }, onDismiss = { confirmDelete = false },
        alternative = "Archivieren", onAlternative = { env.act { save(mood.copy(archivedAt = System.currentTimeMillis())) }; confirmDelete = false; onClose() },
    )
}

// ---- Gruppen und Skalen in gemeinsamer Reihenfolge -----------------------------------------

@Composable
private fun BlocksPage(onBack: () -> Unit, open: (String) -> Unit) {
    val env = LocalEnv.current
    val d = env.diary
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val source = (d.groups + d.scales).filter { showArchived || archivedAt(it) == null }.sortedBy { sortOrder(it) }
    var list by remember(source) { mutableStateOf<List<SyncRow>>(source) }
    val lazy = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazy) { from, to -> list = list.toMutableList().apply { add(to.index, removeAt(from.index)) } }
    fun persist(rows: List<SyncRow>) = env.act { save(rows.mapIndexed { i, r -> if (r is TagGroup) r.copy(sortOrder = i) else (r as Scale).copy(sortOrder = i) }) }
    fun move(i: Int, by: Int) { val j = i + by; if (j in list.indices) { list = list.toMutableList().apply { add(j, removeAt(i)) }; persist(list) } }
    var newGroup by remember { mutableStateOf(false) }

    SubScreen("Aktivitäten und Skalen", onBack, scroll = false) {
        Text("Gruppen und Skalen erscheinen beim Eintragen in dieser Reihenfolge. Zum Sortieren am Griff ziehen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ newGroup = true }, Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Gruppe") }
            OutlinedButton({ open("scale:new") }, Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Skala") }
        }
        LazyColumn(Modifier.weight(1f), state = lazy, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.id }) { row ->
                ReorderableItem(reorder, key = row.id) { _ ->
                    val i = list.indexOf(row)
                    val archived = archivedAt(row) != null
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().clickable { open(if (row is TagGroup) "group:${row.id}" else "scale:${row.id}") }.heightIn(min = 60.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton({}, Modifier.size(48.dp).draggableHandle(onDragStopped = { persist(list) })) { Icon(Icons.Rounded.DragIndicator, "Zum Sortieren ziehen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (row is TagGroup) {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(groupColor(row.colorIndex)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Gruppe · " + plural(d.tags.count { it.groupId == row.id && it.archivedAt == null }, "Aktivität", "Aktivitäten") + if (archived) " · archiviert" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else if (row is Scale) {
                                Icon(iconFor(row.icon), null, tint = groupColor(row.colorIndex), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                                    Text("Skala · " + scaleTypeLabel(row.type) + if (archived) " · archiviert" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton({ move(i, -1) }, Modifier.size(40.dp), enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Nach oben") }
                            IconButton({ move(i, 1) }, Modifier.size(40.dp), enabled = i < list.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Nach unten") }
                        }
                    }
                }
            }
            val loose = d.tags.filter { it.groupId == null && (showArchived || it.archivedAt == null) }
            if (loose.isNotEmpty()) item("loose") { RowGroup { SettingRow("Ohne Gruppe", plural(loose.size, "Aktivität", "Aktivitäten"), Icons.Rounded.Inbox, onClick = { open("group:none") }) } }
            if ((d.groups + d.scales + d.tags).any { archivedAt(it) != null }) item("arch") { TextButton({ showArchived = !showArchived }) { Text(if (showArchived) "Archivierte ausblenden" else "Archivierte anzeigen") } }
        }
    }
    if (newGroup) TextPromptDialog("Neue Gruppe", "Name (Emoji erlaubt)", confirm = "Anlegen", onDismiss = { newGroup = false }, onConfirm = { name ->
        val g = TagGroup(name = name, colorIndex = d.groups.size % 12, sortOrder = (list.size))
        env.act { save(g) }; newGroup = false; open("group:${g.id}")
    })
}

private fun archivedAt(r: SyncRow): Long? = when (r) { is TagGroup -> r.archivedAt; is Scale -> r.archivedAt; is Tag -> r.archivedAt; else -> null }
private fun sortOrder(r: SyncRow): Int = when (r) { is TagGroup -> r.sortOrder; is Scale -> r.sortOrder; else -> 0 }
fun scaleTypeLabel(t: ScaleType) = when (t) { ScaleType.TEXT -> "Textskala"; ScaleType.NUMBER -> "Zahlenskala"; ScaleType.DURATION -> "Zeitdauer" }

// ---- Eine Gruppe mit ihren Aktivitaeten ---------------------------------------------------

@Composable
private fun GroupPage(id: String, onBack: () -> Unit) {
    val env = LocalEnv.current
    val d = env.diary
    val group = d.groups.firstOrNull { it.id == id }
    if (group == null && id != "none") { LaunchedEffect(Unit) { onBack() }; return }
    val gid = group?.id
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val source = d.tags.filter { it.groupId == gid && (showArchived || it.archivedAt == null) }
    var list by remember(source) { mutableStateOf(source) }
    val lazy = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazy) { from, to -> list = list.toMutableList().apply { add(to.index, removeAt(from.index)) } }
    fun persist(rows: List<Tag>) = env.act { save(rows.mapIndexed { i, t -> t.copy(sortOrder = i) }) }
    var editing by remember { mutableStateOf<Tag?>(null) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val color = groupColor(group?.colorIndex)

    SubScreen(group?.name ?: "Ohne Gruppe", onBack, scroll = false, actions = {
        if (group != null) {
            IconButton({ rename = true }) { Icon(Icons.Rounded.Edit, "Gruppe umbenennen") }
            IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Gruppe löschen") }
        }
    }) {
        if (group != null) Panel(padding = 12.dp) {
            Text("Farbe", style = MaterialTheme.typography.labelLarge)
            ColorPicker(group.colorIndex, { env.act { save(group.copy(colorIndex = it)) } })
            SwitchRowCompact("Beim Eintragen eingeklappt", group.collapsed) { env.act { save(group.copy(collapsed = it)) } }
            if (group.archivedAt == null) TextButton({ env.act { save(group.copy(archivedAt = System.currentTimeMillis())) }; onBack() }) { Text("Gruppe archivieren") }
            else TextButton({ env.act { save(group.copy(archivedAt = null)) } }) { Text("Gruppe wiederherstellen") }
        }
        Button({ editing = Tag(groupId = gid, name = "", sortOrder = list.size) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aktivität hinzufügen") }
        LazyColumn(Modifier.weight(1f), state = lazy, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.id }) { t ->
                ReorderableItem(reorder, key = t.id) { _ ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().clickable { editing = t }.heightIn(min = 60.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton({}, Modifier.size(48.dp).draggableHandle(onDragStopped = { persist(list) })) { Icon(Icons.Rounded.DragIndicator, "Zum Sortieren ziehen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconBubble(t.icon, t.colorIndex?.let { groupColor(it) } ?: color, selected = false, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.bodyLarge)
                                val n = d.entries.count { e -> t.id in e.tagIds }
                                Text(plural(n, "Eintrag", "Einträge") + if (t.archivedAt != null) " · archiviert" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState("Noch keine Aktivitäten", "Füge die erste Aktivität dieser Gruppe hinzu.") }
            if (d.tags.any { it.groupId == gid && it.archivedAt != null }) item("arch") { TextButton({ showArchived = !showArchived }) { Text(if (showArchived) "Archivierte ausblenden" else "Archivierte anzeigen") } }
        }
    }
    editing?.let { t -> TagDialog(t, isNew = d.tags.none { it.id == t.id }, groupColor = color) { editing = null } }
    if (rename && group != null) TextPromptDialog("Gruppe umbenennen", "Name (Emoji erlaubt)", group.name, onDismiss = { rename = false }, onConfirm = { env.act { save(group.copy(name = it)) }; rename = false })
    if (confirmDelete && group != null) {
        val n = d.tags.count { it.groupId == group.id }
        ConfirmDialog("Gruppe „${group.name}“ löschen?", "${plural(n, "Aktivität bleibt", "Aktivitäten bleiben")} erhalten und ${if (n == 1) "steht" else "stehen"} danach ohne Gruppe da. Einträge ändern sich nicht.", "Löschen", destructive = true,
            onConfirm = { env.act { deleteGroup(group) }; confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false },
            alternative = "Archivieren", onAlternative = { env.act { save(group.copy(archivedAt = System.currentTimeMillis())) }; confirmDelete = false; onBack() })
    }
}

@Composable
private fun SwitchRowCompact(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Switch) { onChange(!checked) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagDialog(tag: Tag, isNew: Boolean, groupColor: Color, onClose: () -> Unit) {
    val env = LocalEnv.current
    val d = env.diary
    var name by remember { mutableStateOf(tag.name) }
    var icon by remember { mutableStateOf(tag.icon) }
    var colorIndex by remember { mutableStateOf(tag.colorIndex) }
    var groupId by remember { mutableStateOf(tag.groupId) }
    var pickIcon by remember { mutableStateOf(false) }
    var ownColor by remember { mutableStateOf(tag.colorIndex != null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    val color = colorIndex?.let { groupColor(it) } ?: groupColor
    val affected = d.entries.count { tag.id in it.tagIds }
    fun current() = tag.copy(name = name.trim(), icon = icon, colorIndex = if (ownColor) colorIndex ?: 0 else null, groupId = groupId,
        sortOrder = if (groupId != tag.groupId) (d.tags.filter { it.groupId == groupId }.maxOfOrNull { it.sortOrder } ?: -1) + 1 else tag.sortOrder)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neue Aktivität" else "Aktivität bearbeiten") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Symbol wählen") { pickIcon = true }) { IconBubble(icon, color, true, 52.dp) }
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton({ pickIcon = true }) { Text("Symbol wählen") }
                Box {
                    OutlinedButton({ groupMenu = true }, Modifier.fillMaxWidth()) { Text("Gruppe: " + (d.groupById[groupId]?.name ?: "Ohne Gruppe")) }
                    DropdownMenu(groupMenu, { groupMenu = false }) {
                        DropdownMenuItem({ Text("Ohne Gruppe") }, { groupId = null; groupMenu = false })
                        for (g in d.groups.filter { it.archivedAt == null }) DropdownMenuItem({ Text(g.name) }, { groupId = g.id; groupMenu = false })
                    }
                }
                SwitchRowCompact("Eigene Farbe statt Gruppenfarbe", ownColor) { ownColor = it; if (it && colorIndex == null) colorIndex = 0 }
                if (ownColor) ColorPicker(colorIndex, { colorIndex = it })
                if (!isNew) {
                    HorizontalDivider()
                    if (tag.archivedAt == null) TextButton({ env.act { save(current().copy(archivedAt = System.currentTimeMillis())) }; onClose() }) { Text("Archivieren") }
                    else TextButton({ env.act { save(current().copy(archivedAt = null)) }; onClose() }) { Text("Wiederherstellen") }
                    TextButton({ replace = true }) { Text("Durch andere Aktivität ersetzen") }
                    TextButton({ confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") }
                }
            }
        },
        confirmButton = { TextButton({ env.act { save(current()) }; onClose() }, enabled = name.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClose) { Text("Abbrechen") } },
    )
    if (pickIcon) IconPickerDialog(icon, color, { icon = it; pickIcon = false }, { pickIcon = false })
    if (confirmDelete) ConfirmDialog("„${tag.name}“ löschen?",
        if (affected == 0) "Diese Aktivität steht in keinem Eintrag." else "Die Aktivität wird aus ${plural(affected, "Eintrag", "Einträgen")} entfernt. Mit Archivieren bleibt sie in alten Einträgen sichtbar und fehlt nur beim Eintragen.",
        "Löschen", destructive = true, onConfirm = { env.act { deleteTag(tag) }; confirmDelete = false; onClose() }, onDismiss = { confirmDelete = false },
        alternative = "Archivieren", onAlternative = { env.act { save(tag.copy(archivedAt = System.currentTimeMillis())) }; confirmDelete = false; onClose() })
    if (replace) AlertDialog(
        onDismissRequest = { replace = false },
        title = { Text("„${tag.name}“ ersetzen durch") },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                Text("Alle ${plural(affected, "Eintrag", "Einträge")} bekommen stattdessen die gewählte Aktivität. „${tag.name}“ wird danach gelöscht.", style = MaterialTheme.typography.bodyMedium)
                for (other in d.tags.filter { it.id != tag.id && it.archivedAt == null }) SettingRow(other.name, d.groupById[other.groupId]?.name, onClick = { env.act { replaceTag(tag, other) }; replace = false; onClose() })
            }
        },
        confirmButton = {}, dismissButton = { TextButton({ replace = false }) { Text("Abbrechen") } },
    )
}

/** Begrenzte Hoehe mit eigenem Scrollen, fuer lange Dialoginhalte. */
@Composable
fun Modifier.verticalScrollCompat(): Modifier =
    this.heightIn(max = 460.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())

// ---- Skala --------------------------------------------------------------------------------

@Composable
private fun ScalePage(id: String, onBack: () -> Unit) {
    val env = LocalEnv.current
    val d = env.diary
    val existing = d.scales.firstOrNull { it.id == id }
    val isNew = existing == null
    var s by remember(id) {
        mutableStateOf(existing ?: Scale(type = ScaleType.TEXT, name = "", colorIndex = d.scales.size % 12, sortOrder = d.groups.size + d.scales.size,
            points = listOf("niedrig", "mittel", "hoch").mapIndexed { i, l -> ScalePoint(position = i + 1, label = l) }, max = 3))
    }
    var pickIcon by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val color = groupColor(s.colorIndex)
    val affected = d.entries.count { e -> e.scaleValues.any { it.scaleId == s.id } }
    val valid = s.name.isNotBlank() && when (s.type) {
        ScaleType.TEXT -> s.points.size >= 2 && s.points.all { it.label.isNotBlank() }
        ScaleType.NUMBER -> s.max > s.min
        ScaleType.DURATION -> s.max > s.min && s.step > 0
    }
    fun normalized(): Scale = when (s.type) {
        ScaleType.TEXT -> s.copy(name = s.name.trim(), min = 1, max = s.points.size, points = s.points.mapIndexed { i, p -> p.copy(position = i + 1, label = p.label.trim()) })
        else -> s.copy(name = s.name.trim(), points = emptyList())
    }

    SubScreen(if (isNew) "Neue Skala" else "Skala bearbeiten", onBack, actions = {
        TextButton({ env.act { save(normalized()) }; onBack() }, enabled = valid) { Text("Speichern") }
    }) {
        Panel(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Symbol wählen") { pickIcon = true }) { IconBubble(s.icon, color, true, 52.dp) }
                OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            ColorPicker(s.colorIndex, { s = s.copy(colorIndex = it) })
        }
        Panel(title = "Art der Skala", padding = 12.dp) {
            if (!isNew) Text(scaleTypeLabel(s.type) + ". Die Art lässt sich nachträglich nicht ändern, weil vorhandene Werte sonst nicht mehr passen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Choice(listOf(ScaleType.TEXT to "Textskala", ScaleType.NUMBER to "Zahlenskala", ScaleType.DURATION to "Zeitdauer"), s.type) { t ->
                s = when (t) {
                    ScaleType.TEXT -> s.copy(type = t, chartMode = ChartMode.DAILY_AVERAGE)
                    ScaleType.NUMBER -> s.copy(type = t, min = 1, max = 10, step = 1, chartMode = ChartMode.DAILY_AVERAGE)
                    ScaleType.DURATION -> s.copy(type = t, min = 0, max = 12 * 60, step = 15, chartMode = ChartMode.DAILY_SUM)
                }
            }
            when (s.type) {
                ScaleType.TEXT -> {
                    Text("Punkte von niedrig nach hoch", style = MaterialTheme.typography.labelLarge)
                    s.points.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(p.label, { v -> s = s.copy(points = s.points.map { if (it.id == p.id) it.copy(label = v) else it }) }, label = { Text("Punkt ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f))
                            IconButton({ s = s.copy(points = s.points.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Punkt nach oben") }
                            IconButton({ s = s.copy(points = s.points.filter { it.id != p.id }) }, enabled = s.points.size > 2) { Icon(Icons.Rounded.Close, "Punkt entfernen") }
                        }
                    }
                    if (s.points.size < 7) TextButton({ s = s.copy(points = s.points + ScalePoint(position = s.points.size + 1, label = "")) }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Punkt hinzufügen") }
                }
                ScaleType.NUMBER -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Kleinster Wert", s.min, Modifier.weight(1f)) { s = s.copy(min = it) }
                    NumberField("Größter Wert", s.max, Modifier.weight(1f)) { s = s.copy(max = it) }
                }
                ScaleType.DURATION -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Höchstens Stunden", s.max / 60, Modifier.weight(1f)) { s = s.copy(max = it.coerceIn(1, 48) * 60) }
                    NumberField("Schritt in Minuten", s.step, Modifier.weight(1f)) { s = s.copy(step = it.coerceIn(1, 120)) }
                }
            }
        }
        Panel(title = "Auswertung", padding = 12.dp) {
            Text("Mehrere Werte an einem Tag", style = MaterialTheme.typography.labelLarge)
            Choice(listOf(ChartMode.DAILY_AVERAGE to "Tagesdurchschnitt", ChartMode.DAILY_SUM to "Tagessumme"), s.chartMode) { s = s.copy(chartMode = it) }
            Text("Was ist gut?", style = MaterialTheme.typography.labelLarge)
            Choice(listOf(Trend.HIGHER_IS_BETTER to "Höher ist gut", Trend.LOWER_IS_BETTER to "Niedriger ist gut", Trend.NEUTRAL to "Neutral"), s.trend) { s = s.copy(trend = it) }
        }
        if (!isNew) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.archivedAt == null) OutlinedButton({ env.act { save(normalized().copy(archivedAt = System.currentTimeMillis())) }; onBack() }) { Text("Archivieren") }
            else OutlinedButton({ env.act { save(normalized().copy(archivedAt = null)) }; onBack() }) { Text("Wiederherstellen") }
            OutlinedButton({ confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") }
        }
    }
    if (pickIcon) IconPickerDialog(s.icon, color, { s = s.copy(icon = it); pickIcon = false }, { pickIcon = false })
    if (confirmDelete) ConfirmDialog("Skala „${s.name}“ löschen?",
        if (affected == 0) "Zu dieser Skala gibt es keine Werte." else "Die Werte dieser Skala werden aus ${plural(affected, "Eintrag", "Einträgen")} entfernt. Mit Archivieren bleiben sie erhalten und die Skala fehlt nur beim Eintragen.",
        "Löschen", destructive = true, onConfirm = { env.act { deleteScale(s) }; confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false },
        alternative = "Archivieren", onAlternative = { env.act { save(s.copy(archivedAt = System.currentTimeMillis())) }; confirmDelete = false; onBack() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { t -> text = t.filter { it.isDigit() || it == '-' }.take(5); text.toIntOrNull()?.let(onChange) }, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}

// ---- Schreibvorlagen ----------------------------------------------------------------------

@Composable
private fun TemplatesPage(onBack: () -> Unit, open: (String) -> Unit) {
    val env = LocalEnv.current
    val source = env.diary.templates
    var list by remember(source) { mutableStateOf(source) }
    val lazy = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazy) { from, to -> list = list.toMutableList().apply { add(to.index, removeAt(from.index)) } }
    var restore by remember { mutableStateOf(false) }
    SubScreen("Schreibvorlagen", onBack, scroll = false, actions = { TextButton({ restore = true }) { Text("Standard") } }) {
        Text("Vorlagen geben einer Notiz ein Gerüst. Du fügst sie in der ganzen Notiz über „Vorlagen“ ein.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({ open("template:new") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Vorlage hinzufügen") }
        LazyColumn(Modifier.weight(1f), state = lazy, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.id }) { t ->
                ReorderableItem(reorder, key = t.id) { _ ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().clickable { open("template:${t.id}") }.heightIn(min = 60.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton({}, Modifier.size(48.dp).draggableHandle(onDragStopped = { env.act { save(list.mapIndexed { i, x -> x.copy(sortOrder = i) }) } })) { Icon(Icons.Rounded.DragIndicator, "Zum Sortieren ziehen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                NoteText(t.body, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState("Keine Vorlagen", "Lege eine eigene an oder stelle die Standardvorlagen wieder her.") }
        }
    }
    if (restore) ConfirmDialog("Standardvorlagen wiederherstellen?", "Die mitgelieferten Vorlagen werden wieder hinzugefügt. Deine eigenen bleiben erhalten.", "Wiederherstellen",
        onConfirm = {
            val have = source.map { it.title }.toSet()
            env.act { save(StarterPacks.defaultTemplates().filter { it.title !in have }.mapIndexed { i, t -> t.copy(sortOrder = source.size + i) }) }
            restore = false
        }, onDismiss = { restore = false })
}

@Composable
private fun TemplatePage(id: String, onBack: () -> Unit) {
    val env = LocalEnv.current
    val existing = env.diary.templates.firstOrNull { it.id == id }
    var title by rememberSaveable(id) { mutableStateOf(existing?.title ?: "") }
    var body by rememberSaveable(id) { mutableStateOf(de.mediclever.tagebuch.core.markup.NoteMarkup.fromHtml(existing?.body ?: "")) }
    var confirmDelete by remember { mutableStateOf(false) }
    SubScreen(if (existing == null) "Neue Vorlage" else "Vorlage bearbeiten", onBack, actions = {
        if (existing != null) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Vorlage löschen") }
        TextButton({
            val t = (existing ?: Template(title = "", sortOrder = env.diary.templates.size)).copy(title = title.trim(), body = de.mediclever.tagebuch.core.markup.NoteMarkup.toHtml(body))
            env.act { save(t) }; onBack()
        }, enabled = title.isNotBlank()) { Text("Speichern") }
    }) {
        OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
            visualTransformation = MarkupTransformation(MaterialTheme.colorScheme.outline),
            supportingText = { Text("**fett**, _kursiv_, Zeilen mit „- “ werden zur Liste, mit „1. “ zur nummerierten Liste.") })
    }
    if (confirmDelete && existing != null) ConfirmDialog("Vorlage „${existing.title}“ löschen?", "Bereits geschriebene Notizen bleiben unverändert.", "Löschen", destructive = true,
        onConfirm = { env.act { deleteSimple(existing) }; confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false })
}
