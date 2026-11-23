package app.fotosortierer.core

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Duplicates {
    private const val PARTIAL = 64 * 1024

    fun hash(file: File, limit: Long = Long.MAX_VALUE): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            var remaining = limit
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n <= 0) break
                md.update(buf, 0, n)
                remaining -= n
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Gruppen identischer Dateien. Erst nach Größe, dann Teil-Hash, dann voller SHA-256 –
     * so wird nur gelesen, was wirklich in Frage kommt.
     */
    fun find(items: List<MediaItem>, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<List<MediaItem>> {
        val candidates = items.groupBy { it.size }.values.filter { it.size > 1 && it.first().size > 0 }
        val total = candidates.sumOf { it.size }
        var done = 0
        val groups = ArrayList<List<MediaItem>>()
        for (sameSize in candidates) {
            val byPartial = sameSize.groupBy { runCatching { hash(File(it.path), PARTIAL.toLong()) }.getOrNull() }
            for ((partial, list) in byPartial) {
                if (partial == null || list.size < 2) continue
                val byFull = if (list.first().size <= PARTIAL) mapOf(partial to list)
                else list.groupBy { runCatching { hash(File(it.path)) }.getOrNull() }
                byFull.filter { it.key != null && it.value.size > 1 }.values.forEach { groups += it }
            }
            done += sameSize.size
            progress(done, total)
        }
        return groups.sortedByDescending { g -> g.first().size * (g.size - 1) }
    }

    /** Welche Datei behalten? Bevorzugt bereits sortierte, dann mit echtem Datum, dann älteste. */
    fun suggestKeeper(group: List<MediaItem>): MediaItem =
        group.sortedWith(
            compareByDescending<MediaItem> { Naming.sortedName.matches(it.name) }
                .thenByDescending { it.source.reliable }
                .thenBy { it.lastModified }
                .thenBy { it.path.length }
        ).first()
}
