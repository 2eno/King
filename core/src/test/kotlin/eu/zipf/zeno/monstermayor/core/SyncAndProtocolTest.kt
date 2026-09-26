package eu.zipf.zeno.monstermayor.core

import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.GameJson
import eu.zipf.zeno.monstermayor.core.net.PlayerProfile
import eu.zipf.zeno.monstermayor.core.net.RoomCodes
import eu.zipf.zeno.monstermayor.core.net.ServerMessage
import eu.zipf.zeno.monstermayor.core.net.StateSync
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncAndProtocolTest {

    @Test
    fun `payload survives a JSON round trip`() {
        val (engine, start) = newGame(players = 4)
        val state = engine.ok(start, "p0", GameIntent.RollDice)
        val payload = state.toPayload()
        val json = GameJson.encodeToString(GameStatePayload.serializer(), payload)
        assertEquals(payload, GameJson.decodeFromString(GameStatePayload.serializer(), json))
        assertFalse("deck\"" in json, "the hidden deck order must never be synced")
    }

    @Test
    fun `authoritative state survives a JSON round trip for saving`() {
        val (engine, start) = newGame(players = 3)
        val state = engine.ok(start, "p0", GameIntent.RollDice)
        val json = GameJson.encodeToString(GameState.serializer(), state)
        assertEquals(state, GameJson.decodeFromString(GameState.serializer(), json))
    }

    @Test
    fun `deltas only carry changed fields and rebuild the next payload`() {
        val (engine, start) = newGame(players = 4)
        val rolled = engine.ok(start, "p0", GameIntent.RollDice)
        val held = engine.ok(rolled, "p0", GameIntent.ToggleHold(2))

        val delta = StateSync.delta(rolled.toPayload(), held.toPayload())
        assertEquals(setOf("diceHeld", "revision"), delta.changes.keys)
        assertEquals(held.toPayload(), StateSync.apply(rolled.toPayload(), delta))
    }

    @Test
    fun `a delta is refused on the wrong base revision`() {
        val (engine, start) = newGame()
        val a = engine.ok(start, "p0", GameIntent.RollDice)
        val b = engine.ok(a, "p0", GameIntent.ToggleHold(0))
        val c = engine.ok(b, "p0", GameIntent.ToggleHold(1))
        assertNull(StateSync.apply(a.toPayload(), StateSync.delta(b.toPayload(), c.toPayload())))
    }

    @Test
    fun `fields that become null are carried by deltas`() {
        val (engine, start) = newGame()
        val attacked = engine.ok(start.inTokyo("p1").withDice(CLAW, ONE, TWO, THREE, ONE, TWO), "p0", GameIntent.ResolveDice)
        val decided = engine.ok(attacked, "p1", GameIntent.TokyoDecision(yieldTokyo = false))
        val delta = StateSync.delta(attacked.toPayload(), decided.toPayload())
        assertTrue("pendingYieldPlayerId" in delta.changes)
        assertEquals(decided.toPayload(), StateSync.apply(attacked.toPayload(), delta))
    }

    @Test
    fun `messages use a type discriminator`() {
        val message: ClientMessage = ClientMessage.JoinRoom("ABCD", PlayerProfile("Zeno", CharacterId.CYBER_KITTY))
        val json = GameJson.encodeToString(ClientMessage.serializer(), message)
        assertEquals("join_room", GameJson.parseToJsonElement(json).jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(message, GameJson.decodeFromString(ClientMessage.serializer(), json))

        val intent: ClientMessage = ClientMessage.SubmitIntent(GameIntent.ToggleHold(3))
        val intentJson = GameJson.encodeToString(ClientMessage.serializer(), intent)
        assertEquals(intent, GameJson.decodeFromString(ClientMessage.serializer(), intentJson))

        val objectIntent: ClientMessage = ClientMessage.SubmitIntent(GameIntent.RollDice)
        assertEquals(
            objectIntent,
            GameJson.decodeFromString(ClientMessage.serializer(), GameJson.encodeToString(ClientMessage.serializer(), objectIntent)),
        )

        val kicked: ServerMessage = ServerMessage.Kicked
        assertEquals(kicked, GameJson.decodeFromString(ServerMessage.serializer(), GameJson.encodeToString(ServerMessage.serializer(), kicked)))
    }

    @Test
    fun `room codes are four unambiguous letters`() {
        assertTrue(RoomCodes.isValid("ABCD"))
        assertFalse(RoomCodes.isValid("ABC"))
        assertFalse(RoomCodes.isValid("ABIO"))
        assertEquals("WXYZ", RoomCodes.normalize(" wxyz "))
    }
}
