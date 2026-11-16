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
import de.mediclever.tagebuch.ui.theme.groupColor
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
 * Eintrag erfassen in zwei Schritten: erst Datum, Uhrzeit und Stimmung, dann Aktivitaeten,
 * Skalen, Ziele, Notiz, Fotos und Sprachnotiz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    initial: EntryDraft,
    onClose: () -> Unit,
    onOpenSetup: (String) -> Unit,
    startStep: Int = if (initial.isNew) 1 else 2,
) {
    val env = LocalEnv.current
    val diary = env.diary
    val draftState = rememberSaveable(saver = DraftSaver) { mutableStateOf(initial) }
    var draft by draftState
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val addedAssets = rememberSaveable { mutableStateOf(listOf<String>()) }
    val dirty = draft != initial

    fun discard() {
        // Fotos und Aufnahmen aus dieser Sitzung wieder entfernen
        val ids = addedAssets.value
        if (ids.isNotEmpty()) env.act { deleteAssets(ids) }
        onClose()
    }
    fun requestClose() { if (dirty) confirmDiscard = true else discard() }

    fun save() {
        val d = draft
        if (d.moodId == null) { step = 1; return }
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

    BackHandler { if (step == 2 && initial.isNew) step = 1 else requestClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (step == 1) Text(if (initial.isNew) "Neuer Eintrag" else "Eintrag bearbeiten", maxLines = 1, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    else Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                        Text(fmtDay(LocalDate.parse(draft.date), env.today), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(fmtTime(draft.minuteOfDay) + " Uhr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (step == 2 && initial.isNew) IconButton({ step = 1 }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück zur Stimmung") }
                    else IconButton(::requestClose) { Icon(Icons.Rounded.Close, "Schließen") }
                },
                actions = {
                    if (step == 2) {
                        IconButton({ draft = draft.copy(favorite = !draft.favorite) }) {
                            Icon(if (draft.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (draft.favorite) "Favorit entfernen" else "Als Favorit markieren",
                                tint = if (draft.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        TextButton(::save) { Text("Speichern", fontWeight = FontWeight.Bold) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step == 1) StepOne(draft, { draft = it }, onNext = { step = 2 }, onOpenSetup = onOpenSetup, canSaveDirectly = true, onSave = ::save)
            else StepTwo(draft, { draft = it }, onOpenSetup, onEditMood = { step = 1 }, onAssetAdded = { addedAssets.value = addedAssets.value + it }, onSave = ::save)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDiscard) ConfirmDialog(
        "Änderungen verwerfen?", "Deine Eingaben in diesem Eintrag gehen verloren.", "Verwerfen",
        onConfirm = { confirmDiscard = false; discard() }, onDismiss = { confirmDiscard = false }, destructive = true,
    )
}

/** Ziele, die an einem Tag gelten (nicht archiviert, innerhalb von Start und Ende). */
fun goalsOn(diary: Diary, date: LocalDate): List<Goal> = diary.goals.filter { g ->
    g.archivedAt == null && g.startDate <= date.toString() && (g.endDate == null || date.toString() <= g.endDate!!)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepOne(draft: EntryDraft, onChange: (EntryDraft) -> Unit, onNext: () -> Unit, onOpenSetup: (String) -> Unit, canSaveDirectly: Boolean, onSave: () -> Unit) {
    val env = LocalEnv.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Text("Wie geht es dir?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip({ pickDate = true }, { Text(fmtDay(LocalDate.parse(draft.date), env.today)) }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) })
        AssistChip({ pickTime = true }, { Text(fmtTime(draft.minuteOfDay) + " Uhr") }, leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) })
    }
    Panel {
        if (env.diary.moods.none { it.archivedAt == null }) Text("Es gibt noch keine Stimmungen. Lege sie in der Einrichtung an.")
        MoodPicker(env.diary.moods, draft.moodId, onSelect = { onChange(draft.copy(moodId = it.id)); onNext() })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton({ onOpenSetup("moods") }) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Stimmungen bearbeiten") }
        if (draft.moodId != null) Button(onNext) { Text("Weiter") }
    }
    if (canSaveDirectly && draft.moodId != null) OutlinedButton(onSave, Modifier.fillMaxWidth()) { Text("Nur Stimmung speichern") }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTwo(
    draft: EntryDraft, onChange: (EntryDraft) -> Unit, onOpenSetup: (String) -> Unit, onEditMood: () -> Unit,
    onAssetAdded: (String) -> Unit, onSave: () -> Unit,
) {
    val env = LocalEnv.current
    val diary = env.diary
    val mood = diary.moodById[draft.moodId]
    var fullNote by rememberSaveable { mutableStateOf(false) }
    var newTagFor by remember { mutableStateOf<TagGroup?>(null) }
    var newTagUngrouped by remember { mutableStateOf(false) }

    // Gewaehlte Stimmung, antippbar zum Aendern
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onEditMood).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mood != null) de.mediclever.tagebuch.ui.icons.MoodFace(mood.level, mood.icon, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(mood?.name ?: "Stimmung wählen", style = MaterialTheme.typography.titleLarge)
            Text("Datum, Uhrzeit oder Stimmung ändern", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Gruppen und Skalen in der gemeinsamen Reihenfolge des Nutzers
    val blocks: List<Any> = (diary.groups.filter { it.archivedAt == null } + diary.scales.filter { it.archivedAt == null || draft.valueOf(it.id) != null })
        .sortedBy { if (it is TagGroup) it.sortOrder else (it as Scale).sortOrder }
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

    for (block in blocks) {
        if (block is TagGroup) {
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
                if (!isCollapsed) tagGrid(tags, groupColor(block.colorIndex)) { newTagFor = block }
            }
        } else if (block is Scale) {
            Panel(padding = 12.dp) { ScaleInput(block, draft.valueOf(block.id), lastValue = lastValueOf(diary, block.id)) { onChange(draft.withValue(block.id, it)) } }
        }
    }
    val loose = diary.tags.filter { (it.groupId == null || diary.groupById[it.groupId]?.let { g -> g.archivedAt != null } != false) && (it.archivedAt == null || it.id in draft.tagIds) }
    if (loose.isNotEmpty() || diary.groups.none { it.archivedAt == null }) Panel(title = if (diary.groups.any { it.archivedAt == null }) "Ohne Gruppe" else "Aktivitäten", padding = 12.dp) {
        tagGrid(loose, groupColor(10)) { newTagUngrouped = true }
    }
    TextButton({ onOpenSetup("activities") }) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aktivitäten und Skalen bearbeiten") }

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

    // Notiz
    Panel(title = "Notiz", padding = 12.dp, action = {
        TextButton({ fullNote = true }) { Icon(Icons.Rounded.OpenInFull, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ganze Notiz") }
    }) {
        if (draft.title.isNotBlank()) Text(draft.title, style = MaterialTheme.typography.titleMedium)
        if ('\n' in draft.note || draft.note.length > 120) {
            NoteText(NoteMarkup.toHtml(draft.note), Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { fullNote = true }.padding(4.dp), maxLines = 6)
        } else OutlinedTextField(
            draft.note, { onChange(draft.copy(note = it)) }, placeholder = { Text("Kurze Notiz …") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }

    MediaSection(draft, onChange, onAssetAdded)

    Button(onSave, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Speichern") }

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
private fun MediaSection(draft: EntryDraft, onChange: (EntryDraft) -> Unit, onAssetAdded: (String) -> Unit) {
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
        Recorder { name, durationMs ->
            env.act { val a = assets.register(name, AssetType.AUDIO, System.currentTimeMillis(), durationMs); save(a); attach(a) }
        }
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

@Composable
private fun Recorder(onRecorded: (String, Long) -> Unit) {
    val env = LocalEnv.current
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var target by remember { mutableStateOf<Pair<String, File>?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }

    fun start() {
        error = ""
        val container = (context.applicationContext as? de.mediclever.tagebuch.TagebuchApp)?.container ?: return
        val t = container.assets.newFile(System.currentTimeMillis(), "m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000); r.setAudioSamplingRate(44_100)
            r.setOutputFile(t.second.absolutePath)
            r.prepare(); r.start()
        }.onSuccess { recorder = r; target = t; startedAt = System.currentTimeMillis() }
            .onFailure { r.release(); t.second.delete(); error = "Die Aufnahme konnte nicht gestartet werden." }
    }
    fun stop() {
        val r = recorder ?: return
        val t = target
        val ok = runCatching { r.stop() }.isSuccess
        r.release(); recorder = null; target = null
        if (ok && t != null && t.second.length() > 0) onRecorded(t.first, System.currentTimeMillis() - startedAt) else t?.second?.delete()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "Ohne Zugriff auf das Mikrofon ist keine Aufnahme möglich."
    }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; it.release() }; target?.second?.delete() } }

    if (recorder == null) OutlinedButton({
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aufnahme starten") }
    else Button({ stop() }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Aufnahme beenden und anhängen") }
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Suppress("unused") private fun unusedContext(c: Context) = c
