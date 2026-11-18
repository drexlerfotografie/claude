package de.mediclever.tagebuch

import de.mediclever.tagebuch.ui.entry.EntryDraft
import de.mediclever.tagebuch.ui.entry.EntryEditScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryShots : ShotBase() {
    private val d = SampleData.diary

    @Test fun newEntry() = shot("messung_neu") {
        EntryEditScreen(EntryDraft(id = "neu", date = "2026-11-16", minuteOfDay = 19 * 60 + 30), {}, {})
    }

    @Test fun existing() = shot("messung") {
        val e = d.entries.first()
        EntryEditScreen(EntryDraft.from(e, listOf(d.goals[1].id)), {}, {})
    }

    @Test fun voiceReview() = shot("sprachnotiz") {
        val text = "Dritter Nachtdienst, ziemlich müde. Das Team war super. Nach dem Dienst noch kurz laufen gewesen."
        val hits = de.mediclever.tagebuch.core.voice.VoiceMatcher.suggest(text, SampleData.diary)
        de.mediclever.tagebuch.ui.entry.VoiceReview(text, {}, 24_000, null, hits, { it.sure }, {}, auto = false, onAuto = {}, waiting = false, recognitionAvailable = true, onApply = {}, onDismiss = {})
    }
}
