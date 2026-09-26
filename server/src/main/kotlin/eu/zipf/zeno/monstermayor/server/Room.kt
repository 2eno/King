package eu.zipf.zeno.monstermayor.server

import eu.zipf.zeno.monstermayor.core.engine.EngineResult
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.host.GameHost
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameRules
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import eu.zipf.zeno.monstermayor.core.model.Seat
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.ErrorCode
import eu.zipf.zeno.monstermayor.core.net.LobbySeat
import eu.zipf.zeno.monstermayor.core.net.LobbyState
import eu.zipf.zeno.monstermayor.core.net.PlayerProfile
import eu.zipf.zeno.monstermayor.core.net.RoomStatus
import eu.zipf.zeno.monstermayor.core.net.ServerMessage
import eu.zipf.zeno.monstermayor.core.net.StateSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** One client connection as seen by a [Room]. Sending never suspends; implementations queue. */
interface ClientConnection {
    fun send(message: ServerMessage)

    /** Last payload this connection received, the base for the next delta. Null forces a snapshot. */
    var lastSyncedState: GameStatePayload?

    fun close()
}

data class RoomConfig(
    val rules: GameRules = GameRules(),
    /** Pause between automatic moves of CPU monsters, so humans can follow along. */
    val botStepDelay: Duration = 700.milliseconds,
    /** How long a disconnected player's turn waits before the autopilot takes over. */
    val disconnectGrace: Duration = 20.seconds,
    /** Rooms without any connected human are closed after this long. */
    val abandonedRoomTtl: Duration = 10.minutes,
)

/**
 * A lobby plus, once started, one host-authoritative game. Every mutation happens under [mutex];
 * the game itself is owned by a [GameHost], whose state changes are broadcast to all seats.
 */
