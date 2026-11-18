package de.mediclever.tagebuch.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import de.mediclever.tagebuch.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class Converters {
    @TypeConverter fun stringsToJson(v: List<String>): String = AppJson.encodeToString(v)
    @TypeConverter fun jsonToStrings(v: String): List<String> = AppJson.decodeFromString(v)
    @TypeConverter fun intsToJson(v: List<Int>): String = AppJson.encodeToString(v)
    @TypeConverter fun jsonToInts(v: String): List<Int> = AppJson.decodeFromString(v)
    @TypeConverter fun valuesToJson(v: List<ScaleValue>): String = AppJson.encodeToString(v)
    @TypeConverter fun jsonToValues(v: String): List<ScaleValue> = AppJson.decodeFromString(v)
    @TypeConverter fun pointsToJson(v: List<ScalePoint>): String = AppJson.encodeToString(v)
    @TypeConverter fun jsonToPoints(v: String): List<ScalePoint> = AppJson.decodeFromString(v)
}

/** Gemeinsame Zugriffe aller abgeglichenen Tabellen. */
interface SyncDao<T : SyncRow> {
    fun observe(): Flow<List<T>>
    suspend fun all(): List<T>
    suspend fun dirty(): List<T>
    suspend fun byId(id: String): T?
    suspend fun byIds(ids: List<String>): List<T>
    suspend fun upsert(rows: List<T>)
    suspend fun markClean(id: String, updatedAt: Long)
    suspend fun markAllDirty()
    suspend fun purgeTombstones()
    suspend fun wipe()
}

