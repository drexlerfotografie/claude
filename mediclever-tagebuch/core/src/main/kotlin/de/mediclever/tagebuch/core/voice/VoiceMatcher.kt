package de.mediclever.tagebuch.core.voice

import de.mediclever.tagebuch.core.model.Diary
import de.mediclever.tagebuch.core.model.Scale
import de.mediclever.tagebuch.core.model.ScaleType
import de.mediclever.tagebuch.core.model.ScaleValue
import de.mediclever.tagebuch.core.shift.Shifts

/**
 * Schlaegt aus dem Text einer Sprachnotiz Dienstart, Aktivitaeten und Skalenwerte vor. Reiner
 * Wortabgleich mit festen Regeln, ohne Netz und ohne Sprachmodell:
 *  - Wortformen: "gelaufen", "laeuft" und "Lauf" zaehlen wie "laufen"
 *  - Verneinung ("nicht", "kein", "kaum", "ohne", "nie") kurz vor dem Wort hebt den Treffer auf
 *  - Absicht ("wollte", "sollte", "muesste", "haette") macht ihn unsicher
 *  - Sperrwoerter und abgelehnte Woerter schlagen nie vor
 * Skalenwerte sind immer unsicher und werden nie ohne Nachfrage uebernommen.
 */
object VoiceMatcher {
    enum class Kind { SHIFT, TAG, SCALE }

    data class Hit(
        val kind: Kind,
        /** Aktivitaet (auch Dienstart) oder Skala. */
        val id: String,
        val label: String,
        /** Die Stelle im Text, aus der der Vorschlag stammt. */
        val word: String,
        val sure: Boolean,
        val value: ScaleValue? = null,
        /** Zeichenbereich im Text, zum Markieren. */
        val start: Int = 0,
        val end: Int = 0,
    ) {
        /** Schluessel fuer "abgelehnt": Ziel und das ausloesende Stichwort. */
        val rejectKey: String get() = "$id|${fold(word)}"
    }

    private class Token(val text: String, val stem: String, val alt: String, val start: Int, val end: Int, val sentence: Int)

