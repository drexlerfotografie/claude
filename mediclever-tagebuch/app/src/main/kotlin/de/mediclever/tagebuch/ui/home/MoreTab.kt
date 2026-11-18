package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.ui.*

/** Bereich "Mehr": alles ausser Eintraegen, Statistiken und Kalender. */
@Composable
fun MoreTab() {
    val nav = LocalNav.current
    val d = LocalEnv.current.diary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowGroup {
            SettingRow("Kalender", "Der Monat mit Tageswert und Dienstart", Icons.Rounded.CalendarMonth, onClick = { nav.go(Routes.section(2)) })
            RowDivider()
            SettingRow("Ziele", if (d.goals.none { it.archivedAt == null }) "Regelmäßig dranbleiben" else plural(d.goals.count { it.archivedAt == null }, "aktives Ziel", "aktive Ziele"), Icons.Rounded.Flag, onClick = { nav.go(Routes.GOALS) })
            RowDivider()
            SettingRow("Berichte", "Wochen und Monate im Überblick", Icons.Rounded.Assessment, onClick = { nav.go(Routes.REPORTS) })
            RowDivider()
            SettingRow("Rückblick", "Dieser Tag in früheren Jahren", Icons.Rounded.History, onClick = { nav.go(Routes.MEMORIES) })
            RowDivider()
            SettingRow("Wichtige Tage", "Jahrestage und Ereignisse", Icons.Rounded.Event, onClick = { nav.go(Routes.DAYS) })
            RowDivider()
            SettingRow("Favoriten", plural(d.entries.count { it.favorite }, "markierter Eintrag", "markierte Einträge"), Icons.Rounded.Star, onClick = { nav.go(Routes.FAVORITES) })
            RowDivider()
            SettingRow("Fotos", "Alle Fotos nach Monat", Icons.Rounded.PhotoLibrary, onClick = { nav.go(Routes.PHOTOS) })
            RowDivider()
            SettingRow("Erfolge", "Was du schon geschafft hast", Icons.Rounded.EmojiEvents, onClick = { nav.go(Routes.ACHIEVEMENTS) })
        }
        SectionLabel("Dein Tagebuch")
        RowGroup {
            SettingRow("Dienstarten", "Welche Dienste es bei dir gibt", Icons.Rounded.WorkHistory, onClick = { nav.go(Routes.SHIFTS) })
            RowDivider()
            SettingRow("Einrichtung", "Stimmungen, Aktivitäten, Skalen, Vorlagen", Icons.Rounded.Tune, onClick = { nav.go(Routes.setup()) })
            RowDivider()
            SettingRow("Erinnerungen", "Uhrzeiten mit eigenem Text", Icons.Rounded.Notifications, onClick = { nav.go(Routes.REMINDERS) })
            RowDivider()
            SettingRow("Aussehen", "Farben, Schrift, Hell und Dunkel", Icons.Rounded.Palette, onClick = { nav.go(Routes.APPEARANCE) })
            RowDivider()
            SettingRow("Sicherheit", "PIN-Sperre", Icons.Rounded.Lock, onClick = { nav.go(Routes.SECURITY) })
            RowDivider()
            SettingRow("Einstellungen", "Wochenanfang, Mitternacht, Rückblick", Icons.Rounded.Settings, onClick = { nav.go(Routes.SETTINGS) })
        }
        SectionLabel("Daten und Konto")
        RowGroup {
            SettingRow("Daten", "Sichern, wiederherstellen, Import, Export", Icons.Rounded.Storage, onClick = { nav.go(Routes.DATA) })
            RowDivider()
            SettingRow("MediClever-Konto", "Anmelden und zwischen Geräten abgleichen", Icons.Rounded.AccountCircle, onClick = { nav.go(Routes.ACCOUNT) })
            RowDivider()
            SettingRow("Über die App", "Datenschutz, Lizenzen, Version", Icons.Rounded.Info, onClick = { nav.go(Routes.ABOUT) })
        }
        Spacer(Modifier.height(24.dp))
    }
}
