package de.mediclever.tagebuch.core.importer

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.SyncRow
import de.mediclever.tagebuch.core.model.UserSettings

enum class MergeMode {
    /** In ein leeres Tagebuch importieren: die vorhandene Grundeinrichtung wird ersetzt. */
    EMPTY_ONLY,

    /** Mit dem bestehenden Tagebuch zusammenfuehren: gleiche Namen werden zusammengelegt. */
    MERGE,
}

enum class ImportKind(val label: String) {
    MOODS("Stimmungen"),
    GROUPS("Gruppen"),
    TAGS("Aktivitäten"),
    SCALES("Skalen"),
    ENTRIES("Einträge"),
    ASSETS("Fotos und Sprachnotizen"),
    GOALS("Ziele"),
    GOAL_CHECKS("Abgehakte Tage"),
    IMPORTANT_DAYS("Wichtige Tage"),
    TEMPLATES("Vorlagen"),
    REMINDERS("Erinnerungen"),
}

data class KindReport(val taken: Int = 0, val skipped: Int = 0, val errors: Int = 0)

/** Abschlussbericht: uebernommen, uebersprungen, Fehler, jeweils mit Anzahl je Art. */
data class MergeReport(val kinds: Map<ImportKind, KindReport>) {
    operator fun get(kind: ImportKind): KindReport = kinds[kind] ?: KindReport()
    val taken: Int get() = kinds.values.sumOf { it.taken }
    val skipped: Int get() = kinds.values.sumOf { it.skipped }
    val errors: Int get() = kinds.values.sumOf { it.errors }
}

data class MergeResult(val diary: Diary, val report: MergeReport)

/**
 * Fuehrt einen gelesenen Datenbestand mit dem vorhandenen zusammen. Rein rechnerisch,
 * gespeichert wird hier nichts; die App schreibt das Ergebnis in die Datenbank.
 *
 * Grundregel fuer beide Arten: was es mit derselben ID schon gibt, bleibt unveraendert und
 * zaehlt als uebersprungen. Weil der Import feste IDs vergibt, erzeugt ein zweiter Lauf
 * derselben Datei keine Doppelten, und ein abgebrochener Lauf laesst sich fortsetzen.
 * Als geloescht markierte Zeilen mit derselben ID werden wiederhergestellt.
 */
object DiaryMerge {

    /** Leer heisst: keine Eintraege. Die Grundeinrichtung (Stimmungen, Beispielaktivitaeten) zaehlt nicht. */
    fun isEmpty(diary: Diary): Boolean = diary.entries.none { !it.deleted }

