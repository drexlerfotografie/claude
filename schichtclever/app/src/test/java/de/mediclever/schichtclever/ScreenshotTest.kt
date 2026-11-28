package de.mediclever.schichtclever

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Role
import de.mediclever.schichtclever.ui.screens.EinsatzEditorScreen
import de.mediclever.schichtclever.ui.screens.FilterScreen
import de.mediclever.schichtclever.ui.screens.MoreScreen
import de.mediclever.schichtclever.ui.screens.OnboardingScreen
import de.mediclever.schichtclever.ui.screens.ShiftScreen
import de.mediclever.schichtclever.ui.screens.SkillDetailScreen
import de.mediclever.schichtclever.ui.screens.SkillsScreen
import de.mediclever.schichtclever.ui.screens.StatsScreen
import de.mediclever.schichtclever.ui.screens.TemplatesScreen
import de.mediclever.schichtclever.ui.theme.SchichtCleverTheme
import org.junit.Rule
import org.junit.Test

/** Rendert die echten Compose-Screens mit Beispieldaten als PNG (./gradlew recordPaparazziDebug). */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, showSystemUi = false, maxPercentDifference = 1.0)

    private fun shot(dark: Boolean = true, content: @Composable () -> Unit) = paparazzi.snapshot {
        SchichtCleverTheme(dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }

    private val data = SampleData.build()

    @Test fun onboarding() = shot { OnboardingScreen(data.copy(settings = data.settings.copy(onboardingDone = false)), true) }
    @Test fun schichtStart() = shot { ShiftScreen(SampleData.build(active = false), {}, {}, {}, {}) }
    @Test fun schichtAktivNacht() = shot { ShiftScreen(data, {}, {}, {}, {}) }
    @Test fun schichtAktivTagHell() = shot(dark = false) {
        val d = SampleData.build(activeType = SampleData.FRUEH)
        ShiftScreen(d.copy(settings = d.settings.copy(role = Role.PFLEGE)), {}, {}, {}, {})
    }
    @Test fun einsatzErfassen() = shot { EinsatzEditorScreen(data, data.einsaetze.first { it.note == "ACS-Verdacht" }.id) {} }
    @Test fun statistik() = shot { StatsScreen(data) }
    @Test fun skills() = shot { SkillsScreen(data, true) {} }
    @Test fun skillDetail() = shot { SkillDetailScreen(data, "io") {} }
    @Test fun blaufilter() = shot { FilterScreen(data) {} }
    @Test fun schichtStartEigene() = shot {
        val d = SampleData.build(active = false)
        ShiftScreen(d.copy(settings = d.settings.copy(shiftTemplates = SampleData.CUSTOM)), {}, {}, {}, {})
    }
    @Test fun meineSchichten() = shot { TemplatesScreen(data.copy(settings = data.settings.copy(shiftTemplates = SampleData.CUSTOM))) {} }
    @Test fun mehr() = shot { MoreScreen(data, true, {}, {}) }
}
