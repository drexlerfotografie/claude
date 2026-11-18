package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

private val sections = listOf("Einträge", "Auswertung", "Kalender")

/**
 * Die drei Monatsbereiche (Eintraege, Auswertung, Kalender) als Unterseite der Startseite.
 * Oben stehen Zurueck, Monat und Suche, darunter der Umschalter zwischen den Bereichen.
 * Eine Leiste am unteren Rand gibt es nicht mehr.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(startTab: Int = 0, startMonth: YearMonth? = null) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    var tab by rememberSaveable { mutableIntStateOf(startTab.coerceIn(0, 2)) }
    var monthText by rememberSaveable { mutableStateOf((startMonth ?: YearMonth.from(env.today)).toString()) }
    val month = YearMonth.parse(monthText)
    val setMonth: (YearMonth) -> Unit = { monthText = it.toString() }
    var pickMonth by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(nav.home) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück zur Startseite") } },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ setMonth(month.minusMonths(1)) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.ChevronLeft, "Vorheriger Monat") }
                            Text(fmtMonth(month), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Monat wählen") { pickMonth = true }.padding(horizontal = 4.dp, vertical = 12.dp).semantics { heading() })
                            IconButton({ setMonth(month.plusMonths(1)) }, Modifier.size(48.dp), enabled = month < YearMonth.from(env.today)) { Icon(Icons.Rounded.ChevronRight, "Nächster Monat") }
                        }
                    },
                    actions = { IconButton({ nav.go(Routes.SEARCH) }) { Icon(Icons.Rounded.Search, "Suchen") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = { PillBar(when (tab) { 0 -> PillTab.ENTRIES; 1 -> PillTab.STATS; else -> PillTab.NONE }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> EntriesTab(month, setMonth)
                1 -> StatsTab(month)
                else -> CalendarTab(month)
            }
        }
    }
    if (pickMonth) MonthPickerDialog(month, env.today, first = env.diary.entries.lastOrNull()?.let { LocalDate.parse(it.localDate) }, onPick = { setMonth(it); pickMonth = false }, onDismiss = { pickMonth = false })
}

/** Alles ausser den Monatsbereichen, als eigene Seite. */
@Composable
fun MenuScreen() {
    val nav = LocalNav.current
    SubScreen("Menü", nav.back, scroll = false, bottomBar = { PillBar(PillTab.MENU) }) { MoreTab() }
}

/** Auswahl "Neu anlegen": anderer Tag, Ziel, wichtiger Tag. Der schnelle Weg fuer heute ist der Knopf "Messen". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSheet(onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    var pickOtherDay by remember { mutableStateOf(false) }
    if (!pickOtherDay) ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Neu anlegen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() })
            SettingRow("Heute", "Eintrag für ${fmtDayShort(env.today)}", Icons.Rounded.Today, onClick = { onDismiss(); nav.go(Routes.newEntry(env.today.toString())) })
            SettingRow("Gestern", "Eintrag für ${fmtDayShort(env.today.minusDays(1))}", Icons.Rounded.History, onClick = { onDismiss(); nav.go(Routes.newEntry(env.today.minusDays(1).toString())) })
            SettingRow("Anderer Tag", "Datum auswählen und nachtragen", Icons.Rounded.EditCalendar, onClick = { pickOtherDay = true })
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SettingRow("Neues Ziel", "Etwas regelmäßig tun", Icons.Rounded.Flag, onClick = { onDismiss(); nav.go(Routes.goalEdit()) })
            SettingRow("Neuer wichtiger Tag", "Jahrestag, Geburtstag oder Ereignis", Icons.Rounded.Event, onClick = { onDismiss(); nav.go(Routes.dayEdit()) })
        }
    } else {
        val todayMs = env.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = todayMs, selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
        })
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton({ onDismiss(); state.selectedDateMillis?.let { nav.go(Routes.newEntry(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) } }) { Text("Weiter") } },
            dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}

/** Monatsauswahl: Jahr blaettern, Monat antippen. */
@Composable
fun MonthPickerDialog(current: YearMonth, today: LocalDate, first: LocalDate?, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableIntStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ year-- }, enabled = year > (first?.year ?: (today.year - 20)).coerceAtMost(today.year - 1)) { Icon(Icons.Rounded.ChevronLeft, "Vorheriges Jahr") }
                Text(year.toString(), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ year++ }, enabled = year < today.year) { Icon(Icons.Rounded.ChevronRight, "Nächstes Jahr") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in (1..12).chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in row) {
                        val ym = YearMonth.of(year, m)
                        FilterChip(
                            selected = ym == current, onClick = { onPick(ym) }, enabled = ym <= YearMonth.from(today),
                            label = { Text(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMANY), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton({ onPick(YearMonth.from(today)) }) { Text("Heute") } },
        dismissButton = { TextButton(onDismiss) { Text("Schließen") } },
    )
}
