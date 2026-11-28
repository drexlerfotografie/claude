package de.mediclever.schichtclever.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mediclever.schichtclever.R
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.FilterSchedule
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.focus.FocusController
import de.mediclever.schichtclever.focus.FocusService
import de.mediclever.schichtclever.tiles.EinsatzTileService
import de.mediclever.schichtclever.tiles.FocusTileService
import de.mediclever.schichtclever.tiles.ShiftTileService
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.McChip
import de.mediclever.schichtclever.ui.components.SectionLabel
import de.mediclever.schichtclever.ui.components.SettingRow
import de.mediclever.schichtclever.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Zählt bei jedem Zurückkehren in die App hoch, damit Berechtigungen neu geprüft werden. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return tick
}

fun Context.openDndSettings() = startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
fun Context.openUsageSettings() = startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
fun Context.openOverlaySettings() = startActivity(
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

/** Android 13+: Kachel direkt per Systemdialog zu den Schnelleinstellungen hinzufügen lassen. */
fun Context.requestAddTile(cls: Class<*>, label: String, icon: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(this, cls), label, AndroidIcon.createWithResource(this, icon), mainExecutor,
    ) { }
}

@Composable
fun PermissionCard(title: String, text: String, granted: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onGrant: () -> Unit) {
    McCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), accent = !granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalExtra.current.accent)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) Icon(Icons.Outlined.CheckCircle, "erteilt", tint = LocalExtra.current.success)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 6.dp))
        if (!granted) OutlinedButton(onClick = onGrant, modifier = Modifier.padding(top = 8.dp), shape = MaterialTheme.shapes.small) { Text("Freigeben") }
    }
}

