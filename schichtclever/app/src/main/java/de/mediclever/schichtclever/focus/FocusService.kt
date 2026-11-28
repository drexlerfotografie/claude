package de.mediclever.schichtclever.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import de.mediclever.schichtclever.MainActivity
import de.mediclever.schichtclever.R
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * Läuft als Vordergrunddienst (mit Benachrichtigung), solange Fokus oder Blaufilter gebraucht werden.
 * - App-Sperre: fragt jede Sekunde über die Nutzungsstatistik ab, welche App gerade vorne ist.
 *   Gelesen wird nur der Paketname, keine Inhalte.
 * - Blaufilter: legt ein Overlay über den Bildschirm ("Über anderen Apps einblenden").
 * Bewusst ohne Bedienungshilfe: Die blockiert Google Play Protect bei selbst installierten Apps.
 */
class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastPkg: String? = null
    private var lastCheck = System.currentTimeMillis()

    private val loop = object : Runnable {
        override fun run() {
            val data = Repository.data.value
            if (!FocusController.serviceNeeded(data)) { stopSelf(); return }
            applyFilter(data)
            if (data.focusActive && data.settings.blockedApps.isNotEmpty()) checkForeground(data)
            updateNotification(data)
            handler.postDelayed(this, if (data.focusActive) 1_000 else 30_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Repository.init(applicationContext)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Laufende Schicht", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Zeigt an, dass Fokus-Modus oder Blaufilter aktiv sind."
            setShowBadge(false)
        })
        val n = buildNotification(Repository.data.value)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        removeOverlay()
        super.onDestroy()
    }

    private fun checkForeground(data: AppData) {
        if (!FocusController.hasUsageAccess(this)) return
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastCheck - 2_000, now)
        lastCheck = now
        val e = UsageEvents.Event()
        var fg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) fg = e.packageName
        }
        if (fg == null) return
        val previous = lastPkg
        lastPkg = fg
        if (fg == packageName || fg !in data.settings.blockedApps) return
        if ((allowedUntil[fg] ?: 0L) > now) return
        if (fg == previous && now - lastBlockAt < 1_500) return
        lastBlockAt = now
        // Mit "Über anderen Apps einblenden" darf der Dienst die Sperrseite aus dem Hintergrund öffnen.
        runCatching {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .putExtra(BlockActivity.EXTRA_PACKAGE, fg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.onFailure { Log.w("SchichtClever", "Sperrseite konnte nicht geöffnet werden", it) }
    }

    private var lastBlockAt = 0L

    private fun applyFilter(data: AppData) {
        val wm = getSystemService(WindowManager::class.java)
        if (!FocusController.filterActive(data) || !Settings.canDrawOverlays(this)) { removeOverlay(); return }
        val (color, alpha) = filterColor(data.settings.filterIntensity, data.settings.filterDim)
        val existing = overlay
        if (existing != null) {
            existing.setBackgroundColor(color)
            val lp = existing.layoutParams as WindowManager.LayoutParams
            if (lp.alpha != alpha) { lp.alpha = alpha; runCatching { wm.updateViewLayout(existing, lp) } }
            return
        }
        val v = View(this).apply { setBackgroundColor(color) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.alpha = alpha
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { wm.addView(v, lp); overlay = v }.onFailure { Log.w("SchichtClever", "Overlay fehlgeschlagen", it) }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
    }

    private fun buildNotification(data: AppData): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val shift = data.activeShift
        val parts = buildList {
            if (data.focusActive) add("Fokus an · ${data.settings.blockedApps.size} Apps pausiert")
            if (FocusController.filterActive(data)) add("Blaufilter ${(data.settings.filterIntensity * 100).toInt()} %")
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_focus)
            .setContentTitle(shift?.let { "${it.type.code} · ${it.type.label} läuft" } ?: "SchichtClever")
            .setContentText(parts.joinToString(" · ").ifEmpty { "Blaufilter-Zeitplan aktiv" })
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(data: AppData) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(data))
    }

    companion object {
        private const val CHANNEL = "schicht"
        private const val NOTIF_ID = 1

        /** Kurzzeitig freigegebene Apps (Paket → erlaubt bis). */
        val allowedUntil = ConcurrentHashMap<String, Long>()

        /**
         * Bernstein-Ton plus Abdunklung. Liefert eine deckende Farbe und die Fenster-Transparenz.
         * Die Transparenz bleibt unter 0,8: Android 12+ lässt Berührungen sonst nicht durch das Overlay.
         */
        fun filterColor(intensity: Float, dim: Float): Pair<Int, Float> {
            val amber = intensity.coerceIn(0f, 1f) * 0.5f
            val dark = dim.coerceIn(0f, 1f) * 0.6f
            val a = (1f - (1f - amber) * (1f - dark)).coerceAtMost(0.8f)
            if (a <= 0f) return Color.TRANSPARENT to 0f
            val share = amber / (amber + dark).coerceAtLeast(0.0001f)
            return Color.rgb((255 * share).toInt(), (138 * share).toInt(), 0) to a
        }

        /** Für die Vorschau in der App: dieselbe Farbe als ARGB. */
        fun previewArgb(intensity: Float, dim: Float): Int {
            val (c, a) = filterColor(intensity, dim)
            return (((a * 255).toInt() shl 24) or (c and 0xFFFFFF))
        }

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, FocusService::class.java)) }
                .onFailure { Log.w("SchichtClever", "Dienst konnte nicht gestartet werden", it) }
        }
    }
}