@Dao
interface MoodDao : SyncDao<Mood> {
    @Query("SELECT * FROM moods WHERE deleted = 0") override fun observe(): Flow<List<Mood>>
    @Query("SELECT * FROM moods WHERE deleted = 0") override suspend fun all(): List<Mood>
    @Query("SELECT * FROM moods WHERE dirty = 1") override suspend fun dirty(): List<Mood>
    @Query("SELECT * FROM moods WHERE id = :id") override suspend fun byId(id: String): Mood?
    @Query("SELECT * FROM moods WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Mood>
    @Upsert override suspend fun upsert(rows: List<Mood>)
    @Query("UPDATE moods SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE moods SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM moods WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM moods") override suspend fun wipe()
}

@Dao
interface TagGroupDao : SyncDao<TagGroup> {
    @Query("SELECT * FROM tag_groups WHERE deleted = 0") override fun observe(): Flow<List<TagGroup>>
    @Query("SELECT * FROM tag_groups WHERE deleted = 0") override suspend fun all(): List<TagGroup>
    @Query("SELECT * FROM tag_groups WHERE dirty = 1") override suspend fun dirty(): List<TagGroup>
    @Query("SELECT * FROM tag_groups WHERE id = :id") override suspend fun byId(id: String): TagGroup?
    @Query("SELECT * FROM tag_groups WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<TagGroup>
    @Upsert override suspend fun upsert(rows: List<TagGroup>)
    @Query("UPDATE tag_groups SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE tag_groups SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM tag_groups WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM tag_groups") override suspend fun wipe()
}

@Dao
interface TagDao : SyncDao<Tag> {
    @Query("SELECT * FROM tags WHERE deleted = 0") override fun observe(): Flow<List<Tag>>
    @Query("SELECT * FROM tags WHERE deleted = 0") override suspend fun all(): List<Tag>
    @Query("SELECT * FROM tags WHERE dirty = 1") override suspend fun dirty(): List<Tag>
    @Query("SELECT * FROM tags WHERE id = :id") override suspend fun byId(id: String): Tag?
    @Query("SELECT * FROM tags WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Tag>
    @Upsert override suspend fun upsert(rows: List<Tag>)
    @Query("UPDATE tags SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE tags SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM tags WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM tags") override suspend fun wipe()
}

@Dao
interface ScaleDao : SyncDao<Scale> {
    @Query("SELECT * FROM scales WHERE deleted = 0") override fun observe(): Flow<List<Scale>>
    @Query("SELECT * FROM scales WHERE deleted = 0") override suspend fun all(): List<Scale>
    @Query("SELECT * FROM scales WHERE dirty = 1") override suspend fun dirty(): List<Scale>
    @Query("SELECT * FROM scales WHERE id = :id") override suspend fun byId(id: String): Scale?
    @Query("SELECT * FROM scales WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Scale>
    @Upsert override suspend fun upsert(rows: List<Scale>)
    @Query("UPDATE scales SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE scales SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM scales WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM scales") override suspend fun wipe()
}

@Dao
interface EntryDao : SyncDao<Entry> {
    @Query("SELECT * FROM entries WHERE deleted = 0") override fun observe(): Flow<List<Entry>>
    @Query("SELECT * FROM entries WHERE deleted = 0") override suspend fun all(): List<Entry>
    @Query("SELECT * FROM entries WHERE dirty = 1") override suspend fun dirty(): List<Entry>
    @Query("SELECT * FROM entries WHERE id = :id") override suspend fun byId(id: String): Entry?
    @Query("SELECT * FROM entries WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Entry>
    @Upsert override suspend fun upsert(rows: List<Entry>)
    @Query("UPDATE entries SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE entries SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM entries WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM entries") override suspend fun wipe()
}

@Dao
interface AssetDao : SyncDao<Asset> {
    @Query("SELECT * FROM assets WHERE deleted = 0") override fun observe(): Flow<List<Asset>>
    @Query("SELECT * FROM assets WHERE deleted = 0") override suspend fun all(): List<Asset>
    @Query("SELECT * FROM assets WHERE dirty = 1") override suspend fun dirty(): List<Asset>
    @Query("SELECT * FROM assets WHERE id = :id") override suspend fun byId(id: String): Asset?
    @Query("SELECT * FROM assets WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Asset>
    @Upsert override suspend fun upsert(rows: List<Asset>)
    @Query("UPDATE assets SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE assets SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM assets WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM assets") override suspend fun wipe()
}

@Dao
interface GoalDao : SyncDao<Goal> {
    @Query("SELECT * FROM goals WHERE deleted = 0") override fun observe(): Flow<List<Goal>>
    @Query("SELECT * FROM goals WHERE deleted = 0") override suspend fun all(): List<Goal>
    @Query("SELECT * FROM goals WHERE dirty = 1") override suspend fun dirty(): List<Goal>
    @Query("SELECT * FROM goals WHERE id = :id") override suspend fun byId(id: String): Goal?
    @Query("SELECT * FROM goals WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Goal>
    @Upsert override suspend fun upsert(rows: List<Goal>)
    @Query("UPDATE goals SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE goals SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM goals WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM goals") override suspend fun wipe()
}

@Dao
interface GoalCheckDao : SyncDao<GoalCheck> {
    @Query("SELECT * FROM goal_checks WHERE deleted = 0") override fun observe(): Flow<List<GoalCheck>>
    @Query("SELECT * FROM goal_checks WHERE deleted = 0") override suspend fun all(): List<GoalCheck>
    @Query("SELECT * FROM goal_checks WHERE dirty = 1") override suspend fun dirty(): List<GoalCheck>
    @Query("SELECT * FROM goal_checks WHERE id = :id") override suspend fun byId(id: String): GoalCheck?
    @Query("SELECT * FROM goal_checks WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<GoalCheck>
    @Upsert override suspend fun upsert(rows: List<GoalCheck>)
    @Query("UPDATE goal_checks SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE goal_checks SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM goal_checks WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM goal_checks") override suspend fun wipe()
}

@Dao
interface ImportantDayDao : SyncDao<ImportantDay> {
    @Query("SELECT * FROM important_days WHERE deleted = 0") override fun observe(): Flow<List<ImportantDay>>
    @Query("SELECT * FROM important_days WHERE deleted = 0") override suspend fun all(): List<ImportantDay>
    @Query("SELECT * FROM important_days WHERE dirty = 1") override suspend fun dirty(): List<ImportantDay>
    @Query("SELECT * FROM important_days WHERE id = :id") override suspend fun byId(id: String): ImportantDay?
    @Query("SELECT * FROM important_days WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<ImportantDay>
    @Upsert override suspend fun upsert(rows: List<ImportantDay>)
    @Query("UPDATE important_days SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE important_days SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM important_days WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM important_days") override suspend fun wipe()
}

@Dao
interface TemplateDao : SyncDao<Template> {
    @Query("SELECT * FROM templates WHERE deleted = 0") override fun observe(): Flow<List<Template>>
    @Query("SELECT * FROM templates WHERE deleted = 0") override suspend fun all(): List<Template>
    @Query("SELECT * FROM templates WHERE dirty = 1") override suspend fun dirty(): List<Template>
    @Query("SELECT * FROM templates WHERE id = :id") override suspend fun byId(id: String): Template?
    @Query("SELECT * FROM templates WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Template>
    @Upsert override suspend fun upsert(rows: List<Template>)
    @Query("UPDATE templates SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE templates SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM templates WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM templates") override suspend fun wipe()
}

@Dao
interface ReminderDao : SyncDao<Reminder> {
    @Query("SELECT * FROM reminders WHERE deleted = 0") override fun observe(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders WHERE deleted = 0") override suspend fun all(): List<Reminder>
    @Query("SELECT * FROM reminders WHERE dirty = 1") override suspend fun dirty(): List<Reminder>
    @Query("SELECT * FROM reminders WHERE id = :id") override suspend fun byId(id: String): Reminder?
    @Query("SELECT * FROM reminders WHERE id IN (:ids)") override suspend fun byIds(ids: List<String>): List<Reminder>
    @Upsert override suspend fun upsert(rows: List<Reminder>)
    @Query("UPDATE reminders SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt") override suspend fun markClean(id: String, updatedAt: Long)
    @Query("UPDATE reminders SET dirty = 1") override suspend fun markAllDirty()
    @Query("DELETE FROM reminders WHERE deleted = 1 AND dirty = 0") override suspend fun purgeTombstones()
    @Query("DELETE FROM reminders") override suspend fun wipe()
}

@Database(
    entities = [Mood::class, TagGroup::class, Tag::class, Scale::class, Entry::class, Asset::class, Goal::class, GoalCheck::class, ImportantDay::class, Template::class, Reminder::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
    abstract fun tagGroupDao(): TagGroupDao
    abstract fun tagDao(): TagDao
    abstract fun scaleDao(): ScaleDao
    abstract fun entryDao(): EntryDao
    abstract fun assetDao(): AssetDao
    abstract fun goalDao(): GoalDao
    abstract fun goalCheckDao(): GoalCheckDao
    abstract fun importantDayDao(): ImportantDayDao
    abstract fun templateDao(): TemplateDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tagebuch.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