private data class LaunchableApp(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun FocusScreen(data: AppData, onBack: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val dndOk = remember(tick) { FocusController.hasDndAccess(context) }
    val usageOk = remember(tick) { FocusController.hasUsageAccess(context) }
    val overlayOk = remember(tick) { FocusController.canOverlay(context) }
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by remember { mutableStateOf("") }
    val s = data.settings

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { ri ->
                    LaunchableApp(
                        ri.activityInfo.packageName,
                        ri.loadLabel(pm).toString(),
                        runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    SubScreen("Fokus-Modus", onBack) {
        McCard(Modifier.fillMaxWidth(), accent = data.focusActive) {
            SettingRow(
                "Fokus jetzt",
                if (data.activeShift != null) "gilt für die laufende Schicht" else "unabhängig von einer Schicht",
                checked = data.focusActive, onCheckedChange = { Repository.setShiftFocus(it) },
            )
        }
        SectionLabel("Automatik")
        McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            SettingRow("Mit Schichtbeginn starten", "Endet automatisch mit der Schicht", Icons.Outlined.Schedule,
                s.focusAutoWithShift, { v -> Repository.settings { it.copy(focusAutoWithShift = v) } })
            HorizontalDivider(color = LocalExtra.current.line)
            SettingRow("Bitte nicht stören", "Anrufe von Favoriten und wiederholte Anrufe kommen je nach System-Einstellung durch", Icons.Outlined.DoNotDisturbOn,
                s.dndEnabled, { v -> Repository.settings { it.copy(dndEnabled = v) } })
        }
        if (!dndOk || !usageOk || !overlayOk) SectionLabel("Fehlende Freigaben")
        if (!dndOk) PermissionCard("Bitte nicht stören", "Damit SchichtClever den Modus ein- und ausschalten darf.", false, Icons.Outlined.DoNotDisturbOn) { context.openDndSettings() }
        if (!usageOk) PermissionCard(
            "Nutzungszugriff",
            "In der Liste SchichtClever wählen und erlauben. Damit erkennt die App, welche App gerade geöffnet ist. Inhalte werden nicht gelesen.",
            false, Icons.Outlined.QueryStats,
        ) { context.openUsageSettings() }
        if (!overlayOk) PermissionCard(
            "Über anderen Apps einblenden",
            "Damit die Sperrseite erscheinen kann, wenn du eine pausierte App öffnest.",
            false, Icons.Outlined.Layers,
        ) { context.openOverlaySettings() }

        SectionLabel("Apps pausieren", trailing = "${s.blockedApps.size} pausiert")
        Text(
            "Alarmierungs-, Navigations- und Telefon-Apps solltest du nie pausieren.",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("App suchen …") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = MaterialTheme.shapes.small)
        val list = apps
        if (list == null) Text("Apps werden geladen …", color = LocalExtra.current.muted)
        else McCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            list.filter { query.isBlank() || it.label.contains(query.trim(), true) }
                // Bereits pausierte Apps oben anzeigen
                .sortedByDescending { it.pkg in s.blockedApps }
                .forEachIndexed { i, app ->
                    if (i > 0) HorizontalDivider(color = LocalExtra.current.line)
                    val blocked = app.pkg in s.blockedApps
                    SettingRow(
                        app.label, if (blocked) "pausiert" else "erlaubt",
                        checked = blocked,
                        onCheckedChange = { v -> Repository.settings { it.copy(blockedApps = if (v) it.blockedApps + app.pkg else it.blockedApps - app.pkg) } },
                        trailing = {
                            app.icon?.let { Image(it, null, Modifier.size(28.dp)) }
                        },
                    )
                }
        }
    }
}

@Composable
fun FilterScreen(data: AppData, onBack: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val overlayOk = remember(tick) { FocusController.canOverlay(context) }
    val s = data.settings
    val active = FocusController.filterActive(data)

    SubScreen("Blaufilter & Nacht", onBack) {
        Row(Modifier.fillMaxWidth().height(110.dp)) {
            PreviewHalf("Ohne Filter", Color.Transparent, Modifier.weight(1f))
            PreviewHalf("Mit Filter", Color(FocusService.previewArgb(s.filterIntensity, s.filterDim)), Modifier.weight(1f))
        }
        if (!overlayOk) {
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                "Über anderen Apps einblenden",
                "Der Blaufilter liegt als halbtransparente Schicht über dem Bildschirm. Dafür braucht SchichtClever diese Freigabe.",
                false, Icons.Outlined.Layers,
            ) { context.openOverlaySettings() }
        }
        Spacer(Modifier.height(12.dp))
        McCard(Modifier.fillMaxWidth(), accent = active, padding = 0.dp) {
            SettingRow("Manuell einschalten", if (active) "Filter ist gerade aktiv" else "Filter ist gerade aus, läuft sonst nach Zeitplan",
                checked = s.filterManualOn,
                onCheckedChange = { v -> Repository.settings { it.copy(filterManualOn = v) } })
        }
        SectionLabel("Intensität", trailing = "${(s.filterIntensity * 100).toInt()} %")
        Slider(
            value = s.filterIntensity, onValueChange = { v -> Repository.settings { it.copy(filterIntensity = v) } },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
        SectionLabel("Zusätzlich abdunkeln", trailing = "${(s.filterDim * 100).toInt()} %")
        Slider(
            value = s.filterDim, onValueChange = { v -> Repository.settings { it.copy(filterDim = v) } },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
        )
        SectionLabel("Zeitplan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterSchedule.entries.forEach { f -> McChip(f.label, s.filterSchedule == f, { Repository.settings { it.copy(filterSchedule = f) } }) }
        }
        Row(Modifier.padding(top = 16.dp)) {
            Icon(Icons.Outlined.Info, null, tint = LocalExtra.current.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Das Android-Nachtlicht kann zusätzlich laufen. Beide gleichzeitig auf voller Stärke machen Farben auf dem Monitor-EKG schwer lesbar.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }
    }
}

@Composable
private fun PreviewHalf(label: String, overlay: Color, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF5A606B))
            Text("02:31 · Einsatz", style = MaterialTheme.typography.titleLarge, color = Color(0xFF11202C))
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).background(Color(0xFFB9303F)))
        }
        Box(Modifier.fillMaxSize().background(overlay))
    }
}

@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), content = content)
    }
}

/** Abschnitt zum Hinzufügen der Kacheln (Onboarding und Einstellungen). */
@Composable
fun TilesSection() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { context.requestAddTile(ShiftTileService::class.java, "Schicht", R.drawable.ic_tile_shift) }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("Schicht") }
            OutlinedButton(onClick = { context.requestAddTile(EinsatzTileService::class.java, "+ Einsatz", R.drawable.ic_tile_einsatz) }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("Einsatz") }
            OutlinedButton(onClick = { context.requestAddTile(FocusTileService::class.java, "Fokus", R.drawable.ic_tile_focus) }, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) { Text("Fokus") }
        }
    } else {
        Text(
            "Zieh die Schnelleinstellungen herunter, tippe auf den Stift und zieh die SchichtClever-Kacheln nach oben.",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
        )
    }
}
