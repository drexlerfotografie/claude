package de.mediclever.tagebuch.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.stats.*
import de.mediclever.tagebuch.ui.*
import de.mediclever.tagebuch.ui.icons.MoodFace
import de.mediclever.tagebuch.ui.icons.iconFor
import de.mediclever.tagebuch.ui.setup.Choice
import de.mediclever.tagebuch.ui.theme.LocalDiaryColors
import de.mediclever.tagebuch.ui.theme.groupColor
import java.time.YearMonth
import java.util.Locale

private fun num(v: Double, digits: Int = 1) = String.format(Locale.GERMANY, "%.${digits}f", v)
private fun signed(v: Double, digits: Int = 1) = (if (v > 0) "+" else "") + num(v, digits)

/** Statistiken fuer den gewaehlten Monat oder das ganze Jahr. Gerechnet wird in :core. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsTab(month: YearMonth) {
    val env = LocalEnv.current
    val nav = LocalNav.current
    val diary = env.diary
    val colors = LocalDiaryColors.current
    var yearly by rememberSaveable { mutableStateOf(false) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var bars by rememberSaveable { mutableStateOf(false) }
    var allActivities by rememberSaveable { mutableStateOf(false) }
    var togetherLevel by rememberSaveable { mutableIntStateOf(1) }
    val s: PeriodStats = remember(diary, month, yearly, overlay, env.today) {
        if (yearly) yearStats(diary, month.year, env.today, overlay).stats else monthStats(diary, month, env.today, overlay).stats
    }
    val period = if (yearly) "im Jahr ${month.year}" else "im ${fmtMonth(month)}"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Choice(listOf(false to "Monat", true to "Jahr ${month.year}"), yearly) { yearly = it }

        // Tage in Folge
        SharePanel("Tage in Folge bis heute") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plural(s.streak.current, "Tag", "Tage"), style = MaterialTheme.typography.headlineMedium)
                    Text("Längste Reihe: ${plural(s.streak.longest, "Tag", "Tage")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in s.streak.lastFiveDays) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(if (f.hasEntry) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(if (f.hasEntry) Icons.Rounded.Check else Icons.Rounded.Remove, if (f.hasEntry) "Eintrag vorhanden" else "kein Eintrag", tint = if (f.hasEntry) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        Text(weekdayShort(f.date.dayOfWeek.value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (!s.enoughData.any) {
            EmptyState("Zu wenig Daten", "Für diesen Zeitraum gibt es noch keine Einträge. Sobald du ein paar Tage festgehalten hast, erscheinen hier deine Auswertungen.",
                actionLabel = "Eintrag anlegen", onAction = { nav.go(Routes.newEntry()) })
            return@Column
        }

        // Hinweise in Klartext
        if (s.insights.isNotEmpty()) Panel(title = "Auf einen Blick", padding = 14.dp) {
            for (line in s.insights) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Stimmungsverlauf
        SharePanel("Stimmungsverlauf") {
            if (!s.enoughData.moodCourse) TooLittle()
            else {
                val pts = s.moodCourse.map { p ->
                    ChartPoint(if (yearly) java.time.Month.of(p.date.monthValue).getDisplayName(java.time.format.TextStyle.NARROW, Locale.GERMANY) else p.date.dayOfMonth.toString(),
                        p.averageScore, p.averageScore?.let { colors.level(roundedLevel(it)) }, p.overlay)
                }
                SeriesChart(pts, 1.0, 5.0, "Stimmungsverlauf $period, Durchschnitt ${s.averageScore?.let { num(it) } ?: "ohne Wert"} von 5", bars = bars, labelEvery = if (yearly) 1 else 5,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(listOf(false to "Linie", true to "Balken"), bars) { bars = it }
                    Spacer(Modifier.weight(1f))
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip({ menu = true }, { Text(s.overlayTag?.name ?: "Aktivität einblenden", maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.Layers, null, Modifier.size(18.dp)) })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Keine") }, { overlay = null; menu = false })
                            for (a in s.activityCounter.items.take(25)) DropdownMenuItem({ Text(a.tag.name) }, { overlay = a.tag.id; menu = false })
                        }
                    }
                }
                if (s.overlayTag != null) Text("Punkte unter der Achse: Tage mit „${s.overlayTag!!.name}“.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Stimmungszaehler als Halbring
        SharePanel("Stimmungen") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingChart(s.moodCounter.byLevel.map { colors.level(it.level) to it.count.toFloat() }, "Stimmungszähler: " + s.moodCounter.byLevel.joinToString { "${levelName(it.level)}: ${it.count}" }, half = true, size = 220.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.moodCounter.total.toString(), style = MaterialTheme.typography.headlineMedium)
                        Text(if (s.moodCounter.total == 1) "Eintrag" else "Einträge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (l in s.moodCounter.byLevel) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoodFace(l.level, diary.moods.firstOrNull { it.level == l.level }?.icon ?: "a", 34.dp)
                    Text(l.count.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(diary.moods.firstOrNull { it.level == l.level }?.name ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (s.moodCounter.byMood.size > 5) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in s.moodCounter.byMood) MiniChip("${m.mood.name} ${m.count}", null, colors.level(m.mood.level))
            }
        }

        // Aktivitaetenzaehler
        SharePanel("Aktivitäten") {
            if (s.activityCounter.items.isEmpty()) Text("In diesem Zeitraum wurden keine Aktivitäten erfasst.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val top = s.activityCounter.items.firstOrNull()?.count ?: 1
            for ((i, a) in (if (allActivities) s.activityCounter.items else s.activityCounter.items.take(6)).withIndex()) {
                val c = groupColor(a.tag.colorIndex ?: diary.groupById[a.tag.groupId]?.colorIndex)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", Modifier.width(22.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Icon(iconFor(a.tag.icon), null, tint = c, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(a.tag.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        ShareBar(a.count.toFloat() / top, c)
                    }
                    Text("${a.count}×", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (s.activityCounter.items.size > 6) TextButton({ allActivities = !allActivities }) { Text(if (allActivities) "Weniger anzeigen" else "Alle ${s.activityCounter.items.size} anzeigen") }
        }

        // Verteilung nach Gruppe
        if (s.groupDistribution.isNotEmpty()) SharePanel("Verteilung nach Gruppen") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingChart(s.groupDistribution.map { groupColor(it.colorIndex) to it.count.toFloat() }, "Verteilung nach Gruppen: " + s.groupDistribution.joinToString { "${it.name} ${(it.share * 100).toInt()} Prozent" }, size = 130.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (g in s.groupDistribution) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(groupColor(g.colorIndex)))
                        Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text("${(g.share * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Wochentage
        SharePanel("Stimmung je Wochentag") {
            if (!s.enoughData.weekdayMood) TooLittle()
            else {
                val order = weekdayOrder(diary.settings.weekStart)
                val byDay = s.weekdayMood.associateBy { it.isoDay }
                SeriesChart(order.map { d -> byDay[d].let { ChartPoint(weekdayShort(d), it?.averageScore, it?.averageScore?.let { v -> colors.level(roundedLevel(v)) }) } }, 1.0, 5.0,
                    "Durchschnittliche Stimmung je Wochentag: " + order.joinToString { d -> weekdayLong(d) + " " + (byDay[d]?.averageScore?.let { num(it) } ?: "ohne Wert") }, bars = true, labelEvery = 1, height = 130.dp)
            }
        }

        // Einfluss von Aktivitaeten
        SharePanel("Einfluss auf die Stimmung") {
            if (!s.enoughData.activityInfluence) TooLittle("Dafür braucht eine Aktivität mindestens drei Einträge.")
            else {
                Text("Durchschnitt mit der Aktivität im Vergleich zu Einträgen ohne sie. Das zeigt Zusammenhänge, keine Ursachen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = (s.activityInfluence.take(4) + s.activityInfluence.takeLast(3)).distinctBy { it.tag.id }
                for (a in shown) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(iconFor(a.tag.icon), null, tint = groupColor(a.tag.colorIndex ?: diary.groupById[a.tag.groupId]?.colorIndex), modifier = Modifier.size(20.dp))
                    Text(a.tag.name, Modifier.weight(1f), maxLines = 1)
                    Icon(if (a.delta >= 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, if (a.delta >= 0) "besser" else "schlechter", Modifier.size(16.dp), tint = if (a.delta >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                    Text(signed(a.delta, 2), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                }
            }
        }

        // Stabilitaet
        SharePanel("Stimmungsstabilität") {
            val st = s.stability
            if (st == null) TooLittle()
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingChart(listOf(MaterialTheme.colorScheme.primary to st.toFloat(), MaterialTheme.colorScheme.surfaceVariant to (100 - st).toFloat()), "Stimmungsstabilität $st von 100", size = 96.dp) { Text(st.toString(), style = MaterialTheme.typography.headlineSmall) }
                Text(when { st >= 80 -> "Deine Stimmung war sehr gleichmäßig."; st >= 60 -> "Deine Stimmung schwankte nur wenig."; st >= 40 -> "Deine Stimmung wechselte spürbar von Tag zu Tag."; else -> "Deine Stimmung schwankte stark." } + " 100 bedeutet: jeden Tag gleich.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Oft zusammen
        SharePanel("Oft zusammen") {
            if (!s.enoughData.oftenTogether) TooLittle()
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in 1..5) FilterChip(togetherLevel == l, { togetherLevel = l }, label = { MoodFace(l, "a", 26.dp) }, modifier = Modifier.height(48.dp))
                }
                val la = s.oftenTogether.firstOrNull { it.level == togetherLevel }
                if (la == null || la.items.isEmpty()) Text("Keine Einträge auf dieser Stufe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else for (a in la.items) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(iconFor(a.tag.icon), null, tint = groupColor(a.tag.colorIndex ?: diary.groupById[a.tag.groupId]?.colorIndex), modifier = Modifier.size(20.dp))
                    Text(a.tag.name, Modifier.weight(1f), maxLines = 1)
                    Text("${a.count}× · ${(a.share * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Laengste Serie guter Tage
        s.goodDays?.let { g ->
            SharePanel("Längste Serie guter Tage") {
                Text(plural(g.length, "Tag", "Tage"), style = MaterialTheme.typography.headlineMedium)
                Text("${fmtDayShort(g.start)} bis ${fmtDayShort(g.end)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (g.activities.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (a in g.activities.take(8)) MiniChip("${a.tag.name} ${a.count}×", a.tag.icon, groupColor(a.tag.colorIndex ?: diary.groupById[a.tag.groupId]?.colorIndex))
                }
            }
        }

        // Skalen
        for (sc in s.scales) {
            val c = groupColor(sc.scale.colorIndex)
            SharePanel(sc.scale.name) {
                if (sc.dayCount == 0) TooLittle("Für diese Skala gibt es $period keine Werte.")
                else {
                    fun fmt(v: Double) = if (sc.scale.type == ScaleType.DURATION) fmtDuration(v.toInt()) else num(v)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(sc.average?.let { fmt(it) } ?: "–", style = MaterialTheme.typography.headlineSmall)
                        Text("im Durchschnitt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp).weight(1f))
                        sc.change?.let { ch ->
                            val tint: Color = when (sc.changeRating) { ChangeRating.GOOD -> MaterialTheme.colorScheme.tertiary; ChangeRating.BAD -> MaterialTheme.colorScheme.error; ChangeRating.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (ch > 0) Icons.Rounded.ArrowUpward else if (ch < 0) Icons.Rounded.ArrowDownward else Icons.Rounded.Remove, null, Modifier.size(16.dp), tint = tint)
                                Text((if (ch > 0) "+" else if (ch < 0) "−" else "") + fmt(kotlin.math.abs(ch)) + if (yearly) " zum Vorjahr" else " zum Vormonat", style = MaterialTheme.typography.labelLarge, color = tint)
                            }
                        }
                    }
                    val values = sc.series.mapNotNull { it.value }
                    val lo = if (sc.scale.type == ScaleType.DURATION) 0.0 else sc.scale.min.toDouble()
                    val hi = maxOf(values.maxOrNull() ?: 1.0, if (sc.scale.type == ScaleType.DURATION) 60.0 else sc.scale.max.toDouble())
                    SeriesChart(sc.series.map { ChartPoint(if (yearly) java.time.Month.of(it.date.monthValue).getDisplayName(java.time.format.TextStyle.NARROW, Locale.GERMANY) else it.date.dayOfMonth.toString(), it.value) }, lo, hi,
                        "${sc.scale.name} $period, Durchschnitt ${sc.average?.let { fmt(it) } ?: "ohne Wert"}", bars = true, color = c, labelEvery = if (yearly) 1 else 5, height = 110.dp)
                    if (sc.scale.type == ScaleType.TEXT) Text(sc.scale.points.sortedBy { it.position }.joinToString("  ·  ") { "${it.position} ${it.label}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Ziele
        if (s.goals.isNotEmpty()) Panel(title = "Ziele", padding = 14.dp, action = { TextButton({ nav.go(Routes.GOALS) }) { Text("Alle Ziele") } }) {
            for (g in s.goals) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(g.goal.name, Modifier.weight(1f), maxLines = 1); Text("${g.completions}×" + (g.successRate?.let { " · ${(it * 100).toInt()} %" } ?: ""), style = MaterialTheme.typography.labelLarge) }
                ShareBar((g.successRate ?: 0.0).toFloat(), groupColor(g.goal.colorIndex))
            }
        }

        // Erfolge
        Panel(title = "Erfolge", padding = 14.dp, onClick = { nav.go(Routes.ACHIEVEMENTS) }) {
            Text("${s.achievements.unlockedCount} von ${s.achievements.totalCount} erreicht", style = MaterialTheme.typography.bodyLarge)
            if (s.achievements.unlockedInPeriod.isNotEmpty()) Text("Neu $period: " + s.achievements.unlockedInPeriod.joinToString { it.title }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.achievements.next?.let { n -> Text("Als Nächstes: ${n.title} (${n.progress} von ${n.target})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun TooLittle(text: String = "Zu wenig Daten für diese Auswertung. Ein paar Einträge mehr, dann erscheint sie hier.") {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}
