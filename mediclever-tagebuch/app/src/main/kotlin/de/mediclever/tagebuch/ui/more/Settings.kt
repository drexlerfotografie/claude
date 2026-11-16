package de.mediclever.tagebuch.ui.more

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.stats.importantDayInfos
import de.mediclever.tagebuch.data.Pin
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.setup.Choice
import de.mediclever.tagebuch.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---- Wichtige Tage -------------------------------------------------------------------------

@Composable
fun ImportantDaysScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val infos = remember(env.diary, env.today) { importantDayInfos(env.diary, env.today) }
    SubScreen("Wichtige Tage", nav.back, floating = { ExtendedFloatingActionButton(onClick = { nav.go(Routes.dayEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Neuer Tag") }) }) {
        if (infos.isEmpty()) EmptyState("Noch keine wichtigen Tage", "Halte Jahrestage, Geburtstage oder besondere Ereignisse fest. Du siehst, wie lange sie her sind und wann der nächste Jahrestag ansteht.", actionLabel = "Ersten Tag anlegen", onAction = { nav.go(Routes.dayEdit()) })
        for (i in infos) Panel(onClick = { nav.go(Routes.dayEdit(i.day.id)) }, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val photo = i.day.assetId?.let { env.asset(it) }
                if (photo != null) AsyncImage(env.assetFile(photo), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                else Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Column(Modifier.weight(1f)) {
                    Text(i.day.name, style = MaterialTheme.typography.titleMedium)
                    Text(fmtDayShort(i.date) + if (i.day.category.isNotBlank()) " · ${i.day.category}" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val parts = listOfNotNull(
                        if (i.isToday) "heute" else null,
                        i.daysSince?.takeIf { it > 0 }?.let { "seit ${plural(it.toInt(), "Tag", "Tagen")}" },
                        i.daysUntil?.takeIf { it > 0 }?.let { (if (i.day.yearly && i.daysSince != null) "nächster Jahrestag in " else "in ") + plural(it.toInt(), "Tag", "Tagen") },
                    )
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (i.day.note.isNotBlank()) Text(i.day.note, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        Spacer(Modifier.height(72.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportantDayEditScreen(id: String?) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val existing = env.diary.importantDays.firstOrNull { it.id == id }
    var d by remember(id) { mutableStateOf(existing ?: ImportantDay(name = "", date = env.today.toString(), sortOrder = env.diary.importantDays.size)) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) env.act { assets.importPhoto(context, uri, System.currentTimeMillis())?.let { a -> save(a); d = d.copy(assetId = a.id) } }
    }
    val categories = (listOf("Privat", "Familie", "Beruf", "Gesundheit", "Sport") + env.diary.importantDays.map { it.category }).filter { it.isNotBlank() }.distinct()
    SubScreen(if (existing == null) "Neuer wichtiger Tag" else "Wichtiger Tag", nav.back, actions = {
        if (existing != null) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Löschen") }
        TextButton({ env.act { save(d.copy(name = d.name.trim(), category = d.category.trim())) }; nav.back() }, enabled = d.name.isNotBlank()) { Text("Speichern") }
    }) {
        OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        RowGroup {
            SettingRow("Datum", fmtDayShort(LocalDate.parse(d.date)), Icons.Rounded.CalendarMonth, onClick = { pickDate = true })
            RowDivider()
            SwitchRow("Jährlich wiederholen", "Zeigt den nächsten Jahrestag", Icons.Rounded.Repeat, d.yearly) { d = d.copy(yearly = it) }
            RowDivider()
            SwitchRow("Am Tag erinnern", null, Icons.Rounded.Notifications, d.remindOnDay) { d = d.copy(remindOnDay = it) }
        }
        Panel(title = "Vorher erinnern", padding = 12.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((n, label) in listOf(1 to "1 Tag", 3 to "3 Tage", 7 to "1 Woche", 30 to "1 Monat")) FilterChip(n in d.remindDaysBefore, { d = d.copy(remindDaysBefore = if (n in d.remindDaysBefore) d.remindDaysBefore - n else (d.remindDaysBefore + n).sorted()) }, label = { Text(label) }, modifier = Modifier.height(48.dp))
            }
        }
        OutlinedTextField(d.category, { d = d.copy(category = it) }, label = { Text("Kategorie (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (c in categories) SuggestionChip({ d = d.copy(category = c) }, { Text(c) }) }
        OutlinedTextField(d.note, { d = d.copy(note = it) }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        val photo = d.assetId?.let { env.asset(it) }
        if (photo != null) Box {
            AsyncImage(env.assetFile(photo), "Foto zum Tag", Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
            FilledTonalIconButton({ d = d.copy(assetId = null) }, Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Rounded.Close, "Foto entfernen") }
        } else OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Foto hinzufügen") }
    }
    if (pickDate) {
        val state = rememberDatePickerState(LocalDate.parse(d.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { d = d.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; pickDate = false }) { Text("Übernehmen") } },
            dismissButton = { TextButton({ pickDate = false }) { Text("Abbrechen") } }) { DatePicker(state) }
    }
    if (confirmDelete && existing != null) ConfirmDialog("„${existing.name}“ löschen?", "Der wichtige Tag wird samt Foto und Erinnerungen gelöscht.", "Löschen", destructive = true,
        onConfirm = { env.act { deleteSimple(existing) }; confirmDelete = false; nav.back() }, onDismiss = { confirmDelete = false })
}

// ---- Erinnerungen --------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && env.diary.reminders.any { it.active }) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    SubScreen("Erinnerungen", nav.back, floating = { ExtendedFloatingActionButton(onClick = { editing = Reminder(minuteOfDay = 20 * 60) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Neue Erinnerung") }) }) {
        Text("Die App erinnert dich zu diesen Uhrzeiten ans Eintragen. Der Hinweis zeigt nie Inhalte aus deinem Tagebuch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (env.diary.reminders.isEmpty()) EmptyState("Keine Erinnerungen", "Lege eine Uhrzeit fest, zu der du gewöhnlich Zeit für einen Eintrag hast.")
        else RowGroup {
            env.diary.reminders.forEachIndexed { i, r ->
                if (i > 0) RowDivider()
                SettingRow(fmtTime(r.minuteOfDay) + " Uhr", r.text.ifBlank { "Zeit für deinen Eintrag" }, Icons.Rounded.Notifications, onClick = { editing = r }) {
                    Switch(r.active, { on -> env.act { save(r.copy(active = on)) }; if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
    editing?.let { r ->
        val isNew = env.diary.reminders.none { it.id == r.id }
        val state = rememberTimePickerState(r.minuteOfDay / 60, r.minuteOfDay % 60, is24Hour = true)
        var text by remember { mutableStateOf(r.text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (isNew) "Neue Erinnerung" else "Erinnerung bearbeiten") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { TimeInput(state); OutlinedTextField(text, { text = it }, label = { Text("Eigener Text (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { TextButton({ env.act { save(r.copy(minuteOfDay = state.hour * 60 + state.minute, text = text.trim())) }; if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS); editing = null }) { Text("Speichern") } },
            dismissButton = { Row { if (!isNew) TextButton({ env.act { deleteSimple(r) }; editing = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Löschen") }; TextButton({ editing = null }) { Text("Abbrechen") } } })
    }
}

// ---- Aussehen ------------------------------------------------------------------------------

@Composable
fun AppearanceScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val s = env.diary.settings
    fun set(change: (UserSettings) -> UserSettings) = env.act { updateSettings(change) }
    var customIndex by remember { mutableStateOf<Int?>(null) }
    SubScreen("Aussehen", nav.back) {
        Panel(title = "Farbmodus", padding = 12.dp) { Choice(listOf(ThemeMode.SYSTEM to "Wie das Gerät", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel"), s.themeMode) { m -> set { it.copy(themeMode = m) } } }
        Panel(title = "Farben der fünf Stufen", padding = 12.dp) {
            for (scheme in MoodScheme.entries) SchemeRow(scheme.label, scheme.colors, s.moodScheme == scheme.key) { set { it.copy(moodScheme = scheme.key) } }
            val custom = s.customMoodColors.mapNotNull { parseHex(it) }.takeIf { it.size == 5 } ?: moodColors(s)
            SchemeRow("Eigenes Schema", custom, s.moodScheme == "custom") { set { it.copy(moodScheme = "custom", customMoodColors = custom.map { c -> hex(c) }) } }
            if (s.moodScheme == "custom") {
                Text("Tippe eine Stufe an, um ihre Farbe zu ändern.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (i in 0..4) Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Farbe für Stufe ${i + 1} ändern") { customIndex = i }.padding(4.dp)) { MoodFace(i + 1, "a", 44.dp) } }
            }
            Text("Jede Stufe hat zusätzlich ein eigenes Symbol. Die Farbe ist also nie das einzige Merkmal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel(title = "Schrift", padding = 12.dp) { Choice(FontChoices.map { it.first to it.second }, s.fontChoice) { f -> set { it.copy(fontChoice = f) } } }
    }
    customIndex?.let { i ->
        AlertDialog(onDismissRequest = { customIndex = null }, title = { Text("Farbe für Stufe ${i + 1}") }, confirmButton = { TextButton({ customIndex = null }) { Text("Schließen") } },
            text = {
                val options = (MoodScheme.entries.flatMap { it.colors } + GroupPalette).distinct()
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in options) Box(Modifier.size(44.dp).clip(CircleShape).background(c).clickable(role = Role.RadioButton) {
                        val cur = (s.customMoodColors.takeIf { it.size == 5 } ?: moodColors(s).map { hex(it) }).toMutableList(); cur[i] = hex(c)
                        set { it.copy(moodScheme = "custom", customMoodColors = cur) }; customIndex = null
                    }.semantics { contentDescription = "Farbe ${hex(c)}" })
                }
            })
    }
}

private fun hex(c: Color): String = "%02X%02X%02X%02X".format((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

@Composable
private fun SchemeRow(label: String, colors: List<Color>, isSelected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = isSelected }.heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RadioButton(isSelected, null)
        Text(label, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (c in colors) Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(c)) }
    }
}

// ---- Sicherheit ----------------------------------------------------------------------------

@Composable
fun SecurityScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val hasPin = env.local.pinHash.isNotEmpty()
    var set by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    SubScreen("Sicherheit", nav.back) {
        RowGroup {
            SwitchRow("PIN-Sperre", if (hasPin) "Die App fragt beim Öffnen nach deiner PIN" else "Aus", Icons.Rounded.Lock, hasPin) { on -> if (on) set = true else remove = true }
            if (hasPin) { RowDivider(); SettingRow("PIN ändern", icon = Icons.Rounded.Password, onClick = { set = true }) }
        }
        Text("Die PIN sperrt die Oberfläche auf diesem Gerät. Sie ersetzt nicht die Anmeldung am Konto und schützt nicht, wenn jemand das entsperrte Gerät in der Hand hat. Vergisst du die PIN, hilft nur, die App-Daten zu löschen und dein Tagebuch aus dem Konto oder einer Sicherung zurückzuholen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Im Sperrzustand und in der App-Übersicht des Geräts wird der Inhalt nicht angezeigt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (set) {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { set = false }, title = { Text("PIN festlegen") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField("PIN (4 bis 8 Ziffern)", a) { a = it }
                PinField("PIN wiederholen", b) { b = it }
                if (b.isNotEmpty() && a != b) Text("Die Eingaben stimmen nicht überein.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton({ val salt = Pin.newSalt(); env.act { prefs.setPin(Pin.hash(a, salt), salt) }; set = false }, enabled = Pin.isValid(a) && a == b) { Text("Speichern") } },
            dismissButton = { TextButton({ set = false }) { Text("Abbrechen") } })
    }
    if (remove) {
        var a by remember { mutableStateOf("") }
        val ok = Pin.matches(a, env.local.pinSalt, env.local.pinHash)
        AlertDialog(onDismissRequest = { remove = false }, title = { Text("PIN-Sperre ausschalten") }, text = { PinField("Aktuelle PIN", a) { a = it } },
            confirmButton = { TextButton({ env.act { prefs.setPin("", "") }; remove = false }, enabled = ok) { Text("Ausschalten") } },
            dismissButton = { TextButton({ remove = false }) { Text("Abbrechen") } })
    }
}

@Composable
fun PinField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, { v -> onChange(v.filter { it.isDigit() }.take(8)) }, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

// ---- Einstellungen -------------------------------------------------------------------------

@Composable
fun SettingsScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val s = env.diary.settings
    fun set(change: (UserSettings) -> UserSettings) = env.act { updateSettings(change) }
    SubScreen("Einstellungen", nav.back) {
        Panel(title = "Wochenanfang", padding = 12.dp) { Choice(listOf(1 to "Montag", 6 to "Samstag", 7 to "Sonntag"), s.weekStart) { w -> set { it.copy(weekStart = w) } } }
        Panel(title = "Einträge nach Mitternacht", padding = 12.dp) {
            Text("Wenn du zwischen 0 und 5 Uhr einen neuen Eintrag anlegst:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choice(listOf(MidnightRule.ASK to "Jedes Mal fragen", MidnightRule.PREVIOUS_DAY to "Zählt zum Vortag", MidnightRule.CURRENT_DAY to "Zählt zum neuen Tag"), s.midnightRule) { m -> set { it.copy(midnightRule = m) } }
        }
        RowGroup {
            SwitchRow("Gedanke des Tages", "Ein ruhiger Satz über deinen Einträgen", Icons.Rounded.FormatQuote, s.showQuote) { on -> set { it.copy(showQuote = on) } }
            RowDivider()
            SwitchRow("Rückblick", "Hinweis „Vor 1 Jahr“ in den Einträgen", Icons.Rounded.History, s.showMemories) { on -> set { it.copy(showMemories = on) } }
            RowDivider()
            SwitchRow("Berichte ankündigen", "Benachrichtigung zum Wochen- und Monatsbericht", Icons.Rounded.Assessment, s.reportNotifications) { on -> set { it.copy(reportNotifications = on) } }
        }
    }
}
