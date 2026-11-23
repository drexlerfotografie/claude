package app.fotosortierer.data

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExecResult(val journal: Journal, val failed: List<Pair<String, String>>)
data class UndoResult(val restored: Int, val failed: List<Pair<String, String>>)

class FileOps(private val context: Context, private val store: JournalStore) {

    suspend fun execute(
        ops: List<Pair<String, String>>,
        type: OpType,
        title: String,
        progress: (Int, Int) -> Unit,
    ): ExecResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var journal = Journal("run_$now", now, title, emptyList())
        val done = ArrayList<JournalEntry>()
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()

        ops.forEachIndexed { i, (from, to) ->
            val error = when (type) {
                OpType.MOVE -> move(File(from), File(to))
                OpType.COPY -> copy(File(from), File(to))
            }
            if (error == null) {
                done += JournalEntry(type, from, to)
                touched += to
                if (type == OpType.MOVE) touched += from
            } else {
                failed += from to error
            }
            // Zwischendurch speichern, damit ein Absturz das Protokoll nicht verliert.
            if (done.isNotEmpty() && (i % 50 == 49)) store.save(journal.copy(entries = done.toList()))
            progress(i + 1, ops.size)
        }
        journal = journal.copy(entries = done.toList())
        if (done.isNotEmpty()) store.save(journal)
        scan(touched)
        ExecResult(journal, failed)
    }

    suspend fun undo(journal: Journal, progress: (Int, Int) -> Unit): UndoResult = withContext(Dispatchers.IO) {
        val failed = ArrayList<Pair<String, String>>()
        val touched = ArrayList<String>()
        var restored = 0
        val entries = journal.entries.reversed()
        entries.forEachIndexed { i, e ->
            val src = File(e.from)
            val dst = File(e.to)
            val error = when (e.type) {
                OpType.MOVE -> when {
                    !dst.exists() -> "Datei nicht mehr am Zielort"
                    src.exists() -> "Am Ursprungsort liegt inzwischen eine andere Datei"
                    else -> move(dst, src)
                }
                OpType.COPY -> if (!dst.exists() || dst.delete()) null else "Kopie ließ sich nicht löschen"
            }
            if (error == null) {
                restored++
                touched += e.to
                touched += e.from
                removeEmptyParents(dst.parentFile)
            } else failed += e.to to error
            progress(i + 1, entries.size)
        }
        store.save(journal.copy(undone = true))
        scan(touched)
        UndoResult(restored, failed)
    }

    private fun move(from: File, to: File): String? {
        if (!from.exists()) return "Quelle existiert nicht mehr"
        if (to.exists()) return "Ziel existiert bereits"
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return null
        // renameTo klappt nicht über Speichergrenzen (z. B. SD-Karte) – dann kopieren und löschen.
        copy(from, to)?.let { return it }
        return if (from.delete()) null else {
            to.delete()
            "Original ließ sich nicht löschen"
        }
    }

    private fun copy(from: File, to: File): String? {
        if (!from.exists()) return "Quelle existiert nicht mehr"
        if (to.exists()) return "Ziel existiert bereits"
        to.parentFile?.mkdirs()
        return try {
            from.copyTo(to)
            if (to.length() != from.length()) {
                to.delete()
                "Kopie unvollständig"
            } else {
                to.setLastModified(from.lastModified())
                null
            }
        } catch (t: Throwable) {
            to.delete()
            t.message ?: "Kopieren fehlgeschlagen"
        }
    }

    private fun removeEmptyParents(dir: File?) {
        var d = dir
        for (i in 0 until 2) {
            val cur = d ?: return
            if (cur.isDirectory && cur.list()?.isEmpty() == true && cur.delete()) d = cur.parentFile else return
        }
    }

    /** Galerie (MediaStore) über neue und alte Pfade informieren. */
    private fun scan(paths: List<String>) {
        paths.distinct().chunked(300).forEach { chunk ->
            MediaScannerConnection.scanFile(context, chunk.toTypedArray(), null, null)
        }
    }
}
