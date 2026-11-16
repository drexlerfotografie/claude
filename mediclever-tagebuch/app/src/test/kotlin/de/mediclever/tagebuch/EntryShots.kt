package de.mediclever.tagebuch

import de.mediclever.tagebuch.ui.entry.EntryDraft
import de.mediclever.tagebuch.ui.entry.EntryEditScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryShots : ShotBase() {
    private val d = SampleData.diary

    @Test fun step1() = shot("eintrag_schritt1") {
        EntryEditScreen(EntryDraft(id = "neu", date = "2026-11-16", minuteOfDay = 19 * 60 + 30), {}, {})
    }

    @Test fun step2() = shot("eintrag_schritt2") {
        val e = d.entries.first()
        EntryEditScreen(EntryDraft.from(e, listOf(d.goals[1].id)), {}, {})
    }
}
