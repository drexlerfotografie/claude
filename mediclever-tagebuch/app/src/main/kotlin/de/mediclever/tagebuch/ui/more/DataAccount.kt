package de.mediclever.tagebuch.ui.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mediclever.tagebuch.AppContainer
import de.mediclever.tagebuch.BuildConfig
import de.mediclever.tagebuch.cloud.CloudException
import de.mediclever.tagebuch.core.importer.ImportException
import de.mediclever.tagebuch.core.importer.ImportKind
import de.mediclever.tagebuch.core.importer.ImportPreview
import de.mediclever.tagebuch.core.importer.MergeMode
import de.mediclever.tagebuch.data.ImportOutcome
import de.mediclever.tagebuch.data.Opened
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.setup.verticalScrollCompat
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalContainer = compositionLocalOf<AppContainer?> { null }

@Composable
private fun PreviewTable(p: ImportPreview) {
    val rows = listOf("Einträge" to "${p.entries}", "davon mit Notiz" to "${p.entriesWithNote}", "Favoriten" to "${p.favorites}", "Je Stufe 1 bis 5" to p.entriesPerLevel.joinToString(" / "),
        "Stimmungen" to "${p.moods}", "Gruppen" to "${p.groups}", "Aktivitäten" to "${p.tags} (${p.archivedTags} archiviert)", "Skalen" to "${p.scales}",
        "Fotos und Aufnahmen" to "${p.assets} Dateien, ${p.assetLinks} Zuordnungen", "Ziele" to "${p.goals}", "Abgehakte Tage" to "${p.goalChecks}",
        "Wichtige Tage" to "${p.importantDays}", "Vorlagen" to "${p.templates}", "Erinnerungen" to "${p.reminders}", "Zeitraum" to "${p.earliestDate ?: "–"} bis ${p.latestDate ?: "–"}")
    Column { for ((a, b) in rows) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(a, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(b, style = MaterialTheme.typography.bodyMedium) } }
}

