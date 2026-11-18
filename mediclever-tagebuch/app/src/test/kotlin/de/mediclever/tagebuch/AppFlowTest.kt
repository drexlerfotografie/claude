package de.mediclever.tagebuch

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import de.mediclever.tagebuch.core.model.Diary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Durchlauf durch die echte App mit echter Datenbank (ohne Firebase-Konfiguration, also lokal):
 * Einwilligung, Startpaket, Eintrag anlegen, bearbeiten, Ziel abhaken, loeschen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @org.junit.After fun tearDown() { runCatching { container.close() } }

    private val container get() = (rule.activity.application as TagebuchApp).container
    private fun diary(): Diary = runBlocking { container.repo.snapshot() }
    private fun waitFor(what: String, check: () -> Boolean) = rule.waitUntil(15_000) { rule.waitForIdle(); check() }.also { }
    private fun waitText(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun onboard(pack: String = "BOS und Schichtdienst") {
        waitText("Dein Tagebuch für Stimmung und Alltag")
        rule.onNodeWithText("Weiter").assertIsNotEnabled()
        rule.onNodeWithText("Ich bin einverstanden", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("Ich bin mindestens 18 Jahre alt.").performScrollTo().performClick()
        rule.onNodeWithText("Weiter").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText(pack).performClick()
        rule.onNodeWithText("Tagebuch einrichten").performScrollTo().performClick()
        waitText("Wie war dein Tag?")
    }

    @Test fun onboardingCreatesSetupAndRecordsConsent() {
        onboard()
        val d = diary()
        assertEquals(5, d.moods.size)
        assertEquals((1..5).toList(), d.moods.map { it.level }.sorted())
        assertTrue(d.tags.any { it.name == "Nachtdienst" })
        assertTrue(d.scales.any { it.name == "Schlafdauer" })
        assertTrue(runBlocking { container.prefs.current() }.consentAt > 0)
        rule.onRoot().captureRoboImage("build/shots/ablauf_nach_einrichtung.png")
    }

    @Test fun createEditAndDeleteEntry() {
        onboard()
        // Ohne Wert laesst sich nicht speichern
        rule.onNodeWithContentDescription("Anderen Tag, Ziel oder wichtigen Tag anlegen").performClick()
        waitText("Heute"); rule.onNodeWithText("Heute").performClick()
        waitText("NEUER EINTRAG")
        rule.onNodeWithText("Speichern").performClick()
        waitText("Bitte erst einen Wert wählen.")
        // Barometer auf 4 von 5 stellen: das ist die gute Stufe. Danach steht der Wert klein oben rechts.
        rule.onNodeWithContentDescription("Stimmung").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Wert 4 von 5, Gut. Ändern").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Stimmung").assertDoesNotExist()
        // Dienstart: genau eine
        rule.onNodeWithContentDescription("Dienstart Spätdienst").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Dienstart Nachtdienst").performScrollTo().performClick()
        rule.onNodeWithContentDescription("gut").performScrollTo().performClick()
        rule.onNodeWithText("Notiz schreiben …").performScrollTo().performTextInput("Ruhige Nacht")
        rule.onNodeWithText("Speichern").performClick()
        waitFor("Eintrag gespeichert") { diary().entries.size == 1 }

        val e = diary().entries.single()
        val d = diary()
        assertEquals("Gut", d.moods.first { it.id == e.moodId }.name)
        assertEquals(listOf("Nachtdienst"), e.tagIds.map { id -> d.tags.first { it.id == id }.name })
        assertEquals("<p>Ruhige Nacht</p>", e.note)
        assertEquals(LocalDate.now().toString(), e.localDate)
        val quality = d.scales.first { it.name == "Schlafqualität" }
        assertEquals("gut", quality.points.first { it.id == e.scaleValues.single().pointId }.label)
        assertTrue(e.dirty)

        // Eintrag erscheint in der Liste und laesst sich aendern
        waitText("Wie war dein Tag?")
        rule.onRoot().captureRoboImage("build/shots/ablauf_erster_eintrag.png")
        rule.onNodeWithContentDescription("Einträge").performClick()
        waitText("Ruhige Nacht")
        rule.onNodeWithText("Ruhige Nacht").performClick()
        waitText("EINTRAG")
        rule.onNodeWithContentDescription("Als Favorit markieren").performClick()
        rule.onNodeWithText("Speichern").performClick()
        waitFor("Favorit gespeichert") { diary().entries.single().favorite }
        assertEquals(e.timestamp, diary().entries.single().timestamp)

        // Loeschen ueber das Kartenmenue, mit Rueckfrage
        waitText("Ruhige Nacht")
        rule.onNodeWithContentDescription("Aktionen zum Eintrag").performClick()
        rule.onNodeWithText("Löschen").performClick()
        waitText("Eintrag löschen?")
        rule.onAllNodesWithText("Löschen").onLast().performClick()
        waitFor("Eintrag geloescht") { diary().entries.isEmpty() }
        waitText("Dein Tagebuch ist noch leer")
    }

    @Test fun tabsAndMoreScreensOpenWithoutCrash() {
        onboard("Allgemein")
        for (section in listOf("Auswertung", "Einträge")) {
            rule.onNodeWithContentDescription(section).performClick(); rule.waitForIdle()
            rule.onNodeWithContentDescription("Zurück zur Startseite").performClick(); rule.waitForIdle()
            rule.onNodeWithText("Wie war dein Tag?").assertExists()
        }
        // Der Kalender oeffnet sich ueber die Monatsmatrix
        rule.onNode(hasContentDescription("Öffnet den Kalender", substring = true)).performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Zurück zur Startseite").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Wie war dein Tag?").assertExists()
        // Die Leiste wechselt direkt zwischen den Bereichen und fuehrt zurueck zum Start
        rule.onNodeWithContentDescription("Einträge").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Auswertung").performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Start").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Wie war dein Tag?").assertExists()
        rule.onNodeWithContentDescription("Menü").performClick(); rule.waitForIdle()
        for (row in listOf("Dienstarten", "Ziele", "Berichte", "Rückblick", "Wichtige Tage", "Favoriten", "Fotos", "Erfolge", "Einrichtung", "Erinnerungen", "Aussehen", "Sicherheit", "Einstellungen", "Daten", "MediClever-Konto", "Über die App")) {
            rule.onNodeWithText(row).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onAllNodesWithContentDescription("Zurück").onLast().assertExists("Seite $row hat keinen Zurück-Pfeil")
            rule.onAllNodesWithContentDescription("Zurück").onLast().performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Dein Tagebuch", ignoreCase = true).assertExists()
        }
    }

    @Test fun goalCanBeCreatedAndCheckedToday() {
        onboard("Allgemein")
        rule.onNodeWithContentDescription("Anderen Tag, Ziel oder wichtigen Tag anlegen").performClick()
        waitText("Neues Ziel"); rule.onNodeWithText("Neues Ziel").performClick()
        waitText("Was möchtest du erreichen?")
        rule.onNodeWithText("Was möchtest du erreichen?").performTextInput("Abends lesen")
        rule.onNodeWithText("Speichern").performClick()
        waitFor("Ziel gespeichert") { diary().goals.size == 1 }
        waitText("Wie war dein Tag?")
        rule.onNodeWithContentDescription("Einträge").performClick()
        waitText("Ziele heute")
        rule.onNodeWithText("Abends lesen").performClick()
        waitFor("Ziel abgehakt") { diary().goalChecks.any { !it.deleted && it.date == LocalDate.now().toString() } }
    }

    @Test fun pinLocksTheApp() {
        onboard("Leer starten")
        runBlocking { val salt = de.mediclever.tagebuch.data.Pin.newSalt(); container.prefs.setPin(de.mediclever.tagebuch.data.Pin.hash("2468", salt), salt) }
        waitText("Gib deine PIN ein, um das Tagebuch zu öffnen.")
        rule.onNodeWithText("PIN").performTextInput("1111")
        rule.onNodeWithText("Entsperren").performClick()
        waitText("Die PIN stimmt nicht.")
        rule.onNodeWithText("PIN").performTextInput("2468")
        rule.onNodeWithText("Entsperren").performClick()
        waitText("Wie war dein Tag?")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class ResetFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val container get() = (rule.activity.application as TagebuchApp).container
    @org.junit.After fun tearDown() { runCatching { container.close() } }
    private fun waitText(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** "App zurücksetzen" in den Einstellungen loescht alles und fuehrt zurueck zur Einrichtung. */
    @Test fun resetReturnsToOnboarding() {
        waitText("Dein Tagebuch für Stimmung und Alltag")
        rule.onNodeWithText("Ich bin einverstanden", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("Ich bin mindestens 18 Jahre alt.").performScrollTo().performClick()
        rule.onNodeWithText("Weiter").performScrollTo().performClick()
        rule.onNodeWithText("Tagebuch einrichten").performScrollTo().performClick()
        waitText("Wie war dein Tag?")
        assertTrue(runBlocking { container.repo.snapshot() }.tags.isNotEmpty())
        rule.onNodeWithContentDescription("Menü").performClick()
        rule.onNodeWithText("Einstellungen").performScrollTo().performClick()
        rule.onNodeWithText("App zurücksetzen").performScrollTo().performClick()
        waitText("App zurücksetzen?")
        rule.onNodeWithText("Endgültig zurücksetzen").performClick()
        waitText("Dein Tagebuch für Stimmung und Alltag")
        val d = runBlocking { container.repo.snapshot() }
        assertTrue(d.moods.isEmpty() && d.tags.isEmpty() && d.entries.isEmpty())
        assertEquals(0L, runBlocking { container.prefs.current() }.consentAt)
    }
}