    /**
     * @throws ImportException bei [MergeMode.EMPTY_ONLY], wenn das Tagebuch schon Eintraege hat,
     *   die nicht aus derselben Datei stammen.
     */
    fun merge(
        existing: Diary,
        incoming: Diary,
        mode: MergeMode,
        now: Long = System.currentTimeMillis(),
    ): MergeResult {
        if (mode == MergeMode.EMPTY_ONLY) {
            val incomingIds = incoming.entries.mapTo(HashSet()) { it.id }
            if (existing.entries.any { !it.deleted && it.id !in incomingIds }) {
                throw ImportException("Dein Tagebuch enthält schon Einträge. Wähle „Zusammenführen“, um sie zu behalten.")
            }
        }
        val byName = mode == MergeMode.MERGE
        val report = HashMap<ImportKind, KindReport>()
        fun count(kind: ImportKind, taken: Int = 0, skipped: Int = 0, errors: Int = 0) {
            val old = report[kind] ?: KindReport()
            report[kind] = KindReport(old.taken + taken, old.skipped + skipped, old.errors + errors)
        }

        /**
         * Legt eine Liste zusammen. [mapTo] liefert die ID einer vorhandenen Zeile, auf die
         * die neue abgebildet wird (Namensgleichheit), oder null. Ergebnis: neue Liste und
         * Zuordnung alte ID -> gueltige ID.
         */
        fun <T : SyncRow> combine(
            kind: ImportKind,
            old: List<T>,
            new: List<T>,
            prepare: (T) -> T? = { it },
            mapTo: (T) -> String? = { null },
        ): Pair<List<T>, Map<String, String>> {
            val result = old.toMutableList()
            val index = HashMap<String, Int>()
            old.forEachIndexed { i, row -> index[row.id] = i }
            val ids = HashMap<String, String>()
            for (raw in new) {
                if (raw.deleted) continue
                val at = index[raw.id]
                if (at != null && !result[at].deleted) {
                    ids[raw.id] = raw.id
                    count(kind, skipped = 1)
                    continue
                }
                val row = prepare(raw)
                if (row == null) {
                    count(kind, errors = 1)
                    continue
                }
                val target = if (at == null) mapTo(row) else null
                if (target != null) {
                    ids[raw.id] = target
                    count(kind, skipped = 1)
                    continue
                }
                ids[raw.id] = row.id
                if (at != null) result[at] = row else { index[row.id] = result.size; result.add(row) }
                count(kind, taken = 1)
            }
            return result to ids
        }

        fun key(name: String) = name.trim().lowercase()

        // --- Einrichtung -----------------------------------------------------
        val (moods, moodIds) = combine(ImportKind.MOODS, existing.moods, incoming.moods, mapTo = { mood ->
            // Gleicher Name reicht nur bei gleicher Stufe, sonst wuerden Auswertungen verfaelscht.
            if (!byName) null
            else existing.moods.firstOrNull { !it.deleted && it.level == mood.level && key(it.name) == key(mood.name) }?.id
        })

        val (groups, groupIds) = combine(ImportKind.GROUPS, existing.groups, incoming.groups, mapTo = { group ->
            if (!byName) null else existing.groups.firstOrNull { !it.deleted && key(it.name) == key(group.name) }?.id
        })

        val (tags, tagIds) = combine(
            ImportKind.TAGS, existing.tags, incoming.tags,
            prepare = { tag -> tag.copy(groupId = tag.groupId?.let { groupIds[it] }) },
            mapTo = { tag ->
                if (!byName) null
                else {
                    val same = existing.tags.filter { !it.deleted && key(it.name) == key(tag.name) }
                    (same.firstOrNull { it.groupId == tag.groupId } ?: same.firstOrNull())?.id
                }
            },
        )

        // Bei Textskalen muessen auch die Punkte zugeordnet werden: alte Punkt-ID -> vorhandene Punkt-ID.
        val pointIds = HashMap<String, String>()
        val (scales, scaleIds) = combine(ImportKind.SCALES, existing.scales, incoming.scales, mapTo = { scale ->
            if (!byName) null
            else existing.scales.firstOrNull { old ->
                !old.deleted && old.type == scale.type && key(old.name) == key(scale.name) &&
                    matchPoints(old, scale)?.also { pointIds.putAll(it) } != null
            }?.id
        })

        // --- Dateien, Eintraege ------------------------------------------------
        val (assets, assetIds) = combine(ImportKind.ASSETS, existing.assets, incoming.assets)

        val scaleById = scales.associateBy { it.id }
        val (entries, _) = combine(ImportKind.ENTRIES, existing.entries, incoming.entries, prepare = { entry ->
            val moodId = moodIds[entry.moodId]
            if (moodId == null) null
            else entry.copy(
                moodId = moodId,
                tagIds = entry.tagIds.mapNotNull { tagIds[it] }.distinct(),
                scaleValues = entry.scaleValues.mapNotNull { value ->
                    val scaleId = scaleIds[value.scaleId] ?: return@mapNotNull null
                    val pointId = value.pointId?.let { pointIds[it] ?: it }
                    // Ein Textwert ohne passenden Punkt waere unbrauchbar.
                    if (pointId != null && scaleById[scaleId]?.points?.none { it.id == pointId } == true) null
                    else value.copy(scaleId = scaleId, pointId = pointId)
                }.distinctBy { it.scaleId },
                assetIds = entry.assetIds.filter { it in assetIds },
            )
        })

        // --- Ziele und der Rest ------------------------------------------------
        val (goals, goalIds) = combine(ImportKind.GOALS, existing.goals, incoming.goals, prepare = { goal ->
            goal.copy(tagId = goal.tagId?.let { tagIds[it] })
        })
        val (goalChecks, _) = combine(ImportKind.GOAL_CHECKS, existing.goalChecks, incoming.goalChecks, prepare = { check ->
            if (goalIds[check.goalId] == null) null else check
        })
        val (importantDays, _) = combine(ImportKind.IMPORTANT_DAYS, existing.importantDays, incoming.importantDays, prepare = { day ->
            day.copy(assetId = day.assetId?.takeIf { it in assetIds })
        })
        val (templates, _) = combine(ImportKind.TEMPLATES, existing.templates, incoming.templates)
        val (reminders, _) = combine(ImportKind.REMINDERS, existing.reminders, incoming.reminders)

        var diary = Diary(
            moods = moods, groups = groups, tags = tags, scales = scales, entries = entries, assets = assets,
            goals = goals, goalChecks = goalChecks, importantDays = importantDays, templates = templates,
            reminders = reminders,
            settings = if (mode == MergeMode.EMPTY_ONLY) mergeSettings(existing.settings, incoming.settings, now) else existing.settings,
        )
        if (mode == MergeMode.EMPTY_ONLY) diary = dropUnusedSetup(diary, incoming, now)
        return MergeResult(diary, MergeReport(ImportKind.entries.associateWith { report[it] ?: KindReport() }))
    }

