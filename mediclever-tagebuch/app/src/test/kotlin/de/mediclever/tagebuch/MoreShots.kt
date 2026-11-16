package de.mediclever.tagebuch

import de.mediclever.tagebuch.ui.LockScreen
import de.mediclever.tagebuch.ui.OnboardingScreen
import de.mediclever.tagebuch.ui.more.*
import de.mediclever.tagebuch.ui.setup.SetupScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoreShots : ShotBase() {
    private val d = SampleData.diary
    @Test fun onboarding1() = shot("start_einwilligung") { OnboardingScreen() }
    @Test fun onboarding2() = shot("start_paket") { OnboardingScreen(startStep = 1) }
    @Test fun lock() = shot("sperre") { LockScreen("s", "h") {} }
    @Test fun setup() = shot("einrichtung") { SetupScreen("") {} }
    @Test fun setupMoods() = shot("einrichtung_stimmungen") { SetupScreen("moods") {} }
    @Test fun setupBlocks() = shot("einrichtung_aktivitaeten") { SetupScreen("activities") {} }
    @Test fun setupGroup() = shot("einrichtung_gruppe") { SetupScreen("group:" + d.groups.first().id) {} }
    @Test fun setupScale() = shot("einrichtung_skala") { SetupScreen("scale:" + d.scales.first { it.points.isNotEmpty() }.id) {} }
    @Test fun setupTemplates() = shot("einrichtung_vorlagen") { SetupScreen("templates") {} }
    @Test fun goals() = shot("ziele") { GoalsScreen() }
    @Test fun goalDetail() = shot("ziel_detail") { GoalDetailScreen(d.goals.first().id) }
    @Test fun goalEdit() = shot("ziel_bearbeiten") { GoalEditScreen(d.goals[1].id) }
    @Test fun reports() = shot("berichte") { ReportsScreen() }
    @Test fun memories() = shot("rueckblick") { MemoriesScreen() }
    @Test fun days() = shot("wichtige_tage") { ImportantDaysScreen() }
    @Test fun dayEdit() = shot("wichtiger_tag_bearbeiten") { ImportantDayEditScreen(d.importantDays.first().id) }
    @Test fun favorites() = shot("favoriten") { FavoritesScreen() }
    @Test fun photos() = shot("fotos_leer") { PhotosScreen() }
    @Test fun achievements() = shot("erfolge") { AchievementsScreen() }
    @Test fun reminders() = shot("erinnerungen") { RemindersScreen() }
    @Test fun appearance() = shot("aussehen") { AppearanceScreen() }
    @Test fun security() = shot("sicherheit") { SecurityScreen() }
    @Test fun settings() = shot("einstellungen") { SettingsScreen() }
    @Test fun data() = shot("daten") { DataScreen() }
    @Test fun account() = shot("konto") { AccountScreen() }
    @Test fun about() = shot("ueber") { AboutScreen() }
    @Test fun search() = shot("suche") { SearchScreen() }
}
