package de.mediclever.tagebuch.core.sync

import de.mediclever.tagebuch.core.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Server im Arbeitsspeicher mit fortlaufendem Serverstempel wie syncedAt in Firestore. */
class FakeRemote : RemoteStore {
    private var clock = 0L
    val docs = Table.entries.associateWith { mutableMapOf<String, Pair<Long, SyncRow>>() }
    var settings: UserSettings? = null
    var failNextPush = false

    override suspend fun pull(table: Table, cursor: Long): Pulled {
        val hits = docs.getValue(table).values.filter { it.first > cursor }.sortedBy { it.first }
        return Pulled(hits.map { it.second }, hits.maxOfOrNull { it.first } ?: cursor)
    }
    override suspend fun push(table: Table, rows: List<SyncRow>) {
        if (failNextPush) { failNextPush = false; error("Netz weg") }
        // Ueber die Map-Form schicken, damit der Codec mitgeprueft wird
        rows.forEach { docs.getValue(table)[it.id] = ++clock to RowCodec.fromMap(table, RowCodec.toMap(table, it)) }
    }
    override suspend fun pullSettings() = settings
    override suspend fun pushSettings(settings: UserSettings) { this.settings = settings }
}

class FakeLocal : LocalStore {
    val rows = Table.entries.associateWith { mutableMapOf<String, SyncRow>() }
    private val cursors = mutableMapOf<Table, Long>()
    var userSettings = UserSettings()
    var settingsAreDirty = false

    fun put(table: Table, row: SyncRow) { rows.getValue(table)[row.id] = row }
    @Suppress("UNCHECKED_CAST") fun <T : SyncRow> get(table: Table, id: String) = rows.getValue(table)[id] as T?

    override suspend fun dirty(table: Table) = rows.getValue(table).values.filter { it.dirty }
    override suspend fun byIds(table: Table, ids: List<String>) = ids.mapNotNull { rows.getValue(table)[it] }
    override suspend fun upsert(table: Table, rows: List<SyncRow>) { rows.forEach { put(table, it) } }
    override suspend fun markClean(table: Table, id: String, updatedAt: Long) {
        val r = rows.getValue(table)[id] ?: return
        if (r.updatedAt == updatedAt) put(table, r.withDirty(false))
    }
    override suspend fun cursor(table: Table) = cursors[table] ?: 0
    override suspend fun setCursor(table: Table, cursor: Long) { cursors[table] = cursor }
    override suspend fun settings() = userSettings
    override suspend fun settingsDirty() = settingsAreDirty
    override suspend fun applyRemoteSettings(settings: UserSettings) { userSettings = settings; settingsAreDirty = false }
    override suspend fun markSettingsClean(updatedAt: Long) { if (userSettings.updatedAt == updatedAt) settingsAreDirty = false }
}

class SyncEngineTest {
    private val remote = FakeRemote()
    private val phone = FakeLocal()
    private val tablet = FakeLocal()
    private fun sync(l: FakeLocal) = runBlocking { SyncEngine(l, remote).syncOnce() }

    private fun entry(id: String, at: Long, title: String = "") = Entry(
        id = id, timestamp = 1_700_000_000_000, offsetMinutes = 60, localDate = "2023-11-14", minuteOfDay = 600,
        moodId = "m1", title = title, tagIds = listOf("t1", "t2"),
        scaleValues = listOf(ScaleValue("s1", pointId = "p2"), ScaleValue("s2", minutes = 420)), updatedAt = at,
    )

    @Test fun newRowTravelsFromPhoneToTablet() {
        phone.put(Table.ENTRIES, entry("e1", 10, "Hallo"))
        assertEquals(SyncReport(0, 1), sync(phone))
        assertFalse(phone.get<Entry>(Table.ENTRIES, "e1")!!.dirty)
        assertEquals(1, sync(tablet).pulled)
        assertEquals(entry("e1", 10, "Hallo").copy(dirty = false), tablet.get<Entry>(Table.ENTRIES, "e1"))
    }

    @Test fun secondSyncChangesNothing() {
        phone.put(Table.ENTRIES, entry("e1", 10))
        sync(phone)
        assertEquals(SyncReport(0, 0), sync(phone))
    }

    @Test fun newerEditWins() {
        phone.put(Table.ENTRIES, entry("e1", 10, "alt")); sync(phone); sync(tablet)
        tablet.put(Table.ENTRIES, entry("e1", 30, "Tablet"))
        phone.put(Table.ENTRIES, entry("e1", 20, "Handy"))
        sync(tablet); sync(phone); sync(tablet)
        assertEquals("Tablet", phone.get<Entry>(Table.ENTRIES, "e1")!!.title)
        assertEquals("Tablet", tablet.get<Entry>(Table.ENTRIES, "e1")!!.title)
    }

