package de.mediclever.tagebuch.ui.home

import androidx.compose.runtime.Composable
import de.mediclever.tagebuch.ui.EmptyState
import java.time.YearMonth

@Composable fun StatsTab(month: YearMonth) { EmptyState("Statistiken", "Folgt.") }
@Composable fun CalendarTab(month: YearMonth) { EmptyState("Kalender", "Folgt.") }
