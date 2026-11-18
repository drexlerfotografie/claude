package de.mediclever.tagebuch.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.search.SearchQuery
import de.mediclever.tagebuch.core.search.searchEntries
import de.mediclever.tagebuch.core.stats.*
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.home.DeleteEntryDialog
import de.mediclever.tagebuch.ui.home.EntryCard
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.setup.Choice
import de.mediclever.tagebuch.ui.setup.verticalScrollCompat
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable
fun FavoritesScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val list = env.diary.entries.filter { it.favorite }
    SubScreen("Favoriten", nav.back, scroll = false) {
        if (list.isEmpty()) EmptyState("Noch keine Favoriten", "Markiere besondere Einträge mit dem Stern, dann findest du sie hier wieder.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.id }) { e -> EntryCard(e, onOpen = { nav.go(Routes.editEntry(e.id)) }, onPhoto = { nav.go(Routes.photo(it.id)) }) }
        }
    }
}

/** Suche nach Text, Stimmung und Aktivitaet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val d = env.diary
    var text by rememberSaveable { mutableStateOf("") }
    var levels by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var tagIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pickTag by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val active = text.isNotBlank() || levels.isNotEmpty() || tagIds.isNotEmpty()
    val hits = remember(d, text, levels, tagIds) { if (active) searchEntries(d, SearchQuery(text = text.trim(), levels = levels.toSet(), tagIds = tagIds.toSet())) else emptyList() }
    SubScreen("Suchen", nav.back, scroll = false) {
        OutlinedTextField(text, { text = it }, placeholder = { Text("Text in Titel oder Notiz") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (text.isNotEmpty()) IconButton({ text = "" }) { Icon(Icons.Rounded.Close, "Suchtext löschen") } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (l in 1..5) FilterChip(l in levels, { levels = if (l in levels) levels - l else levels + l }, leadingIcon = { MoodFace(l, "a", 24.dp) },
                label = { Text(d.moods.firstOrNull { it.level == l }?.name ?: "") }, modifier = Modifier.height(48.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in tagIds) d.tagById[id]?.let { t -> InputChip(true, { tagIds = tagIds - id }, { Text(t.name) }, trailingIcon = { Icon(Icons.Rounded.Close, "Entfernen", Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp)) }
            AssistChip({ pickTag = true }, { Text("Aktivität") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        if (!active) EmptyState("Wonach suchst du?", "Gib einen Text ein oder wähle eine Stimmung oder Aktivität.")
        else {
            Text(if (hits.isEmpty()) "Keine Treffer" else plural(hits.size, "Treffer", "Treffer"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(hits, key = { it.id }) { e -> EntryCard(e, onOpen = { nav.go(Routes.editEntry(e.id)) }, onDelete = { deleting = e }) }
            }
        }
    }
    if (pickTag) AlertDialog(onDismissRequest = { pickTag = false }, title = { Text("Aktivität wählen") }, confirmButton = { TextButton({ pickTag = false }) { Text("Schließen") } },
        text = { Column(Modifier.verticalScrollCompat()) { for (t in d.tags.filter { it.id !in tagIds }) SettingRow(t.name, d.groupById[t.groupId]?.name, onClick = { tagIds = tagIds + t.id; pickTag = false }) } })
    deleting?.let { DeleteEntryDialog(it) { deleting = null } }
}

/** Rueckblick: derselbe Tag in frueheren Jahren. */
@Composable
fun MemoriesScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val list = remember(env.diary, env.today) { memories(env.diary, env.today) }
    SubScreen("Rückblick", nav.back) {
        Text(fmtDay(env.today, env.today) + " in früheren Jahren", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (list.isEmpty()) EmptyState("Noch kein Rückblick", "Hier erscheinen Einträge und Fotos, die du an diesem Tag in früheren Jahren festgehalten hast.")
        for (m in list) {
            SectionLabel(m.label)
            for (e in m.entries) EntryCard(e, onOpen = { nav.go(Routes.editEntry(e.id)) }, onPhoto = { nav.go(Routes.photo(it.id)) })
        }
    }
}

