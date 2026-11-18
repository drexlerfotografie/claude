package de.mediclever.tagebuch.ui.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.R
import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.shift.Shifts
import de.mediclever.tagebuch.core.tone.Lines
import de.mediclever.tagebuch.core.tone.Presets
import de.mediclever.tagebuch.core.voice.VoiceMatcher
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.theme.Brand

fun startBlockName(key: String, tone: Tone) = when (key) {
    StartBlocks.SCALES -> "Skalen-Kacheln"; StartBlocks.MATRIX -> "Monatsmatrix"
    StartBlocks.LINE -> if (tone == Tone.CLEVERLE) "Cleverle sagt" else "Ein Satz zur Woche"
    StartBlocks.GOALS -> "Ziele heute"; StartBlocks.LAST -> "Letzter Eintrag"; else -> key
}
private fun entryBlockName(key: String) = when (key) { EntryBlocks.SHIFT -> "Dienstart"; EntryBlocks.SCALES -> "Skalen"; EntryBlocks.TAGS -> "Aktivitäten"; EntryBlocks.NOTE -> "Notiz"; else -> key }

/** Kleine Marke fuer Funktionen, die zu Plus gehoeren. */
@Composable
fun PlusBadge(modifier: Modifier = Modifier) {
    Text("PLUS", modifier.clip(RoundedCornerShape(6.dp)).background(Brand.Cardinal).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
}

@Composable
private fun Head(text: String, plus: Boolean = false) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (plus) PlusBadge()
    }
}

@Composable
private fun <T> Options(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Surface(onClick = { onSelect(value) }, shape = RoundedCornerShape(12.dp), color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, border = if (on) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).height(48.dp).semantics { this.selected = on }) {
                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1) }
            }
        }
    }
}

/** Liste zum Umsortieren mit Pfeilen; optional mit Auge zum Ausblenden. */
@Composable
private fun OrderList(items: List<String>, name: (String) -> String, onChange: (List<String>) -> Unit, onHide: ((String) -> Unit)? = null) {
    RowGroup {
        items.forEachIndexed { i, key ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name(key), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton({ onChange(items.toMutableList().apply { removeAt(i); add(i - 1, key) }) }, Modifier.size(48.dp), enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "${name(key)} nach oben") }
                IconButton({ onChange(items.toMutableList().apply { removeAt(i); add(i + 1, key) }) }, Modifier.size(48.dp), enabled = i < items.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "${name(key)} nach unten") }
                if (onHide != null) IconButton({ onHide(key) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Visibility, "${name(key)} ausblenden") }
            }
        }
    }
}

