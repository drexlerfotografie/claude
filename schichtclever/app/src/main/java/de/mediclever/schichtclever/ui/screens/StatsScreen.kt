package de.mediclever.schichtclever.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Catalog
import de.mediclever.schichtclever.data.Export
import de.mediclever.schichtclever.data.Period
import de.mediclever.schichtclever.data.stats
import de.mediclever.schichtclever.ui.components.BarChart
import de.mediclever.schichtclever.ui.components.McCard
import de.mediclever.schichtclever.ui.components.SectionLabel
import de.mediclever.schichtclever.ui.components.ShareBar
import de.mediclever.schichtclever.ui.components.StatTile
import de.mediclever.schichtclever.ui.theme.LocalExtra

@Composable
fun StatsScreen(data: AppData) {
    var period by rememberSaveable { mutableStateOf(Period.MONAT) }
    val s = remember(data, period) { data.stats(period) }
    val context = LocalContext.current
    val role = data.settings.role

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Statistik", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { Export.share(context, data) }) { Icon(Icons.Outlined.IosShare, "Exportieren", tint = LocalExtra.current.accent) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Period.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p, onClick = { period = p },
                    shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size, MaterialTheme.shapes.small),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary, activeContentColor = Color.White),
                    icon = {},
                ) { Text(p.label) }
            }
        }

        McCard(Modifier.fillMaxWidth().padding(top = 12.dp), accent = true) {
            Text(role.einsatzWord(true).uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.einsaetze}", style = MaterialTheme.typography.displayLarge, modifier = Modifier.weight(1f))
                Text("in ${s.shifts} Schichten", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("%.0f".format(s.hours), "Stunden", Modifier.weight(1f))
            StatTile(if (s.shifts == 0) "–" else "%.1f".format(s.einsaetze.toDouble() / s.shifts), "Ø pro Schicht", Modifier.weight(1f))
            StatTile("${s.measures}", "Maßnahmen", Modifier.weight(1f))
        }

        SectionLabel("${role.einsatzWord(true)} nach Uhrzeit", trailing = s.peakHour?.let { "Peak %02d–%02d Uhr".format(it, (it + 1) % 24) })
        McCard(Modifier.fillMaxWidth()) {
            BarChart(
                values = s.byHour.toList(),
                labels = (0..23).map { if (it % 6 == 0) "$it" else "" },
                height = 130.dp,
            )
            if (s.einsaetze > 0 && s.peakHour != null) {
                val night = (0..5).sumOf { s.byHour[it] } + (22..23).sumOf { s.byHour[it] }
                Text(
                    "${(night * 100 / s.einsaetze)} % deiner ${role.einsatzWord(true)} fallen zwischen 22 und 06 Uhr.",
                    style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        if (s.byCategory.isNotEmpty()) {
            SectionLabel("Kategorien")
            McCard(Modifier.fillMaxWidth()) { s.byCategory.forEach { (c, n) -> ShareBar(c.label, n, s.einsaetze) } }
        }

        if (s.avgPerShiftType.isNotEmpty()) {
            SectionLabel("Ø ${role.einsatzWord(true)} pro Schichttyp")
            McCard(Modifier.fillMaxWidth()) {
                BarChart(
                    values = s.avgPerShiftType.map { (it.second * 10).toInt() },
                    labels = s.avgPerShiftType.map { it.first.label },
                    height = 90.dp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    s.avgPerShiftType.forEach { Text("%.1f".format(it.second), Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        if (s.topMeasures.isNotEmpty()) {
            SectionLabel("Häufigste Maßnahmen")
            McCard(Modifier.fillMaxWidth()) {
                s.topMeasures.forEach { (id, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(Catalog.byId(id, data.settings.customSkills)?.name ?: id, Modifier.weight(1f))
                        Text("${n}×", fontWeight = FontWeight.Bold, color = LocalExtra.current.accent)
                    }
                }
            }
        }

        if (s.nightStreak >= 2) {
            SectionLabel("Nachtschicht-Serie")
            McCard(Modifier.fillMaxWidth()) {
                Text("${s.nightStreak} Nächte am Stück", style = MaterialTheme.typography.headlineSmall)
                Text("Nach der Serie helfen zwei freie Tage beim Umstellen.", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
        }
        if (s.einsaetze == 0) {
            Text("Für diesen Zeitraum gibt es noch keine Daten.", color = LocalExtra.current.muted, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
