package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.entry.AudioRow
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.icons.iconFor
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.groupColor
import java.time.LocalDate

/** Anzeigetext eines Skalenwerts, z. B. "gut" oder "7:30 Std.". */
fun scaleValueText(scale: Scale, v: ScaleValue): String = when (scale.type) {
    ScaleType.TEXT -> scale.points.firstOrNull { it.id == v.pointId }?.label ?: "–"
    ScaleType.NUMBER -> v.number?.toString() ?: "–"
    ScaleType.DURATION -> v.minutes?.let { fmtDuration(it) } ?: "–"
}

/** Karte eines Eintrags mit allem, was dazu erfasst wurde. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(
    entry: Entry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    onDelete: (() -> Unit)? = null,
    onPhoto: ((Asset) -> Unit)? = null,
) {
    val env = LocalEnv.current
    val diary = env.diary
    val mood = diary.moodById[entry.moodId]
    val level = mood?.level ?: 3
    var menu by remember { mutableStateOf(false) }
    val date = LocalDate.parse(entry.localDate)

    Panel(modifier, onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MoodFace(level, mood?.icon ?: "a", size = 46.dp)
            Column(Modifier.weight(1f)) {
                if (showDate) Text(fmtDay(date, env.today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(mood?.name ?: "Unbekannt", style = MaterialTheme.typography.titleLarge, color = if (LocalDiaryColors.current.dark) MaterialTheme.colorScheme.onSurface else LocalDiaryColors.current.level(level).let { c -> androidx.compose.ui.graphics.lerp(c, MaterialTheme.colorScheme.onSurface, 0.35f) })
                    Text(fmtTime(entry.minuteOfDay), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            if (entry.favorite) Icon(Icons.Rounded.Star, "Favorit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Box {
                IconButton({ menu = true }, Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, "Aktionen zum Eintrag") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Bearbeiten") }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem({ Text(if (entry.favorite) "Favorit entfernen" else "Als Favorit markieren") }, { menu = false; env.act { save(entry.copy(favorite = !entry.favorite)) } },
                        leadingIcon = { Icon(if (entry.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star, null) })
                    if (onDelete != null) DropdownMenuItem({ Text("Löschen") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                }
            }
        }

        val tags = entry.tagIds.mapNotNull { diary.tagById[it] }
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in tags) MiniChip(t.name, t.icon, groupColor(t.colorIndex ?: diary.groupById[t.groupId]?.colorIndex))
        }

        val values = entry.scaleValues.mapNotNull { v -> diary.scaleById[v.scaleId]?.let { it to v } }
        if (values.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((s, v) in values) Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(iconFor(s.icon), null, tint = groupColor(s.colorIndex), modifier = Modifier.size(16.dp))
                Text(s.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(scaleValueText(s, v), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        if (entry.title.isNotBlank()) Text(entry.title, style = MaterialTheme.typography.titleMedium)
        if (entry.note.isNotBlank()) NoteText(entry.note, maxLines = 8)

        val assets = entry.assetIds.mapNotNull { env.asset(it) }
        val photos = assets.filter { it.type == AssetType.PHOTO }
        if (photos.isNotEmpty()) {
            val shown = photos.take(6)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in shown.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in row) Box(Modifier.weight(1f).aspectRatio(1f)) {
                        AsyncImage(env.assetFile(a), "Foto zum Eintrag", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (onPhoto != null) Modifier.clickable { onPhoto(a) } else Modifier), contentScale = ContentScale.Crop)
                        if (a == shown.last() && photos.size > shown.size) Text("+${photos.size - shown.size}", Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        for (a in assets.filter { it.type == AssetType.AUDIO }) AudioRow(a, env.assetFile(a), null)

        val done = diary.goalChecks.filter { it.date == entry.localDate }.mapNotNull { c -> diary.goals.firstOrNull { it.id == c.goalId } }
        // Erreichte Ziele nur am letzten Eintrag des Tages zeigen, sonst wiederholen sie sich
        val isLastOfDay = diary.entries.firstOrNull { it.localDate == entry.localDate }?.id == entry.id
        if (done.isNotEmpty() && isLastOfDay) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (g in done) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Text(g.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Frage vor dem Loeschen eines Eintrags. */
@Composable
fun DeleteEntryDialog(entry: Entry, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val n = entry.assetIds.size
    ConfirmDialog(
        "Eintrag löschen?",
        "Der Eintrag vom ${fmtDayShort(LocalDate.parse(entry.localDate))} wird endgültig gelöscht" + (if (n > 0) ", samt ${plural(n, "Anhang", "Anhängen")}." else "."),
        "Löschen", onConfirm = { env.act { deleteEntry(entry) }; onDismiss() }, onDismiss = onDismiss, destructive = true,
    )
}
