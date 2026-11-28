package de.mediclever.schichtclever.focus

import android.app.AppOpsManager
import android.app.NotificationManager
import android.os.Build
import android.os.Process
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.quicksettings.TileService
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.FilterSchedule
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.hourOfDay
import de.mediclever.schichtclever.tiles.EinsatzTileService
import de.mediclever.schichtclever.tiles.FocusTileService
import de.mediclever.schichtclever.tiles.ShiftTileService

object FocusController {

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Der Dienst läuft, solange gesperrt oder gefiltert werden muss – oder der Filter-Zeitplan wartet. */
    fun serviceNeeded(data: AppData): Boolean =
        (data.focusActive && data.settings.blockedApps.isNotEmpty()) ||
            filterActive(data) || data.settings.filterSchedule == FilterSchedule.ZEITRAUM

    fun filterActive(data: AppData, now: Long = System.currentTimeMillis()): Boolean {
        val s = data.settings
        if (s.filterManualOn) return true
        return when (s.filterSchedule) {
            FilterSchedule.MIT_SCHICHT -> data.activeShift?.type?.night == true
            FilterSchedule.ZEITRAUM -> now.hourOfDay().let { it >= 22 || it < 6 }
            FilterSchedule.MANUELL -> false
        }
    }

    /** Gleicht "Bitte nicht stören" und die Kacheln mit dem aktuellen Zustand ab. */
    fun sync(context: Context, data: AppData) {
        syncDnd(context, data)
        if (serviceNeeded(data)) FocusService.start(context)
        listOf(ShiftTileService::class.java, EinsatzTileService::class.java, FocusTileService::class.java).forEach {
            runCatching { TileService.requestListeningState(context, ComponentName(context, it)) }
        }
    }

    private fun syncDnd(context: Context, data: AppData) {
        if (!hasDndAccess(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val wantDnd = data.focusActive && data.settings.dndEnabled
        if (wantDnd && !data.settings.dndSetByApp) {
            // Nur einschalten, wenn der Nutzer nicht schon selbst einen Modus aktiv hat.
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Repository.settings { it.copy(dndSetByApp = true) }
            }
        } else if (!wantDnd && data.settings.dndSetByApp) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            Repository.settings { it.copy(dndSetByApp = false) }
        }
    }
}
