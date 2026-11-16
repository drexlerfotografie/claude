package de.mediclever.tagebuch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.Mood
import de.mediclever.tagebuch.ui.icons.ActivityIcons
import de.mediclever.tagebuch.ui.icons.IconCategories
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.icons.iconFor
import de.mediclever.tagebuch.ui.theme.GroupPalette
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.onColor

/** Unterseite mit Titelzeile und Zurueck-Pfeil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = floating,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                content()
                if (scroll) Spacer(Modifier.height(24.dp))
            },
        )
    }
}

/** Weisse Karte mit optionaler Ueberschrift. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() },
    )
}

/** Zeile in Menues und Einstellungen. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
        else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, icon: ImageVector? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, icon, onClick = { onChange(!checked) }) { Switch(checked, onChange) }
}

/** Gruppe von Zeilen in einer Karte, mit Trennlinien. */
@Composable
fun RowGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(content = content) }
}

@Composable
fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 16.dp))

/** Leerzustand mit kurzer Erklaerung und einer naechsten Aktion. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) Button(onAction, Modifier.padding(top = 8.dp)) { Text(actionLabel) }
    }
}

/** Rundes Symbol einer Aktivitaet, ausgewaehlt gefuellt, sonst als Umriss. */
@Composable
fun IconBubble(icon: String, color: Color, selected: Boolean, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (selected) color else Color.Transparent)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(iconFor(icon), null, tint = if (selected) onColor(color) else color, modifier = Modifier.size(size * 0.5f)) }
}

/** Antippbare Aktivitaet im Raster: Symbol mit Beschriftung darunter. */
@Composable
fun ActivityTile(name: String, icon: String, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBubble(icon, color, selected)
        Text(
            name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kleines Etikett mit Symbol und Name, z. B. auf Eintragskarten. */
@Composable
fun MiniChip(name: String, icon: String?, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = if (LocalDiaryColors.current.dark) 0.28f else 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(iconFor(icon), null, tint = if (LocalDiaryColors.current.dark) Color.White else color, modifier = Modifier.size(14.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Fuenf Stufen nebeneinander; hat eine Stufe mehrere Stimmungen, erscheinen sie nach Antippen darunter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodPicker(moods: List<Mood>, selectedId: String?, onSelect: (Mood) -> Unit, modifier: Modifier = Modifier) {
    val active = moods.filter { it.archivedAt == null || it.id == selectedId }
    val byLevel = (1..5).associateWith { l -> active.filter { it.level == l } }
    val selected = active.firstOrNull { it.id == selectedId }
    var openLevel by remember(selectedId) { mutableStateOf(selected?.level) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (level in 1..5) {
                val list = byLevel.getValue(level)
                val first = list.firstOrNull()
                val isSel = selected?.level == level
                val shown = if (isSel) selected!! else first
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = first != null, role = Role.RadioButton) {
                            if (list.size == 1) onSelect(first!!) else openLevel = level
                        }
                        .semantics(mergeDescendants = true) { this.selected = isSel }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MoodFace(level, shown?.icon ?: "a", size = 52.dp, muted = !isSel && selected != null)
                    Text(
                        shown?.name ?: "–", style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val open = openLevel?.let { byLevel[it] }.orEmpty()
        if (open.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in open) FilterChip(
                selected = m.id == selectedId, onClick = { onSelect(m) }, label = { Text(m.name) },
                leadingIcon = { MoodFace(m.level, m.icon, size = 22.dp) },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    destructive: Boolean = false, alternative: String? = null, onAlternative: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onConfirm, colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) { Text(confirm) }
        },
        dismissButton = {
            Row {
                if (alternative != null && onAlternative != null) TextButton(onAlternative) { Text(alternative) }
                TextButton(onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
fun TextPromptDialog(title: String, label: String, initial: String = "", confirm: String = "Speichern", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}

/** Auswahl aus den zwoelf Palettenfarben. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupPalette.indices.forEach { i ->
            val c = de.mediclever.tagebuch.ui.theme.groupColor(i)
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = "Farbe ${i + 1}"; this.selected = selected == i },
                contentAlignment = Alignment.Center,
            ) { if (selected == i) Icon(Icons.Rounded.Check, null, tint = onColor(c)) }
        }
    }
}

/** Durchsuchbare Symbolauswahl mit Kategorien. */
@Composable
fun IconPickerDialog(selected: String, color: Color, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val hits = remember(query) {
        if (query.isBlank()) ActivityIcons
        else ActivityIcons.filter { it.label.contains(query.trim(), true) || it.category.contains(query.trim(), true) || it.key.contains(query.trim(), true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Symbol wählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Suchen, z. B. Schlaf") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (cat in IconCategories) {
                        val inCat = hits.filter { it.category == cat }
                        if (inCat.isEmpty()) continue
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(cat, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(inCat, key = { it.key }) { info ->
                            Box(
                                Modifier.size(52.dp).clip(CircleShape).clickable(role = Role.RadioButton) { onSelect(info.key) }
                                    .semantics { contentDescription = info.label; this.selected = info.key == selected },
                                contentAlignment = Alignment.Center,
                            ) { IconBubble(info.key, color, info.key == selected, size = 44.dp) }
                        }
                    }
                    if (hits.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Kein Symbol gefunden.", Modifier.padding(16.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Schließen") } },
    )
}
