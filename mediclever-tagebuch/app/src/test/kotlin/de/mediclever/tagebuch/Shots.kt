package de.mediclever.tagebuch

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.ui.LocalEnv
import de.mediclever.tagebuch.ui.UiEnv
import de.mediclever.tagebuch.ui.theme.TagebuchTheme
import org.junit.Rule
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Grundlage der Screenshot-Tests: rendert einen Bildschirm mit Beispieldaten hell und dunkel
 * in Handy-Groesse (412 x 915 dp). Ergebnis unter app/build/shots/.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
abstract class ShotBase {
    @get:Rule val rule = createComposeRule()

    fun shot(name: String, diary: Diary = SampleData.diary, content: @Composable () -> Unit) {
        var dark = false
        rule.setContent {
            // key erzwingt den Neuaufbau beim Wechsel hell/dunkel
            androidx.compose.runtime.key(darkState.value) {
                TagebuchTheme(diary.settings, forceDark = darkState.value) {
                    CompositionLocalProvider(LocalEnv provides UiEnv(diary, now = { SampleData.now })) { Surface { content() } }
                }
            }
        }
        for (mode in listOf(false, true)) {
            dark = mode
            darkState.value = dark
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/shots/${name}_${if (dark) "dunkel" else "hell"}.png")
        }
    }

    private val darkState = androidx.compose.runtime.mutableStateOf(false)
}
