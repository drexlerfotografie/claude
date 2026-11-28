package de.mediclever.schichtclever.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import de.mediclever.schichtclever.MainActivity
import de.mediclever.schichtclever.data.Repository
import de.mediclever.schichtclever.data.ShiftType
import de.mediclever.schichtclever.data.minuteOfDay
import de.mediclever.schichtclever.ui.durationHm
import de.mediclever.schichtclever.ui.time

abstract class BaseTile : TileService() {
    override fun onStartListening() {
        Repository.init(applicationContext)
        qsTile?.let { render(it); it.updateTile() }
    }

    abstract fun render(tile: Tile)

    /** Öffnet die App (und klappt die Schnelleinstellungen zu). */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun openApp(route: String? = null) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) } }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, route.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    protected fun refresh() { qsTile?.let { render(it); it.updateTile() } }
}

/** Startet bzw. beendet die Schicht. Der Schichttyp wird aus der Uhrzeit geschätzt. */
class ShiftTileService : BaseTile() {
    override fun render(tile: Tile) {
        val shift = Repository.data.value.activeShift
        tile.state = if (shift != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (shift != null) "${shift.type.code} aktiv" else "Schicht starten"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = shift?.let { "seit ${durationHm(System.currentTimeMillis() - it.startAt)} h" } ?: guess()?.let { "${it.code} · ${it.label}" } ?: "Keine Vorlage"
        }
    }

    private fun guess() = ShiftType.guess(Repository.data.value.settings.shiftTemplates, System.currentTimeMillis().minuteOfDay())

    override fun onClick() {
        val shift = Repository.data.value.activeShift
        if (shift == null) {
            val t = guess()
            if (t == null) openApp("vorlagen") else { Repository.startShift(t); refresh() }
        } else {
            // Beenden mit Zusammenfassung passiert in der App.
            openApp("schicht")
        }
    }
}

/** Ein Tipp = ein Einsatz mit Zeitstempel. Details trägt man später nach. */
class EinsatzTileService : BaseTile() {
    override fun render(tile: Tile) {
        val d = Repository.data.value
        val count = d.activeShift?.let { s -> d.einsaetze.count { it.shiftId == s.id } }
        tile.state = if (d.activeShift != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "+ Einsatz"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = count?.let { "$it in dieser Schicht" } ?: "Zeit festhalten"
    }

    override fun onClick() {
        val e = Repository.quickEinsatz()
        refresh()
        Toast.makeText(this, "Einsatz um ${e.alarmAt.time()} gespeichert", Toast.LENGTH_SHORT).show()
    }

    override fun onTileAdded() = refresh()
}

/** Fokus-Modus unabhängig von der Schicht ein- und ausschalten. */
class FocusTileService : BaseTile() {
    override fun render(tile: Tile) {
        val d = Repository.data.value
        tile.state = if (d.focusActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (d.focusActive) "Fokus an" else "Fokus"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "${d.settings.blockedApps.size} Apps pausiert"
    }

    override fun onClick() {
        Repository.setShiftFocus(!Repository.data.value.focusActive)
        refresh()
    }
}
