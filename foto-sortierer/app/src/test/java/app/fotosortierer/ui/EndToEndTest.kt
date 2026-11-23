package app.fotosortierer.ui

import android.content.Context
import android.os.Environment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.fotosortierer.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Klickt die App einmal komplett durch: einlesen → Anlass → Vorschau → verschieben →
 * Suche/Duplikate/Ordner ansehen → rückgängig machen.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = app.fotosortierer.FotoApp::class, qualifiers = "w411dp-h1600dp")
class EndToEndTest {

    private val root: File = Environment.getExternalStorageDirectory()
    private val camera = File(root, "DCIM/Camera")

    init {
        StorageAccess.granted = { true }
        camera.mkdirs()
        File(camera, "IMG_20231105_143512.jpg").writeBytes(ByteArray(2000) { 1 })
        File(camera, "IMG_20231105_090000.jpg").writeBytes(ByteArray(3000) { 2 })
        File(camera, "VID_20231106_120000.mp4").writeBytes(ByteArray(4000) { 3 })
        File(camera, "kopie.jpg").writeBytes(ByteArray(3000) { 2 }) // Duplikat, Datum unsicher
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("sources", camera.path).commit()
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) =
        rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 10_000)

    @Test
    fun fullFlow() {
        rule.onNodeWithText("Jetzt einlesen").performClick()
        waitFor("Vorschau für 4 Dateien")
        rule.onNodeWithText("mit unsicherem Datum", substring = true).assertExists()

        // Jahresfilter 2023 → Karten: 06.11. (neueste zuerst), 05.11.
        rule.onNodeWithText("2023").performClick()
        rule.onAllNodesWithText("Sonntag, 05.11.2023").onFirst().assertExists()
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("Urlaub Rom")
        rule.waitForIdle()

        rule.onNodeWithText("Vorschau für 4 Dateien").performClick()
        waitFor("4 Dateien → ", substring = true)
        rule.onNodeWithText("Verschieben").performClick()
        rule.onAllNodesWithText("Verschieben")[1].performClick() // Bestätigungsdialog
        rule.waitUntil(10_000) { File(root, "Pictures/Sortiert/2023/2023_11_05_Urlaub_Rom_001.jpg").exists() }

        val year = File(root, "Pictures/Sortiert/2023")
        val names = year.list()!!.sorted()
        assertTrue(names.toString(), "2023_11_05_Urlaub_Rom_001.jpg" in names)
        assertTrue(names.toString(), "2023_11_05_Urlaub_Rom_002.jpg" in names)
        assertTrue(names.toString(), "2023_11_06_001.mp4" in names)
        // Morgens 9:00 muss vor 14:35 nummeriert sein
        assertEquals(3000, File(year, "2023_11_05_Urlaub_Rom_001.jpg").length())
        assertFalse(File(camera, "IMG_20231105_143512.jpg").exists())
        waitFor("Alles sortiert.")

        // Suche
        rule.onNodeWithText("Suchen").performClick()
        waitFor("4 Treffer")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Rom")
        waitFor("2 Treffer")

        // Duplikate
        rule.onNodeWithText("Duplikate").performClick()
        rule.onNodeWithText("Duplikate suchen").performClick()
        waitFor("1 Gruppe ·", substring = true)

        // Ordner
        rule.onNodeWithText("Ordner").performClick()
        waitFor("Quellordner")

        // Verlauf → rückgängig
        rule.onNodeWithText("Verlauf").performClick()
        waitFor("Sortierung (4 Dateien verschoben)")
        rule.onNodeWithText("Rückgängig").performClick()
        rule.onAllNodesWithText("Rückgängig")[1].performClick()
        rule.waitUntil(10_000) { File(camera, "IMG_20231105_143512.jpg").exists() }
        waitFor("rückgängig gemacht", substring = true)
        assertEquals(4, camera.list()!!.size)
        assertFalse(year.exists()) // leerer Jahresordner wurde aufgeräumt
    }
}
