package de.mediclever.tagebuch.core.voice

import de.mediclever.tagebuch.core.model.*
import de.mediclever.tagebuch.core.voice.VoiceMatcher.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceMatcherTest {
    private val dienst = TagGroup(id = "g", name = "Dienst")
    private val tags = listOf(
        Tag(id = "tag", groupId = "g", name = "Tagschicht", sortOrder = 0),
        Tag(id = "nacht", groupId = "g", name = "Nachtschicht", sortOrder = 1),
        Tag(id = "frei", groupId = "g", name = "Frei", sortOrder = 2),
        Tag(id = "sport", name = "Sport"),
        Tag(id = "team", name = "Gutes Team"),
        Tag(id = "rad", name = "Rad"),
        Tag(id = "alt", name = "Lesen", archivedAt = 1),
    )
    private val energie = Scale(id = "en", type = ScaleType.TEXT, name = "Energie", points = listOf("erschöpft", "gering", "angemessen", "hoch", "energievoll").mapIndexed { i, l -> ScalePoint(id = "p$i", position = i + 1, label = l) })
    private val koffein = Scale(id = "ko", type = ScaleType.NUMBER, name = "Koffein", min = 0, max = 6)
    private val schlaf = Scale(id = "sd", type = ScaleType.DURATION, name = "Schlafdauer", min = 0, max = 14 * 60, step = 15)

    private fun diary(settings: UserSettings = UserSettings()) = Diary(groups = listOf(dienst), tags = tags, scales = listOf(energie, koffein, schlaf), settings = settings)
    private fun ids(text: String, d: Diary = diary(), rejected: Set<String> = emptySet()) = VoiceMatcher.suggest(text, d, rejected).map { it.id }

    @Test fun findsShiftActivitiesAndKeepsTheWord() {
        val hits = VoiceMatcher.suggest("Dritte Nachtschicht, ziemlich müde. Das Team war super, danach noch laufen gewesen.", diary())
        assertEquals(listOf("nacht", "team", "sport"), hits.map { it.id })
        assertEquals(Kind.SHIFT, hits[0].kind)
        assertEquals("Nachtschicht", hits[0].word)
        assertEquals("Team war super", hits[1].word)
        assertEquals("laufen", hits[2].word)
        assertTrue(hits.all { it.sure })
    }

    @Test fun wordFormsCount() {
        assertEquals(listOf("sport"), ids("Bin heute gelaufen."))
        assertEquals(listOf("sport"), ids("Sie läuft jeden Morgen."))
        assertEquals(listOf("sport"), ids("Zwei Stunden trainiert."))
        assertEquals(listOf("nacht"), ids("Wieder Nachtdienst gehabt."))
    }

    @Test fun negationCancelsTheHit() {
        assertEquals(emptyList(), ids("Heute kein Sport."))
        assertEquals(emptyList(), ids("Nicht zum Training geschafft."))
        assertEquals(emptyList(), ids("Ich war nie laufen."))
        // die Verneinung gilt nur im selben Satz
        assertEquals(listOf("sport"), ids("Nicht viel los. Danach laufen."))
    }

    @Test fun intentMakesItUnsure() {
        val hit = VoiceMatcher.suggest("Wollte eigentlich joggen.", diary()).single()
        assertEquals("sport", hit.id)
        assertTrue(!hit.sure)
    }

    @Test fun shortWordsMustMatchExactly() {
        assertEquals(listOf("rad"), ids("Mit dem Rad zur Wache."))
        assertEquals(emptyList(), ids("Ein guter Rat vom Kollegen."))
    }

    @Test fun onlyOneShiftAndFreeIsRecognised() {
        assertEquals(listOf("tag"), ids("Tagschicht, morgen dann Nachtschicht."))
        assertEquals(listOf("frei"), ids("Heute frei gehabt."))
    }

    @Test fun ownKeywordsAndBlockedWords() {
        val d = diary(UserSettings(keywords = listOf(KeywordSet("sport", words = listOf("Bouldern"), blocked = listOf("Sportschau")), KeywordSet("team", words = listOf("Wache drei")))))
        assertEquals(listOf("sport"), ids("Abends bouldern gewesen.", d))
        assertEquals(emptyList(), ids("Abends Sportschau geguckt.", d))
        assertEquals(listOf("team"), ids("Dienst auf Wache drei.", d))
    }

    @Test fun rejectedWordsAreNotSuggestedAgain() {
        assertEquals(emptyList(), ids("Danach laufen.", rejected = setOf("sport|laufen")))
        assertEquals(listOf("sport"), ids("Danach joggen.", rejected = setOf("sport|laufen")))
    }

    @Test fun archivedActivitiesAreIgnored() {
        assertEquals(emptyList(), ids("Viel gelesen."))
    }

    @Test fun textScaleNeedsAClearWord() {
        val hit = VoiceMatcher.suggest("Total erschöpft heute.", diary()).single()
        assertEquals(Kind.SCALE, hit.kind)
        assertEquals("p0", hit.value?.pointId)
        assertTrue(!hit.sure)
        assertEquals("Energie: erschöpft", hit.label)
        // verneint: kein Vorschlag
        assertEquals(emptyList(), ids("Gar nicht erschöpft."))
        // allgemeine Woerter zaehlen nur zusammen mit dem Namen der Skala
        assertEquals(emptyList(), ids("Der Dienst war hoch interessant."))
        assertEquals("p3", VoiceMatcher.suggest("Energie war hoch.", diary()).single().value?.pointId)
    }

    @Test fun numbersForScales() {
        val k = VoiceMatcher.suggest("Zwei Kaffee gegen vier.", diary()).single()
        assertEquals("ko", k.id); assertEquals(2, k.value?.number); assertEquals("Zwei Kaffee", k.word)
        assertEquals(6, VoiceMatcher.suggest("9 Tassen Kaffee.", diary()).single().value?.number)
        val s = VoiceMatcher.suggest("Nur fünf Stunden geschlafen.", diary()).single()
        assertEquals("sd", s.id); assertEquals(300, s.value?.minutes)
        assertEquals(450, VoiceMatcher.suggest("Siebeneinhalb Stunden Schlaf.", diary()).single().value?.minutes)
        // Stunden ohne Bezug zum Schlaf zaehlen nicht
        assertNull(VoiceMatcher.suggest("Zwei Stunden im Stau.", diary()).firstOrNull())
    }

    @Test fun markRangePointsAtTheWord() {
        val text = "Erst Dienst, dann laufen."
        val hit = VoiceMatcher.suggest(text, diary()).single()
        assertEquals("laufen", text.substring(hit.start, hit.end))
    }
}
