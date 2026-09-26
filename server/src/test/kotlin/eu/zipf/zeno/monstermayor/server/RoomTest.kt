package eu.zipf.zeno.monstermayor.server

import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.ErrorCode
import eu.zipf.zeno.monstermayor.core.net.LobbyState
import eu.zipf.zeno.monstermayor.core.net.PlayerProfile
import eu.zipf.zeno.monstermayor.core.net.RoomStatus
import eu.zipf.zeno.monstermayor.core.net.ServerMessage
import eu.zipf.zeno.monstermayor.core.net.StateSync
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Records what a client would receive and rebuilds its game view from snapshots and deltas. */
class FakeConnection : ClientConnection {
    val messages = mutableListOf<ServerMessage>()
    var closed = false
    var view: GameStatePayload? = null
    override var lastSyncedState: GameStatePayload? = null

    override fun send(message: ServerMessage) {
        messages += message
        when (message) {
            is ServerMessage.StateSnapshot -> view = message.state
            is ServerMessage.StateDelta -> view = StateSync.apply(view ?: fail("delta before snapshot"), message)
                ?: fail("delta ${message.baseRevision}->${message.revision} does not apply to ${view?.revision}")
            else -> Unit
        }
    }

    override fun close() {
        closed = true
    }

    val lobby: LobbyState get() = messages.filterIsInstance<ServerMessage.LobbyUpdate>().last().lobby
    val errors: List<ServerMessage.Error> get() = messages.filterIsInstance<ServerMessage.Error>()
    val joined: ServerMessage.Joined get() = messages.filterIsInstance<ServerMessage.Joined>().single()
}

class RoomTest {
    private val config = RoomConfig(botStepDelay = 100.milliseconds, disconnectGrace = 5.seconds)

    private fun TestScope.room() = Room(
        code = "ABCD",
        scope = backgroundScope,
        config = config,
        engineFactory = { GameEngine(Random(42)) },
        clock = { testScheduler.currentTime },
    )

    private suspend fun Room.seat(name: String, character: CharacterId = CharacterId.GIGAZAUR): Pair<String, FakeConnection> {
        val connection = FakeConnection()
        val id = join(PlayerProfile(name, character), connection) ?: fail("join failed: ${connection.errors}")
        return id to connection
    }