    /**
     * Ordnet die Punkte einer neuen Textskala den Punkten einer vorhandenen zu, ueber die
     * Bezeichnung. Null, wenn ein Punkt keine Entsprechung hat: dann sind es zwei verschiedene Skalen.
     */
    private fun matchPoints(old: Scale, new: Scale): Map<String, String>? {
        if (new.type != ScaleType.TEXT) return emptyMap()
        val map = HashMap<String, String>()
        for (point in new.points) {
            val match = old.points.firstOrNull { it.label.trim().equals(point.label.trim(), ignoreCase = true) } ?: return null
            map[point.id] = match.id
        }
        return map
    }

    /** Uebernimmt aus der Datei jede Einstellung, die dort vom Standard abweicht. */
    private fun mergeSettings(old: UserSettings, new: UserSettings, now: Long): UserSettings {
        val std = UserSettings()
        val merged = old.copy(
            weekStart = if (new.weekStart != std.weekStart) new.weekStart else old.weekStart,
            midnightRule = if (new.midnightRule != std.midnightRule) new.midnightRule else old.midnightRule,
            showQuote = if (new.showQuote != std.showQuote) new.showQuote else old.showQuote,
            showMemories = if (new.showMemories != std.showMemories) new.showMemories else old.showMemories,
            themeMode = if (new.themeMode != std.themeMode) new.themeMode else old.themeMode,
            moodScheme = if (new.moodScheme != std.moodScheme) new.moodScheme else old.moodScheme,
            customMoodColors = new.customMoodColors.ifEmpty { old.customMoodColors },
            fontChoice = if (new.fontChoice != std.fontChoice) new.fontChoice else old.fontChoice,
            reportNotifications = if (new.reportNotifications != std.reportNotifications) new.reportNotifications else old.reportNotifications,
        )
        return if (merged == old) old else merged.copy(updatedAt = now)
    }

    /**
     * Leeres Tagebuch: die vorher vorhandene Grundeinrichtung, die nicht aus der Datei stammt
     * und von nichts benutzt wird, bekommt eine Loeschmarke. So hat der Nutzer danach genau
     * die Einrichtung aus der Datei, und die Loeschung erreicht auch andere Geraete.
     */
    private fun dropUnusedSetup(diary: Diary, incoming: Diary, now: Long): Diary {
        val live = diary.entries.filter { !it.deleted }
        val usedMoods = live.mapTo(HashSet()) { it.moodId }
        val usedTags = live.flatMapTo(HashSet()) { it.tagIds }
        diary.goals.filter { !it.deleted }.mapNotNullTo(usedTags) { it.tagId }
        val usedScales = live.flatMapTo(HashSet()) { e -> e.scaleValues.map { it.scaleId } }
        val keepMoods = incoming.moods.mapTo(HashSet()) { it.id }
        val keepGroups = incoming.groups.mapTo(HashSet()) { it.id }
        val keepTags = incoming.tags.mapTo(HashSet()) { it.id }
        val keepScales = incoming.scales.mapTo(HashSet()) { it.id }
        // Ohne eigene Einrichtung in der Datei bleibt die vorhandene stehen.
        val tags = if (keepTags.isEmpty() && keepGroups.isEmpty()) diary.tags else diary.tags.map {
            if (!it.deleted && it.id !in keepTags && it.id !in usedTags) it.copy(deleted = true, dirty = true, updatedAt = now) else it
        }
        val usedGroups = tags.filter { !it.deleted }.mapNotNullTo(HashSet()) { it.groupId }
        return diary.copy(
            moods = if (keepMoods.isEmpty()) diary.moods else diary.moods.map {
                if (!it.deleted && it.id !in keepMoods && it.id !in usedMoods) it.copy(deleted = true, dirty = true, updatedAt = now) else it
            },
            tags = tags,
            groups = if (keepTags.isEmpty() && keepGroups.isEmpty()) diary.groups else diary.groups.map {
                if (!it.deleted && it.id !in keepGroups && it.id !in usedGroups) it.copy(deleted = true, dirty = true, updatedAt = now) else it
            },
            scales = if (keepScales.isEmpty()) diary.scales else diary.scales.map {
                if (!it.deleted && it.id !in keepScales && it.id !in usedScales) it.copy(deleted = true, dirty = true, updatedAt = now) else it
            },
        )
    }
}
