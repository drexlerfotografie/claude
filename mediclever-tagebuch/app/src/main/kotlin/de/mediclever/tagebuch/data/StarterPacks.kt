package de.mediclever.tagebuch.data

import de.mediclever.tagebuch.core.model.*

/**
 * Startpakete fuer den ersten Start. Ein Paket ist nur ein Vorschlag: alles laesst sich danach
 * umbenennen, archivieren oder loeschen. Fest sind allein die fuenf Stimmungsstufen.
 */
object StarterPacks {
    data class Pack(val key: String, val title: String, val description: String)

    val packs = listOf(
        Pack("allgemein", "Allgemein", "Schlaf, Essen, Bewegung, Menschen und Freizeit. Passt für die meisten."),
        Pack("schichtdienst", "Schichtdienst", "Wie Allgemein, dazu Dienste, Erholung zwischen den Schichten und Belastung."),
        Pack("leer", "Leer starten", "Nur die fünf Stimmungen. Du legst alles selbst an."),
    )

    fun defaultMoods(): List<Mood> = listOf("Sehr gut", "Gut", "Mittel", "Schlecht", "Sehr schlecht")
        .mapIndexed { i, n -> Mood(level = i + 1, name = n, icon = "a") }

    private class Builder {
        val groups = mutableListOf<TagGroup>()
        val tags = mutableListOf<Tag>()
        val scales = mutableListOf<Scale>()
        private var order = 0

        fun group(name: String, color: Int, vararg items: Pair<String, String>) {
            val g = TagGroup(name = name, colorIndex = color, sortOrder = order++)
            groups += g
            items.forEachIndexed { i, (n, icon) -> tags += Tag(groupId = g.id, name = n, icon = icon, sortOrder = i) }
        }

        fun textScale(name: String, icon: String, color: Int, trend: Trend, vararg labels: String) {
            scales += Scale(
                type = ScaleType.TEXT, name = name, icon = icon, colorIndex = color, sortOrder = order++, trend = trend,
                min = 1, max = labels.size, points = labels.mapIndexed { i, l -> ScalePoint(position = i + 1, label = l) },
            )
        }

        fun duration(name: String, icon: String, color: Int, maxHours: Int, trend: Trend) {
            scales += Scale(
                type = ScaleType.DURATION, name = name, icon = icon, colorIndex = color, sortOrder = order++,
                min = 0, max = maxHours * 60, step = 15, trend = trend, chartMode = ChartMode.DAILY_SUM,
            )
        }
    }

    fun build(key: String): Diary {
        val moods = defaultMoods()
        if (key == "leer") return Diary(moods = moods)
        val b = Builder()
        if (key == "schichtdienst") {
            b.group("Dienst", 0, "Frühdienst" to "wb_sunny", "Spätdienst" to "schedule", "Nachtdienst" to "nightlight",
                "24-h-Dienst" to "emergency", "Frei" to "weekend", "Fortbildung" to "school", "Überstunden" to "alarm")
            b.textScale("Belastung im Dienst", "speed", 1, Trend.LOWER_IS_BETTER, "ruhig", "normal", "fordernd", "sehr hoch")
        }
        b.duration("Schlafdauer", "bed", 6, 14, Trend.HIGHER_IS_BETTER)
        b.textScale("Schlafqualität", "bedtime", 7, Trend.HIGHER_IS_BETTER, "schlecht", "mäßig", "gut", "sehr gut")
        b.group("Gesundheit", 4, "Sport" to "fitness_center", "Spaziergang" to "directions_walk", "Gesund gegessen" to "restaurant",
            "Viel getrunken" to "water_drop", "Krank" to "sick", "Schmerzen" to "healing", "Medikament" to "medication")
        b.group("Menschen", 8, "Familie" to "family_restroom", "Freunde" to "group", "Partnerschaft" to "favorite",
            "Zeit für mich" to "person", "Feier" to "celebration")
        b.group("Alltag", 6, "Arbeit" to "work", "Lernen" to "menu_book", "Haushalt" to "cleaning_services",
            "Einkaufen" to "shopping_cart", "Erledigungen" to "check_circle")
        b.group("Freizeit", 2, "Lesen" to "book", "Film & Serie" to "movie", "Musik" to "music_note", "Zocken" to "sports_esports",
            "Draußen" to "park", "Kreativ" to "palette", "Entspannen" to "spa")
        if (key == "schichtdienst") b.textScale("Erholung", "spa", 5, Trend.HIGHER_IS_BETTER, "leer", "müde", "okay", "erholt")
        else b.textScale("Energie", "bolt", 2, Trend.HIGHER_IS_BETTER, "leer", "müde", "okay", "voller Energie")
        return Diary(
            moods = moods, groups = b.groups, tags = b.tags, scales = b.scales,
            templates = defaultTemplates(),
            reminders = listOf(Reminder(minuteOfDay = 20 * 60, text = "Wie war dein Tag?")),
        )
    }

    fun defaultTemplates(): List<Template> = listOf(
        "Dankbarkeit" to "<p><b>Dafür bin ich heute dankbar</b></p><ul><li></li></ul>",
        "Tagesrückblick" to "<p><b>Das lief gut</b></p><p><br></p><p><b>Das war schwierig</b></p><p><br></p><p><b>Das nehme ich mit</b></p>",
        "Nach dem Dienst" to "<p><b>Was hat mich beschäftigt?</b></p><p><br></p><p><b>Was hat mir geholfen?</b></p><p><br></p><p><b>Was brauche ich jetzt?</b></p>",
        "Morgen" to "<p><b>Das steht an</b></p><ol><li></li></ol><p><b>Darauf freue ich mich</b></p>",
    ).mapIndexed { i, (t, body) -> Template(title = t, body = body, sortOrder = i) }
}
