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
import app.fotosortierer.core.MediaItem
import app.fotosortierer.core.Naming
import app.fotosortierer.core.PlannedMove
import app.fotosortierer.core.Planner
import app.fotosortierer.data.DUPLICATE_DIR
import app.fotosortierer.data.FileOps
import app.fotosortierer.data.Journal
import app.fotosortierer.data.JournalStore
import app.fotosortierer.data.MediaRepository
import app.fotosortierer.data.OpType
import app.fotosortierer.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class Progress(val text: String, val done: Int = 0, val total: Int = 0)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val repo = MediaRepository(app)
    private val journalStore = JournalStore(File(app.filesDir, "journal"))
    private val ops = FileOps(app, journalStore)

    val storageRoot get() = settings.storageRoot

    // ---------- Einstellungen ----------
    var sources by mutableStateOf(settings.sources); private set
    var targetRoot by mutableStateOf(settings.targetRoot); private set
    var recursive by mutableStateOf(settings.recursive); private set
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
    val dayAnlass = mutableStateMapOf<LocalDate, String>()
    val itemAnlass = mutableStateMapOf<String, String>()
    var excluded by mutableStateOf(emptySet<String>()); private set
    var excludedDays by mutableStateOf(emptySet<LocalDate>()); private set
    var selection by mutableStateOf(emptySet<String>()); private set
    var plan by mutableStateOf<List<PlannedMove>?>(null); private set

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
        excluded = excluded.filterTo(HashSet()) { it in known }
        itemAnlass.keys.retainAll(known)
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

    // ---------- Sortieren ----------
    fun anlassFor(item: MediaItem): String = itemAnlass[item.path] ?: dayAnlass[item.taken.toLocalDate()] ?: ""

    fun setDayAnlass(day: LocalDate, text: String) {
        if (text.isEmpty()) dayAnlass.remove(day) else dayAnlass[day] = text
    }

    fun setDayIncluded(day: LocalDate, include: Boolean) {
        excludedDays = if (include) excludedDays - day else excludedDays + day
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

    fun toggleExcludeSelection() {
        excluded = if (selection.all { it in excluded }) excluded - selection else excluded + selection
        selection = emptySet()
    }

    fun buildPlan() {
        viewModelScope.launch {
            val assignments = toSort
                .filter { it.path !in excluded && it.taken.toLocalDate() !in excludedDays }
                .map { Assignment(it, anlassFor(it)) }
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
            val title = "Sortierung (${p.size} Dateien $verb)"
            val res = ops.execute(p.map { it.item.path to it.target }, type, title, progress)
            applyToLibrary(res.journal.entries.map { it.from to it.to }, copy = type == OpType.COPY)
            plan = null
            selection = emptySet()
            dayAnlass.clear()
            itemAnlass.clear()
            journals = journalStore.list()
            message = buildString {
                append("${res.journal.entries.size} Dateien $verb.")
                if (res.failed.isNotEmpty()) append(" ${res.failed.size} fehlgeschlagen: ${res.failed.first().second}")
            }
        }
    }

    private fun applyToLibrary(moves: List<Pair<String, String>>, copy: Boolean) {
        val map = moves.toMap()
        moves.forEach { (f, t) -> repo.relocate(f, t, copy) }
        repo.persist()
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