/** Alle Fotos nach Monat. */
@Composable
fun PhotosScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val byMonth = remember(env.diary) {
        env.diary.entries.flatMap { e -> e.assetIds.mapNotNull { env.asset(it) }.filter { it.type == AssetType.PHOTO }.map { YearMonth.from(LocalDate.parse(e.localDate)) to it } }
            .groupBy({ it.first }, { it.second }).toSortedMap(compareByDescending { it })
    }
    SubScreen("Fotos", nav.back, scroll = false) {
        if (byMonth.isEmpty()) EmptyState("Noch keine Fotos", "Fotos, die du an Einträge hängst, sammeln sich hier nach Monaten.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            for ((month, photos) in byMonth) {
                item(key = "h$month") { SectionLabel(fmtMonth(month) + " · " + photos.size) }
                items(photos.distinctBy { it.id }.chunked(3), key = { "r" + it.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (a in row) AsyncImage(env.assetFile(a), "Foto vom ${fmtMonth(month)}", Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { nav.go(Routes.photo(a.id)) }, contentScale = ContentScale.Crop)
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Ein Foto gross, mit Zoomen und Teilen. */
@Composable
fun PhotoViewerScreen(id: String) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val asset = env.asset(id)
    val entry = env.diary.entries.firstOrNull { id in it.assetIds }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (asset != null) AsyncImage(env.assetFile(asset), "Foto", Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan } }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(nav.back) { Icon(Icons.Rounded.Close, "Schließen", tint = Color.White) }
            Text(entry?.let { fmtDayShort(LocalDate.parse(it.localDate)) } ?: "", Modifier.weight(1f), color = Color.White)
            if (entry != null) IconButton({ nav.go(Routes.editEntry(entry.id)) }) { Icon(Icons.Rounded.Article, "Zum Eintrag", tint = Color.White) }
            if (asset != null) IconButton({
                env.assetFile(asset)?.let { src -> val f = java.io.File(shareDir(context), "foto.jpg"); src.copyTo(f, true); shareFile(context, f, "image/jpeg", "Foto teilen") }
            }) { Icon(Icons.Rounded.Share, "Foto teilen", tint = Color.White) }
        }
    }
}

