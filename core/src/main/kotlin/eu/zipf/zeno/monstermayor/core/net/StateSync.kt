package eu.zipf.zeno.monstermayor.core.net

import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Top-level JSON delta sync for [GameStatePayload].
 *
 * A typical action (toggling a die, rolling) only changes a handful of fields, so the server sends
 * just those. A client applies a delta only on top of the exact revision it was computed from and
 * asks for a fresh snapshot otherwise.
 */
object StateSync {
    private val json = GameJson

    fun encode(payload: GameStatePayload): JsonObject =
        json.encodeToJsonElement(GameStatePayload.serializer(), payload).jsonObject

    fun diff(previous: GameStatePayload, next: GameStatePayload): JsonObject {
        val before = encode(previous)
        val after = encode(next)
        return JsonObject(after.filter { (key, value) -> before[key] != value })
    }

    fun delta(previous: GameStatePayload, next: GameStatePayload): ServerMessage.StateDelta =
        ServerMessage.StateDelta(previous.revision, next.revision, diff(previous, next))

    /** Returns the patched payload, or null when the delta does not fit onto [base]. */
    fun apply(base: GameStatePayload, delta: ServerMessage.StateDelta): GameStatePayload? {
        if (base.revision != delta.baseRevision) return null
        val merged = JsonObject(encode(base) + delta.changes)
        return runCatching { json.decodeFromJsonElement(GameStatePayload.serializer(), merged) }.getOrNull()
    }
}
