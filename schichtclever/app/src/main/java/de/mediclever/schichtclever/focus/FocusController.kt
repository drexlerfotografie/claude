package de.mediclever.schichtclever.focus

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.TextUtils
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

    fun isGuardEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GuardService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

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
