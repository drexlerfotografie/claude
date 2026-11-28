package de.mediclever.schichtclever.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Export
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.Role
import de.mediclever.schichtclever.data.ThemeMode
import de.mediclever.schichtclever.focus.FocusController
import de.mediclever.schichtclever.ui.components.Cleverle
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.McChip
import de.mediclever.schichtclever.ui.components.SectionLabel
import de.mediclever.schichtclever.ui.components.SettingRow
import de.mediclever.schichtclever.ui.theme.LocalExtra

@Composable
fun MoreScreen(data: AppData, dark: Boolean, onOpenFocus: () -> Unit, onOpenFilter: () -> Unit) {
    val context = LocalContext.current
    val s = data.settings
    var confirmDelete by remember { mutableStateOf(false) }
    var pickRole by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Mehr", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        McCard(Modifier.fillMaxWidth(), accent = true, padding = 0.dp) {
            SettingRow(s.role.label, "Bestimmt Maßnahmen-Katalog und Begriffe", Icons.Outlined.Person, onClick = { pickRole = true },
                trailing = { Text("ÄNDERN", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.accent) })
        }

        SectionLabel("Design")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { t -> McChip(t.label, s.theme == t, { Repository.settings { it.copy(theme = t) } }) }
        }
        if (s.theme == ThemeMode.AUTO_SCHICHT) {
            Text("Nacht-, 12-h-Nacht- und 24-h-Schichten dunkel, Tagschichten hell. Ohne Schicht wie das System.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 4.dp))
        }

        SectionLabel("Schicht-Werkzeuge")
        McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            SettingRow("Fokus-Modus", "${s.blockedApps.size} Apps · " + if (s.dndEnabled) "Bitte nicht stören" else "ohne Bitte nicht stören",
                Icons.Outlined.Block, onClick = onOpenFocus, trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = LocalExtra.current.muted) })
            HorizontalDivider(color = LocalExtra.current.line)
            SettingRow("Blaufilter & Nacht", "${s.filterSchedule.label} · ${(s.filterIntensity * 100).toInt()} %",
                Icons.Outlined.Contrast, onClick = onOpenFilter, trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = LocalExtra.current.muted) })
        }
        SectionLabel("Kacheln für die Schnelleinstellungen")
        TilesSection()

        SectionLabel("Daten")
        McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            SettingRow("Exportieren (CSV)", "Einsätze, Schichten und Übungen, z. B. für Nachweise oder Logbuch",
                Icons.Outlined.IosShare, onClick = { Export.share(context, data) })
            HorizontalDivider(color = LocalExtra.current.line)
            SettingRow("Alle Daten löschen", "${data.shifts.size} Schichten, ${data.einsaetze.size} Einträge, ${data.practices.size} Übungen",
                Icons.Outlined.DeleteForever, onClick = { confirmDelete = true })
        }
        McCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = LocalExtra.current.success)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Alle Daten nur auf diesem Gerät", style = MaterialTheme.typography.titleSmall)
                    Text("Keine Cloud, kein Konto, keine Internet-Berechtigung. Nur du entscheidest, was du exportierst.",
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Cleverle(Modifier.size(24.dp, 28.dp), dark)
            Spacer(Modifier.width(8.dp))
            Text("Teil von ", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            Text("Medi", style = MaterialTheme.typography.bodySmall)
            Text("Clever", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.accent)
            Spacer(Modifier.weight(1f))
            Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
    }

    if (pickRole) {
        AlertDialog(
            onDismissRequest = { pickRole = false },
            title = { Text("Wo arbeitest du?") },
            text = {
                Column {
                    Role.entries.forEach { r ->
                        TextButton(onClick = { Repository.settings { it.copy(role = r) }; pickRole = false }) { Text(r.label) }
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Alle Daten löschen?") },
            text = { Text("Schichten, Einträge, Übungen und Ziele werden endgültig gelöscht. Einstellungen bleiben. Exportiere vorher, wenn du die Daten noch brauchst.") },
            confirmButton = { TextButton(onClick = { Repository.deleteAll(); confirmDelete = false }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun OnboardingScreen(data: AppData, dark: Boolean) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val tick = rememberResumeTick()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        if (step > 0) Text("SCHRITT ${step + 1} VON 3", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cleverle(Modifier.size(120.dp, 140.dp), dark)
                    Spacer(Modifier.height(24.dp))
                    Row {
                        Text("Schicht", style = MaterialTheme.typography.displayMedium)
                        Text("Clever", style = MaterialTheme.typography.displayMedium, color = LocalExtra.current.accent)
                    }
                    Text("EIN MEDICLEVER-PRODUKT", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
                    Spacer(Modifier.height(32.dp))
                    Text("Deine Schicht.\nDeine Skills.\nDein Fokus.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Einsätze mitzählen, Maßnahmen im Blick behalten und in der Nacht weniger abgelenkt sein. Alles bleibt auf deinem Handy.",
                        style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, textAlign = TextAlign.Center,
                    )
                }
                1 -> Column {
                    Text("Wo arbeitest du?", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("Danach richten sich Maßnahmen und Begriffe. Du kannst das jederzeit ändern.",
                        style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.padding(vertical = 12.dp))
                    Role.entries.forEach { r ->
                        McCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), accent = data.settings.role == r, onClick = { Repository.settings { it.copy(role = r) } }) {
                            Text(r.label, style = MaterialTheme.typography.titleMedium, color = if (data.settings.role == r) LocalExtra.current.accent else MaterialTheme.colorScheme.onSurface)
                            Text(
                                when (r) {
                                    Role.RETTUNGSDIENST -> "RS, NotSan: Einsätze mit Alarmzeit"
                                    Role.PFLEGE -> "Intensiv, ZNA, Station"
                                    Role.ARZT -> "Dienste, NEF, Prozeduren-Logbuch"
                                    Role.AUSBILDUNG -> "Praxiseinsätze, Nachweise, Skills"
                                },
                                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                            )
                        }
                    }
                }
                else -> Column {
                    val dndOk = remember(tick) { FocusController.hasDndAccess(context) }
                    val guardOk = remember(tick) { FocusController.isGuardEnabled(context) }
                    Text("Damit Fokus wirklich wirkt", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
                    Text("Diese Freigaben sind optional, du kannst sie auch später erteilen.",
                        style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, modifier = Modifier.padding(vertical = 12.dp))
                    PermissionCard("Bitte nicht stören", "Schaltet „Bitte nicht stören“ automatisch zu Schichtbeginn ein und zum Ende wieder aus.", dndOk, Icons.Outlined.DoNotDisturbOn) { context.openDndSettings() }
                    PermissionCard("Bedienungshilfe", "Erkennt, wenn du eine pausierte App öffnest, und legt den Blaufilter über den Bildschirm. Es werden keine Inhalte gelesen.", guardOk, Icons.Outlined.Accessibility) { context.openAccessibilitySettings() }
                    SectionLabel("Kacheln hinzufügen")
                    TilesSection()
                    Text("SchichtClever hat keine Internet-Berechtigung. Deine Daten bleiben auf dem Gerät.",
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
        Button(
            onClick = { if (step < 2) step++ else Repository.settings { it.copy(onboardingDone = true) } },
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.small,
        ) { Text(listOf("Los geht's", "Weiter", "Fertig")[step].uppercase(), style = MaterialTheme.typography.titleLarge) }
        if (step == 0) Text("Kein Konto nötig · Keine Cloud", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
    }
}