class Room(
    val code: String,
    private val scope: CoroutineScope,
    private val config: RoomConfig,
    private val engineFactory: () -> GameEngine,
    private val clock: () -> Long,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
    private val onClosed: (Room) -> Unit = {},
) {
    private class SeatInfo(
        val playerId: String,
        var name: String,
        var characterId: CharacterId,
        val token: String,
        val isBot: Boolean,
        var ready: Boolean,
        var connection: ClientConnection?,
        /** Set when a player left mid-game: the autopilot plays for them from then on. */
        var forfeited: Boolean = false,
        /** Set once a disconnected player's grace period ran out, until they come back. */
        var autopilot: Boolean = false,
    ) {
        val isHuman get() = !isBot
        val automated get() = isBot || forfeited || autopilot
    }

    private val mutex = Mutex()
    private val seats = mutableListOf<SeatInfo>()
    private var hostPlayerId: String? = null
    private var status = RoomStatus.LOBBY
    private var gameHost: GameHost? = null
    private var gameWatcher: Job? = null
    private var automation: Job? = null
    private var gameCounter = 0
    private var closed = false

    /** Clock time since which no human has been connected, or null while someone is. */
    @Volatile
    var abandonedSince: Long? = clock()
        private set

    // region connection lifecycle

    /** Seats a new player. Returns their id, or null after sending the connection an error. */
    suspend fun join(profile: PlayerProfile, connection: ClientConnection): String? = mutex.withLock {
        when {
            closed -> return@withLock connection.fail(ErrorCode.ROOM_NOT_FOUND, "Room $code is closed")
            status != RoomStatus.LOBBY -> return@withLock connection.fail(ErrorCode.GAME_IN_PROGRESS, "The game in $code has already started")
            seats.size >= config.rules.maxPlayers -> return@withLock connection.fail(ErrorCode.ROOM_FULL, "Room $code is full")
        }
        val name = profile.name.trim().take(MAX_NAME_LENGTH).ifBlank { "Monster ${seats.size + 1}" }
        val seat = SeatInfo(
            playerId = UUID.randomUUID().toString(),
            name = name,
            characterId = freeCharacter(preferred = profile.characterId),
            token = newToken(),
            isBot = false,
            ready = false,
            connection = connection,
        )
        seats += seat
        if (hostPlayerId == null) hostPlayerId = seat.playerId
        connection.send(ServerMessage.Joined(code, seat.playerId, seat.token))
        onConnectionsChanged()
        broadcastLobby()
        seat.playerId
    }

    /** Re-attaches a returning player to their seat. */
    suspend fun resume(playerId: String, token: String, connection: ClientConnection): Boolean = mutex.withLock {
        val seat = seats.firstOrNull { it.playerId == playerId && it.token == token && it.isHuman && !it.forfeited }
        if (closed || seat == null) {
            connection.fail(ErrorCode.SESSION_EXPIRED, "That seat is no longer available")
            return@withLock false
        }
        seat.connection?.takeIf { it !== connection }?.close()
        seat.connection = connection
        seat.autopilot = false
        connection.lastSyncedState = null
        connection.send(ServerMessage.Joined(code, seat.playerId, seat.token))
        onConnectionsChanged()
        broadcastLobby()
        gameHost?.state?.value?.let { state ->
            syncState(seat, state.toPayload())
            scheduleAutomation(state)
        }
        true
    }

    suspend fun disconnect(playerId: String, connection: ClientConnection) = mutex.withLock {
        val seat = seats.firstOrNull { it.playerId == playerId }
        if (seat == null || seat.connection !== connection) return@withLock
        seat.connection = null
        onConnectionsChanged()
        broadcastLobby()
        gameHost?.state?.value?.let(::scheduleAutomation)
    }

    // endregion

    suspend fun handle(playerId: String, connection: ClientConnection, message: ClientMessage) = mutex.withLock {
        val seat = seats.firstOrNull { it.playerId == playerId }
        if (seat == null || seat.connection !== connection) {
            connection.fail(ErrorCode.INVALID_REQUEST, "You are not seated in room $code")
            return@withLock
        }
        val isHost = playerId == hostPlayerId
        when (message) {
            is ClientMessage.SetReady -> inLobby(connection) {
                seat.ready = message.ready
                broadcastLobby()
            }
            is ClientMessage.SelectCharacter -> inLobby(connection) {
                if (seats.any { it !== seat && it.characterId == message.characterId }) {
                    connection.fail(ErrorCode.INVALID_REQUEST, "${message.characterId.displayName} is already taken")
                } else {
                    seat.characterId = message.characterId
                    broadcastLobby()
                }
            }
            ClientMessage.AddBot -> hostOnly(isHost, connection) {
                inLobby(connection) {
                    if (seats.size >= config.rules.maxPlayers) {
                        connection.fail(ErrorCode.ROOM_FULL, "The room is full")
                    } else {
                        val character = freeCharacter(preferred = null)
                        seats += SeatInfo(
                            playerId = "bot-" + UUID.randomUUID().toString().take(8),
                            name = "CPU ${character.displayName}",
                            characterId = character,
                            token = newToken(),
                            isBot = true,
                            ready = true,
                            connection = null,
                        )
                        broadcastLobby()
                    }
                }
            }
            is ClientMessage.KickPlayer -> hostOnly(isHost, connection) {
                inLobby(connection) {
                    val target = seats.firstOrNull { it.playerId == message.playerId }
                    if (target == null || target === seat) {
                        connection.fail(ErrorCode.INVALID_REQUEST, "Cannot kick that player")
                    } else {
                        seats.remove(target)
                        target.connection?.let {
                            it.send(ServerMessage.Kicked)
                            it.close()
                        }
                        onConnectionsChanged()
                        broadcastLobby()
                    }
                }
            }
            ClientMessage.StartGame -> hostOnly(isHost, connection) { startGame(connection) }
            ClientMessage.ReturnToLobby -> hostOnly(isHost, connection) {
                if (status != RoomStatus.FINISHED) {
                    connection.fail(ErrorCode.INVALID_REQUEST, "The game is still running")
                } else {
                    stopGame()
                    seats.removeAll { it.forfeited }
                    seats.forEach { it.ready = it.isBot }
                    status = RoomStatus.LOBBY
                    broadcastLobby()
                }
            }
            is ClientMessage.SubmitIntent -> {
                val host = gameHost
                if (host == null || status != RoomStatus.IN_GAME) {
                    connection.send(ServerMessage.IntentRejected("No game is running"))
                } else {
                    val result = host.submit(playerId, message.intent)
                    if (result is EngineResult.Rejected) connection.send(ServerMessage.IntentRejected(result.reason))
                }
            }
            ClientMessage.RequestSnapshot -> {
                connection.lastSyncedState = null
                gameHost?.state?.value?.let { syncState(seat, it.toPayload()) }
            }
            ClientMessage.LeaveRoom -> leave(seat)
            is ClientMessage.CreateRoom, is ClientMessage.JoinRoom, is ClientMessage.Resume ->
                connection.fail(ErrorCode.INVALID_REQUEST, "You are already in room $code")
        }
    }

    /** Closes the room and disconnects everyone. */
    suspend fun close() = mutex.withLock { closeLocked() }

    // region internals (all called with the mutex held)

    private fun leave(seat: SeatInfo) {
        seat.connection?.close()
        seat.connection = null
        if (status == RoomStatus.LOBBY) {
            seats.remove(seat)
        } else {
            seat.forfeited = true
            gameHost?.state?.value?.let(::scheduleAutomation)
        }
        if (hostPlayerId == seat.playerId) {
            hostPlayerId = (seats.firstOrNull { it.isHuman && !it.forfeited && it.connection != null }
                ?: seats.firstOrNull { it.isHuman && !it.forfeited })?.playerId
        }
        if (seats.none { it.isHuman && !it.forfeited }) {
            closeLocked()
            return
        }
        onConnectionsChanged()
        broadcastLobby()
    }

    private fun startGame(connection: ClientConnection) {
        val lobby = lobbyState()
        if (!lobby.canStart) {
            val reason = when {
                status != RoomStatus.LOBBY -> "The game has already started"
                seats.size < config.rules.minPlayers -> "At least ${config.rules.minPlayers} monsters are needed"
                else -> "Everyone must be ready"
            }
            connection.fail(ErrorCode.CANNOT_START, reason)
            return
        }
        val engine = engineFactory()
        val state = engine.newGame(
            gameId = "$code-${++gameCounter}",
            seats = seats.map { Seat(it.playerId, it.name, it.characterId, isBot = it.isBot) },
            rules = config.rules,
        )
        val host = GameHost(state, engine)
        gameHost = host
        status = RoomStatus.IN_GAME
        seats.forEach { it.connection?.lastSyncedState = null }
        broadcastLobby()
        gameWatcher = scope.launch {
            host.state.collect { latest -> mutex.withLock { if (gameHost === host) onGameState(latest) } }
        }
    }

    private fun onGameState(state: GameState) {
        val payload = state.toPayload()
        seats.forEach { syncState(it, payload) }
        if (state.turnPhase == TurnPhase.GAME_OVER && status == RoomStatus.IN_GAME) {
            status = RoomStatus.FINISHED
            broadcastLobby()
        }
        scheduleAutomation(state)
    }

    private fun syncState(seat: SeatInfo, payload: GameStatePayload) {
        val connection = seat.connection ?: return
        val last = connection.lastSyncedState
        when {
            last == null -> connection.send(ServerMessage.StateSnapshot(payload))
            last.revision == payload.revision -> return
            else -> connection.send(StateSync.delta(last, payload))
        }
        connection.lastSyncedState = payload
    }

    /** Plays for CPU seats, forfeited seats, and disconnected players whose grace period ran out. */
    private fun scheduleAutomation(state: GameState) {
        automation?.cancel()
        automation = null
        val host = gameHost ?: return
        val actorId = state.actingPlayerId ?: return
        val seat = seats.firstOrNull { it.playerId == actorId } ?: return
        val wait = when {
            seat.automated -> config.botStepDelay
            seat.connection == null -> config.disconnectGrace
            else -> return
        }
        val revision = state.revision
        automation = scope.launch {
            delay(wait)
            mutex.withLock { if (seat.connection == null && seat.isHuman) seat.autopilot = true }
            host.autoplay(actorId, revision)
        }
    }

    private fun stopGame() {
        gameWatcher?.cancel()
        automation?.cancel()
        gameWatcher = null
        automation = null
        gameHost = null
        seats.forEach { it.connection?.lastSyncedState = null }
    }

    private fun closeLocked() {
        if (closed) return
        closed = true
        stopGame()
        seats.forEach { it.connection?.close() }
        seats.clear()
        onClosed(this)
    }

    private fun onConnectionsChanged() {
        val anyoneHere = seats.any { it.isHuman && it.connection != null }
        abandonedSince = if (anyoneHere) null else abandonedSince ?: clock()
    }

    private fun freeCharacter(preferred: CharacterId?): CharacterId {
        val taken = seats.map { it.characterId }.toSet()
        return preferred?.takeIf { it !in taken } ?: CharacterId.entries.first { it !in taken }
    }

    private fun lobbyState() = LobbyState(
        roomCode = code,
        hostPlayerId = hostPlayerId ?: "",
        seats = seats.map {
            LobbySeat(
                playerId = it.playerId,
                name = it.name,
                characterId = it.characterId,
                isReady = it.ready,
                isHost = it.playerId == hostPlayerId,
                isBot = it.isBot || it.forfeited,
                isConnected = it.isBot || it.connection != null,
            )
        },
        status = status,
        minPlayers = config.rules.minPlayers,
        maxPlayers = config.rules.maxPlayers,
    )

    private fun broadcastLobby() {
        val message = ServerMessage.LobbyUpdate(lobbyState())
        seats.forEach { it.connection?.send(message) }
    }

    private inline fun inLobby(connection: ClientConnection, block: () -> Unit) {
        if (status == RoomStatus.LOBBY) block() else connection.fail(ErrorCode.GAME_IN_PROGRESS, "Only possible in the lobby")
    }

    private inline fun hostOnly(isHost: Boolean, connection: ClientConnection, block: () -> Unit) {
        if (isHost) block() else connection.fail(ErrorCode.NOT_HOST, "Only the host can do that")
    }

    private fun ClientConnection.fail(code: ErrorCode, message: String): Nothing? {
        send(ServerMessage.Error(code, message))
        return null
    }

    // endregion

    /** Snapshot for diagnostics and tests. */
    suspend fun lobby(): LobbyState = mutex.withLock { lobbyState() }

    companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
