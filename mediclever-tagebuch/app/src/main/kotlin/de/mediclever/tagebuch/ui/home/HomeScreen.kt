package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

private data class TabInfo(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabInfo("Einträge", Icons.AutoMirrored.Rounded.MenuBook),
    TabInfo("Statistiken", Icons.Rounded.BarChart),
    TabInfo("Kalender", Icons.Rounded.CalendarMonth),
    TabInfo("Mehr", Icons.Rounded.MoreHoriz),
)

/** Hauptansicht: Kopfzeile mit Monat, vier Bereiche und das Plus in der Mitte der Leiste. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(startTab: Int = 0, startMonth: YearMonth? = null) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var monthText by rememberSaveable { mutableStateOf((startMonth ?: YearMonth.from(env.today)).toString()) }
    val month = YearMonth.parse(monthText)
    val setMonth: (YearMonth) -> Unit = { monthText = it.toString() }
    var plus by rememberSaveable { mutableStateOf(false) }
    var pickMonth by remember { mutableStateOf(false) }
    var pickOtherDay by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (tab != 3) TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ setMonth(month.minusMonths(1)) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.ChevronLeft, "Vorheriger Monat") }
                        Text(fmtMonth(month), style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Monat wählen") { pickMonth = true }.padding(horizontal = 6.dp, vertical = 8.dp).semantics { heading() })
                        IconButton({ setMonth(month.plusMonths(1)) }, Modifier.size(48.dp), enabled = month < YearMonth.from(env.today)) { Icon(Icons.Rounded.ChevronRight, "Nächster Monat") }
                    }
                },
                actions = { IconButton({ nav.go(Routes.SEARCH) }) { Icon(Icons.Rounded.Search, "Suchen") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            ) else TopAppBar(
                title = { Text("Mehr", modifier = Modifier.semantics { heading() }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                    @Composable
                    fun tabItem(i: Int) {
                        val sel = tab == i
                        val c = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) { tab = i }.semantics { selected = sel },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(tabs[i].icon, null, tint = c)
                            Text(tabs[i].label, style = MaterialTheme.typography.labelSmall, color = c, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium)
                        }
                    }
                    tabItem(0); tabItem(1)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FloatingActionButton({ plus = true }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(2.dp), modifier = Modifier.semantics { contentDescription = "Neu anlegen" }) { Icon(Icons.Rounded.Add, null, Modifier.size(30.dp)) }
                    }
                    tabItem(2); tabItem(3)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> EntriesTab(month, setMonth)
                1 -> StatsTab(month)
                2 -> CalendarTab(month)
                else -> MoreTab()
            }
        }
    }

    if (plus) ModalBottomSheet({ plus = false }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Neu anlegen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() })
            SettingRow("Heute", "Eintrag für ${fmtDayShort(env.today)}", Icons.Rounded.Today, onClick = { plus = false; nav.go(Routes.newEntry()) })
            SettingRow("Gestern", "Eintrag für ${fmtDayShort(env.today.minusDays(1))}", Icons.Rounded.History, onClick = { plus = false; nav.go(Routes.newEntry(env.today.minusDays(1).toString())) })
            SettingRow("Anderer Tag", "Datum auswählen und nachtragen", Icons.Rounded.EditCalendar, onClick = { plus = false; pickOtherDay = true })
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SettingRow("Neues Ziel", "Etwas regelmäßig tun", Icons.Rounded.Flag, onClick = { plus = false; nav.go(Routes.goalEdit()) })
            SettingRow("Neuer wichtiger Tag", "Jahrestag, Geburtstag oder Ereignis", Icons.Rounded.Event, onClick = { plus = false; nav.go(Routes.dayEdit()) })
        }
    }

    if (pickOtherDay) {
        val todayMs = env.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = todayMs, selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
        })
        DatePickerDialog(
            onDismissRequest = { pickOtherDay = false },
            confirmButton = { TextButton({ pickOtherDay = false; state.selectedDateMillis?.let { nav.go(Routes.newEntry(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())) } }) { Text("Weiter") } },
            dismissButton = { TextButton({ pickOtherDay = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }

    if (pickMonth) MonthPickerDialog(month, env.today, first = env.diary.entries.lastOrNull()?.let { LocalDate.parse(it.localDate) }, onPick = { setMonth(it); pickMonth = false }, onDismiss = { pickMonth = false })
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