    @Test fun localNewerEditSurvivesPullAndIsPushed() {
        phone.put(Table.ENTRIES, entry("e1", 10, "alt")); sync(phone); sync(tablet)
        tablet.put(Table.ENTRIES, entry("e1", 20, "Tablet")); sync(tablet)
        phone.put(Table.ENTRIES, entry("e1", 30, "Handy"))
        sync(phone); sync(tablet)
        assertEquals("Handy", tablet.get<Entry>(Table.ENTRIES, "e1")!!.title)
    }

    @Test fun deletionReachesOtherDevice() {
        phone.put(Table.ENTRIES, entry("e1", 10)); sync(phone); sync(tablet)
        phone.put(Table.ENTRIES, entry("e1", 20).copy(deleted = true)); sync(phone); sync(tablet)
        assertTrue(tablet.get<Entry>(Table.ENTRIES, "e1")!!.deleted)
    }

    @Test fun failedPushKeepsRowDirtyAndRetries() {
        phone.put(Table.ENTRIES, entry("e1", 10))
        remote.failNextPush = true
        runCatching { sync(phone) }
        assertTrue(phone.get<Entry>(Table.ENTRIES, "e1")!!.dirty)
        assertEquals(1, sync(phone).pushed)
        assertEquals(1, remote.docs.getValue(Table.ENTRIES).size)
    }

    @Test fun editDuringUploadStaysDirty() = runBlocking {
        val l = FakeLocal()
        l.put(Table.ENTRIES, entry("e1", 20))
        l.markClean(Table.ENTRIES, "e1", 10)
        assertTrue(l.get<Entry>(Table.ENTRIES, "e1")!!.dirty)
    }

    @Test fun settingsNewerSideWins() {
        phone.userSettings = UserSettings(weekStart = 7, updatedAt = 5); phone.settingsAreDirty = true
        sync(phone)
        assertEquals(7, remote.settings!!.weekStart)
        tablet.userSettings = UserSettings(weekStart = 1, updatedAt = 1)
        sync(tablet)
        assertEquals(7, tablet.userSettings.weekStart)
        tablet.userSettings = tablet.userSettings.copy(showQuote = false, updatedAt = 9); tablet.settingsAreDirty = true
        sync(tablet); sync(phone)
        assertFalse(phone.userSettings.showQuote)
    }

    @Test fun everyTableRoundTripsThroughCodec() {
        val samples: Map<Table, SyncRow> = mapOf(
            Table.MOODS to Mood(id = "a", level = 2, name = "Gut", icon = "l2a"),
            Table.TAG_GROUPS to TagGroup(id = "a", name = "Alltag", collapsed = true, archivedAt = 5),
            Table.TAGS to Tag(id = "a", groupId = null, name = "Lesen", colorIndex = 3),
            Table.SCALES to Scale(id = "a", type = ScaleType.TEXT, name = "Schlaf", points = listOf(ScalePoint("p", 1, "schlecht"))),
            Table.ENTRIES to entry("a", 3),
            Table.ASSETS to Asset(id = "a", fileName = "2026/01/x.jpg", sizeBytes = 12345678901),
            Table.GOALS to Goal(id = "a", name = "Sport", repeat = GoalRepeat.WEEKDAYS, weekdays = listOf(1, 3), startDate = "2026-01-01", reminderMinuteOfDay = 480),
            Table.GOAL_CHECKS to GoalCheck(id = "a|2026-01-02", goalId = "a", date = "2026-01-02"),
            Table.IMPORTANT_DAYS to ImportantDay(id = "a", name = "Examen", date = "2020-03-01", remindDaysBefore = listOf(1, 7)),
            Table.TEMPLATES to Template(id = "a", title = "Dankbarkeit", body = "<p>Heute</p>"),
            Table.REMINDERS to Reminder(id = "a", minuteOfDay = 1200, text = "Wie war dein Tag?"),
        )
        assertEquals(Table.entries.toSet(), samples.keys)
        for ((table, row) in samples) {
            val map = RowCodec.toMap(table, row)
            assertFalse("dirty" in map, table.path)
            // Firestore gibt Zahlen als Long oder Double zurueck
            val fromServer = map.mapValues { (_, v) -> if (v is Long && v < 100) v.toDouble() else v } + ("syncedAt" to 1L)
            assertEquals(row.withDirty(false), RowCodec.fromMap(table, fromServer), table.path)
        }
        assertNull(RowCodec.toMap(Table.TAGS, samples.getValue(Table.TAGS))["groupId"])
    }
}
