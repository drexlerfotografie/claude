package app.fotosortierer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Merkt sich deine Zuordnungen (Label → Kategorie) und zuletzt benutzte Anlässe. */
class LearnStore(context: Context) {
    private val prefs = context.getSharedPreferences("learn", Context.MODE_PRIVATE)

    var learned: Map<String, Map<String, Int>> = load()
        private set

    var recentAnlass: List<String> =
        prefs.getString("recent", null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }
            ?: emptyList()
        private set

    fun learn(labels: List<String>, category: String) {
        if (category.isBlank()) return
        val m = learned.mapValues { it.value.toMutableMap() }.toMutableMap()
        for (l in labels) {
            val inner = m.getOrPut(l) { mutableMapOf() }
            inner[category] = (inner[category] ?: 0) + 1
        }
        learned = m
        prefs.edit().putString("learned", JSONObject().apply {
            m.forEach { (l, cats) -> put(l, JSONObject().apply { cats.forEach { (c, n) -> put(c, n) } }) }
        }.toString()).apply()
    }

    fun useAnlass(values: Collection<String>) {
        val list = (values.map { it.trim() }.filter { it.isNotEmpty() } + recentAnlass).distinct().take(30)
        recentAnlass = list
        prefs.edit().putString("recent", JSONArray(list).toString()).apply()
    }

    private fun load(): Map<String, Map<String, Int>> = runCatching {
        val o = JSONObject(prefs.getString("learned", null) ?: return emptyMap())
        o.keys().asSequence().associateWith { l ->
            val inner = o.getJSONObject(l)
            inner.keys().asSequence().associateWith { inner.getInt(it) }
        }
    }.getOrDefault(emptyMap())
}
