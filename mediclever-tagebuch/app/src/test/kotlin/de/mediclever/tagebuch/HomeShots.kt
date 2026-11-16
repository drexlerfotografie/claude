package de.mediclever.tagebuch

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.data.StarterPacks
import de.mediclever.tagebuch.ui.home.HomeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class HomeShots : ShotBase() {
    @Test fun entries() = shot("eintraege") { HomeScreen() }
    @Test fun entriesPastMonth() = shot("eintraege_vormonat") { HomeScreen(startMonth = YearMonth.of(2026, 9)) }
    @Test fun entriesEmpty() = shot("eintraege_leer", Diary(moods = StarterPacks.defaultMoods())) { HomeScreen() }
    @Test fun more() = shot("mehr") { HomeScreen(startTab = 3) }
}

@RunWith(RobolectricTestRunner::class)
class StatsShots : ShotBase() {
    @Test fun stats() = shot("statistiken") { HomeScreen(startTab = 1, startMonth = YearMonth.of(2026, 10)) }
    @Test fun statsEmpty() = shot("statistiken_leer", Diary(moods = StarterPacks.defaultMoods())) { HomeScreen(startTab = 1) }
    @Test fun calendar() = shot("kalender") { HomeScreen(startTab = 2) }
}
