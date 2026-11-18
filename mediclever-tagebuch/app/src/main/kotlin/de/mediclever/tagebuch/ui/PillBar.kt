package de.mediclever.tagebuch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.ui.theme.Brand
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors

/** Plaetze der Leiste. */
enum class PillTab { START, ENTRIES, STATS, MENU, NONE }

/**
 * Die Leiste am unteren Rand: Start, Eintraege, Einsprechen, Auswertung, Menue. Sie sieht auf
 * jeder Hauptseite gleich aus. Der Kalender oeffnet sich ueber die Monatsmatrix und das Menue.
 */
@Composable
fun PillBar(current: PillTab, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val dark = LocalDiaryColors.current.dark
    val pill = if (dark) Color(0xFF2A3947) else Brand.RichBlack
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp, top = 6.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(30.dp)).background(pill).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable
            fun item(tab: PillTab, label: String, icon: ImageVector, wide: Boolean = false, onClick: () -> Unit) {
                val active = tab == current && tab != PillTab.NONE
                val bg = when { wide -> Brand.RustyRed; active -> Color.White; else -> Color.Transparent }
                Box(
                    Modifier.width(if (wide) 60.dp else 52.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(bg)
                        .clickable(role = Role.Tab, onClickLabel = label, onClick = { if (!active) onClick() }).semantics { selected = active },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, label, tint = if (active && !wide) Brand.RichBlack else Color.White, modifier = Modifier.size(24.dp)) }
            }
            item(PillTab.START, "Start", Icons.Rounded.Speed) { nav.home() }
            item(PillTab.ENTRIES, "Einträge", Icons.AutoMirrored.Rounded.FormatListBulleted) { nav.go(Routes.section(0)) }
            item(PillTab.NONE, "Einsprechen", Icons.Rounded.Mic, wide = true) { nav.go(Routes.newEntry(voice = true)) }
            item(PillTab.STATS, "Auswertung", Icons.Rounded.BarChart) { nav.go(Routes.section(1)) }
            item(PillTab.MENU, "Menü", Icons.Rounded.Menu) { nav.go(Routes.MENU) }
        }
    }
}
