package de.mediclever.tagebuch.core.sync

import de.mediclever.tagebuch.core.model.SyncRow
import de.mediclever.tagebuch.core.model.Table
import de.mediclever.tagebuch.core.model.UserSettings
import de.mediclever.tagebuch.core.model.withDirty

/** Zeilen einer Sammlung, die sich auf dem Server seit dem Merker geaendert haben. */
data class Pulled(val rows: List<SyncRow>, val cursor: Long)

/** Serverseite des Abgleichs. Die App setzt das mit Firestore um, Tests mit einem Speicher im RAM. */
interface RemoteStore {
    /** Liefert alle Zeilen mit Serverstempel groesser als cursor und den neuen Merker. */
    suspend fun pull(table: Table, cursor: Long): Pulled
    suspend fun push(table: Table, rows: List<SyncRow>)
    suspend fun pullSettings(): UserSettings?
    suspend fun pushSettings(settings: UserSettings)
}

/** Geraeteseite des Abgleichs. */
interface LocalStore {
    suspend fun dirty(table: Table): List<SyncRow>
    suspend fun byIds(table: Table, ids: List<String>): List<SyncRow>
    suspend fun upsert(table: Table, rows: List<SyncRow>)
    /** Setzt dirty nur zurueck, wenn die Zeile seit dem Hochladen nicht erneut geaendert wurde. */
    suspend fun markClean(table: Table, id: String, updatedAt: Long)
    suspend fun cursor(table: Table): Long
    suspend fun setCursor(table: Table, cursor: Long)
    suspend fun settings(): UserSettings
    suspend fun settingsDirty(): Boolean
    suspend fun applyRemoteSettings(settings: UserSettings)
    suspend fun markSettingsClean(updatedAt: Long)
}

data class SyncReport(val pulled: Int, val pushed: Int)

/**
 * Abgleich in zwei Schritten je Sammlung: erst holen, dann hochladen.
 * Bei Konflikten gewinnt die Zeile mit dem juengeren updatedAt. Bei Gleichstand gewinnt
 * der Server, ausser die lokale Zeile wartet noch aufs Hochladen.
 */
class SyncEngine(private val local: LocalStore, private val remote: RemoteStore) {

    suspend fun syncOnce(): SyncReport {
        var pulled = 0
        var pushed = 0
        for (table in Table.entries) {
            val p = remote.pull(table, local.cursor(table))
            if (p.rows.isNotEmpty()) {
                val mine = local.byIds(table, p.rows.map { it.id }).associateBy { it.id }
                val take = p.rows.filter { r ->
                    val l = mine[r.id]
                    l == null || r.updatedAt > l.updatedAt || (r.updatedAt == l.updatedAt && !l.dirty)
                }
                // Nur echte Aenderungen schreiben, sonst flackern Listen ohne Grund
                val changed = take.filter { r -> mine[r.id]?.withDirty(false) != r.withDirty(false) }
                if (changed.isNotEmpty()) local.upsert(table, changed.map { it.withDirty(false) })
                pulled += changed.size
            }
            if (p.cursor > local.cursor(table)) local.setCursor(table, p.cursor)

            val dirty = local.dirty(table)
            for (chunk in dirty.chunked(PUSH_CHUNK)) {
                remote.push(table, chunk)
                chunk.forEach { local.markClean(table, it.id, it.updatedAt) }
                pushed += chunk.size
            }
        }

        val remoteSettings = remote.pullSettings()
        val mine = local.settings()
        when {
            remoteSettings != null && remoteSettings.updatedAt > mine.updatedAt -> local.applyRemoteSettings(remoteSettings)
            local.settingsDirty() || remoteSettings == null -> {
                remote.pushSettings(mine)
                local.markSettingsClean(mine.updatedAt)
            }
        }
        return SyncReport(pulled, pushed)
    }

    companion object {
        /** Firestore erlaubt hoechstens 500 Schreibvorgaenge je Stapel. */
        const val PUSH_CHUNK = 400
    }
}