/** Typ, Detailtiefe, Tonfall, Reihenfolge und Startseite. Der Typ ist frei; einzeln aendern gehoert zu Plus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val s = env.diary.settings
    val plus = env.local.plus
    fun change(f: (UserSettings) -> UserSettings) = env.act { updateSettings(f) }
    /** Einzelne Aenderungen nur mit Plus; sonst fuehrt der Tipp zur Erklaerung. */
    fun gated(f: (UserSettings) -> UserSettings) { if (plus) change(f) else nav.go(Routes.PLUS) }

    SubScreen("So soll dein Tagebuch sein", nav.back) {
        Head("Dein Typ")
        for (row in UserType.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in row) {
                val on = t == s.userType
                Surface(onClick = { change { Presets.apply(it, t) } }, shape = RoundedCornerShape(12.dp), color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, border = if (on) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f).height(52.dp).semantics { selected = on }) { Box(contentAlignment = Alignment.Center) { Text(Presets.title(t), fontWeight = if (on) FontWeight.Bold else FontWeight.Medium) } }
            }
        }
        Text(Presets.hint(s.userType), style = MaterialTheme.typography.bodyMedium)
        Text(if (plus) "Der Typ stellt die Punkte unten vor. Du kannst jeden einzeln ändern." else "Der Typ stellt die Punkte unten vor. Einzeln ändern kannst du sie mit Plus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Head("Detailtiefe", plus = !plus)
        Options(listOf(Detail.KOMPAKT to "Kompakt", Detail.NORMAL to "Normal", Detail.ALLES to "Alles"), s.detail) { d -> gated { it.copy(detail = d) } }

        Head("Tonfall", plus = !plus)
        for (t in Tone.entries) {
            val on = t == s.tone
            Surface(onClick = { gated { it.copy(tone = t) } }, shape = RoundedCornerShape(16.dp), color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().semantics { selected = on }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (t == Tone.CLEVERLE) Image(painterResource(R.drawable.cleverle), null, Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Lines.title(t), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        Text(Lines.sample(t), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (s.tone == Tone.CLEVERLE) RowGroup {
            SwitchRow("Cleverle darf Hinweise geben", "Nicht nur Zahlen nennen. Nach einem schweren Tag hält er sich immer zurück.", checked = s.cleverleHints, onChange = { on -> change { it.copy(cleverleHints = on) } })
        }

        Head("Reihenfolge im Eintrag", plus = !plus)
        OrderList((s.entryOrder + EntryBlocks.DEFAULT).distinct(), ::entryBlockName, onChange = { o -> gated { it.copy(entryOrder = o) } })

        Head("Startseite", plus = !plus)
        Text("Das Barometer mit dem Wochenring bleibt immer oben. Alles darunter kannst du verschieben oder ausblenden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val visible = s.startBlocks.filter { it in StartBlocks.ALL }
        if (visible.isNotEmpty()) OrderList(visible, { startBlockName(it, s.tone) }, onChange = { o -> gated { it.copy(startBlocks = o) } }, onHide = { key -> gated { it.copy(startBlocks = it.startBlocks - key) } })
        val hidden = StartBlocks.ALL - visible.toSet()
        if (hidden.isNotEmpty()) {
            Text("Ausgeblendet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RowGroup {
                hidden.forEachIndexed { i, key ->
                    if (i > 0) RowDivider()
                    SettingRow(startBlockName(key, s.tone), "einblenden", Icons.Rounded.VisibilityOff, onClick = { gated { it.copy(startBlocks = it.startBlocks + key) } })
                }
            }
        }
        val scales = env.diary.scales.filter { it.archivedAt == null }.sortedBy { it.sortOrder }
        if (scales.isNotEmpty() && StartBlocks.SCALES in visible) {
            Text("Skalen in den Kacheln (bis zu drei)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val chosen = s.startScaleIds.filter { id -> scales.any { it.id == id } }.ifEmpty { scales.take(3).map { it.id } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (sc in scales) FilterChip(sc.id in chosen, { gated { st -> st.copy(startScaleIds = if (sc.id in chosen) chosen - sc.id else (chosen + sc.id).takeLast(3)) } }, label = { Text(sc.name) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        if (!plus) Button({ nav.go(Routes.PLUS) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Was Plus kann") }
    }
}

/** MediClever Tagebuch Plus. Bis es einen Kauf gibt, laesst es sich zum Ausprobieren einschalten. */
@Composable
fun PlusScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    SubScreen("MediClever Tagebuch Plus", nav.back) {
        Text("Das Tagebuch funktioniert vollständig ohne Plus: Einträge, Dienstarten, Skalen, Sprachnotiz, Auswertungen. Mit Plus bestimmst du selbst, wie es aussieht und klingt.", style = MaterialTheme.typography.bodyLarge)
        RowGroup {
            SettingRow("Tonfall wählen", "Sachlich, freundlich, knapp oder Cleverle", Icons.Rounded.RecordVoiceOver)
            RowDivider()
            SettingRow("Reihenfolge festlegen", "Im Eintrag und auf der Startseite", Icons.Rounded.SwapVert)
            RowDivider()
            SettingRow("Startseite anpassen", "Blöcke verschieben und ausblenden, Skalen-Kacheln wählen", Icons.Rounded.Dashboard)
            RowDivider()
            SettingRow("Detailtiefe einzeln einstellen", "Unabhängig vom gewählten Typ", Icons.Rounded.Tune)
        }
        RowGroup {
            SwitchRow("Plus einschalten", "Testphase: kostenlos. Einen Kauf gibt es noch nicht.", checked = env.local.plus, onChange = { on -> env.act { prefs.setPlus(on) } })
        }
    }
}

/** Alle Aktivitaeten; ein Tipp fuehrt zu ihren Stichwoertern fuer die Sprachnotiz. */
@Composable
fun KeywordsListScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val d = env.diary
    SubScreen("Stichwörter", nav.back) {
        Text("Aus deiner Sprachnotiz schlägt die App Dienstart und Aktivitäten vor. Hier legst du fest, bei welchen Wörtern.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RowGroup {
            SwitchRow("Aus meinen Korrekturen lernen", "Dreimal abgelehnt: Die App schlägt das Wort nicht mehr vor.", checked = d.settings.voiceLearn, onChange = { on -> env.act { updateSettings { it.copy(voiceLearn = on) } } })
            RowDivider()
            SwitchRow("Ohne Nachfrage übernehmen", "Gilt für Dienstart und Aktivitäten. Skalen fragt die App immer nach.", checked = d.settings.voiceAutoApply, onChange = { on -> env.act { updateSettings { it.copy(voiceAutoApply = on) } } })
        }
        val groups = d.groups.filter { it.archivedAt == null }.sortedBy { it.sortOrder }
        for (g in groups) {
            val tags = d.tags.filter { it.groupId == g.id && it.archivedAt == null }.sortedBy { it.sortOrder }
            if (tags.isEmpty()) continue
            SectionLabel(g.name)
            RowGroup {
                tags.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    val own = d.settings.keywords.firstOrNull { it.tagId == t.id }
                    SettingRow(t.name, VoiceMatcher.keywords(d, t.id, t.name).take(4).joinToString(", ") + (own?.blocked?.takeIf { it.isNotEmpty() }?.let { " · gesperrt: ${it.size}" } ?: ""), onClick = { nav.go(Routes.keywords(t.id)) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordsScreen(tagId: String) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val d = env.diary
    val tag = d.tagById[tagId]
    if (tag == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val own = d.settings.keywords.firstOrNull { it.tagId == tagId } ?: KeywordSet(tagId)
    var addWord by remember { mutableStateOf(false) }
    var addBlocked by remember { mutableStateOf(false) }
    fun save(k: KeywordSet) = env.act { updateSettings { s -> s.copy(keywords = s.keywords.filter { it.tagId != tagId } + listOfNotNull(k.takeIf { it.words.isNotEmpty() || it.blocked.isNotEmpty() })) } }
    val all = VoiceMatcher.keywords(d, tagId, tag.name)

    SubScreen(tag.name, nav.back) {
        Text(if (Shifts.isShift(d, tagId)) "Dienstart" else "Aktivität", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Panel(title = "Bei diesen Wörtern vorschlagen", padding = 14.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in all) {
                    val mine = own.words.any { VoiceMatcher.fold(it) == VoiceMatcher.fold(w) }
                    if (mine) InputChip(true, { save(own.copy(words = own.words.filterNot { VoiceMatcher.fold(it) == VoiceMatcher.fold(w) })) }, { Text(w) }, trailingIcon = { Icon(Icons.Rounded.Close, "$w entfernen", Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
                    else AssistChip({}, { Text(w) }, enabled = false, modifier = Modifier.heightIn(min = 48.dp))
                }
                AssistChip({ addWord = true }, { Text("Wort hinzufügen") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text("Wortformen erkennt die App selbst, zum Beispiel „gelaufen“ und „läuft“ für „laufen“. Graue Wörter sind eingebaut.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel(title = "Nie vorschlagen bei", padding = 14.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in own.blocked) InputChip(true, { save(own.copy(blocked = own.blocked - w)) }, { Text(w) }, trailingIcon = { Icon(Icons.Rounded.Close, "$w entfernen", Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
                AssistChip({ addBlocked = true }, { Text("Wort hinzufügen") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Panel(title = "So liest die App einen Satz", padding = 14.dp) {
            val word = all.getOrElse(1) { all.first() }
            for ((sentence, result) in listOf("„war noch $word“" to tag.name, "„nicht $word gewesen“" to "kein Vorschlag", "„heute ohne $word“" to "kein Vorschlag", "„wollte eigentlich $word“" to "fragt nach"))
                Row { Text(sentence, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(result, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (result == tag.name) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Verneinungen und „wollte, sollte, müsste“ direkt vor dem Wort heben den Treffer auf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Alles läuft auf deinem Gerät. Es wird nichts verschickt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (addWord) TextPromptDialog("Stichwort für ${tag.name}", "Wort oder kurze Wortfolge", confirm = "Hinzufügen", onConfirm = { w -> if (w.isNotBlank()) save(own.copy(words = (own.words + w.trim()).distinct())); addWord = false }, onDismiss = { addWord = false })
    if (addBlocked) TextPromptDialog("Nie vorschlagen bei", "Wort", confirm = "Hinzufügen", onConfirm = { w -> if (w.isNotBlank()) save(own.copy(blocked = (own.blocked + w.trim()).distinct())); addBlocked = false }, onDismiss = { addBlocked = false })
}
