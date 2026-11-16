package de.mediclever.tagebuch.core.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IconGuessTest {

    @Test fun onlyCatalogIconsAreUsed() {
        assertEquals(106, IconGuess.ALLOWED.size)
        val foreign = IconGuess.usedIcons() - IconGuess.ALLOWED
        assertTrue(foreign.isEmpty(), "nicht im Katalog: $foreign")
    }

    @Test fun germanAndEnglishNames() {
        val cases = mapOf(
            "Schlaf" to "bed", "Gut geschlafen" to "bed", "sleep early" to "bed",
            "Sport" to "fitness_center", "Arbeit" to "work", "Familie" to "family_restroom", "Freunde" to "group",
            "Dienst" to "emergency", "Spätdienst" to "emergency", "Nachtschicht" to "emergency", "Schicht " to "emergency",
            "Lesen" to "menu_book", "reading" to "menu_book", "Putzen" to "cleaning_services", "Einkaufen" to "shopping_cart",
            "Kochen" to "kitchen", "Kaffee" to "local_cafe", "Spaziergang" to "directions_walk", "Laufen" to "directions_run",
            "Radtour" to "directions_bike", "Schwimmen" to "pool", "Yoga" to "self_improvement", "Meditation" to "self_improvement",
            "Kino" to "movie", "Netflix" to "tv", "Zocken" to "sports_esports", "Musik hören" to "music_note",
            "Urlaub" to "beach_access", "Arzttermin" to "medical_services", "Krank" to "sick", "Medikamente" to "medication",
            "Hund" to "pets", "Garten" to "yard", "Wäsche" to "local_laundry_service", "Party" to "celebration",
            "Date" to "favorite", "work" to "work", "friends" to "group", "family" to "family_restroom",
            "Alkohol" to "local_bar", "Wandern" to "hiking", "Lernen" to "school", "Therapie" to "psychology",
        )
        for ((name, icon) in cases) assertEquals(icon, IconGuess.guess(name), name)
    }

    @Test fun fallbackAndFalseFriends() {
        assertEquals("circle", IconGuess.guess(""))
        assertEquals("circle", IconGuess.guess("   "))
        assertEquals("circle", IconGuess.guess("Xyzzy"))
        // Kurze Stichwoerter duerfen nicht mitten in fremden Woertern treffen.
        assertEquals("circle", IconGuess.guess("Dienstag"))
        assertEquals("circle", IconGuess.guess("Vergessen"))
        assertEquals("circle", IconGuess.guess("Artikel"))
        assertEquals("circle", IconGuess.guess("Daten"))
        for (name in listOf("Schlaf", "🙂", "a|b", "ÄÖÜ ß", "x".repeat(500))) assertTrue(IconGuess.guess(name) in IconGuess.ALLOWED)
    }
}
