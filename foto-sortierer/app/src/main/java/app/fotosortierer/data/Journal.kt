package app.fotosortierer.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class OpType { MOVE, COPY }

data class JournalEntry(val type: OpType, val from: String, val to: String)

data class Journal(
    val id: String,
    val createdAt: Long,
    val title: String,
    val entries: List<JournalEntry>,
    val undone: Boolean = false,
)

/** Protokoll jedes Laufs als JSON-Datei – Grundlage für „Rückgängig“. */
class JournalStore(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun list(): List<Journal> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { read(it) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    fun save(j: Journal) {
        val obj = JSONObject()
            .put("id", j.id)
            .put("createdAt", j.createdAt)
            .put("title", j.title)
            .put("undone", j.undone)
            .put("entries", JSONArray().apply {
                j.entries.forEach { e ->
                    put(JSONObject().put("type", e.type.name).put("from", e.from).put("to", e.to))
                }
            })
        val tmp = File(dir, "${j.id}.json.tmp")
        tmp.writeText(obj.toString())
        tmp.renameTo(File(dir, "${j.id}.json"))
    }

    fun delete(j: Journal) {
        File(dir, "${j.id}.json").delete()
    }

    private fun read(f: File): Journal {
        val o = JSONObject(f.readText())
        val arr = o.getJSONArray("entries")
        val entries = (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            JournalEntry(OpType.valueOf(e.getString("type")), e.getString("from"), e.getString("to"))
        }
        return Journal(o.getString("id"), o.getLong("createdAt"), o.getString("title"), entries, o.optBoolean("undone"))
    }
}
