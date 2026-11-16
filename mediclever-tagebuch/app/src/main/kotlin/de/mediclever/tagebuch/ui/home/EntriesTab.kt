package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.Entry
import de.mediclever.tagebuch.core.model.ImportantDay
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.entry.goalsOn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Was zwischen den Eintragskarten eines Monats steht. */
private sealed interface Row {
    data class Day(val date: LocalDate, val entries: List<Entry>) : Row
    data class Gap(val days: Int, val from: LocalDate, val to: LocalDate) : Row
    data class Special(val day: ImportantDay, val date: LocalDate, val years: Int) : Row
}

/** Baut die Monatsliste: Tage neueste zuerst, dazwischen Luecken und wichtige Tage. */
private fun monthRows(entries: List<Entry>, days: List<ImportantDay>, month: YearMonth, today: LocalDate): List<Row> {
    val byDay = entries.filter { it.localDate.startsWith(month.toString()) }.groupBy { LocalDate.parse(it.localDate) }
    val last = minOf(month.atEndOfMonth(), today)
    if (last < month.atDay(1)) return emptyList()
    val special = days.mapNotNull { d ->
        val base = LocalDate.parse(d.date)
        val on = if (d.yearly) runCatching { base.withYear(month.year) }.getOrElse { LocalDate.of(month.year, 2, 28) } else base
        if (YearMonth.from(on) == month && on >= base) Row.Special(d, on, on.year - base.year) else null
    }.groupBy { it.date }
    val out = mutableListOf<Row>()
    var gapEnd: LocalDate? = null
    var d = last
    while (d >= month.atDay(1)) {
        val list = byDay[d]
        val sp = special[d].orEmpty()
        if (list == null && sp.isEmpty()) { if (gapEnd == null) gapEnd = d }
        else {
            gapEnd?.let { out += Row.Gap(ChronoUnit.DAYS.between(d, it).toInt(), d.plusDays(1), it) }
            gapEnd = null
            out += sp
            if (list != null) out += Row.Day(d, list.sortedByDescending { it.timestamp })
        }
        d = d.minusDays(1)
    }
    gapEnd?.let { out += Row.Gap(ChronoUnit.DAYS.between(month.atDay(1), it).toInt() + 1, month.atDay(1), it) }
    return out
}

@Composable
fun EntriesTab(month: YearMonth, onMonth: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val diary = env.diary
    val today = env.today
    val rows = remember(diary.entries, diary.importantDays, month, today) { monthRows(diary.entries, diary.importantDays, month, today) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val isCurrent = month == YearMonth.from(today)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isCurrent) item("today") {
            OutlinedButton({ onMonth(YearMonth.from(today)) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Today, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Heute anzeigen") }
        }
        if (isCurrent) {
            if (diary.settings.showQuote) item("quote") {
                Text("„${Quotes.forDay(today)}“", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            }
            val goals = goalsOn(diary, today)
            if (goals.isNotEmpty()) item("goals") {
                Panel(title = "Ziele heute", padding = 12.dp, action = { TextButton({ nav.go(Routes.GOALS) }) { Text("Alle Ziele") } }) {
                    for (g in goals) {
                        val checked = diary.goalChecks.any { it.goalId == g.id && it.date == today.toString() }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Checkbox) { env.act { setGoalCheck(g.id, today.toString(), !checked) } }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) { Checkbox(checked, null); Text(g.name, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            if (diary.settings.showMemories) {
                val memory = diary.entries.filter { e -> LocalDate.parse(e.localDate).let { it.year < today.year && it.monthValue == today.monthValue && it.dayOfMonth == today.dayOfMonth } }
                if (memory.isNotEmpty()) item("memory") {
                    val years = today.year - LocalDate.parse(memory.first().localDate).year
                    Panel(onClick = { nav.go(Routes.MEMORIES) }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(if (years == 1) "Vor 1 Jahr" else "Vor $years Jahren", style = MaterialTheme.typography.titleMedium)
                                Text("Sieh nach, wie es dir an diesem Tag ging.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                }
            }
        } else if (month < YearMonth.from(today) && rows.any { it is Row.Day }) item("report") {
            Panel(onClick = { nav.go(Routes.REPORTS) }, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Assessment, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Monatsbericht ${fmtMonth(month)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }

        if (rows.none { it is Row.Day }) item("empty") {
            if (diary.entries.isEmpty()) EmptyState("Dein Tagebuch ist noch leer", "Tippe unten auf das Plus und halte fest, wie es dir heute geht. Das dauert nur einen Moment.", actionLabel = "Ersten Eintrag anlegen", onAction = { nav.go(Routes.newEntry()) })
            else EmptyState("Keine Einträge im ${fmtMonth(month)}", if (month > YearMonth.from(today)) "Dieser Monat liegt noch vor dir." else "Du kannst Tage jederzeit nachtragen.",
                actionLabel = if (month > YearMonth.from(today)) null else "Tag nachtragen", onAction = { nav.go(Routes.newEntry(minOf(month.atEndOfMonth(), today).toString())) })
        }

        items(rows, key = { r -> when (r) { is Row.Day -> "d${r.date}"; is Row.Gap -> "g${r.from}"; is Row.Special -> "s${r.day.id}" } }) { r ->
            when (r) {
                is Row.Day -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in r.entries) EntryCard(e, onOpen = { nav.go(Routes.editEntry(e.id)) }, onDelete = { deleting = e }, onPhoto = { nav.go(Routes.photo(it.id)) })
                }
                is Row.Gap -> if (rows.any { it is Row.Day }) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.go(Routes.newEntry(r.to.toString())) }.heightIn(min = 48.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (r.days == 1) "1 Tag fehlt · ${fmtDayShort(r.to)} nachtragen" else "${r.days} Tage fehlen · nachtragen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Row.Special -> Panel(onClick = { nav.go(Routes.DAYS) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(r.day.name, style = MaterialTheme.typography.titleMedium)
                            Text(fmtDayShort(r.date) + if (r.years > 0) " · ${plural(r.years, "Jahr", "Jahre")} her" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { DeleteEntryDialog(it) { deleting = null } }
}