    private val negations = setOf("nicht", "kein", "keine", "keinen", "keinem", "keiner", "keines", "kaum", "ohne", "nie", "niemals", "weder", "nichts")
    private val intents = setOf("wollte", "wollten", "sollte", "sollten", "müsste", "müssten", "hätte", "hätten", "würde", "eigentlich")
    private val generic = setOf("gut", "sehr gut", "schlecht", "sehr schlecht", "okay", "normal", "mittel", "hoch", "sehr hoch", "niedrig", "ruhig", "leer", "mäßig", "gering", "angemessen")
    private val numbers = mapOf("null" to 0, "kein" to 0, "keinen" to 0, "keine" to 0, "ein" to 1, "eine" to 1, "einen" to 1, "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12)

    /** Eingebaute Stichwoerter zu gaengigen Namen. Eigene Woerter des Nutzers kommen dazu. */
    private val builtIn: Map<String, List<String>> = mapOf(
        "sport" to listOf("laufen", "joggen", "training", "trainiert", "fitnessstudio", "radfahren", "schwimmen", "workout"),
        "spaziergang" to listOf("spazieren", "spaziert", "runde gedreht"),
        "gutes team" to listOf("team war super", "team war gut", "tolles team", "super team"),
        "familie" to listOf("kinder", "eltern", "familienbesuch"),
        "freunde" to listOf("freund", "freundin", "kumpel", "kollegen getroffen"),
        "lesen" to listOf("gelesen", "buch"),
        "zocken" to listOf("gezockt", "gespielt", "konsole", "gaming"),
        "gaming" to listOf("gezockt", "zocken", "konsole"),
        "krank" to listOf("erkältet", "erkältung", "fieber"),
        "schmerzen" to listOf("kopfschmerzen", "rückenschmerzen", "kopfweh", "wehgetan"),
        "viele einsätze" to listOf("viel los", "einsatz nach einsatz", "kaum pause"),
        "ruhige schicht" to listOf("ruhiger dienst", "ruhige nacht", "wenig los", "ruhig gewesen"),
        "belastender einsatz" to listOf("schwerer einsatz", "harter einsatz", "reanimation"),
        "überstunden" to listOf("länger geblieben", "mehrarbeit"),
        "keine pause" to listOf("ohne pause", "pause ausgefallen"),
        "fortbildung" to listOf("schulung", "lehrgang", "seminar"),
        "entspannen" to listOf("entspannt", "sauna", "badewanne"),
        "koffein" to listOf("kaffee", "espresso", "energydrink", "cola", "tassen"),
        "schlafdauer" to listOf("geschlafen", "schlaf"),
    )

    fun fold(s: String): String = s.lowercase().replace("ß", "ss").trim()

    /** Wortstamm fuer den Vergleich: klein, Umlaute geglaettet, gaengige Endungen ab. */
    private fun stem(word: String): String {
        var w = fold(word).replace('ä', 'a').replace('ö', 'o').replace('ü', 'u')
        for (end in listOf("en", "er", "es", "em", "st", "e", "n", "s", "t")) {
            if (w.length - end.length >= 3 && w.endsWith(end)) { w = w.dropLast(end.length); break }
        }
        return w
    }

    /** Zweite Lesart fuer Partizipien: "gelaufen" wird "lauf", "gejoggt" wird "jogg". */
    private fun alt(word: String): String {
        val w = stem(word)
        return if (w.length >= 5 && w.startsWith("ge")) w.drop(2) else w
    }

    private fun tokens(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var sentence = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isLetterOrDigit()) {
                var j = i
                while (j < text.length && (text[j].isLetterOrDigit() || (text[j] == '-' && j + 1 < text.length && text[j + 1].isLetterOrDigit()))) j++
                val word = text.substring(i, j)
                out += Token(fold(word), stem(word), alt(word), i, j, sentence)
                i = j
            } else {
                if (c == '.' || c == '!' || c == '?' || c == ';') sentence++
                i++
            }
        }
        return out
    }

    private class Found(val from: Int, val to: Int)

    /** Stellen, an denen die Wortfolge phrase vorkommt (Vergleich ueber Wortstaemme). */
    private fun find(tokens: List<Token>, phrase: String, skip: Set<Int>): List<Found> {
        val want = tokens(phrase)
        if (want.isEmpty()) return emptyList()
        val out = mutableListOf<Found>()
        for (i in 0..tokens.size - want.size) {
            var ok = true
            for (k in want.indices) {
                val t = tokens[i + k]; val w = want[k]
                if (i + k in skip || t.sentence != tokens[i].sentence) { ok = false; break }
                // kurze Woerter muessen genau passen, sonst trifft "Rad" auch "Rat"
                val same = if (w.text.length <= 3) t.text == w.text else t.stem == w.stem || t.alt == w.stem || t.stem == w.alt
                if (!same) { ok = false; break }
            }
            if (ok) out += Found(i, i + want.size - 1)
        }
        return out
    }

    private fun before(tokens: List<Token>, at: Int, words: Set<String>): Boolean =
        (maxOf(0, at - 3) until at).any { tokens[it].sentence == tokens[at].sentence && tokens[it].text in words }

    /** Stichwoerter eines Ziels: sein Name, eingebaute und eigene Woerter. */
    fun keywords(d: Diary, id: String, name: String): List<String> {
        val own = d.settings.keywords.firstOrNull { it.tagId == id }?.words.orEmpty()
        return (listOf(name) + builtIn[fold(name)].orEmpty() + own).map { it.trim() }.filter { it.length >= 2 }.distinctBy { fold(it) }
    }

    fun suggest(text: String, d: Diary, rejected: Set<String> = emptySet()): List<Hit> {
        val tokens = tokens(text)
        if (tokens.isEmpty()) return emptyList()
        val hits = mutableListOf<Hit>()

        fun blockedIndices(id: String): Set<Int> {
            val blocked = d.settings.keywords.firstOrNull { it.tagId == id }?.blocked.orEmpty().map { fold(it) }.toSet()
            return if (blocked.isEmpty()) emptySet() else tokens.indices.filter { tokens[it].text in blocked }.toSet()
        }

        /** Erster brauchbarer Treffer eines Ziels: nicht verneint, nicht abgelehnt. */
        fun firstMatch(id: String, words: List<String>): Triple<Found, String, Boolean>? {
            val skip = blockedIndices(id)
            var best: Triple<Found, String, Boolean>? = null
            for (w in words) for (f in find(tokens, w, skip)) {
                if (before(tokens, f.from, negations)) continue
                val word = text.substring(tokens[f.from].start, tokens[f.to].end)
                if ("$id|${fold(word)}" in rejected || "$id|${fold(w)}" in rejected) continue
                val sure = !before(tokens, f.from, intents)
                if (best == null || f.from < best!!.first.from) best = Triple(f, word, sure)
            }
            return best
        }

        // Dienstart: hoechstens eine, die frueheste im Text
        val shiftIds = Shifts.active(d).map { it.id }.toSet()
        Shifts.active(d).mapNotNull { t ->
            val short = Shifts.short(d, t)
            val words = (keywords(d, t.id, t.name) + listOf(short + "schicht", short + "dienst") + if (Shifts.isFree(d, t)) listOf("frei", "freier tag", "dienstfrei") else emptyList()).distinct()
            firstMatch(t.id, words)?.let { (f, word, sure) -> Hit(Kind.SHIFT, t.id, t.name, word, sure, start = tokens[f.from].start, end = tokens[f.to].end) }
        }.minByOrNull { it.start }?.let { hits += it }

        for (t in d.tags) {
            if (t.archivedAt != null || t.deleted || t.id in shiftIds) continue
            firstMatch(t.id, keywords(d, t.id, t.name))?.let { (f, word, sure) -> hits += Hit(Kind.TAG, t.id, t.name, word, sure, start = tokens[f.from].start, end = tokens[f.to].end) }
        }

        for (s in d.scales) {
            if (s.archivedAt != null || s.deleted) continue
            scaleHit(text, tokens, d, s, rejected)?.let { hits += it }
        }
        return hits.sortedWith(compareBy({ it.kind.ordinal }, { it.start }))
    }

    private fun scaleHit(text: String, tokens: List<Token>, d: Diary, s: Scale, rejected: Set<String>): Hit? {
        val names = keywords(d, s.id, s.name)
        fun nameInSentence(sentence: Int) = names.any { n -> find(tokens, n, emptySet()).any { tokens[it.from].sentence == sentence } }
        fun hit(f: Found, label: String, v: ScaleValue): Hit? {
            val word = text.substring(tokens[f.from].start, tokens[f.to].end)
            if ("${s.id}|${fold(word)}" in rejected) return null
            return Hit(Kind.SCALE, s.id, "${s.name}: $label", word, sure = false, value = v, start = tokens[f.from].start, end = tokens[f.to].end)
        }
        when (s.type) {
            ScaleType.TEXT -> {
                // laengste Bezeichnung zuerst, damit "sehr gut" vor "gut" gewinnt
                for (p in s.points.sortedByDescending { it.label.length }) {
                    for (f in find(tokens, p.label, emptySet())) {
                        if (before(tokens, f.from, negations)) continue
                        if (fold(p.label) in generic && !nameInSentence(tokens[f.from].sentence)) continue
                        hit(f, p.label, ScaleValue(s.id, pointId = p.id))?.let { return it }
                    }
                }
            }
            ScaleType.NUMBER, ScaleType.DURATION -> {
                for (i in tokens.indices) {
                    val n = number(tokens[i].text) ?: continue
                    val sentence = tokens[i].sentence
                    val near = (i + 1..minOf(tokens.lastIndex, i + 3)).filter { tokens[it].sentence == sentence }
                    if (s.type == ScaleType.DURATION) {
                        val unit = near.firstOrNull { tokens[it].stem == "stund" || tokens[it].text == "std" || tokens[it].text == "h" } ?: continue
                        if (!nameInSentence(sentence)) continue
                        val minutes = (n * 60).toInt().coerceIn(s.min, s.max)
                        hit(Found(i, unit), "%d:%02d Std.".format(minutes / 60, minutes % 60), ScaleValue(s.id, minutes = minutes))?.let { return it }
                    } else {
                        val at = near.firstOrNull { k -> names.any { nm -> find(tokens, nm, emptySet()).any { it.from == k } } } ?: continue
                        val value = n.toInt().coerceIn(s.min, s.max)
                        hit(Found(i, at), value.toString(), ScaleValue(s.id, number = value))?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /** "zwei", "7", "siebeneinhalb", "7,5". */
    private fun number(token: String): Double? {
        token.replace(',', '.').toDoubleOrNull()?.let { return it }
        numbers[token]?.let { return it.toDouble() }
        if (token.endsWith("einhalb")) numbers[token.removeSuffix("einhalb")]?.let { return it + 0.5 }
        return null
    }
}
