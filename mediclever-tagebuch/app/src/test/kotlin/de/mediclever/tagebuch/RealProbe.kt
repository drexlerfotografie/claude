package de.mediclever.tagebuch

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.mediclever.tagebuch.core.importer.MergeMode
import de.mediclever.tagebuch.data.Opened
import de.mediclever.tagebuch.data.StarterPacks
import de.mediclever.tagebuch.ui.entry.EntryDraft
import de.mediclever.tagebuch.ui.entry.EntryEditScreen
import de.mediclever.tagebuch.ui.home.HomeScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.YearMonth

/**
 * Lokale Sichtprobe mit dem echten Backup (private/, nie im Repository). Ohne Datei uebersprungen.
 * Die Bilder landen unter app/build/private-shots/ und bleiben auf diesem Rechner.
 */
@RunWith(RobolectricTestRunner::class)
class RealProbe : ShotBase() {
    private fun load(): de.mediclever.tagebuch.core.model.Diary {
        val f = File("../private/backup.daylio"); Assume.assumeTrue(f.isFile)
        val c = (ApplicationProvider.getApplicationContext<Application>() as TagebuchApp).container
        runBlocking {
            val s = StarterPacks.build("allgemein")
            c.repo.save(s.moods + s.groups + s.tags + s.scales)
            c.dataOps.importArchive(c.dataOps.open(Uri.fromFile(f)) as Opened.Archive, MergeMode.EMPTY_ONLY, replace = true) { _, _ -> }
            kotlinx.coroutines.delay(800)
        }
        val live = c.repo.diary.value!!
        c.close()
        org.junit.Assert.assertEquals(1765, live.entries.size)
        org.junit.Assert.assertEquals("custom", live.settings.moodScheme)
        return live
    }

    @Test fun calendar() { val d = load(); shot("../private-shots/kalender", d) { HomeScreen(startTab = 2, startMonth = YearMonth.of(2026, 4)) } }
    @Test fun entry() { val d = load(); shot("../private-shots/eintrag", d) { EntryEditScreen(EntryDraft.from(d.entries.first { it.scaleValues.size >= 2 }, emptyList()), {}, {}) } }
    @Test fun stats() { val d = load(); shot("../private-shots/statistik", d) { HomeScreen(startTab = 1, startMonth = YearMonth.of(2026, 5)) } }
}
