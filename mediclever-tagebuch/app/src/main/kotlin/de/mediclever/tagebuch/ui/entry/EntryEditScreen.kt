package de.mediclever.tagebuch.ui.entry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import de.mediclever.tagebuch.core.markup.NoteMarkup
import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.data.AppJson
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.icons.iconFor
import de.mediclever.tagebuch.core.shift.Shifts
import de.mediclever.tagebuch.ui.home.scaleReading
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.groupColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import de.mediclever.tagebuch.ui.theme.onColor
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val DraftSaver = Saver<MutableState<EntryDraft>, String>(
    save = { AppJson.encodeToString(it.value) },
    restore = { mutableStateOf(AppJson.decodeFromString<EntryDraft>(it)) },
)

/**
 * Eintrag erfassen auf einer Seite: Wert (klein oben rechts, sobald gewaehlt), Dienstart, Skalen,
 * Aktivitaeten und Notiz in der Reihenfolge des Nutzers, danach Ziele, Fotos und Sprachnotiz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    initial: EntryDraft,
    onClose: () -> Unit,
    onOpenSetup: (String) -> Unit,
    startVoice: Boolean = false,
) {
    val env = LocalEnv.current
    val diary = env.diary
    val draftState = rememberSaveable(saver = DraftSaver) { mutableStateOf(initial) }
    var draft by draftState
    var needMood by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickMood by remember { mutableStateOf(false) }
    var voice by rememberSaveable { mutableStateOf(startVoice) }
    val addedAssets = rememberSaveable { mutableStateOf(listOf<String>()) }
    val dirty = draft != initial
    val mood = diary.moodById[draft.moodId]

    fun discard() {
        // Fotos und Aufnahmen aus dieser Sitzung wieder entfernen
        val ids = addedAssets.value
        if (ids.isNotEmpty()) env.act { deleteAssets(ids) }
        onClose()
    }
    fun requestClose() { if (dirty) confirmDiscard = true else discard() }

    fun save() {
        val d = draft
        if (d.moodId == null) { needMood = true; return }
        val entry = d.toEntry(ZoneId.systemDefault())
        val removed = initial.assetIds - d.assetIds.toSet()
        val goalIds = goalsOn(diary, LocalDate.parse(d.date)).map { it.id }
        env.act {
            saveEntry(entry)
            if (removed.isNotEmpty()) deleteAssets(removed)
            // Haken der Tagesziele uebernehmen; verknuepfte Aktivitaeten haken zusaetzlich selbst ab
            for (g in goalIds) {
                val linked = diary.goals.first { it.id == g }.tagId?.let { it in entry.tagIds } == true
                setGoalCheck(g, d.date, g in d.checkedGoalIds || linked)
            }
        }
        onClose()
    }

    BackHandler { requestClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (initial.isNew) "NEUER EINTRAG" else "EINTRAG", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = { IconButton(::requestClose) { Icon(Icons.Rounded.Close, "Schließen") } },
                actions = {
                    IconButton({ draft = draft.copy(favorite = !draft.favorite) }) {
                        Icon(if (draft.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (draft.favorite) "Favorit entfernen" else "Als Favorit markieren",
                            tint = if (draft.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    if (mood != null) ValueChip(mood) { pickMood = true }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(::save, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Speichern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DateTimeRow(draft) { draft = it }
            // Solange kein Wert gewaehlt ist, steht das Barometer gross da; danach reicht der Wert oben rechts
            if (mood == null) Panel(padding = 12.dp) {
                MoodGauge(null, "", onScore = { score -> diary.moods.firstOrNull { it.level == 6 - score && it.archivedAt == null }?.let { draft = draft.copy(moodId = it.id); needMood = false } })
                Text(if (needMood) "Bitte erst einen Wert wählen." else "Zeiger ziehen oder auf den Bogen tippen", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium, color = if (needMood) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EntryBody(draft, { draft = it }, onOpenSetup, onAssetAdded = { addedAssets.value = addedAssets.value + it }, onVoice = { voice = true })
            Spacer(Modifier.height(12.dp))
        }
    }

    if (voice) VoiceDialog(draft, onApply = { draft = it }, onAssetAdded = { addedAssets.value = addedAssets.value + it }) { voice = false }
    if (pickMood) MoodDialog(draft, { draft = it }, onOpenSetup = { pickMood = false; onOpenSetup("moods") }) { pickMood = false }
    if (confirmDiscard) ConfirmDialog(
        "Änderungen verwerfen?", "Deine Eingaben in diesem Eintrag gehen verloren.", "Verwerfen",
        onConfirm = { confirmDiscard = false; discard() }, onDismiss = { confirmDiscard = false }, destructive = true,
    )
}

/** Ziele, die an einem Tag gelten (nicht archiviert, innerhalb von Start und Ende). */
fun goalsOn(diary: Diary, date: LocalDate): List<Goal> = diary.goals.filter { g ->
    g.archivedAt == null && g.startDate <= date.toString() && (g.endDate == null || date.toString() <= g.endDate!!)
}

