package app.fotosortierer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Environment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.fotosortierer.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Erzeugt Bildschirmfotos der App. Läuft nur mit -Dscreenshots.dir=… */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = app.fotosortierer.FotoApp::class, qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    private val out = System.getProperty("screenshots.dir")?.let { File(it) }
    private val root: File = Environment.getExternalStorageDirectory()

    private var seed = 0
    private fun photo(dir: File, name: String, c1: Int, c2: Int) {
        val k = seed++
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 400f, 400f, c1, c2, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 400f, 400f, p)
        canvas.drawCircle(90f + (k * 47) % 240, 80f + (k * 31) % 90, 40f, Paint().apply { color = Color.argb(200, 255, 230, 150) })
        val hill = Paint().apply { color = Color.argb(160, 20, 40, 30) }
        canvas.drawOval(-100f, 260f, 300f, 560f, hill)
        canvas.drawOval(150f - (k * 23) % 120, 290f, 550f, 600f, hill)
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    init {
        StorageAccess.granted = { true }
        // Ohne Einblend-Animation: In Robolectric steht die Uhr, Bilder blieben sonst unsichtbar.
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
                .crossfade(false)
                .components { add(coil.decode.VideoFrameDecoder.Factory()) }.build()
        )
        val cam = File(root, "DCIM/Camera").apply { mkdirs() }
        val wa = File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images").apply { mkdirs() }
        File(root, "Pictures/Screenshots").mkdirs()
        val palettes = listOf(
            Color.rgb(255, 140, 60) to Color.rgb(120, 40, 90),
            Color.rgb(70, 160, 220) to Color.rgb(20, 60, 120),
            Color.rgb(250, 200, 80) to Color.rgb(200, 90, 40),
            Color.rgb(90, 190, 140) to Color.rgb(20, 90, 80),
            Color.rgb(240, 120, 150) to Color.rgb(90, 50, 140),
        )
        var i = 0
        fun next() = palettes[i++ % palettes.size]
        listOf("101532", "113010", "124501", "153320", "171205", "193040").forEach {
            val (a, b) = next(); photo(cam, "IMG_20260815_$it.jpg", a, b)
        }
        listOf("092011", "104455", "141210", "160032").forEach {
            val (a, b) = next(); photo(cam, "IMG_20260816_$it.jpg", a, b)
        }
        listOf("183000", "190512", "201533").forEach {
            val (a, b) = next(); photo(cam, "IMG_20261024_$it.jpg", a, b)
        }
        listOf("WA0001", "WA0002").forEach {
            val (a, b) = next(); photo(wa, "IMG-20261102-$it.jpg", a, b)
        }
        val (a, b) = palettes[0]
        photo(cam, "IMG_20260815_101532_kopie.jpg", a, b)
        File(cam, "IMG_20260815_101532_kopie.jpg").delete()
        File(cam, "IMG_20260815_101532.jpg").copyTo(File(wa, "IMG-20260815-WA0007.jpg"))
        val (c, d) = palettes[3]; photo(cam, "bearbeitet.jpg", c, d)

        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("sources", "${cam.path}\n${wa.path}").commit()
    }

    private val dayList = hasScrollToIndexAction() and
        androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun settle() {
        repeat(8) { rule.waitForIdle(); Thread.sleep(500) }
    }

    /** Zeichnet alle Fenster (Activity + Dialoge) übereinander in ein Bild. */
    private fun shot(name: String, idle: Boolean = true) {
        if (idle) settle() else repeat(5) {
            rule.mainClock.advanceTimeBy(200); Thread.sleep(500)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        lateinit var bmp: Bitmap
        rule.runOnUiThread {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getMethod("getInstance").invoke(null)
            val views = (wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<*>)
                .filterIsInstance<android.view.View>().filter { it.isShown }
            val main = rule.activity.window.decorView
            bmp = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            main.draw(canvas)
            views.filter { it !== main }.forEach { v ->
                canvas.drawColor(Color.argb(110, 0, 0, 0))
                canvas.save()
                canvas.translate((main.width - v.width) / 2f, (main.height - v.height) / 2f)
                v.draw(canvas)
                canvas.restore()
            }
        }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun screenshots() {
        assumeTrue(out != null)
        out!!.mkdirs()

        rule.onNodeWithText("Ordner").performClick()
        shot("1_ordner")

        rule.onNodeWithText("Sortieren").performClick()
        rule.onNodeWithText("Jetzt einlesen").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Vorschau für", substring = true), 10_000)
        // Tage (neueste zuerst): 23.11. (ohne Datum), 02.11. (WhatsApp), 24.10., 16.08., 15.08.
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("Konzert")
        rule.onNode(dayList).performScrollToIndex(3)
        settle()
        rule.onAllNodesWithText("Sonntag, 16.08.2026").onFirst().assertExists()
        val f = rule.onAllNodes(hasSetTextAction())
        val n = f.fetchSemanticsNodes().size
        f[n - 2].performTextInput("Gardasee")
        f[n - 1].performTextInput("Gardasee")
        rule.onNode(dayList).performScrollToIndex(0)
        shot("2_sortieren")

        rule.onAllNodesWithText("Alle wählen")[1].performClick()
        shot("3_auswahl")
        rule.onNodeWithText("Anlass …").performClick()
        shot("4_anlass_dialog", idle = false)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getMethod("getInstance").invoke(null)
            (wmg.getDeclaredField("mViews").apply { isAccessible = true }.get(inst) as List<*>)
                .filterIsInstance<android.view.View>().filter { it !== rule.activity.window.decorView }
                .forEach { v -> v.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK)); v.rootView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK)) }
        }
        rule.mainClock.autoAdvance = true
        rule.runOnUiThread {
            androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java].setAnlassForSelection("Geburtstag Anna")
        }

        rule.onNodeWithText("Vorschau für", substring = true).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ordner unter", substring = true), 10_000)
        shot("5_vorschau")
        rule.onNodeWithText("Verschieben").performClick()
        shot("6_bestaetigung")
        rule.onAllNodesWithText("Verschieben")[1].performClick()
        rule.waitUntilAtLeastOneExists(hasText("Alles sortiert."), 10_000)

        rule.onNodeWithText("Suchen").performClick()
        shot("7_suchen")

        rule.onNodeWithText("Duplikate").performClick()
        rule.onNodeWithText("Duplikate suchen").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Gruppe", substring = true), 10_000)
        shot("8_duplikate")

        rule.onNodeWithText("Verlauf").performClick()
        shot("9_verlauf")
    }
}
