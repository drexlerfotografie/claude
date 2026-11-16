package de.mediclever.tagebuch

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.mediclever.tagebuch.core.importer.ImportKind
import de.mediclever.tagebuch.core.importer.ImportPreview
import de.mediclever.tagebuch.core.importer.MergeMode
import de.mediclever.tagebuch.core.model.AssetType
import de.mediclever.tagebuch.core.model.Table
import de.mediclever.tagebuch.core.sync.SyncEngine
import de.mediclever.tagebuch.data.Opened
import de.mediclever.tagebuch.notify.Plan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

/**
 * Import, Sicherung und Export mit echter Datenbank und der kuenstlichen Beispieldatei
 * tests/fixtures/sample.daylio. Die Sollzahlen stammen aus dem Erzeuger der Datei.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class DataOpsTest {
    private lateinit var c: AppContainer
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val fixture = File("../tests/fixtures/sample.daylio")

    // Die App legt ihren Container selbst an; ein zweiter wuerde dieselben Dateien oeffnen
    @Before fun setUp() { c = (app as TagebuchApp).container }
    @After fun tearDown() { c.close() }

    private fun open(f: File): Opened = runBlocking { c.dataOps.open(Uri.fromFile(f)) }
    private fun preview() = runBlocking { ImportPreview.of(c.repo.snapshot()) }

    @Test fun daylioImportMatchesPreviewAndCopiesFiles() = runBlocking {
        val opened = open(fixture) as Opened.Archive
        val want = opened.result.preview
        assertEquals(120, want.entries); assertEquals(14, want.tags); assertEquals(3, want.archivedTags); assertEquals(9, want.assets)
        val out = c.dataOps.importArchive(opened, MergeMode.EMPTY_ONLY) { _, _ -> }
        assertEquals(0, out.report.errors); assertEquals(9, out.filesCopied); assertEquals(0, out.filesFailed)
        val got = preview()
        assertEquals(want, got)
        val d = c.repo.snapshot()
        assertTrue("jede Datei liegt im privaten Speicher", d.assets.all { c.assets.exists(it) })
        assertTrue(d.assets.all { it.type == AssetType.PHOTO || it.type == AssetType.AUDIO })
        assertTrue("alles wartet aufs Hochladen", d.entries.all { it.dirty })
    }

    @Test fun secondImportCreatesNoDuplicates() = runBlocking {
        c.dataOps.importArchive(open(fixture) as Opened.Archive, MergeMode.EMPTY_ONLY) { _, _ -> }
        val first = preview()
        val again = c.dataOps.importArchive(open(fixture) as Opened.Archive, MergeMode.MERGE) { _, _ -> }
        assertEquals(first, preview())
        assertEquals(0, again.report[ImportKind.ENTRIES].taken)
        assertEquals(120, again.report[ImportKind.ENTRIES].skipped)
        assertEquals("vorhandene Dateien werden nicht erneut kopiert", 0, again.filesCopied)
    }

    @Test fun backupRoundTripIntoFreshInstall() = runBlocking {
        c.dataOps.importArchive(open(fixture) as Opened.Archive, MergeMode.EMPTY_ONLY) { _, _ -> }
        val before = preview()
        val backup = File(app.cacheDir, "sicherung.tagebuch")
        val report = c.dataOps.writeBackup(Uri.fromFile(backup))
        assertEquals(9, report.assetsWritten); assertEquals(0, report.assetsMissing)
        c.repo.wipeLocal()
        assertEquals(0, preview().entries)
        val restored = c.dataOps.importArchive(open(backup) as Opened.Archive, MergeMode.EMPTY_ONLY) { _, _ -> }
        assertEquals(9, restored.filesCopied)
        assertEquals(before, preview())
        assertTrue(c.repo.snapshot().assets.all { c.assets.exists(it) })
    }

    @Test fun csvExportReimportsWithoutChanges() = runBlocking {
        c.dataOps.importArchive(open(fixture) as Opened.Archive, MergeMode.EMPTY_ONLY) { _, _ -> }
        val csv = File(app.cacheDir, "export.csv")
        csv.outputStream().use { c.dataOps.writeCsv(it) }
        assertEquals("eine Zeile je Eintrag plus Kopfzeile", 121, de.mediclever.tagebuch.core.importer.CsvFormat.parse(csv.readText(), c.repo.snapshot()).rows.size + 1)
        val opened = open(csv) as Opened.Csv
        assertTrue(opened.import.unknownMoods.isEmpty() && opened.import.unknownActivities.isEmpty() && opened.import.unknownScales.isEmpty())
    }

    @Test fun pdfExportWritesAllEntries() = runBlocking {
        // Robolectric bringt keine PDF-Bibliothek mit. Dort wird dieser Test uebersprungen und
        // muss auf einem Geraet geprueft werden (steht so in docs/abnahme.md).
        val supported = runCatching { android.graphics.pdf.PdfDocument().run { finishPage(startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(10, 10, 1).create())); close() } }.isSuccess
        org.junit.Assume.assumeTrue("PDF-Erzeugung in dieser Testumgebung nicht verfuegbar", supported)
        c.dataOps.importArchive(open(fixture) as Opened.Archive, MergeMode.EMPTY_ONLY) { _, _ -> }
        val pdf = File(app.cacheDir, "export.pdf")
        val n = pdf.outputStream().use { c.dataOps.writePdf(it) }
        assertEquals(120, n)
        assertEquals("%PDF", pdf.inputStream().use { String(it.readNBytes(4)) })
    }

    @Test fun importedDiarySyncsToSecondDeviceThroughFakeServer() = runBlocking {
        c.dataOps.importArchive(open(fixture) as Opened.Archive, MergeMode.EMPTY_ONLY) { _, _ -> }
        val remote = FakeServer()
        val up = SyncEngine(c.repo, remote).syncOnce()
        assertTrue(up.pushed >= 120)
        assertTrue(c.repo.snapshot().entries.none { it.dirty })
        assertEquals(120, remote.docs.getValue(Table.ENTRIES).size)
        assertEquals("zweiter Lauf ohne Arbeit", 0, SyncEngine(c.repo, remote).syncOnce().pushed)
        // Jede hochgeladene Zeile hat die Form, die die Firestore-Regeln verlangen: nur Long, Boolean, String, Liste, Map, null
        for ((_, rows) in remote.maps) for (m in rows) assertTrue(m.values.all { it == null || it is Long || it is Boolean || it is String || it is List<*> || it is Map<*, *> })
    }

    @Test fun remindersArePlannedForActiveItemsOnly() {
        val now = LocalDateTime.of(2026, 11, 16, 12, 0)
        val plan = Plan.upcoming(SampleData.diary, now)
        assertTrue(plan.all { it.at > now })
        assertEquals("eine aktive Erinnerung um 20 Uhr an drei Tagen", 3, plan.count { it.kind == Plan.ENTRY })
        assertEquals("Ziel mit Erinnerung um 18 Uhr an drei Tagen", 3, plan.count { it.kind == Plan.GOAL })
        assertTrue(plan.none { it.kind == Plan.REPORT_MONTH })
    }
}

/** Server im Arbeitsspeicher, der Zeilen in der Map-Form entgegennimmt wie Firestore. */
class FakeServer : de.mediclever.tagebuch.core.sync.RemoteStore {
    private var clock = 0L
    val docs = Table.entries.associateWith { mutableMapOf<String, Pair<Long, de.mediclever.tagebuch.core.model.SyncRow>>() }
    val maps = Table.entries.associateWith { mutableListOf<Map<String, Any?>>() }
    var settings: de.mediclever.tagebuch.core.model.UserSettings? = null
    override suspend fun pull(table: Table, cursor: Long) = docs.getValue(table).values.filter { it.first > cursor }.sortedBy { it.first }
        .let { de.mediclever.tagebuch.core.sync.Pulled(it.map { p -> p.second }, it.maxOfOrNull { p -> p.first } ?: cursor) }
    override suspend fun push(table: Table, rows: List<de.mediclever.tagebuch.core.model.SyncRow>) {
        for (r in rows) { val m = de.mediclever.tagebuch.core.sync.RowCodec.toMap(table, r); maps.getValue(table) += m; docs.getValue(table)[r.id] = ++clock to de.mediclever.tagebuch.core.sync.RowCodec.fromMap(table, m) }
    }
    override suspend fun pullSettings() = settings
    override suspend fun pushSettings(settings: de.mediclever.tagebuch.core.model.UserSettings) { this.settings = settings }
}