/** Sichern, Wiederherstellen, Import und Export. */
@Composable
fun DataScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf<Opened?>(null) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }
    val stamp = env.today.toString()

    fun run(label: String, block: suspend () -> String) {
        busy = label; message = ""
        scope.launch {
            message = try { block() } catch (e: ImportException) { e.message ?: "Die Datei konnte nicht gelesen werden." } catch (e: Exception) { "Das hat nicht geklappt. Es wurde nichts verändert." }
            busy = ""
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null && c != null) run("Sicherung wird geschrieben …") { val r = c.dataOps.writeBackup(uri); "Sicherung gespeichert: ${r.assetsWritten} Dateien enthalten" + if (r.assetsMissing > 0) ", ${r.assetsMissing} fehlten auf dem Gerät." else "." }
    }
    val savePhotos = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null && c != null) run("Fotos werden gepackt …") { "${plural(c.dataOps.writePhotos(uri), "Foto", "Fotos")} exportiert." }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && c != null) run("Datei wird gelesen …") { opened = c.dataOps.open(uri); "" }
    }
    fun share(name: String, mime: String, write: suspend (java.io.OutputStream) -> Unit) {
        if (c == null) return
        run("Export wird erstellt …") { val f = File(shareDir(context), name); f.outputStream().use { write(it) }; shareFile(context, f, mime, "Export teilen"); "" }
    }

    SubScreen("Daten", nav.back) {
        if (busy.isNotEmpty()) Panel(padding = 14.dp) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp); Text(busy) } }
        if (message.isNotEmpty()) Panel(padding = 14.dp) { Text(message) }
        SectionLabel("Sichern und wiederherstellen")
        RowGroup {
            SettingRow("Sicherung erstellen", "Eine Datei mit allen Einträgen, Einstellungen, Fotos und Aufnahmen", Icons.Rounded.Save, onClick = { saveBackup.launch("tagebuch-$stamp.tagebuch") })
            RowDivider()
            SettingRow("Wiederherstellen oder importieren", "Eigene Sicherung oder Backup im Daylio-Format (.daylio). CSV geht auch, bringt aber nur Einträge ohne Gruppen, Farben und Fotos mit.", Icons.Rounded.Restore, onClick = { openFile.launch(arrayOf("*/*")) })
        }
        Text("Fotos und Sprachnotizen liegen nur auf diesem Gerät. Erstelle regelmäßig eine Sicherung und bewahre sie an einem sicheren Ort auf. Die Datei ist nicht verschlüsselt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("Export")
        RowGroup {
            SettingRow("Als PDF", "Alle Einträge zum Lesen und Drucken", Icons.Rounded.PictureAsPdf, onClick = { share("tagebuch-$stamp.pdf", "application/pdf") { c!!.dataOps.writePdf(it) } })
            RowDivider()
            SettingRow("Als CSV", "Tabelle mit Datum, Stimmung, Aktivitäten, Skalen und Notiz", Icons.Rounded.TableChart, onClick = { share("tagebuch-$stamp.csv", "text/csv") { c!!.dataOps.writeCsv(it) } })
            RowDivider()
            SettingRow("Fotos exportieren", "Alle Fotos als ZIP-Datei", Icons.Rounded.PhotoLibrary, onClick = { savePhotos.launch("tagebuch-fotos-$stamp.zip") })
        }
        Text("Exportierte Dateien enthalten dein Tagebuch im Klartext. Überlege, mit wem und worüber du sie teilst.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    when (val o = opened) {
        is Opened.Archive -> {
            val empty = env.diary.entries.isEmpty()
            var mode by remember(o) { mutableStateOf(if (empty) MergeMode.EMPTY_ONLY else MergeMode.MERGE) }
            var replace by remember(o) { mutableStateOf(false) }
            var progress by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { if (busy.isEmpty()) opened = null }, title = { Text(o.kind) },
                text = { Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Das steckt in der Datei:", style = MaterialTheme.typography.labelLarge)
                    PreviewTable(o.result.preview)
                    if (o.result.warnings.isNotEmpty()) { Text("Hinweise (${o.result.warnings.size})", style = MaterialTheme.typography.labelLarge); for (w in o.result.warnings) Text("• $w", style = MaterialTheme.typography.bodySmall) }
                    Text("Wie importieren?", style = MaterialTheme.typography.labelLarge)
                    if (empty) Row { RadioButton(true, null); Column { Text("In ein leeres Tagebuch"); Text("Übernimmt auch die Einrichtung der Datei.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    else {
                        Row { RadioButton(!replace, { replace = false; mode = MergeMode.MERGE }); Column { Text("Mit dem bestehenden zusammenführen"); Text("Gleichnamige Stimmungen, Aktivitäten und Skalen werden zugeordnet. Einträge, die schon aus derselben Datei stammen, entstehen nicht doppelt. Einträge aus einem früheren CSV-Import dagegen schon.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        Row { RadioButton(replace, { replace = true; mode = MergeMode.EMPTY_ONLY }); Column { Text("Bestehendes Tagebuch ersetzen"); Text("Löscht vorher alle ${env.diary.entries.size} Einträge, Fotos und die Einrichtung auf diesem Gerät und übernimmt dann genau den Inhalt der Datei. Richtig, wenn ein früherer Import fehlerhaft war.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
                    }
                    if (progress.isNotEmpty()) Text(progress)
                    if (o.kind.contains("Daylio")) {
                        HorizontalDivider()
                        Text("Stimmt nach dem Import etwas nicht, etwa Farben oder Stufen? Der Strukturbericht hilft bei der Fehlersuche. Er enthält nur Feldnamen und Zahlen, keine Namen, Notizen oder Titel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton({ scope.launch { runCatching { shareFile(context, c!!.dataOps.structureReport(o), "text/plain", "Strukturbericht teilen") } } }, enabled = c != null) { Text("Strukturbericht teilen") }
                    }
                } },
                confirmButton = { TextButton({
                    busy = "Import läuft …"
                    scope.launch {
                        try { outcome = c!!.dataOps.importArchive(o, mode, replace) { done, total -> progress = "Dateien: $done von $total" }; opened = null }
                        catch (e: ImportException) { message = e.message ?: "Import fehlgeschlagen."; opened = null }
                        catch (e: Exception) { message = "Der Import wurde unterbrochen. Starte ihn einfach noch einmal, es entsteht nichts doppelt."; opened = null }
                        busy = ""
                    }
                }, enabled = busy.isEmpty() && c != null) { Text(if (replace) "Ersetzen und importieren" else "Importieren") } },
                dismissButton = { TextButton({ opened = null }, enabled = busy.isEmpty()) { Text("Abbrechen") } })
        }
        is Opened.Csv -> {
            val i = o.import
            val levels = remember(o) { mutableStateMapOf<String, Int>().apply { i.unknownMoods.forEach { put(it, de.mediclever.tagebuch.core.importer.CsvFormat.suggestedMoodLevel(it)) } } }
            AlertDialog(onDismissRequest = { opened = null }, title = { Text("CSV-Datei") },
                text = { Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${plural(i.rows.size, "Eintrag", "Einträge")} gefunden. Eine CSV-Datei enthält weder Gruppen noch Farben, Symbole, Fotos oder Ziele. Für eine vollständige Übernahme nimm die Backup-Datei (.daylio).")
                    val unknown = i.unknownMoods.size + i.unknownActivities.size + i.unknownScales.size
                    if (unknown > 0) {
                        Text("Diese Dinge gibt es in deinem Tagebuch noch nicht. Beim Import werden sie angelegt:", style = MaterialTheme.typography.labelLarge)
                        if (i.unknownMoods.isNotEmpty()) {
                            Text("Eine CSV-Datei sagt nicht, wie gut oder schlecht eine Stimmung ist. Ordne bitte jede Stimmung einer Stufe zu, von der besten (links) bis zur schlechtesten (rechts):", style = MaterialTheme.typography.bodySmall)
                            for (name in i.unknownMoods) Column {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (l in 1..5) FilterChip(levels[name] == l, { levels[name] = l }, label = { de.mediclever.tagebuch.ui.icons.MoodFace(l, "a", 26.dp) }, modifier = Modifier.height(48.dp).semantics { contentDescription = "$name: " + de.mediclever.tagebuch.ui.home.levelName(l) })
                                }
                            }
                        }
                        if (i.unknownActivities.isNotEmpty()) Text("Aktivitäten: " + i.unknownActivities.joinToString(), style = MaterialTheme.typography.bodySmall)
                        if (i.unknownScales.isNotEmpty()) Text("Skalen: " + i.unknownScales.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
                    }
                    for (w in i.warnings) Text("• $w", style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { TextButton({ opened = null; if (c != null) run("Import läuft …") { outcome = c.dataOps.importCsv(o, levels.toMap()); "" } }, enabled = i.rows.isNotEmpty()) { Text(if (i.unknownMoods.size + i.unknownActivities.size + i.unknownScales.size > 0) "Anlegen und importieren" else "Importieren") } },
                dismissButton = { TextButton({ opened = null }) { Text("Abbrechen") } })
        }
        null -> {}
    }
    outcome?.let { r ->
        AlertDialog(onDismissRequest = { outcome = null }, title = { Text("Import abgeschlossen") }, confirmButton = { TextButton({ outcome = null }) { Text("Fertig") } },
            text = { Column(Modifier.verticalScrollCompat()) {
                Row(Modifier.fillMaxWidth()) { Text("", Modifier.weight(1f)); for (h in listOf("neu", "schon da", "Fehler")) Text(h, Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium) }
                for (k in ImportKind.entries) { val x = r.report[k]; if (x.taken + x.skipped + x.errors > 0) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(k.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); for (n in listOf(x.taken, x.skipped, x.errors)) Text("$n", Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium) } }
                if (r.filesCopied + r.filesFailed > 0) Text("Dateien: ${r.filesCopied} kopiert, ${r.filesFailed} fehlgeschlagen", Modifier.padding(top = 8.dp))
            } })
    }
}

/** Konto: anmelden, abgleichen, abmelden, alles loeschen. */
@Composable
fun AccountScreen() {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val account = c?.auth?.account?.collectAsStateWithLifecycle()?.value
    val syncing = c?.sync?.running?.collectAsStateWithLifecycle()?.value ?: false
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var decision by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLocalDelete by remember { mutableStateOf(false) }
    fun run(block: suspend () -> String) { busy = true; message = ""; scope.launch { message = try { block() } catch (e: CloudException) { e.message ?: "" } catch (e: Exception) { "Das hat nicht geklappt." }; busy = false } }
    LaunchedEffect(account?.uid) { decision = c?.sync?.needsDecision() == true }

    SubScreen("MediClever-Konto", nav.back) {
        if (c == null || !c.auth.available) {
            Panel(padding = 14.dp) {
                Text("Konto und Abgleich sind in dieser Version noch nicht eingerichtet.", style = MaterialTheme.typography.titleMedium)
                Text("Dein Tagebuch liegt vollständig auf diesem Gerät. Über „Daten“ kannst du es sichern und auf ein anderes Gerät übertragen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (account == null) {
            Text("Mit einem Konto wird dein Tagebuch zwischen deinen Geräten abgeglichen und später auch auf mediclever.de erreichbar. Ohne Konto bleibt alles nur auf diesem Gerät.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(email, { email = it }, label = { Text("E-Mail-Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button({ run { c.auth.signIn(email, password); "" } }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy && email.isNotBlank() && password.isNotEmpty()) { Text("Anmelden") }
            OutlinedButton({ run { c.auth.register(email, password); "Konto angelegt. Wir haben dir eine E-Mail zur Bestätigung geschickt." } }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy && email.isNotBlank() && password.length >= 8) { Text("Neues Konto anlegen") }
            OutlinedButton({ run { c.auth.signInWithGoogle(context); "" } }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy) { Text("Mit Google anmelden") }
            TextButton({ run { c.auth.sendPasswordReset(email); "Wenn es zu dieser Adresse ein Konto gibt, ist eine E-Mail zum Zurücksetzen unterwegs." } }, enabled = !busy && email.isNotBlank()) { Text("Passwort vergessen?") }
            Text("Für ein neues Konto braucht das Passwort mindestens 8 Zeichen. Tagebuchtexte, Stimmungen und Einstellungen werden bei Google Firebase in der EU gespeichert. Fotos und Sprachnotizen bleiben auf dem Gerät.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Panel(padding = 14.dp) {
                Text("Angemeldet als", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(account.email.ifBlank { "Konto ohne E-Mail-Adresse" }, style = MaterialTheme.typography.titleMedium)
                val last = env.local.lastSyncAt
                Text(when { syncing -> "Abgleich läuft …"; env.local.lastSyncError.isNotEmpty() -> "Letzter Versuch: ${env.local.lastSyncError}"; last > 0 -> "Zuletzt abgeglichen: " + Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")) + " Uhr"; else -> "Noch nicht abgeglichen" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button({ run { c.sync.syncNow()?.let { "$it. Deine Änderungen bleiben gespeichert und werden beim nächsten Mal übertragen." } ?: "Alles ist abgeglichen." } }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy && !syncing && !decision) { Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(8.dp)); Text("Jetzt abgleichen") }
            OutlinedButton({ run { c.auth.signOut(); "Abgemeldet. Dein Tagebuch bleibt auf diesem Gerät." } }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy) { Text("Abmelden") }
        }
        if (message.isNotEmpty()) Panel(padding = 14.dp) { Text(message) }
        SectionLabel("Deine Rechte")
        RowGroup {
            SettingRow("Alles exportieren", "Vollständige Kopie deiner Daten", Icons.Rounded.Download, onClick = { nav.go(Routes.DATA) })
            RowDivider()
            if (account != null) SettingRow("Konto und alle Daten löschen", "Entfernt dein Tagebuch vom Server und von diesem Gerät", Icons.Rounded.DeleteForever, iconTint = MaterialTheme.colorScheme.error, onClick = { confirmDelete = true })
            else SettingRow("Alle Daten auf diesem Gerät löschen", "Entfernt Einträge, Fotos, Einstellungen und die Einwilligung", Icons.Rounded.DeleteForever, iconTint = MaterialTheme.colorScheme.error, onClick = { confirmLocalDelete = true })
        }
        Text("App-Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (decision && c != null) AlertDialog(onDismissRequest = {}, title = { Text("Anderes Konto") },
        text = { Text("Auf diesem Gerät liegt ein Tagebuch, das zu einem anderen Konto gehört. Was soll damit geschehen?") },
        confirmButton = { TextButton({ decision = false; run { c.sync.adoptLocalData(); "Das Tagebuch dieses Geräts wurde in dein Konto übernommen." } }) { Text("In dieses Konto übernehmen") } },
        dismissButton = { Column { TextButton({ decision = false; run { c.sync.replaceLocalData(); "Das Gerät zeigt jetzt das Tagebuch deines Kontos." } }) { Text("Gerät leeren, Konto laden") }; TextButton({ decision = false; run { c.auth.signOut(); "Abgemeldet." } }) { Text("Abmelden") } } })
    if (confirmDelete && c != null) ConfirmDialog("Konto und alle Daten löschen?", "Dein Tagebuch wird vom Server und von diesem Gerät entfernt, samt Fotos und Aufnahmen. Das lässt sich nicht rückgängig machen. Erstelle vorher eine Sicherung, wenn du etwas behalten möchtest.", "Endgültig löschen", destructive = true,
        onConfirm = { confirmDelete = false; run { c.sync.deleteEverything(); "Dein Konto und alle Daten wurden gelöscht." } }, onDismiss = { confirmDelete = false }, alternative = "Erst sichern", onAlternative = { confirmDelete = false; nav.go(Routes.DATA) })
    if (confirmLocalDelete) ConfirmDialog("Alle Daten auf diesem Gerät löschen?", "Einträge, Fotos, Aufnahmen und Einstellungen werden entfernt. Das lässt sich nicht rückgängig machen.", "Endgültig löschen", destructive = true,
        onConfirm = { confirmLocalDelete = false; env.act { wipeLocal() } }, onDismiss = { confirmLocalDelete = false }, alternative = "Erst sichern", onAlternative = { confirmLocalDelete = false; nav.go(Routes.DATA) })
}

@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    SubScreen("Über die App", nav.back) {
        Panel(title = "MediClever Tagebuch", padding = 14.dp) {
            Text("Ein Stimmungs- und Aktivitäten-Tagebuch, das du selbst einrichtest. Version ${BuildConfig.VERSION_NAME}.")
            Text("Das Tagebuch ist eine Hilfe zur Selbstbeobachtung. Es stellt keine Diagnosen und ersetzt keine ärztliche oder therapeutische Beratung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel(title = "Wenn es dir sehr schlecht geht", padding = 14.dp) {
            Text("Sprich mit jemandem. Die TelefonSeelsorge ist rund um die Uhr kostenlos erreichbar: 0800 111 0 111 oder 0800 111 0 222. In akuter Gefahr wähle den Notruf 112.")
        }
        Panel(title = "Datenschutz in Kürze", padding = 14.dp) {
            Text("• Dein Tagebuch liegt auf diesem Gerät. Nur wenn du dich anmeldest, werden Texte, Stimmungen und Einstellungen zusätzlich bei Google Firebase in der EU gespeichert.\n• Fotos und Sprachnotizen verlassen das Gerät nicht. Aufnahmeort und Gerätedaten werden aus Fotos entfernt.\n• Es gibt keine Werbung und keine Nutzungsanalyse.\n• Benachrichtigungen enthalten nie Inhalte aus deinem Tagebuch.\n• Du kannst alles exportieren und alles löschen.")
            Text("Bitte trage keine Patienten- und Einsatzdaten ein.", style = MaterialTheme.typography.titleSmall)
        }
        Panel(title = "Lizenzen", padding = 14.dp) {
            Text("Symbole: Material Icons von Google, Apache-Lizenz 2.0. Schrift: Roboto, Apache-Lizenz 2.0. Stimmungssymbole, Texte und Gestaltung: eigene Arbeit von MediClever.\nVerwendete Bibliotheken: AndroidX und Jetpack Compose, Room, Coil, Reorderable, Kotlin-Bibliotheken, Firebase (alle Apache-Lizenz 2.0).", style = MaterialTheme.typography.bodyMedium)
        }
        Text("MediClever by Drexlerfotografie Lukas Drexler", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
