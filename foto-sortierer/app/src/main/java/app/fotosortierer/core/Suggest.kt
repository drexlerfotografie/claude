package app.fotosortierer.core

/** Erkannte Bildinhalte (englische ML-Kit-Labels) mit Sicherheit 0..1. */
data class Label(val text: String, val confidence: Float)

object Suggest {
    val DEFAULT_CATEGORIES = listOf("Familie & Freunde", "Urlaub & Ausflüge", "Arbeit", "Dokumente & Screenshots")

    /** Startwissen: welche Labels auf welche Standard-Kategorie hindeuten. */
    private val KEYWORDS = mapOf(
        "Familie & Freunde" to listOf(
            "party", "smile", "selfie", "wedding", "baby", "child", "fun", "crowd", "birthday", "cake",
            "christmas", "bonfire", "dinner", "toy", "event",
        ),
        "Urlaub & Ausflüge" to listOf(
            "beach", "mountain", "lake", "sea", "river", "waterfall", "forest", "sunset", "cliff", "snow",
            "hiking", "pier", "bridge", "monument", "park", "rafting", "desert", "glacier", "island",
            "swimming", "ski", "sky", "canyon", "ruins", "boat",
        ),
        "Arbeit" to listOf("factory", "machine", "helmet", "office"),
        "Dokumente & Screenshots" to listOf(
            "paper", "receipt", "menu", "poster", "document", "screenshot", "monitor", "computer",
            "comics", "handwriting", "text", "font", "whiteboard",
        ),
    )

    /** Englische Labels, die als Anlass taugen, auf Deutsch. */
    private val ANLASS_WORDS = linkedMapOf(
        "wedding" to "Hochzeit", "birthday" to "Geburtstag", "christmas" to "Weihnachten", "party" to "Feier",
        "graduation" to "Abschluss", "concert" to "Konzert", "musician" to "Konzert", "stadium" to "Stadion",
        "fireworks" to "Feuerwerk", "bonfire" to "Lagerfeuer", "circus" to "Zirkus", "zoo" to "Zoo",
        "aquarium" to "Aquarium", "museum" to "Museum", "beach" to "Strand", "mountain" to "Berge",
        "lake" to "See", "sea" to "Meer", "waterfall" to "Wasserfall", "snow" to "Schnee", "ski" to "Skifahren",
        "hiking" to "Wandern", "forest" to "Wald", "sunset" to "Sonnenuntergang", "garden" to "Garten",
        "flower" to "Blumen", "bicycle" to "Radtour", "boat" to "Bootsfahrt", "airplane" to "Flug",
        "pool" to "Schwimmbad", "dog" to "Hund", "cat" to "Katze", "horse" to "Pferde",
        "pizza" to "Pizza", "lunch" to "Mittagessen", "dinner" to "Abendessen", "cake" to "Kuchen",
        "food" to "Essen", "car" to "Auto",
    )

    /** Häufigkeit × Sicherheit der Labels über alle analysierten Bilder eines Ereignisses. */
    fun topLabels(labels: List<List<Label>>, limit: Int = 5): List<String> =
        labels.flatten()
            .groupBy { it.text.lowercase() }
            .mapValues { (_, l) -> l.sumOf { it.confidence.toDouble() } }
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }

    /**
     * Kategorie-Vorschlag oder null. Regeln (Screenshot) zuerst, dann Gelerntes, dann Startwissen.
     * [learned]: label -> (kategorie -> wie oft du das so zugeordnet hast).
     */
    fun category(
        items: List<MediaItem>,
        labels: List<String>,
        categories: List<String>,
        learned: Map<String, Map<String, Int>>,
    ): String? {
        if (categories.isEmpty()) return null
        val screenshots = items.count { it.name.contains("screenshot", true) || it.folder.substringAfterLast('/').contains("screenshot", true) }
        if (screenshots * 2 > items.size) {
            categories.firstOrNull { it.contains("screenshot", true) }?.let { return it }
        }
        val scores = HashMap<String, Double>()
        labels.forEachIndexed { rank, label ->
            val weight = 1.0 / (rank + 1)
            learned[label]?.forEach { (cat, n) -> if (cat in categories) scores.merge(cat, n * 3.0 * weight, Double::plus) }
            for (cat in categories) {
                if (KEYWORDS[cat].orEmpty().any { label.contains(it) }) scores.merge(cat, 2.0 * weight, Double::plus)
            }
        }
        return scores.maxByOrNull { it.value }?.takeIf { it.value >= 1.0 }?.key
    }

    /** Anlass-Ideen aus Bildinhalt, max. [limit]. */
    fun anlassFromLabels(labels: List<String>, limit: Int = 2): List<String> =
        labels.mapNotNull { l -> ANLASS_WORDS.entries.firstOrNull { l.contains(it.key) }?.value }
            .distinct().take(limit)
}
