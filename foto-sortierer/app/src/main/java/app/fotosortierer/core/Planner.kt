package app.fotosortierer.core

import java.time.LocalDate

data class Assignment(val item: MediaItem, val anlass: String, val category: String = "")

data class PlannedMove(val item: MediaItem, val target: String) {
    val targetName: String get() = target.substringAfterLast('/')
    val targetDir: String get() = target.substringBeforeLast('/')
}

/**
 * Berechnet Zielpfade. [listNames] liefert die Dateinamen, die schon im Zielordner liegen,
 * damit nichts überschrieben wird und die Nummerierung weiterläuft.
 */
class Planner(targetRoot: String, private val listNames: (dir: String) -> List<String>) {
    private val root = targetRoot.trimEnd('/')

    fun plan(assignments: List<Assignment>): List<PlannedMove> {
        val takenByDir = HashMap<String, MutableSet<String>>()
        fun taken(dir: String) = takenByDir.getOrPut(dir) {
            listNames(dir).mapTo(HashSet()) { it.lowercase() }
        }

        val groups = assignments
            .groupBy {
                val date = it.item.taken.toLocalDate()
                Triple(date, Naming.dir(root, date, it.category), Naming.prefix(date, it.anlass))
            }
            .toSortedMap(compareBy<Triple<LocalDate, String, String>>({ it.first }, { it.second }, { it.third }))

        val result = ArrayList<PlannedMove>()
        for ((key, list) in groups) {
            val (_, dir, prefix) = key
            val names = taken(dir)

            // Was schon korrekt benannt im richtigen Ordner liegt, bleibt unangetastet.
            val todo = list.filterNot { it.item.folder == dir && Naming.numberFor(prefix, it.item.name) != null }
            var next = (names.mapNotNull { Naming.numberFor(prefix, it) }.maxOrNull() ?: 0) + 1

            for (a in todo.sortedWith(compareBy({ it.item.taken }, { it.item.name }))) {
                val ext = Naming.normalizedExt(a.item.extension)
                var name: String
                do {
                    name = Naming.fileName(prefix, next++, ext)
                } while (name.lowercase() in names)
                names += name.lowercase()
                result += PlannedMove(a.item, "$dir/$name")
            }
        }
        return result
    }
}
