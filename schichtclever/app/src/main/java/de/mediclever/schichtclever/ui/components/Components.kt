package de.mediclever.schichtclever.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mediclever.schichtclever.R
import de.mediclever.schichtclever.data.Freshness
import de.mediclever.schichtclever.ui.theme.LocalExtra

/** Karte im mediclever-Stil: flache Fläche, feine Linie, optional roter Akzentbalken oben. */
@Composable
fun McCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val line = LocalExtra.current.line
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, line)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (accent) Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.accent)
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    McCard(modifier, accent = accent, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun McChip(label: String, selected: Boolean, onClick: () -> Unit, leading: ImageVector? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leading?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = LocalExtra.current.line, selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    checked != null && onCheckedChange != null -> Modifier.clickable { onCheckedChange(!checked) }
                    else -> Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = LocalExtra.current.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
        if (trailing != null) trailing()
        if (checked != null) {
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun Cleverle(modifier: Modifier = Modifier, dark: Boolean) {
    Image(painterResource(if (dark) R.drawable.cleverle_night else R.drawable.cleverle), contentDescription = null, modifier = modifier)
}

@Composable
fun freshnessColor(f: Freshness): Color = when (f) {
    Freshness.FRISCH -> LocalExtra.current.success
    Freshness.BALD -> LocalExtra.current.warning
    Freshness.LANGE_HER, Freshness.NIE -> LocalExtra.current.accent
}

/** Einfaches Balkendiagramm; markiert den höchsten Wert in Rot. */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 120.dp, highlight: Set<Int> = emptySet()) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val muted = LocalExtra.current.line
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val frac = v.toFloat() / max
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(frac.coerceAtLeast(0.02f))
                        .background(if (i in highlight || (highlight.isEmpty() && v == max && v > 0)) accent else muted)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, maxLines = 1) }
        }
    }
}

/** Horizontaler Anteilsbalken mit Beschriftung. */
@Composable
fun ShareBar(label: String, value: Int, total: Int) {
    val frac = if (total == 0) 0f else value.toFloat() / total
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$value · ${(frac * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(LocalExtra.current.line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        }
    }
}
