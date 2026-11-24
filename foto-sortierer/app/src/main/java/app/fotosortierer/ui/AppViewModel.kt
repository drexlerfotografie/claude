package app.fotosortierer.ui

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fotosortierer.core.Assignment
import app.fotosortierer.core.Duplicates
import app.fotosortierer.core.Event
import app.fotosortierer.core.Events
import app.fotosortierer.core.Label
import app.fotosortierer.core.Suggest
import app.fotosortierer.core.MediaItem
import app.fotosortierer.core.Naming
import app.fotosortierer.core.PlannedMove
import app.fotosortierer.core.Planner
import app.fotosortierer.data.Analyzer
import app.fotosortierer.data.DUPLICATE_DIR
import app.fotosortierer.data.LearnStore
import app.fotosortierer.data.FileOps
import app.fotosortierer.data.Journal
import app.fotosortierer.data.JournalStore
import app.fotosortierer.data.MediaRepository
import app.fotosortierer.data.OpType
import app.fotosortierer.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Progress(val text: String, val done: Int = 0, val total: Int = 0)

/** Analyse-Ergebnis je Ereignis: wichtigste Bildinhalte und Ortsname. */
data class EventInfo(val labels: List<String>, val place: String?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val repo = MediaRepository(app)
    private val journalStore = JournalStore(File(app.filesDir, "journal"))
    private val ops = FileOps(app, journalStore)
    private val analyzer = Analyzer(app)
    private val learn = LearnStore(app)

    val storageRoot get() = settings.storageRoot

    // ---------- Einstellungen ----------
    var sources by mutableStateOf(settings.sources); private set
    var targetRoot by mutableStateOf(settings.targetRoot); private set
    var recursive by mutableStateOf(settings.recursive); private set
    var categories by mutableStateOf(settings.categories); private set
    var gapHours by mutableStateOf(settings.gapHours); private set
    var usePlaces by mutableStateOf(settings.usePlaces); private set
    /** Ordnerauswahl geändert, seit zuletzt eingelesen wurde. */
    var stale by mutableStateOf(false); private set

    // ---------- Bibliothek ----------
    var library by mutableStateOf<List<MediaItem>>(emptyList()); private set
    var scanned by mutableStateOf(false); private set

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress
    var message by mutableStateOf<String?>(null)

    fun isSorted(item: MediaItem): Boolean =
        item.folder.startsWith(targetRoot.trimEnd('/') + "/") && Naming.sortedName.matches(item.name)

    val toSort: List<MediaItem> by derivedStateOf { library.filter { !isSorted(it) } }

    // ---------- Sortieren ----------
    val events: List<Event> by derivedStateOf { Events.group(toSort, gapHours) }
    private val eventOfItem: Map<String, Event> by derivedStateOf {
        HashMap<String, Event>().apply { events.forEach { e -> e.items.forEach { put(it.path, e) } } }
    }

    /** Von dir gesetzte Werte je Ereignis ("" = bewusst leer). Fehlt ein Eintrag, gilt der Vorschlag. */
    val eventAnlass = mutableStateMapOf<String, String>()
    val eventCategory = mutableStateMapOf<String, String>()
    val itemAnlass = mutableStateMapOf<String, String>()
    val itemCategory = mutableStateMapOf<String, String>()
    /** Zum Sortieren ausgewählte Dateien. Startet leer – du wählst selbst aus. */
    var included by mutableStateOf(emptySet<String>()); private set
    var selection by mutableStateOf(emptySet<String>()); private set
    var plan by mutableStateOf<List<PlannedMove>?>(null); private set

    // ---------- Analyse ----------
    val eventInfo = mutableStateMapOf<String, EventInfo>()
    private val _analysis = MutableStateFlow<Pair<Int, Int>?>(null)
    val analysis: StateFlow<Pair<Int, Int>?> = _analysis
    private var analysisJob: Job? = null

    // ---------- Duplikate ----------
    var dupGroups by mutableStateOf<List<List<MediaItem>>?>(null); private set
    var dupMarked by mutableStateOf(emptySet<String>()); private set

    // ---------- Verlauf ----------
    var journals by mutableStateOf(journalStore.list()); private set

    // =====================================================================

    private fun busy(text: String, block: suspend ((Int, Int) -> Unit) -> Unit) {
        if (_progress.value != null) return
        _progress.value = Progress(text)
        viewModelScope.launch {
            try {
                block { d, t -> _progress.value = Progress(text, d, t) }
            } catch (t: Throwable) {
                message = "Fehler: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                _progress.value = null
            }
        }
    }

    fun scan() = busy("Ordner werden eingelesen …") { p -> doScan(p) }

    private suspend fun doScan(p: (Int, Int) -> Unit) {
        val folders = (sources + targetRoot).distinct().filter { File(it).isDirectory }
        library = repo.scan(folders, recursive, p)
        scanned = true
        stale = false
        dupGroups = null
        plan = null
        selection = emptySet()
        val known = library.mapTo(HashSet()) { it.path }
        included = included.filterTo(HashSet()) { it in known }
        itemAnlass.keys.retainAll(known)
        itemCategory.keys.retainAll(known)
        startAnalysis()
    }

    /** Analysiert im Hintergrund pro Ereignis bis zu 3 Bilder (neueste Ereignisse zuerst). */
    fun startAnalysis() {
        analysisJob?.cancel()
        val todo = events.sortedByDescending { it.start }.filter { it.id !in eventInfo }
        if (todo.isEmpty()) return
        analysisJob = viewModelScope.launch {
            _analysis.value = 0 to todo.size
            try {
                todo.forEachIndexed { i, event ->
                    if (!isActive) return@launch
                    val samples = sample(event)
                    val infos = samples.map { analyzer.analyze(it) }
                    val gps = infos.firstOrNull { it.lat != null && it.lon != null }
                    val place = if (usePlaces && gps != null) analyzer.place(gps.lat!!, gps.lon!!) else null
                    eventInfo[event.id] = EventInfo(Suggest.topLabels(infos.map { it.labels }), place)
                    _analysis.value = (i + 1) to todo.size
                    if (i % 20 == 19) withContext(Dispatchers.IO) { analyzer.persist() }
                }
            } finally {
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { analyzer.persist() }
                _analysis.value = null
            }
        }
    }

    private fun sample(event: Event): List<MediaItem> {
        val photos = event.items.filter { !it.isVideo }.ifEmpty { event.items }
        if (photos.size <= 3) return photos
        return listOf(photos.first(), photos[photos.size / 2], photos.last())
    }

    // ---------- Einstellungen ändern ----------
    fun addSource(path: String) {
        if (path in sources) return
        sources = sources + path
        settings.sources = sources
        stale = true
    }

    fun removeSource(path: String) {
        sources = sources - path
        settings.sources = sources
        stale = true
    }

    fun changeTarget(path: String) {
        targetRoot = path.trimEnd('/')
        settings.targetRoot = targetRoot
        stale = true
    }

    fun changeRecursive(value: Boolean) {
        recursive = value
        settings.recursive = value
        stale = true
    }

    fun addCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty() || categories.any { it.equals(n, true) }) return
        categories = categories + n
        settings.categories = categories
    }

    fun removeCategory(name: String) {
        categories = categories - name
        settings.categories = categories
    }

    fun changeGapHours(h: Int) {
        gapHours = h
        settings.gapHours = h
        eventInfo.clear()
        if (scanned) startAnalysis()
    }

    fun changeUsePlaces(v: Boolean) {
        usePlaces = v
        settings.usePlaces = v
        eventInfo.clear()
        if (scanned) startAnalysis()
    }

    // ---------- Sortieren ----------
    fun suggestedCategory(e: Event): String? =
        Suggest.category(e.items, eventInfo[e.id]?.labels.orEmpty(), categories, learn.learned)

    fun suggestedAnlass(e: Event): String? = eventInfo[e.id]?.place

    fun categoryFor(e: Event): String = eventCategory[e.id] ?: suggestedCategory(e) ?: ""
    fun anlassFor(e: Event): String = eventAnlass[e.id] ?: suggestedAnlass(e) ?: ""
    fun isSuggestedCategory(e: Event) = e.id !in eventCategory && suggestedCategory(e) != null
    fun isSuggestedAnlass(e: Event) = e.id !in eventAnlass && suggestedAnlass(e) != null

    /** Weitere Ideen als Chips: Ort, Bildinhalt, zuletzt benutzt. */
    fun anlassIdeas(e: Event): List<String> {
        val info = eventInfo[e.id]
        val current = anlassFor(e)
        return (listOfNotNull(info?.place) + Suggest.anlassFromLabels(info?.labels.orEmpty()) + learn.recentAnlass.take(4))
            .distinct().filter { !it.equals(current, true) }.take(6)
    }

    fun anlassFor(item: MediaItem): String =
        itemAnlass[item.path] ?: eventOfItem[item.path]?.let { anlassFor(it) } ?: ""

    fun categoryFor(item: MediaItem): String =
        itemCategory[item.path] ?: eventOfItem[item.path]?.let { categoryFor(it) } ?: ""

    fun setEventAnlass(e: Event, text: String) {
        eventAnlass[e.id] = text
    }

    fun setEventCategory(e: Event, cat: String) {
        eventCategory[e.id] = cat
    }

    /** true = alle Dateien des Ereignisses, false = keine, null = teilweise. */
    fun eventIncluded(e: Event): Boolean? {
        val n = e.items.count { it.path in included }
        return when (n) {
            0 -> false
            e.items.size -> true
            else -> null
        }
    }

    fun setEventIncluded(e: Event, include: Boolean) {
        val paths = e.items.map { it.path }
        included = if (include) included + paths else included - paths.toSet()
    }

    fun includeAll(events: List<Event>) {
        included = included + events.flatMap { e -> e.items.map { it.path } }
    }

    fun excludeAll(events: List<Event>) {
        included = included - events.flatMap { e -> e.items.map { it.path } }.toSet()
    }

    fun toggleSelect(path: String) {
        selection = if (path in selection) selection - path else selection + path
    }

    fun selectAll(paths: Collection<String>) {
        selection = selection + paths
    }

    fun clearSelection() {
        selection = emptySet()
    }

    fun setAnlassForSelection(text: String) {
        for (p in selection) if (text.isBlank()) itemAnlass.remove(p) else itemAnlass[p] = text.trim()
        selection = emptySet()
    }

    /** null = wieder die Kategorie des Ereignisses verwenden. */
    fun setCategoryForSelection(cat: String?) {
        for (p in selection) if (cat == null) itemCategory.remove(p) else itemCategory[p] = cat
        selection = emptySet()
    }

    fun toggleIncludeSelection() {
        included = if (selection.all { it in included }) included - selection else included + selection
        selection = emptySet()
    }

    fun buildPlan() {
        viewModelScope.launch {
            val assignments = events.flatMap { it.items }
                .filter { it.path in included }
                .map { Assignment(it, anlassFor(it), categoryFor(it)) }
            val result = withContext(Dispatchers.IO) {
                Planner(targetRoot) { dir -> File(dir).list()?.toList() ?: emptyList() }.plan(assignments)
            }
            if (result.isEmpty()) message = "Nichts zu tun – alle ausgewählten Dateien liegen schon richtig."
            else plan = result
        }
    }

    fun closePlan() {
        plan = null
    }

    fun executePlan(type: OpType) {
        val p = plan ?: return
        val verb = if (type == OpType.MOVE) "verschoben" else "kopiert"
        busy("Dateien werden $verb …") { progress ->
            // Lernen: deine Kategorie-Entscheidungen zu den erkannten Bildinhalten merken
            val planned = p.mapTo(HashSet()) { it.item.path }
            events.filter { e -> e.items.any { it.path in planned } }.forEach { e ->
                learn.learn(eventInfo[e.id]?.labels.orEmpty(), categoryFor(e))
            }
            learn.useAnlass(p.map { anlassFor(it.item) }.toSet())
            val title = "Sortierung (${p.size} Dateien $verb)"
            val res = ops.execute(p.map { it.item.path to it.target }, type, title, progress)
            applyToLibrary(res.journal.entries.map { it.from to it.to }, copy = type == OpType.COPY)
            plan = null
            selection = emptySet()
            eventAnlass.clear()
            eventCategory.clear()
            itemAnlass.clear()
            itemCategory.clear()
            journals = journalStore.list()
            startAnalysis()
            message = buildString {
                append("${res.journal.entries.size} Dateien $verb.")
                if (res.failed.isNotEmpty()) append(" ${res.failed.size} fehlgeschlagen: ${res.failed.first().second}")
            }
        }
    }

    private fun applyToLibrary(moves: List<Pair<String, String>>, copy: Boolean) {
        val map = moves.toMap()
        moves.forEach { (f, t) ->
            repo.relocate(f, t, copy)
            analyzer.relocate(f, t)
        }
        repo.persist()
        analyzer.persist()
        library = if (copy) {
            library + library.mapNotNull { item -> map[item.path]?.let { item.copy(path = it) } }
        } else {
            library.mapNotNull { item ->
                val to = map[item.path] ?: return@mapNotNull item
                if (to.contains("/$DUPLICATE_DIR/")) null else item.copy(path = to)
            }
        }
    }

    // ---------- Duplikate ----------
    fun findDuplicates() = busy("Duplikate werden gesucht …") { p ->
        val groups = withContext(Dispatchers.IO) { Duplicates.find(library, p) }
        dupGroups = groups
        dupMarked = groups.flatMap { g ->
            val keep = Duplicates.suggestKeeper(g)
            g.filter { it.path != keep.path }.map { it.path }
        }.toSet()
    }

    fun toggleDupMark(group: List<MediaItem>, item: MediaItem) {
        if (item.path in dupMarked) {
            dupMarked = dupMarked - item.path
        } else {
            val remaining = group.count { it.path !in dupMarked && it.path != item.path }
            if (remaining == 0) message = "Mindestens eine Datei pro Gruppe bleibt erhalten."
            else dupMarked = dupMarked + item.path
        }
    }

    fun moveDuplicates() {
        val marked = dupMarked.toList()
        if (marked.isEmpty()) return
        busy("Duplikate werden verschoben …") { p ->
            val dir = "${targetRoot.trimEnd('/')}/$DUPLICATE_DIR"
            val used = HashSet<String>()
            File(dir).list()?.forEach { used += it.lowercase() }
            val moves = marked.map { from ->
                val name = from.substringAfterLast('/')
                val base = name.substringBeforeLast('.')
                val ext = name.substringAfterLast('.', "")
                var candidate = name
                var i = 1
                while (candidate.lowercase() in used) candidate = "${base}_${i++}.$ext"
                used += candidate.lowercase()
                from to "$dir/$candidate"
            }
            val res = ops.execute(moves, OpType.MOVE, "Duplikate (${moves.size} Dateien)", p)
            applyToLibrary(res.journal.entries.map { it.from to it.to }, copy = false)
            val movedFrom = res.journal.entries.mapTo(HashSet()) { it.from }
            dupGroups = dupGroups?.map { g -> g.filter { it.path !in movedFrom } }?.filter { it.size > 1 }
            dupMarked = dupMarked - movedFrom
            journals = journalStore.list()
            message = "${res.journal.entries.size} Duplikate nach $DUPLICATE_DIR verschoben." +
                if (res.failed.isNotEmpty()) " ${res.failed.size} fehlgeschlagen." else ""
        }
    }

    // ---------- Verlauf ----------
    fun undo(journal: Journal) = busy("Wird rückgängig gemacht …") { p ->
        val res = ops.undo(journal, p)
        journals = journalStore.list()
        message = "${res.restored} Dateien wiederhergestellt." +
            if (res.failed.isNotEmpty()) " ${res.failed.size} nicht möglich: ${res.failed.first().second}" else ""
        if (scanned) doScan { d, t -> _progress.value = Progress("Ordner werden neu eingelesen …", d, t) }
    }

    fun deleteJournal(journal: Journal) {
        journalStore.delete(journal)
        journals = journalStore.list()
    }
}