@Composable
fun AchievementsScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val list = remember(env.diary, env.today) { achievements(env.diary, env.today) }
    val titles = mapOf(AchievementCategory.ENTRIES to "Einträge", AchievementCategory.STREAK to "Tage in Folge", AchievementCategory.GOALS to "Ziele", AchievementCategory.YEARS to "Jahre", AchievementCategory.PHOTOS to "Fotos")
    val icons = mapOf(AchievementCategory.ENTRIES to Icons.Rounded.EditNote, AchievementCategory.STREAK to Icons.Rounded.LocalFireDepartment, AchievementCategory.GOALS to Icons.Rounded.Flag, AchievementCategory.YEARS to Icons.Rounded.Cake, AchievementCategory.PHOTOS to Icons.Rounded.PhotoCamera)
    SubScreen("Erfolge", nav.back) {
        Text("${list.count { it.unlocked }} von ${list.size} erreicht", style = MaterialTheme.typography.titleMedium)
        for ((cat, items) in list.groupBy { it.category }) {
            SectionLabel(titles[cat] ?: "")
            RowGroup {
                items.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (a.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(if (a.unlocked) icons.getValue(cat) else Icons.Rounded.Lock, null, tint = if (a.unlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            if (a.unlocked) Text(a.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!a.unlocked) { ShareBar(a.progress.toFloat() / a.target.coerceAtLeast(1), MaterialTheme.colorScheme.primary); Text("Noch nicht erreicht · ${a.progress} von ${a.target}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            else Text("Erreicht", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Wochen- und Monatsberichte. */
@Composable
fun ReportsScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val colors = LocalDiaryColors.current
    var weekly by rememberSaveable { mutableStateOf(false) }
    var back by rememberSaveable { mutableIntStateOf(1) }
    val r = remember(env.diary, env.today, weekly, back) {
        if (weekly) weeklyReport(env.diary, env.today.minusWeeks(back.toLong()), env.today) else monthlyReport(env.diary, YearMonth.from(env.today).minusMonths(back.toLong()), env.today)
    }
    @Composable fun change(c: Int, label: String) = Text(if (c == 0) "unverändert $label" else (if (c > 0) "+$c" else "−${-c}") + " $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val prev = if (weekly) "zur Vorwoche" else "zum Vormonat"
    SubScreen("Berichte", nav.back) {
        Choice(listOf(false to "Monat", true to "Woche"), weekly) { weekly = it; back = 1 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ back++ }) { Icon(Icons.Rounded.ChevronLeft, "Früher") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Text("${fmtDayShort(r.range.start)} bis ${fmtDayShort(r.range.endInclusive)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ back-- }, enabled = back > 0) { Icon(Icons.Rounded.ChevronRight, "Später") }
        }
        if (r.entries.value == 0) EmptyState("Keine Einträge in diesem Zeitraum", "Sobald du Einträge schreibst, entsteht hier dein Bericht.")
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f), padding = 14.dp) { Text("Einträge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${r.entries.value}", style = MaterialTheme.typography.headlineSmall); change(r.entries.change, prev) }
                Panel(Modifier.weight(1f), padding = 14.dp) { Text("Aktivitäten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${r.activities.value}", style = MaterialTheme.typography.headlineSmall); change(r.activities.change, prev) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f), padding = 14.dp) {
                    Text("Stimmung", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.averageScore.value?.let { String.format(Locale.GERMANY, "%.1f von 5", it) } ?: "–", style = MaterialTheme.typography.headlineSmall)
                    val ch = r.averageScore.change
                    Text(ch?.let { String.format(Locale.GERMANY, "%+.1f $prev", it) } ?: "kein Vergleich", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel(Modifier.weight(1f), padding = 14.dp) { Text("Wörter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${r.words.value}", style = MaterialTheme.typography.headlineSmall); change(r.words.change, prev) }
            }
            SharePanel("Verlauf") {
                SeriesChart(r.moodCourse.map { ChartPoint(if (weekly) weekdayShort(it.date.dayOfWeek.value) else it.date.dayOfMonth.toString(), it.averageScore, it.averageScore?.let { v -> colors.level(roundedLevel(v)) }) }, 1.0, 5.0,
                    "Stimmungsverlauf im Bericht", bars = true, labelEvery = if (weekly) 1 else 5, height = 120.dp)
            }
            if (r.goals.isNotEmpty()) Panel(title = "Ziele", padding = 14.dp) {
                Text("${r.goalCompletions.value} Abschlüsse · ${plural(r.goalsAchieved, "Ziel", "Ziele")} vollständig erreicht", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (g in r.goals) Row { Text(g.goal.name, Modifier.weight(1f), maxLines = 1); Text("${g.completions}×" + (g.successRate?.let { " · ${(it * 100).toInt()} %" } ?: ""), style = MaterialTheme.typography.labelLarge) }
            }
            if (r.photoCount > 0) Panel(title = plural(r.photoCount, "Foto", "Fotos"), padding = 14.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in r.photoAssetIds.take(4)) env.asset(id)?.let { a -> AsyncImage(env.assetFile(a), "Foto", Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { nav.go(Routes.photo(id)) }, contentScale = ContentScale.Crop) }
                }
            }
        }
        SwitchRow("Benachrichtigung zu neuen Berichten", "Montags zur Woche, am Monatsersten zum Monat", checked = env.diary.settings.reportNotifications) { on -> env.act { updateSettings { it.copy(reportNotifications = on) } } }
    }
}
