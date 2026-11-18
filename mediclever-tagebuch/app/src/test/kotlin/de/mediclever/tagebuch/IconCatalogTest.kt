package de.mediclever.tagebuch

import de.mediclever.tagebuch.core.importer.IconGuess
import de.mediclever.tagebuch.ui.icons.ActivityIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogTest {
    @Test fun keysAreUnique() = assertEquals(ActivityIcons.size, ActivityIcons.map { it.key }.toSet().size)

    /** Der Import darf nur Symbole waehlen, die es in der App gibt, und kennt umgekehrt alle. */
    @Test fun importerKnowsExactlyTheCatalog() = assertEquals(ActivityIcons.map { it.key }.toSet(), IconGuess.ALLOWED)

    @Test fun catalogIsLargeAndGrouped() {
        assertTrue(ActivityIcons.size >= 350)
        assertTrue(ActivityIcons.map { it.category }.distinct().size >= 15)
    }
}
