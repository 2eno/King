package eu.zipf.zeno.monstermayor.client

import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.ErrorCode
import eu.zipf.zeno.monstermayor.core.net.GameJson
import eu.zipf.zeno.monstermayor.core.net.LobbyState
import eu.zipf.zeno.monstermayor.core.net.PlayerProfile
import eu.zipf.zeno.monstermayor.core.net.RoomCodes
import eu.zipf.zeno.monstermayor.core.net.ServerMessage
import eu.zipf.zeno.monstermayor.core.net.StateSync
import eu.zipf.zeno.monstermayor.core.session.GameSession
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Credentials that let a client reclaim its seat after a dropped connection. */
@Serializable
data class SessionIdentity(val serverUrl: String, val roomCode: String, val playerId: String, val sessionToken: String)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/**
 * Client side of the room protocol. Keeps the latest lobby and game state as [StateFlow]s,
 * applies state deltas, and transparently resumes the seat when the socket drops.
 */
class OnlineGameClient(
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val maxReconnectAttempts: Int = 8,
    private val reconnectBackoff: (attempt: Int) -> Duration = { attempt -> (attempt.coerceAtMost(5) * 2).seconds },
) {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _identity = MutableStateFlow<SessionIdentity?>(null)
    val identity: StateFlow<SessionIdentity?> = _identity.asStateFlow()

    private val _lobby = MutableStateFlow<LobbyState?>(null)
    val lobby: StateFlow<LobbyState?> = _lobby.asStateFlow()

    private val _game = MutableStateFlow<GameStatePayload?>(null)
    val game: StateFlow<GameStatePayload?> = _game.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    @Volatile
    private var socket: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    fun createRoom(serverUrl: String, profile: PlayerProfile) = start(serverUrl, ClientMessage.CreateRoom(profile))

    fun joinRoom(serverUrl: String, roomCode: String, profile: PlayerProfile) =
        start(serverUrl, ClientMessage.JoinRoom(RoomCodes.normalize(roomCode), profile))

    fun resume(identity: SessionIdentity) {
        _identity.value = identity
        start(identity.serverUrl, ClientMessage.Resume(identity.roomCode, identity.playerId, identity.sessionToken), keepIdentity = true)
    }

    /** Sends a message on the live socket. Returns false (and emits a notice) when offline. */
    suspend fun send(message: ClientMessage): Boolean {
        val current = socket
        if (current == null) {
            _notices.emit("Not connected to the server")
            return false
        }
        return runCatching { current.send(Frame.Text(GameJson.encodeToString(ClientMessage.serializer(), message))) }
            .onFailure { _notices.emit("Connection lost") }
            .isSuccess
    }

    suspend fun submit(intent: GameIntent) = send(ClientMessage.SubmitIntent(intent))

    /** Leaves the room for good and resets all state. */
    suspend fun leave() {
        send(ClientMessage.LeaveRoom)
        disconnect()
    }

    /** Drops the connection without giving up the seat (it can be resumed later). */
    suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        runCatching { socket?.close() }
        socket = null
        _connection.value = ConnectionState.Idle
        _identity.value = null
        _lobby.value = null
        _game.value = null
    }

    private fun start(serverUrl: String, hello: ClientMessage, keepIdentity: Boolean = false) {
        connectionJob?.cancel()
        if (!keepIdentity) _identity.value = null
        _lobby.value = null
        _game.value = null
        connectionJob = scope.launch { runConnection(serverUrl, hello) }
    }

    private suspend fun runConnection(serverUrl: String, hello: ClientMessage) {
        var firstMessage = hello
        var attempt = 0
        while (true) {
            _connection.value = if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting(attempt)
            var stop: String? = null
            try {
                httpClient.webSocket(urlString = serverUrl) {
                    socket = this
                    send(Frame.Text(GameJson.encodeToString(ClientMessage.serializer(), firstMessage)))
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val message = runCatching { GameJson.decodeFromString(ServerMessage.serializer(), frame.readText()) }
                            .getOrNull() ?: continue
                        stop = handle(message, serverUrl)
                        if (message is ServerMessage.Joined) attempt = 0
                        if (stop != null) break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0 && _identity.value == null) stop = "Could not reach $serverUrl"
            } finally {
                socket = null
            }

            if (stop != null) {
                _connection.value = ConnectionState.Failed(stop!!)
                return
            }
            val identity = _identity.value
            if (identity == null) {
                _connection.value = ConnectionState.Failed("Connection closed")
                return
            }
            attempt++
            if (attempt > maxReconnectAttempts) {
                _connection.value = ConnectionState.Failed("Lost connection to the server")
                return
            }
            _connection.value = ConnectionState.Reconnecting(attempt)
            delay(reconnectBackoff(attempt))
            firstMessage = ClientMessage.Resume(identity.roomCode, identity.playerId, identity.sessionToken)
        }
    }

    /** Applies one server message. Returns a reason when the connection should stop for good. */
    private suspend fun DefaultClientWebSocketSession.handle(message: ServerMessage, serverUrl: String): String? {
        when (message) {
            is ServerMessage.Joined -> {
                _identity.value = SessionIdentity(serverUrl, message.roomCode, message.playerId, message.sessionToken)
                _connection.value = ConnectionState.Connected
            }
            is ServerMessage.LobbyUpdate -> _lobby.value = message.lobby
            is ServerMessage.StateSnapshot -> _game.value = message.state
            is ServerMessage.StateDelta -> {
                val patched = _game.value?.let { StateSync.apply(it, message) }
                if (patched != null) {
                    _game.value = patched
                } else {
                    send(Frame.Text(GameJson.encodeToString(ClientMessage.serializer(), ClientMessage.RequestSnapshot)))
                }
            }
            is ServerMessage.IntentRejected -> _notices.emit(message.reason)
            is ServerMessage.Error -> {
                _notices.emit(message.message)
                val fatal = message.code in setOf(
                    ErrorCode.ROOM_NOT_FOUND,
                    ErrorCode.ROOM_FULL,
                    ErrorCode.GAME_IN_PROGRESS,
                    ErrorCode.SESSION_EXPIRED,
                )
                if (fatal && _connection.value != ConnectionState.Connected) {
                    _identity.value = null
                    return message.message
                }
            }
            ServerMessage.Kicked -> {
                _identity.value = null
                _notices.emit("You were removed from the room")
                return "Removed by the host"
            }
        }
        return null
    }
}

/** [GameSession] backed by the room server: this device controls exactly one monster. */
class RemoteGameSession(
    private val client: OnlineGameClient,
    scope: CoroutineScope,
) : GameSession {
    override val state: StateFlow<GameStatePayload?> = client.game

    override val controlledPlayerIds: StateFlow<Set<String>> = client.identity
        .map { setOfNotNull(it?.playerId) }
        .stateIn(scope, SharingStarted.Eagerly, setOfNotNull(client.identity.value?.playerId))

    override val notices: SharedFlow<String> = client.notices

    override suspend fun submit(actorId: String, intent: GameIntent) {
        client.submit(intent)
    }
}
