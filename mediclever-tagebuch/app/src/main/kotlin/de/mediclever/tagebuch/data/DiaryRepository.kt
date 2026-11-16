package de.mediclever.tagebuch.data

import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.sync.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Einzige Stelle, die Tagebuchdaten schreibt. Jede Aenderung bekommt einen Zeitstempel und
 * wird zum Hochladen vorgemerkt. Loeschen setzt eine Loeschmarke und raeumt Verweise auf.
 */
class DiaryRepository(
    private val db: AppDatabase,
    val prefs: LocalPrefs,
    val assets: AssetStore,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : LocalStore {

    /** Wird nach jeder lokalen Aenderung gerufen, z. B. um den Abgleich anzustossen. */
    var onLocalChange: () -> Unit = {}

    @Suppress("UNCHECKED_CAST")
    private fun dao(t: Table): SyncDao<SyncRow> = when (t) {
        Table.MOODS -> db.moodDao()
        Table.TAG_GROUPS -> db.tagGroupDao()
        Table.TAGS -> db.tagDao()
        Table.SCALES -> db.scaleDao()
        Table.ENTRIES -> db.entryDao()
        Table.ASSETS -> db.assetDao()
        Table.GOALS -> db.goalDao()
        Table.GOAL_CHECKS -> db.goalCheckDao()
        Table.IMPORTANT_DAYS -> db.importantDayDao()
        Table.TEMPLATES -> db.templateDao()
        Table.REMINDERS -> db.reminderDao()
    } as SyncDao<SyncRow>

    private fun tableOf(row: SyncRow): Table = when (row) {
        is Mood -> Table.MOODS
        is TagGroup -> Table.TAG_GROUPS
        is Tag -> Table.TAGS
        is Scale -> Table.SCALES
        is Entry -> Table.ENTRIES
        is Asset -> Table.ASSETS
        is Goal -> Table.GOALS
        is GoalCheck -> Table.GOAL_CHECKS
        is ImportantDay -> Table.IMPORTANT_DAYS
        is Template -> Table.TEMPLATES
        is Reminder -> Table.REMINDERS
        else -> error("Unbekannte Zeile")
    }

    private val setup = combine(
        db.moodDao().observe(), db.tagGroupDao().observe(), db.tagDao().observe(), db.scaleDao().observe(),
    ) { m, g, t, s -> Diary(moods = m, groups = g, tags = t, scales = s) }

    private val content = combine(
        db.entryDao().observe(), db.assetDao().observe(), db.goalDao().observe(), db.goalCheckDao().observe(),
    ) { e, a, g, c -> Diary(entries = e, assets = a, goals = g, goalChecks = c) }

    private val rest = combine(
        db.importantDayDao().observe(), db.templateDao().observe(), db.reminderDao().observe(), prefs.state,
    ) { d, t, r, p -> Diary(importantDays = d, templates = t, reminders = r, settings = p.settings) }

    /** Der ganze Bestand, sortiert. null, solange die Datenbank noch laedt. */
    val diary: StateFlow<Diary?> = combine(setup, content, rest) { a, b, c ->
        Diary(
            moods = a.moods.sortedWith(compareBy({ it.level }, { it.sortOrder }, { it.name })),
            groups = a.groups.sortedBy { it.sortOrder },
            tags = a.tags.sortedWith(compareBy({ it.sortOrder }, { it.name })),
            scales = a.scales.sortedBy { it.sortOrder },
            entries = b.entries.sortedByDescending { it.timestamp },
            assets = b.assets,
            goals = b.goals.sortedBy { it.sortOrder },
            goalChecks = b.goalChecks,
            importantDays = c.importantDays.sortedBy { it.sortOrder },
            templates = c.templates.sortedBy { it.sortOrder },
            reminders = c.reminders.sortedBy { it.minuteOfDay },
            settings = c.settings,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // ---- Schreiben -------------------------------------------------------------------

    /** Speichert Zeilen beliebiger Art mit neuem Aenderungsstempel. */
    suspend fun save(rows: List<SyncRow>) {
        if (rows.isEmpty()) return
        val t = now()
        rows.groupBy { tableOf(it) }.forEach { (table, list) -> dao(table).upsert(list.map { it.stamped(t, it.deleted) }) }
        onLocalChange()
    }
    suspend fun save(vararg rows: SyncRow) = save(rows.toList())

    private suspend fun markDeleted(rows: List<SyncRow>) {
        if (rows.isEmpty()) return
        val t = now()
        rows.groupBy { tableOf(it) }.forEach { (table, list) -> dao(table).upsert(list.map { it.stamped(t, deleted = true) }) }
        onLocalChange()
    }

    /** Speichert einen Eintrag und hakt Ziele ab, die an eine seiner Aktivitaeten geknuepft sind. */
    suspend fun saveEntry(entry: Entry) {
        val linked = db.goalDao().all().filter { g ->
            g.tagId != null && g.tagId in entry.tagIds && g.archivedAt == null &&
                g.startDate <= entry.localDate && (g.endDate == null || entry.localDate <= g.endDate!!)
        }
        val checks = linked.map { GoalCheck(GoalCheck.idFor(it.id, entry.localDate), it.id, entry.localDate) }
        save(listOf<SyncRow>(entry) + checks)
    }

    suspend fun deleteEntry(entry: Entry) {
        val all = db.entryDao().all()
        // Dateien nur entfernen, wenn kein anderer Eintrag sie nutzt
        val orphaned = entry.assetIds.filter { id -> all.none { it.id != entry.id && id in it.assetIds } }
        deleteAssets(orphaned)
        markDeleted(listOf(entry))
    }

    suspend fun deleteAssets(ids: List<String>) {
        val rows = db.assetDao().byIds(ids)
        rows.forEach { assets.delete(it) }
        markDeleted(rows)
    }

    suspend fun deleteMood(mood: Mood, replacement: Mood?) {
        val affected = db.entryDao().all().filter { it.moodId == mood.id }
        if (replacement != null) save(affected.map { it.copy(moodId = replacement.id) }) else affected.forEach { deleteEntry(it) }
        markDeleted(listOf(mood))
    }

    /** Loescht eine Aktivitaet und nimmt sie aus allen Eintraegen. Ziele verlieren die Verknuepfung. */
    suspend fun deleteTag(tag: Tag) {
        save(db.entryDao().all().filter { tag.id in it.tagIds }.map { it.copy(tagIds = it.tagIds - tag.id) })
        save(db.goalDao().all().filter { it.tagId == tag.id }.map { it.copy(tagId = null) })
        markDeleted(listOf(tag))
    }

    /** Ersetzt eine Aktivitaet durch eine andere (zwei zusammenfuehren). from wird danach geloescht. */
    suspend fun replaceTag(from: Tag, into: Tag) {
        save(db.entryDao().all().filter { from.id in it.tagIds }.map { e ->
            e.copy(tagIds = e.tagIds.map { if (it == from.id) into.id else it }.distinct())
        })
        save(db.goalDao().all().filter { it.tagId == from.id }.map { it.copy(tagId = into.id) })
        markDeleted(listOf(from))
    }

    /** Loescht eine Gruppe. Ihre Aktivitaeten bleiben erhalten und stehen danach ohne Gruppe da. */
    suspend fun deleteGroup(group: TagGroup) {
        save(db.tagDao().all().filter { it.groupId == group.id }.map { it.copy(groupId = null) })
        markDeleted(listOf(group))
    }

    suspend fun deleteScale(scale: Scale) {
        save(db.entryDao().all().filter { e -> e.scaleValues.any { it.scaleId == scale.id } }
            .map { e -> e.copy(scaleValues = e.scaleValues.filter { it.scaleId != scale.id }) })
        markDeleted(listOf(scale))
    }

    suspend fun deleteGoal(goal: Goal) {
        markDeleted(db.goalCheckDao().all().filter { it.goalId == goal.id } + goal)
    }

    suspend fun setGoalCheck(goalId: String, date: String, checked: Boolean) {
        val row = GoalCheck(GoalCheck.idFor(goalId, date), goalId, date)
        if (checked) save(row) else markDeleted(listOf(row))
    }

    suspend fun deleteSimple(row: SyncRow) {
        require(row is ImportantDay || row is Template || row is Reminder) { "Eigener Loeschweg noetig" }
        if (row is ImportantDay && row.assetId != null) deleteAssets(listOf(row.assetId!!))
        markDeleted(listOf(row))
    }

    suspend fun updateSettings(change: (UserSettings) -> UserSettings) {
        val s = change(prefs.current().settings).copy(updatedAt = now())
        prefs.setSettings(s, dirty = true)
        onLocalChange()
    }

    /** Uebernimmt einen Import. Bestehende Zeilen mit gleicher ID werden ueberschrieben. */
    suspend fun importDiary(d: Diary, withSettings: Boolean) {
        save(d.moods + d.groups + d.tags + d.scales + d.entries + d.assets + d.goals + d.goalChecks +
            d.importantDays + d.templates + d.reminders)
        if (withSettings) updateSettings { cur ->
            d.settings.copy(themeMode = cur.themeMode, fontChoice = cur.fontChoice, moodScheme = d.settings.moodScheme)
        }
    }

    /** Alles auf dem Geraet loeschen (Konto loeschen, Zuruecksetzen). */
    suspend fun wipeLocal() {
        Table.entries.forEach { dao(it).wipe() }
        assets.wipe()
        prefs.wipe()
    }

    /** Nach Anmeldung mit einem Konto: alles erneut hochladen lassen. */
    suspend fun markEverythingDirty() {
        Table.entries.forEach { dao(it).markAllDirty() }
        prefs.setSettings(prefs.current().settings, dirty = true)
        prefs.resetCursors()
    }

    suspend fun snapshot(): Diary = Diary(
        moods = db.moodDao().all(), groups = db.tagGroupDao().all(), tags = db.tagDao().all(), scales = db.scaleDao().all(),
        entries = db.entryDao().all(), assets = db.assetDao().all(), goals = db.goalDao().all(),
        goalChecks = db.goalCheckDao().all(), importantDays = db.importantDayDao().all(),
        templates = db.templateDao().all(), reminders = db.reminderDao().all(), settings = prefs.current().settings,
    )

    // ---- LocalStore fuer den Abgleich -------------------------------------------------

    override suspend fun dirty(table: Table): List<SyncRow> = dao(table).dirty()
    override suspend fun byIds(table: Table, ids: List<String>): List<SyncRow> =
        ids.chunked(500).flatMap { dao(table).byIds(it) }
    override suspend fun upsert(table: Table, rows: List<SyncRow>) {
        dao(table).upsert(rows)
        // Von einem anderen Geraet geloeschte Dateien auch hier entfernen
        if (table == Table.ASSETS) rows.filter { it.deleted }.forEach { assets.delete(it as Asset) }
    }
    override suspend fun markClean(table: Table, id: String, updatedAt: Long) = dao(table).markClean(id, updatedAt)
    override suspend fun cursor(table: Table): Long = prefs.cursor(table)
    override suspend fun setCursor(table: Table, cursor: Long) { prefs.setCursor(table, cursor) }
    override suspend fun settings(): UserSettings = prefs.current().settings
    override suspend fun settingsDirty(): Boolean = prefs.current().settingsDirty
    override suspend fun applyRemoteSettings(settings: UserSettings) { prefs.setSettings(settings, dirty = false) }
    override suspend fun markSettingsClean(updatedAt: Long) = prefs.markSettingsClean(updatedAt)
}
