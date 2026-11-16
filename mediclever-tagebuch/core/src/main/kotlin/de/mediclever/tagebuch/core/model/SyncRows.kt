package de.mediclever.tagebuch.core.model

import kotlinx.serialization.KSerializer

/** Die abgeglichenen Sammlungen. path ist zugleich Room-Tabellenname und Firestore-Sammlung. */
enum class Table(val path: String, val serializer: KSerializer<out SyncRow>) {
    MOODS("moods", Mood.serializer()),
    TAG_GROUPS("tag_groups", TagGroup.serializer()),
    TAGS("tags", Tag.serializer()),
    SCALES("scales", Scale.serializer()),
    ENTRIES("entries", Entry.serializer()),
    ASSETS("assets", Asset.serializer()),
    GOALS("goals", Goal.serializer()),
    GOAL_CHECKS("goal_checks", GoalCheck.serializer()),
    IMPORTANT_DAYS("important_days", ImportantDay.serializer()),
    TEMPLATES("templates", Template.serializer()),
    REMINDERS("reminders", Reminder.serializer()),
}

/** Kopie mit neuem Aenderungsstempel. Jede lokale Aenderung laeuft hier durch. */
@Suppress("UNCHECKED_CAST")
fun <T : SyncRow> T.stamped(now: Long, deleted: Boolean = this.deleted, dirty: Boolean = true): T = when (this) {
    is Mood -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is TagGroup -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Tag -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Scale -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Entry -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Asset -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Goal -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is GoalCheck -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is ImportantDay -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Template -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    is Reminder -> copy(updatedAt = now, deleted = deleted, dirty = dirty)
    else -> error("Unbekannte Zeile: ${this::class}")
} as T

/** Kopie mit geaendertem dirty-Kennzeichen, updatedAt bleibt. */
fun <T : SyncRow> T.withDirty(dirty: Boolean): T = stamped(updatedAt, deleted, dirty)
