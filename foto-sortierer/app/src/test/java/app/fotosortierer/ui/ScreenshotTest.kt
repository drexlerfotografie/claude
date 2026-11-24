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
        app.fotosortierer.data.AnalysisBackend.geocoder = { _, _, _ -> "Malcesine" }
        app.fotosortierer.data.AnalysisBackend.labeler = { _, bmp ->
            // Simulierte Bilderkennung nach Grundfarbe des Testbilds
            val p = bmp.getPixel(bmp.width / 4, bmp.height / 4)
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            when {
                b > r && b > g -> listOf(app.fotosortierer.core.Label("Lake", 0.9f), app.fotosortierer.core.Label("Sky", 0.8f), app.fotosortierer.core.Label("Beach", 0.6f))
                g > r && g > b -> listOf(app.fotosortierer.core.Label("Plant", 0.9f), app.fotosortierer.core.Label("Mountain", 0.7f))
                r > 200 && g < 160 -> listOf(app.fotosortierer.core.Label("Party", 0.8f), app.fotosortierer.core.Label("Smile", 0.7f), app.fotosortierer.core.Label("Cake", 0.6f))
                else -> listOf(app.fotosortierer.core.Label("Sunset", 0.8f), app.fotosortierer.core.Label("Sky", 0.6f))
            }
        }
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
        cam.listFiles()!!.filter { it.name.startsWith("IMG_202608") }.forEach {
            androidx.exifinterface.media.ExifInterface(it).apply { setLatLong(45.756, 10.808); saveAttributes() }
        }

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
        rule.waitUntilAtLeastOneExists(hasText("ausgewählt", substring = true), 10_000)
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Vorschläge werden berechnet", substring = true).fetchSemanticsNodes().isEmpty() }
        shot("2_sortieren_start")
        // erstes und zweites Ereignis einzeln anhaken
        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[0].performClick()
        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[1].performClick()
        rule.onNode(dayList).performScrollToIndex(2)
        shot("2b_sortieren_vorschlaege")
        rule.onNodeWithText("Alle anwählen").performClick()

        rule.onNode(dayList).performScrollToIndex(0)
        settle()
        rule.onAllNodesWithText("Alle markieren")[1].performClick()
        rule.onNodeWithText("Kategorie").performClick()
        shot("3_kategorie_auswahl")
        rule.onAllNodesWithText("Familie & Freunde").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()

        rule.onNodeWithText("Vorschau für", substring = true).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Ordner unter", substring = true), 10_000)
        shot("4_vorschau")
    }
}