/** Der Wert klein oben rechts: Zahl im Farbkreis und Name. Antippen oeffnet das Barometer. */
@Composable
private fun ValueChip(mood: Mood, onClick: () -> Unit) {
    val color = LocalDiaryColors.current.level(mood.level)
    val score = 6 - mood.level
    Surface(
        onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(48.dp).semantics(mergeDescendants = true) { contentDescription = "Wert $score von 5, ${mood.name}. Ändern" },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(score.toString(), color = onColor(color), fontWeight = FontWeight.Black) }
            Text(mood.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodDialog(draft: EntryDraft, onChange: (EntryDraft) -> Unit, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val moods = env.diary.moods.filter { it.archivedAt == null || it.id == draft.moodId }
    val mood = env.diary.moodById[draft.moodId]
    val sameLevel = moods.filter { it.level == mood?.level }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoodGauge(mood?.let { 6 - it.level }, mood?.name ?: "", onScore = { score ->
                    val level = 6 - score
                    // innerhalb derselben Stufe bleibt die gewaehlte Stimmung, sonst die erste der neuen Stufe
                    val next = if (mood?.level == level) mood else moods.firstOrNull { it.level == level }
                    if (next != null) onChange(draft.copy(moodId = next.id))
                }, modifier = Modifier.requiredWidth(280.dp))
                if (sameLevel.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    for (m in sameLevel) FilterChip(m.id == draft.moodId, { onChange(draft.copy(moodId = m.id)) }, label = { Text(m.name) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Fertig") } },
        dismissButton = { TextButton(onOpenSetup) { Text("Stimmungen bearbeiten") } },
    )
}

/** Datum und Uhrzeit des Eintrags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(draft: EntryDraft, onChange: (EntryDraft) -> Unit) {
    val env = LocalEnv.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip({ pickDate = true }, { Text(fmtDay(LocalDate.parse(draft.date), env.today)) }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        AssistChip({ pickTime = true }, { Text(fmtTime(draft.minuteOfDay) + " Uhr") }, leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(draft.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= env.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { onChange(draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) }; pickDate = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton({ pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(draft.minuteOfDay / 60, draft.minuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Uhrzeit") },
            text = { TimePicker(state) },
            confirmButton = { TextButton({ onChange(draft.copy(minuteOfDay = state.hour * 60 + state.minute)); pickTime = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton({ pickTime = false }) { Text("Abbrechen") } },
        )
    }
}

/** Gestrichelter Rahmen fuer "noch offen". */
private fun Modifier.dashed(color: Color, radius: androidx.compose.ui.unit.Dp = 12.dp): Modifier = drawBehind {
    val w = 2.dp.toPx()
    drawRoundRect(color, androidx.compose.ui.geometry.Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        androidx.compose.ui.geometry.CornerRadius(radius.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
}

/** Dienstart: genau eine, aus den eingeschalteten. Der Stift fuehrt zu den Dienstarten. */
@Composable
private fun ShiftRow(draft: EntryDraft, onChange: (EntryDraft) -> Unit, onOpenSetup: (String) -> Unit) {
    val diary = LocalEnv.current.diary
    val shifts = Shifts.all(diary).filter { it.archivedAt == null || it.id in draft.tagIds }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shifts.isEmpty()) {
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenSetup("shifts") }.dashed(MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) {
                Text("Dienstarten einrichten", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in shifts) {
                    val on = t.id in draft.tagIds
                    Surface(
                        onClick = { onChange(draft.copy(tagIds = Shifts.select(diary, draft.tagIds, t.id))) }, shape = RoundedCornerShape(12.dp),
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface, contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        border = if (on) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(48.dp).semantics { selected = on; contentDescription = "Dienstart ${t.name}" },
                    ) { Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(Shifts.short(diary, t), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1) } }
                }
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Dienstarten anpassen") { onOpenSetup("shifts") }.dashed(MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, "Dienstarten anpassen", Modifier.size(20.dp))
            }
        }
    }
}

private fun segmentLabels(s: Scale): List<String>? = when {
    s.type == ScaleType.TEXT && s.points.size in 2..7 -> s.points.sortedBy { it.position }.map { it.label }
    s.type == ScaleType.NUMBER && (s.max - s.min + 1) in 2..7 -> (s.min..s.max).map { it.toString() }
    else -> null
}

/** Skalen als flache Zeilen: Name, Segmente zum Antippen, gewaehlter Wert. Der Rest liegt als Feld darunter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScalesBlock(draft: EntryDraft, onChange: (EntryDraft) -> Unit) {
    val diary = LocalEnv.current.diary
    val scales = diary.scales.filter { it.archivedAt == null || draft.valueOf(it.id) != null }.sortedBy { it.sortOrder }
    if (scales.isEmpty()) return
    val limit = when (diary.settings.detail) { Detail.KOMPAKT -> 3; Detail.NORMAL -> 6; Detail.ALLES -> Int.MAX_VALUE }
    val segmentable = scales.filter { segmentLabels(it) != null }
    val rows = segmentable.take(limit)
    val rest = scales - rows.toSet()
    var open by remember { mutableStateOf<Scale?>(null) }

    if (rows.isNotEmpty()) Panel(padding = 0.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            for (s in rows) {
                val labels = segmentLabels(s)!!
                val v = draft.valueOf(s.id)
                val pts = s.points.sortedBy { it.position }
                val sel = if (s.type == ScaleType.TEXT) pts.indexOfFirst { it.id == v?.pointId } else v?.number?.let { it - s.min } ?: -1
                val color = groupColor(s.colorIndex)
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.name, Modifier.width(104.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        labels.forEachIndexed { i, label ->
                            val isSel = i == sel
                            Box(
                                Modifier.weight(1f).height(44.dp).clickable(role = Role.RadioButton) {
                                    onChange(draft.withValue(s.id, if (isSel) null else if (s.type == ScaleType.TEXT) ScaleValue(s.id, pointId = pts[i].id) else ScaleValue(s.id, number = s.min + i)))
                                }.semantics { contentDescription = label; selected = isSel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(7.dp)).background(if (sel >= 0 && i <= sel) color else MaterialTheme.colorScheme.surfaceVariant)
                                    .then(if (isSel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)) else Modifier))
                            }
                        }
                    }
                    Text(if (sel >= 0) labels[sel].replaceFirstChar { it.uppercase() } else "offen", Modifier.width(68.dp), textAlign = TextAlign.End, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (sel >= 0) FontWeight.Bold else FontWeight.Normal, color = if (sel >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (rest.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in rest) {
            val reading = scaleReading(s, draft.valueOf(s.id))
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).clickable { open = s }
                .then(if (reading == null) Modifier.dashed(MaterialTheme.colorScheme.outline) else Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, groupColor(s.colorIndex), RoundedCornerShape(12.dp)))
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (reading == null) s.name else "${s.name}: ${reading.second}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
    open?.let { s ->
        AlertDialog(
            onDismissRequest = { open = null },
            text = { Column { ScaleInput(s, draft.valueOf(s.id), lastValue = lastValueOf(diary, s.id)) { onChange(draft.withValue(s.id, it)) } } },
            confirmButton = { TextButton({ open = null }) { Text("Fertig") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryBody(
    draft: EntryDraft, onChange: (EntryDraft) -> Unit, onOpenSetup: (String) -> Unit,
    onAssetAdded: (String) -> Unit, onVoice: () -> Unit,
) {
    val env = LocalEnv.current
    val diary = env.diary
    var fullNote by rememberSaveable { mutableStateOf(false) }
    var newTagFor by remember { mutableStateOf<TagGroup?>(null) }
    var newTagUngrouped by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shiftGroup = Shifts.group(diary)

    for (block in (diary.settings.entryOrder + EntryBlocks.DEFAULT).distinct()) when (block) {
        EntryBlocks.SHIFT -> if ("schicht" in diary.settings.modules) ShiftRow(draft, onChange, onOpenSetup)
        EntryBlocks.SCALES -> ScalesBlock(draft, onChange)
        EntryBlocks.TAGS -> {
            // Die haeufigsten Aktivitaeten der letzten 90 Tage stehen vorn, alle Gruppen folgen auf Wunsch
            val take = when (diary.settings.detail) { Detail.KOMPAKT -> 4; Detail.NORMAL -> 6; Detail.ALLES -> 10 }
            val frequent = remember(diary.entries, diary.tags, shiftGroup?.id, take) {
                val since = env.today.minusDays(90).toString()
                val counts = diary.entries.filter { it.localDate >= since }.flatMap { it.tagIds }.groupingBy { it }.eachCount()
                diary.tags.filter { it.archivedAt == null && it.groupId != shiftGroup?.id && (counts[it.id] ?: 0) > 0 }.sortedByDescending { counts[it.id] }.take(take)
            }
            val shown = frequent + diary.tags.filter { it.id in draft.tagIds && it.groupId != shiftGroup?.id && frequent.none { f -> f.id == it.id } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in shown) FilterChip(t.id in draft.tagIds, { onChange(draft.toggleTag(t.id)) }, label = { Text(t.name) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp),
                    // Gewaehltes hebt sich klar ab: dunkle Flaeche, helle Schrift
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.onSurface, selectedLabelColor = MaterialTheme.colorScheme.surface))
                TextButton({ showAll = !showAll }, Modifier.heightIn(min = 48.dp)) { Text(if (showAll) "Weniger anzeigen" else "Alle Aktivitäten") }
            }
            if (showAll) AllTags(draft, onChange, onNew = { g -> if (g == null) newTagUngrouped = true else newTagFor = g }, onOpenSetup)
        }
        EntryBlocks.NOTE -> Panel(padding = 12.dp) {
            if (draft.title.isNotBlank()) Text(draft.title, style = MaterialTheme.typography.titleMedium)
            if ('\n' in draft.note || draft.note.length > 120) {
                NoteText(NoteMarkup.toHtml(draft.note), Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { fullNote = true }.padding(4.dp), maxLines = 6)
            } else OutlinedTextField(
                draft.note, { onChange(draft.copy(note = it)) }, placeholder = { Text("Notiz schreiben …") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                trailingIcon = { IconButton({ fullNote = true }) { Icon(Icons.Rounded.OpenInFull, "Ganze Notiz mit Titel und Vorlagen") } },
            )
            Button(onVoice, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Mic, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Notiz einsprechen") }
        }
    }

    // Ziele des Tages
    val goals = goalsOn(diary, LocalDate.parse(draft.date))
    if (goals.isNotEmpty()) Panel(title = "Ziele des Tages", padding = 12.dp) {
        for (g in goals) {
            val linked = g.tagId != null && g.tagId in draft.tagIds
            val checked = g.id in draft.checkedGoalIds || linked
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !linked, role = Role.Checkbox) { onChange(draft.copy(checkedGoalIds = if (checked) draft.checkedGoalIds - g.id else draft.checkedGoalIds + g.id)) }
                    .heightIn(min = 48.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(checked, null, enabled = !linked)
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.bodyLarge)
                    val hint = if (linked) "Erledigt durch die Aktivität ${diary.tagById[g.tagId]?.name ?: ""}" else goalSeriesHint(diary, g, LocalDate.parse(draft.date))
                    if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    MediaSection(draft, onChange, onAssetAdded, onVoice)

    if (fullNote) NoteEditorDialog(draft.title, draft.note, diary.templates, onDone = { t, n -> onChange(draft.copy(title = t, note = n)); fullNote = false }, onEditTemplates = { fullNote = false; onOpenSetup("templates") })

    val target = newTagFor
    if (target != null || newTagUngrouped) TextPromptDialog(
        "Neue Aktivität" + (target?.let { " in ${it.name}" } ?: ""), "Name", confirm = "Anlegen",
        onConfirm = { name ->
            val tag = Tag(groupId = target?.id, name = name, icon = "circle", sortOrder = (diary.tags.filter { it.groupId == target?.id }.maxOfOrNull { it.sortOrder } ?: -1) + 1)
            env.act { save(tag) }
            onChange(draft.toggleTag(tag.id))
            newTagFor = null; newTagUngrouped = false
        },
        onDismiss = { newTagFor = null; newTagUngrouped = false },
    )
}

/** Alle Gruppen mit ihren Aktivitaeten, ohne die Dienstarten. */
@Composable
private fun AllTags(draft: EntryDraft, onChange: (EntryDraft) -> Unit, onNew: (TagGroup?) -> Unit, onOpenSetup: (String) -> Unit) {
    val diary = LocalEnv.current.diary
    val shiftGroup = Shifts.group(diary)
    val groups = diary.groups.filter { it.archivedAt == null && it.id != shiftGroup?.id }.sortedBy { it.sortOrder }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    @Composable
    fun tagGrid(tags: List<Tag>, color: Color, onAdd: () -> Unit) {
        val columns = 4
        val cells: List<Tag?> = tags + listOf(null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in cells.chunked(columns)) Row(Modifier.fillMaxWidth()) {
                for (t in row) {
                    if (t != null) ActivityTile(t.name, t.icon, t.colorIndex?.let { groupColor(it) } ?: color, t.id in draft.tagIds, { onChange(draft.toggleTag(t.id)) }, Modifier.weight(1f))
                    else Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Neu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    for (block in groups) {
        val tags = diary.tags.filter { it.groupId == block.id && (it.archivedAt == null || it.id in draft.tagIds) }
        val isCollapsed = collapsed[block.id] ?: block.collapsed
        val chosen = tags.count { it.id in draft.tagIds }
        Panel(padding = 12.dp) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { collapsed[block.id] = !isCollapsed }
                    .semantics { stateDescription = if (isCollapsed) "eingeklappt" else "ausgeklappt" }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(groupColor(block.colorIndex)))
                Spacer(Modifier.width(10.dp))
                Text(block.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (chosen > 0) Text("$chosen gewählt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
                Icon(if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null)
            }
            if (!isCollapsed) tagGrid(tags, groupColor(block.colorIndex)) { onNew(block) }
        }
    }
    val loose = diary.tags.filter { (it.groupId == null || diary.groupById[it.groupId]?.let { g -> g.archivedAt != null } != false) && (it.archivedAt == null || it.id in draft.tagIds) }
    if (loose.isNotEmpty() || groups.isEmpty()) Panel(title = if (groups.isNotEmpty()) "Ohne Gruppe" else "Aktivitäten", padding = 12.dp) {
        tagGrid(loose, groupColor(10)) { onNew(null) }
    }
    TextButton({ onOpenSetup("activities") }) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aktivitäten und Skalen bearbeiten") }
}

/** Hinweis auf die laufende Serie eines Ziels, z. B. "Serie: 4 Tage". */
fun goalSeriesHint(diary: Diary, goal: Goal, date: LocalDate): String {
    val s = de.mediclever.tagebuch.core.goals.goalTracker(diary, goal.id)?.streaks(date) ?: return ""
    if (s.current == 0) return ""
    return "Serie: " + if (s.unit == de.mediclever.tagebuch.core.goals.StreakUnit.WEEKS) plural(s.current, "Woche", "Wochen") else plural(s.current, "Tag", "Tage")
}

private fun lastValueOf(diary: Diary, scaleId: String): ScaleValue? =
    diary.entries.firstNotNullOfOrNull { e -> e.scaleValues.firstOrNull { it.scaleId == scaleId } }

/** Eingabe eines Skalenwerts: feste Punkte zum Antippen, grosse Zahlenbereiche als Schieber, Zeit mit Plus und Minus. */
@Composable
fun ScaleInput(scale: Scale, value: ScaleValue?, lastValue: ScaleValue?, onChange: (ScaleValue?) -> Unit) {
    val color = groupColor(scale.colorIndex)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(4.dp)) {
        Icon(iconFor(scale.icon), null, tint = color, modifier = Modifier.size(22.dp))
        Text(scale.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        if (value != null) TextButton({ onChange(null) }) { Text("Leeren") }
    }
    when (scale.type) {
        ScaleType.TEXT -> {
            val pts = scale.points.sortedBy { it.position }
            PointRow(pts.map { it.label }, pts.indexOfFirst { it.id == value?.pointId }, color) { i -> onChange(if (i == null) null else ScaleValue(scale.id, pointId = pts[i].id)) }
        }
        ScaleType.NUMBER -> {
            val count = scale.max - scale.min + 1
            if (count in 2..10) PointRow((scale.min..scale.max).map { it.toString() }, value?.number?.let { it - scale.min } ?: -1, color, showAll = true) { i -> onChange(i?.let { ScaleValue(scale.id, number = scale.min + it) }) }
            else Column {
                Text(value?.number?.toString() ?: "Nicht gesetzt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Slider(
                    (value?.number ?: scale.min).toFloat(), { onChange(ScaleValue(scale.id, number = it.toInt())) },
                    valueRange = scale.min.toFloat()..scale.max.toFloat(), steps = (count - 2).coerceAtLeast(0),
                    modifier = Modifier.semantics { contentDescription = scale.name },
                )
            }
        }
        ScaleType.DURATION -> {
            val step = scale.step.coerceAtLeast(1)
            fun start(): Int = (lastValue?.minutes ?: ((scale.min + scale.max) / 2 / step * step)).coerceIn(scale.min, scale.max)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                FilledTonalIconButton({ onChange(ScaleValue(scale.id, minutes = ((value?.minutes ?: start()) - step).coerceAtLeast(scale.min))) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Remove, "Weniger") }
                Text(value?.minutes?.let { fmtDuration(it) } ?: "Nicht gesetzt", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(170.dp))
                FilledTonalIconButton({ onChange(ScaleValue(scale.id, minutes = (value?.minutes?.plus(step) ?: start()).coerceAtMost(scale.max))) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Mehr") }
            }
        }
    }
}

/** Reihe fester Punkte. Erneutes Antippen des gewaehlten Punkts nimmt die Auswahl zurueck. */
@Composable
private fun PointRow(labels: List<String>, selected: Int, color: Color, showAll: Boolean = false, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center) {
            HorizontalDivider(Modifier.padding(horizontal = 28.dp), thickness = 3.dp, color = color.copy(alpha = 0.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { i, label ->
                    val isSel = i == selected
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.RadioButton) { onSelect(if (isSel) null else i) }
                            .semantics { contentDescription = label; this.selected = isSel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(if (isSel) 36.dp else 24.dp).clip(CircleShape)
                                .background(if (isSel) color else MaterialTheme.colorScheme.surface).border(3.dp, color, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (showAll) Text(label, style = MaterialTheme.typography.labelSmall, color = if (isSel) onColor(color) else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
        // Beschriftung steht genau unter ihrem Punkt: ohne Auswahl die beiden Enden, sonst der gewaehlte Punkt
        if (!showAll) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { i, label ->
                val show = if (selected < 0) i == 0 || i == labels.lastIndex else i == selected
                Box(Modifier.width(48.dp), contentAlignment = if (i == 0) Alignment.CenterStart else if (i == labels.lastIndex) Alignment.CenterEnd else Alignment.Center) {
                    if (show) Text(label, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(if (i == 0) Alignment.Start else if (i == labels.lastIndex) Alignment.End else Alignment.CenterHorizontally, unbounded = true),
                        style = if (i == selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Ganze Notiz: Titel, Text mit Formatierung und Schreibvorlagen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(title: String, note: String, templates: List<Template>, onDone: (String, String) -> Unit, onEditTemplates: () -> Unit) {
    var t by rememberSaveable { mutableStateOf(title) }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(note, TextRange(note.length))) }
    var showTemplates by remember { mutableStateOf(false) }
    fun inline(style: NoteMarkup.Style) {
        val (text, a, b) = NoteMarkup.toggleInline(field.text, field.selection.start, field.selection.end, style)
        field = TextFieldValue(text, TextRange(a, b))
    }
    fun list(ordered: Boolean) {
        val (text, cursor) = NoteMarkup.toggleList(field.text, field.selection.start, field.selection.end, ordered)
        field = TextFieldValue(text, TextRange(cursor))
    }
    Dialog(onDismissRequest = { onDone(t, field.text) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Notiz") },
                    navigationIcon = { IconButton({ onDone(t, field.text) }) { Icon(Icons.Rounded.Check, "Fertig") } },
                    actions = { TextButton({ showTemplates = true }) { Text("Vorlagen") } },
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton({ inline(NoteMarkup.Style.BOLD) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.FormatBold, "Fett") }
                        IconButton({ inline(NoteMarkup.Style.ITALIC) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.FormatItalic, "Kursiv") }
                        IconButton({ inline(NoteMarkup.Style.UNDERLINE) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.FormatUnderlined, "Unterstrichen") }
                        IconButton({ inline(NoteMarkup.Style.STRIKE) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.FormatStrikethrough, "Durchgestrichen") }
                        IconButton({ list(false) }, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "Liste") }
                        IconButton({ list(true) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.FormatListNumbered, "Nummerierte Liste") }
                    }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("Titel (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(
                    field, { field = it }, placeholder = { Text("Was möchtest du festhalten?") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    visualTransformation = MarkupTransformation(MaterialTheme.colorScheme.outline),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
    if (showTemplates) AlertDialog(
        onDismissRequest = { showTemplates = false },
        title = { Text("Schreibvorlage einfügen") },
        text = {
            Column {
                if (templates.isEmpty()) Text("Du hast noch keine Vorlagen.")
                for (tpl in templates) SettingRow(tpl.title, onClick = {
                    val add = NoteMarkup.fromHtml(tpl.body)
                    val text = if (field.text.isBlank()) add else field.text.trimEnd() + "\n\n" + add
                    field = TextFieldValue(text, TextRange(text.length)); showTemplates = false
                })
            }
        },
        confirmButton = { TextButton({ showTemplates = false; onEditTemplates() }) { Text("Vorlagen bearbeiten") } },
        dismissButton = { TextButton({ showTemplates = false }) { Text("Schließen") } },
    )
}

/** Fotos (Kamera, Galerie) und Sprachnotizen des Eintrags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaSection(draft: EntryDraft, onChange: (EntryDraft) -> Unit, onAssetAdded: (String) -> Unit, onVoice: () -> Unit) {
    val env = LocalEnv.current
    val context = LocalContext.current
    val current = rememberUpdatedState(draft)
    fun attach(asset: Asset) { onAssetAdded(asset.id); onChange(current.value.copy(assetIds = current.value.assetIds + asset.id)) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) env.act {
            for (u in uris) assets.importPhoto(context, u, System.currentTimeMillis())?.let { save(it); attach(it) }
        }
    }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile?.let { File(it) }
        if (ok && f != null && f.length() > 0) env.act {
            assets.importPhotoBytes(f.readBytes(), System.currentTimeMillis())?.let { save(it); attach(it) }
            f.delete()
        } else f?.delete()
    }
    fun openCamera() {
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "aufnahme_${System.currentTimeMillis()}.jpg")
        cameraFile = f.absolutePath
        camera.launch(FileProvider.getUriForFile(context, context.packageName + ".files", f))
    }

    val photos = draft.assetIds.mapNotNull { env.asset(it) }.filter { it.type == AssetType.PHOTO }
    val audios = draft.assetIds.mapNotNull { env.asset(it) }.filter { it.type == AssetType.AUDIO }

    Panel(title = "Fotos", padding = 12.dp) {
        if (photos.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in photos) Box {
                AsyncImage(env.assetFile(a), "Foto", Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                FilledIconButton({ onChange(draft.copy(assetIds = draft.assetIds - a.id)) }, Modifier.align(Alignment.TopEnd).size(48.dp).padding(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xCC11202C), contentColor = Color.White)) { Icon(Icons.Rounded.Close, "Foto entfernen", Modifier.size(18.dp)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(::openCamera, Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aufnehmen") }
            OutlinedButton({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Galerie") }
        }
    }

    Panel(title = "Sprachnotiz", padding = 12.dp) {
        for (a in audios) AudioRow(a, env.assetFile(a)) { onChange(draft.copy(assetIds = draft.assetIds - a.id)) }
        OutlinedButton(onVoice, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sprachnotiz aufnehmen") }
    }
}

@Composable
fun AudioRow(asset: Asset, file: File?, onRemove: (() -> Unit)?) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(asset.id) { onDispose { player?.release(); player = null } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton({
            val p = player
            if (p != null) { p.release(); player = null }
            else if (file != null && file.isFile) player = runCatching {
                MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { it.release(); player = null }; start() }
            }.getOrNull()
        }, Modifier.size(48.dp)) { Icon(if (player != null) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (player != null) "Wiedergabe stoppen" else "Abspielen") }
        Text("Aufnahme · %d:%02d min".format(asset.durationMs / 60000, asset.durationMs / 1000 % 60), Modifier.weight(1f))
        if (onRemove != null) IconButton(onRemove, Modifier.size(48.dp)) { Icon(Icons.Rounded.DeleteOutline, "Aufnahme entfernen") }
    }
}

@Suppress("unused") private fun unusedContext(c: Context) = c
