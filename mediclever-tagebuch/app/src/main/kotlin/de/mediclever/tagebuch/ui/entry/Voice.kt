package de.mediclever.tagebuch.ui.entry

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import de.mediclever.tagebuch.TagebuchApp
import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.shift.Shifts
import de.mediclever.tagebuch.core.voice.VoiceMatcher
import de.mediclever.tagebuch.core.voice.VoiceMatcher.Kind
import de.mediclever.tagebuch.data.VoiceCapture
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.theme.Brand
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import kotlinx.coroutines.delay
import java.io.File

private fun clock(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

/**
 * Sprachnotiz: aufnehmen, als Text und als Aufnahme behalten, und aus dem Text Dienstart,
 * Aktivitaeten und Skalenwerte vorschlagen. Uebernommen wird erst nach "Uebernehmen", ausser der
 * Nutzer hat eingestellt, dass sichere Treffer ohne Nachfrage gelten.
 */
@Composable
fun VoiceDialog(draft: EntryDraft, onApply: (EntryDraft) -> Unit, onAssetAdded: (String) -> Unit, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val diary = env.diary
    val context = LocalContext.current
    val container = (context.applicationContext as? TagebuchApp)?.container
    var capture by remember { mutableStateOf<VoiceCapture?>(null) }
    var fileName by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var seconds by remember { mutableLongStateOf(0L) }
    val state = capture?.state?.collectAsState()?.value ?: VoiceCapture.State()
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    fun start() {
        val c = container ?: return
        val (name, f) = c.assets.newFile(System.currentTimeMillis(), "wav")
        val cap = VoiceCapture(context, f)
        if (cap.start()) { capture = cap; fileName = name; file = f } else { f.delete(); error = "Die Aufnahme konnte nicht gestartet werden." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "Ohne Zugriff auf das Mikrofon ist keine Aufnahme möglich."
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(state.recording) { while (state.recording) { delay(500); seconds += 500 } }
    // Solange der Nutzer nichts geaendert hat, folgt der Text der Erkennung
    LaunchedEffect(state.text) { if (!edited && state.text.isNotBlank()) text = state.text }
    DisposableEffect(Unit) { onDispose { capture?.release(discard = !applied) } }

    val hits = remember(text, diary, env.local.voiceRejects) { VoiceMatcher.suggest(text, diary, env.local.voiceRejected) }
    fun isOn(h: VoiceMatcher.Hit) = checked[h.rejectKey] ?: h.sure

    fun apply() {
        val c = container; val cap = capture
        var d = draft
        if (c != null && cap != null && fileName.isNotEmpty()) {
            val asset = c.assets.register(fileName, AssetType.AUDIO, System.currentTimeMillis(), cap.durationMs)
            env.act { save(asset) }
            onAssetAdded(asset.id)
            d = d.copy(assetIds = d.assetIds + asset.id)
        }
        val clean = text.trim()
        if (clean.isNotEmpty()) d = d.copy(note = listOf(d.note.trim(), clean).filter { it.isNotEmpty() }.joinToString("\n"))
        for (h in hits.filter { isOn(it) }) d = when (h.kind) {
            Kind.SHIFT -> d.copy(tagIds = if (h.id in d.tagIds) d.tagIds else Shifts.select(diary, d.tagIds, h.id))
            Kind.TAG -> if (h.id in d.tagIds) d else d.copy(tagIds = d.tagIds + h.id)
            Kind.SCALE -> d.withValue(h.id, h.value)
        }
        if (diary.settings.voiceLearn) {
            val rejected = hits.filter { it.kind != Kind.SCALE && !isOn(it) }.map { it.rejectKey }
            val accepted = hits.filter { isOn(it) }.map { it.rejectKey }
            env.act { prefs.countVoice(rejected, accepted) }
        }
        applied = true
        onApply(d)
        onDismiss()
    }
    // Ohne Nachfrage: nur wenn alles sicher ist, Skalen fragen immer nach
    LaunchedEffect(state.done, state.recording) {
        if (capture != null && state.done && !state.recording && diary.settings.voiceAutoApply && !edited && hits.isNotEmpty() && hits.all { it.sure }) apply()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (capture == null || state.recording) Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss, Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "Verwerfen") }
                    Text("Sprich einfach los", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(clock(seconds), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(when {
                    error.isNotEmpty() -> error
                    state.text.isNotBlank() -> state.text
                    state.recording && !state.recognitionAvailable -> "Die Aufnahme läuft. Dieses Gerät setzt sie nicht selbst in Text um, du kannst ihn danach eintippen."
                    else -> "Erzähl, wie dein Dienst oder dein Tag war. Ich höre zu und schreibe mit."
                }, Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.headlineSmall,
                    color = if (error.isNotEmpty()) MaterialTheme.colorScheme.error else if (state.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (error.isEmpty()) Button({ capture?.stop() }, Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(22.dp), enabled = state.recording) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)); Text("Fertig", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                } else OutlinedButton(onDismiss, Modifier.fillMaxWidth().height(56.dp)) { Text("Schließen") }
            } else VoiceReview(
                text = text, onText = { text = it; edited = true }, durationMs = capture?.durationMs ?: 0, file = file, hits = hits, isOn = ::isOn,
                onToggle = { h -> checked[h.rejectKey] = !isOn(h) }, auto = diary.settings.voiceAutoApply, onAuto = { on -> env.act { updateSettings { it.copy(voiceAutoApply = on) } } },
                waiting = state.recognising, recognitionAvailable = state.recognitionAvailable, onApply = ::apply, onDismiss = onDismiss,
            )
        }
    }
}

/** Nach der Aufnahme: anhoeren, Text pruefen, Vorschlaege an- und abwaehlen. */
@Composable
fun VoiceReview(
    text: String, onText: (String) -> Unit, durationMs: Long, file: File?, hits: List<VoiceMatcher.Hit>, isOn: (VoiceMatcher.Hit) -> Boolean, onToggle: (VoiceMatcher.Hit) -> Unit,
    auto: Boolean, onAuto: (Boolean) -> Unit, waiting: Boolean, recognitionAvailable: Boolean, onApply: () -> Unit, onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }
    val dark = LocalDiaryColors.current.dark
    val mark = if (dark) Color(0xFF5A2A31) else Color(0xFFFBE3E6)
    val count = hits.count { isOn(it) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onDismiss, Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "Verwerfen") }
            Text("Deine Sprachnotiz", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (dark) Color(0xFF2A3947) else Brand.RichBlack).padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Brand.RustyRed).clickable(role = Role.Button) {
                    val p = player
                    if (p != null) { p.release(); player = null }
                    else if (file != null && file.isFile) player = runCatching { MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { it.release(); player = null }; start() } }.getOrNull()
                }, contentAlignment = Alignment.Center) { Icon(if (player != null) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (player != null) "Wiedergabe stoppen" else "Aufnahme abspielen", tint = Color.White) }
                Text("Aufnahme", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(clock(durationMs), color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Panel(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ALS TEXT", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ editing = !editing }) { Text(if (editing) "Fertig" else "Bearbeiten") }
                }
                if (editing || text.isBlank()) OutlinedTextField(text, onText, Modifier.fillMaxWidth(), minLines = 3, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text(if (waiting) "Der Text wird noch erkannt …" else if (recognitionAvailable) "Es wurde kein Text erkannt. Du kannst ihn hier eintippen." else "Dieses Gerät setzt die Aufnahme nicht selbst in Text um. Du kannst ihn hier eintippen.") })
                else Text(buildAnnotatedString {
                    append(text)
                    for (h in hits) if (h.end <= text.length && h.start < h.end) addStyle(SpanStyle(background = mark), h.start, h.end)
                }, style = MaterialTheme.typography.bodyLarge)
            }
            if (hits.isNotEmpty()) Panel(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("DARAUS ERKANNT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (h in hits) {
                        val on = isOn(h)
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Checkbox) { onToggle(h) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(when (h.kind) { Kind.SHIFT -> "Dienstart: ${h.label}"; Kind.TAG -> "Aktivität: ${h.label}"; Kind.SCALE -> h.label }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("aus „${h.word}“" + if (!h.sure) " · unsicher, bitte prüfen" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(on, null)
                        }
                    }
                }
            } else if (text.isNotBlank()) Text("Aus dem Text ließ sich nichts ableiten. Eigene Stichwörter legst du bei jeder Aktivität an.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Switch) { onAuto(!auto) }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dienstart und Aktivitäten künftig ohne Nachfrage übernehmen. Skalen fragt die App immer nach.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(auto, null)
            }
            Text("Text und Aufnahme werden beide gespeichert und bleiben auf deinem Gerät.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
        Button(onApply, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp), shape = RoundedCornerShape(18.dp)) {
            Text(when (count) { 0 -> "Notiz übernehmen"; 1 -> "1 Vorschlag übernehmen"; else -> "$count Vorschläge übernehmen" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