    @Test
    fun `first player hosts and monsters stay unique`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Zeno", CharacterId.CYBER_KITTY)
        val (guestId, guest) = room.seat("Ada", CharacterId.CYBER_KITTY)
        val lobby = guest.lobby
        assertEquals(hostId, lobby.hostPlayerId)
        assertEquals(listOf(hostId, guestId), lobby.seats.map { it.playerId })
        assertEquals(CharacterId.CYBER_KITTY, lobby.seats[0].characterId)
        assertNotEquals(CharacterId.CYBER_KITTY, lobby.seats[1].characterId)
        assertEquals(lobby, host.lobby)
    }

    @Test
    fun `only the host starts, and only once everyone is ready`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Zeno")
        val (guestId, guest) = room.seat("Ada")

        room.handle(guestId, guest, ClientMessage.StartGame)
        assertEquals(ErrorCode.NOT_HOST, guest.errors.last().code)

        room.handle(hostId, host, ClientMessage.StartGame)
        assertEquals(ErrorCode.CANNOT_START, host.errors.last().code)

        room.handle(guestId, guest, ClientMessage.SetReady(true))
        assertTrue(host.lobby.canStart)
        room.handle(hostId, host, ClientMessage.StartGame)
        runCurrent()

        assertEquals(RoomStatus.IN_GAME, host.lobby.status)
        val hostView = assertNotNull(host.view)
        assertEquals(hostView, guest.view)
        assertEquals(TurnPhase.ROLL_PHASE, hostView.turnPhase)
        assertEquals(3, hostView.cardMarket.size)
    }

    @Test
    fun `lobby rejects a seventh monster and joins after the start`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Host")
        repeat(5) { room.handle(hostId, host, ClientMessage.AddBot) }
        assertEquals(6, host.lobby.seats.size)
        assertEquals(6, host.lobby.seats.map { it.characterId }.toSet().size)

        val late = FakeConnection()
        assertNull(room.join(PlayerProfile("Late", CharacterId.GIGAZAUR), late))
        assertEquals(ErrorCode.ROOM_FULL, late.errors.single().code)

        room.handle(hostId, host, ClientMessage.KickPlayer(host.lobby.seats.last().playerId))
        room.handle(hostId, host, ClientMessage.StartGame)
        runCurrent()
        val later = FakeConnection()
        assertNull(room.join(PlayerProfile("Later", CharacterId.GIGAZAUR), later))
        assertEquals(ErrorCode.GAME_IN_PROGRESS, later.errors.single().code)
    }

    @Test
    fun `kicked players are told and disconnected`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Host")
        val (guestId, guest) = room.seat("Guest")
        room.handle(hostId, host, ClientMessage.KickPlayer(guestId))
        assertTrue(guest.messages.last() is ServerMessage.Kicked)
        assertTrue(guest.closed)
        assertEquals(1, host.lobby.seats.size)
    }

    @Test
    fun `intents are validated and every client stays in sync through deltas`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Zeno")
        val (guestId, guest) = room.seat("Ada")
        room.handle(guestId, guest, ClientMessage.SetReady(true))
        room.handle(hostId, host, ClientMessage.StartGame)
        runCurrent()

        val current = host.view!!.currentTurnPlayerId
        val (activeId, active) = if (current == hostId) hostId to host else guestId to guest
        val (idleId, idle) = if (current == hostId) guestId to guest else hostId to host

        room.handle(idleId, idle, ClientMessage.SubmitIntent(GameIntent.RollDice))
        assertTrue(idle.messages.last() is ServerMessage.IntentRejected)

        room.handle(activeId, active, ClientMessage.SubmitIntent(GameIntent.RollDice))
        runCurrent()
        room.handle(activeId, active, ClientMessage.SubmitIntent(GameIntent.ToggleHold(0)))
        runCurrent()

        val view = host.view!!
        assertTrue(view.hasRolled)
        assertTrue(view.diceHeld[0])
        assertEquals(view, guest.view)
        assertTrue(host.messages.any { it is ServerMessage.StateDelta }, "updates after the first should be deltas")
    }

    @Test
    fun `a disconnected player is played by the autopilot and can resume`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Zeno")
        val (guestId, guest) = room.seat("Ada")
        room.handle(guestId, guest, ClientMessage.SetReady(true))
        room.handle(hostId, host, ClientMessage.StartGame)
        runCurrent()

        val activeId = host.view!!.currentTurnPlayerId
        val (active, other) = if (activeId == hostId) host to guest else guest to host
        room.disconnect(activeId, active)
        runCurrent()
        assertFalse(other.lobby.seats.first { it.playerId == activeId }.isConnected)

        advanceTimeBy(4.seconds)
        runCurrent()
        assertFalse(other.view!!.hasRolled, "grace period not over yet")

        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(other.view!!.hasRolled || other.view!!.currentTurnPlayerId != activeId, "autopilot should have acted")

        val returning = FakeConnection()
        val token = active.joined.sessionToken
        assertTrue(room.resume(activeId, token, returning))
        runCurrent()
        assertNotNull(returning.view, "a resumed player gets a fresh snapshot")
        assertEquals(other.view!!.revision, returning.view!!.revision)
        assertTrue(returning.lobby.seats.first { it.playerId == activeId }.isConnected)

        assertFalse(room.resume(activeId, "wrong-token", FakeConnection()))
    }

    @Test
    fun `a game against bots runs to the end and the host can start a rematch`() = runTest {
        val room = room()
        val (hostId, host) = room.seat("Zeno")
        room.handle(hostId, host, ClientMessage.AddBot)
        room.handle(hostId, host, ClientMessage.AddBot)
        room.handle(hostId, host, ClientMessage.StartGame)
        runCurrent()
        // Walk away: the host's own seat is autopiloted after the grace period.
        room.disconnect(hostId, host)
        val watcher = FakeConnection()
        assertTrue(room.resume(hostId, host.joined.sessionToken, watcher))
        room.handle(hostId, watcher, ClientMessage.LeaveRoom)

        // The host left, but a room with only bots closes.
        assertTrue(watcher.closed)

        val second = room()
        val (id2, c2) = second.seat("Solo")
        second.handle(id2, c2, ClientMessage.AddBot)
        second.handle(id2, c2, ClientMessage.StartGame)
        runCurrent()
        second.disconnect(id2, c2)
        val spectator = FakeConnection()
        advanceTimeBy(1_000.seconds)
        runCurrent()
        assertTrue(second.resume(id2, c2.joined.sessionToken, spectator))
        runCurrent()
        var guard = 0
        while (spectator.view?.turnPhase != TurnPhase.GAME_OVER && guard++ < 200) {
            second.disconnect(id2, spectator)
            advanceTimeBy(1_000.seconds)
            runCurrent()
            assertTrue(second.resume(id2, c2.joined.sessionToken, spectator))
            runCurrent()
        }
        assertEquals(TurnPhase.GAME_OVER, spectator.view?.turnPhase)
        assertEquals(RoomStatus.FINISHED, spectator.lobby.status)

        second.handle(id2, spectator, ClientMessage.ReturnToLobby)
        assertEquals(RoomStatus.LOBBY, spectator.lobby.status)
        assertTrue(spectator.lobby.canStart)
    }

    @Test
    fun `abandoned rooms are swept`() = runTest {
        val manager = RoomManager(
            scope = backgroundScope,
            config = config.copy(abandonedRoomTtl = 60.seconds),
            random = Random(1),
            clock = { testScheduler.currentTime },
        )
        val room = manager.createRoom()
        val (id, connection) = room.seat("Zeno")
        assertEquals(room, manager.find(room.code.lowercase()))
        room.disconnect(id, connection)
        advanceTimeBy(30.seconds)
        manager.sweepAbandoned()
        assertEquals(1, manager.roomCount)
        advanceTimeBy(31.seconds)
        manager.sweepAbandoned()
        assertEquals(0, manager.roomCount)
        assertNull(manager.find(room.code))
    }
}
