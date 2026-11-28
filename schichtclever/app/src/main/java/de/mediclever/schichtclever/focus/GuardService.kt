package de.mediclever.schichtclever.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Bedienungshilfe-Dienst für zwei Dinge:
 * 1. Merkt, wenn während des Fokus-Modus eine gesperrte App in den Vordergrund kommt, und zeigt den Sperrbildschirm.
 * 2. Legt den Blaufilter als Overlay über den ganzen Bildschirm (ohne extra Overlay-Berechtigung).
 * Der Dienst liest keine Bildschirminhalte, nur den Paketnamen der Vordergrund-App.
 */
class GuardService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastBlocked: Pair<String, Long>? = null

    private val tick = object : Runnable {
        override fun run() {
            applyFilter(Repository.data.value)
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onServiceConnected() {
        Repository.init(applicationContext)
        scope.launch { Repository.data.collectLatest { applyFilter(it) } }
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val data = Repository.data.value
        if (!data.focusActive || pkg !in data.settings.blockedApps) return
        if ((allowedUntil[pkg] ?: 0L) > System.currentTimeMillis()) return
        // Mehrfach-Events derselben App kurz hintereinander ignorieren.
        val now = System.currentTimeMillis()
        lastBlocked?.let { (p, t) -> if (p == pkg && now - t < 1500) return }
        lastBlocked = pkg to now
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
        )
    }

    private fun applyFilter(data: AppData) {
        val active = FocusController.filterActive(data)
        val wm = getSystemService(WindowManager::class.java)
        if (!active) {
            overlay?.let { runCatching { wm.removeView(it) } }
            overlay = null
            return
        }
        val color = filterColor(data.settings.filterIntensity, data.settings.filterDim)
        val view = overlay ?: View(this).also { v ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            runCatching { wm.addView(v, params) }.onFailure { return }
            overlay = v
        }
        view.setBackgroundColor(color)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Kurzzeitig freigegebene Apps (Paket → Zeitpunkt, bis zu dem die App erlaubt ist). */
        val allowedUntil = ConcurrentHashMap<String, Long>()

        /** Warmer Bernstein-Ton plus optionale Abdunklung, zu einer Farbe verrechnet. */
        fun filterColor(intensity: Float, dim: Float): Int {
            val amberAlpha = (intensity.coerceIn(0f, 1f) * 0.5f)
            val dimAlpha = dim.coerceIn(0f, 1f) * 0.6f
            // Overlay = Amber über Schwarz-Dimmung, vereinfacht als eine Farbe
            val a = 1f - (1f - amberAlpha) * (1f - dimAlpha)
            if (a <= 0f) return Color.TRANSPARENT
            val amberShare = amberAlpha / a
            val r = (255 * amberShare).toInt()
            val g = (138 * amberShare).toInt()
            val b = (0 * amberShare).toInt()
            return Color.argb((a * 255).toInt(), r, g, b)
        }
    }
}
