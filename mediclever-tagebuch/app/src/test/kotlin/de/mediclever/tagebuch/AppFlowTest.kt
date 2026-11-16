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

    private fun onboard(pack: String = "Schichtdienst") {
        waitText("Dein Tagebuch für Stimmung und Alltag")
        rule.onNodeWithText("Weiter").assertIsNotEnabled()
        rule.onNodeWithText("Ich bin einverstanden", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("Ich bin mindestens 18 Jahre alt.").performScrollTo().performClick()
        rule.onNodeWithText("Weiter").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText(pack).performClick()
        rule.onNodeWithText("Tagebuch einrichten").performScrollTo().performClick()
        waitText("Dein Tagebuch ist noch leer")
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
        rule.onNodeWithContentDescription("Neu anlegen").performClick()
        waitText("Heute"); rule.onNodeWithText("Heute").performClick()
        waitText("Wie geht es dir?")
        rule.onNodeWithText("Gut").performClick()
        // Schritt 2: Aktivitaet, Skala, Notiz
        waitText("Dienst")
        rule.onNodeWithText("Nachtdienst").performScrollTo().performClick()
        rule.onNodeWithContentDescription("gut").performScrollTo().performClick()
        rule.onNodeWithText("Kurze Notiz …").performScrollTo().performTextInput("Ruhige Nacht")
        rule.onNode(hasText("Speichern") and hasAnyAncestor(hasScrollAction())).performScrollTo().performClick()
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
        waitText("Ruhige Nacht")
        rule.onRoot().captureRoboImage("build/shots/ablauf_erster_eintrag.png")
        rule.onNodeWithText("Ruhige Nacht").performClick()
        waitText("Datum, Uhrzeit oder Stimmung ändern")
        rule.onNodeWithContentDescription("Als Favorit markieren").performClick()
        rule.onNode(hasText("Speichern") and !hasAnyAncestor(hasScrollAction())).performClick()
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
        for (tab in listOf("Statistiken", "Kalender", "Mehr")) { rule.onNodeWithText(tab).performClick(); rule.waitForIdle() }
        for (row in listOf("Ziele", "Berichte", "Rückblick", "Wichtige Tage", "Favoriten", "Fotos", "Erfolge", "Einrichtung", "Erinnerungen", "Aussehen", "Sicherheit", "Einstellungen", "Daten", "MediClever-Konto", "Über die App")) {
            rule.onNodeWithText(row).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Zurück").assertExists("Seite $row hat keinen Zurück-Pfeil")
            rule.onNodeWithContentDescription("Zurück").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Dein Tagebuch", ignoreCase = true).assertExists()
        }
    }

    @Test fun goalCanBeCreatedAndCheckedToday() {
        onboard("Allgemein")
        rule.onNodeWithContentDescription("Neu anlegen").performClick()
        waitText("Neues Ziel"); rule.onNodeWithText("Neues Ziel").performClick()
        waitText("Was möchtest du erreichen?")
        rule.onNodeWithText("Was möchtest du erreichen?").performTextInput("Abends lesen")
        rule.onNodeWithText("Speichern").performClick()
        waitFor("Ziel gespeichert") { diary().goals.size == 1 }
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
        waitText("Dein Tagebuch ist noch leer")
    }
}
