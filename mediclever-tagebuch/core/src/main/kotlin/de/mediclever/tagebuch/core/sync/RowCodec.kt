package de.mediclever.tagebuch.core.sync

import de.mediclever.tagebuch.core.model.SyncRow
import de.mediclever.tagebuch.core.model.Table
import de.mediclever.tagebuch.core.model.UserSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Uebersetzt Modellzeilen in einfache Maps (String, Long, Double, Boolean, List, Map, null)
 * fuer Firestore und zurueck. Das lokale Feld "dirty" und der Serverstempel "syncedAt"
 * gehoeren nicht zur Zeile.
 */
object RowCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }

    @Suppress("UNCHECKED_CAST")
    fun toMap(table: Table, row: SyncRow): Map<String, Any?> {
        val obj = json.encodeToJsonElement(table.serializer as KSerializer<SyncRow>, row) as JsonObject
        return (toAny(obj) as Map<String, Any?>) - "dirty"
    }

    fun fromMap(table: Table, map: Map<String, Any?>): SyncRow {
        val obj = JsonObject((map - "syncedAt" - "dirty").mapValues { toJson(it.value) } + ("dirty" to JsonPrimitive(false)))
        return json.decodeFromJsonElement(table.serializer, obj)
    }

    @Suppress("UNCHECKED_CAST")
    fun settingsToMap(s: UserSettings): Map<String, Any?> =
        toAny(json.encodeToJsonElement(UserSettings.serializer(), s)) as Map<String, Any?>

    fun settingsFromMap(map: Map<String, Any?>): UserSettings =
        json.decodeFromJsonElement(UserSettings.serializer(), JsonObject((map - "syncedAt").mapValues { toJson(it.value) }))

    private fun toAny(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValues { toAny(it.value) }
        is JsonArray -> e.map { toAny(it) }
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.content
    }

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        // Firestore liefert ganze Zahlen teils als Double zurueck
        is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 9e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
        is Number -> JsonPrimitive(v.toLong())
        is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toJson(it.value) })
        is Iterable<*> -> JsonArray(v.map { toJson(it) })
        else -> JsonPrimitive(v.toString())
    }
}
