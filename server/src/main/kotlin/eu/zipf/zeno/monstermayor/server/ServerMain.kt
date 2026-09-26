package eu.zipf.zeno.monstermayor.server

import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.ErrorCode
import eu.zipf.zeno.monstermayor.core.net.GameJson
import eu.zipf.zeno.monstermayor.core.net.RoomCodes
import eu.zipf.zeno.monstermayor.core.net.ServerMessage
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("MonsterMayorServer")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        val manager = RoomManager(CoroutineScope(SupervisorJob() + coroutineContext))
        monsterMayorModule(manager)
    }.start(wait = true)
}

fun Application.monsterMayorModule(manager: RoomManager, sweepAbandonedRooms: Boolean = true) {
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 256L * 1024
    }
    if (sweepAbandonedRooms) {
        launch {
            while (isActive) {
                delay(1.minutes)
                manager.sweepAbandoned()
            }
        }
    }
    routing {
        get("/health") { call.respondText("ok rooms=${manager.roomCount}") }
        webSocket("/ws") { serveClient(manager) }
    }
}

/** One WebSocket = one player. The first message must create, join or resume a room. */
private suspend fun DefaultWebSocketServerSession.serveClient(manager: RoomManager) {
    val connection = WebSocketConnection(this)
    var room: Room? = null
    var playerId: String? = null
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = runCatching { GameJson.decodeFromString(ClientMessage.serializer(), frame.readText()) }
                .getOrElse {
                    connection.send(ServerMessage.Error(ErrorCode.INVALID_REQUEST, "Malformed message"))
                    continue
                }
            val currentRoom = room
            val currentPlayer = playerId
            if (currentRoom != null && currentPlayer != null) {
                currentRoom.handle(currentPlayer, connection, message)
                if (message is ClientMessage.LeaveRoom) break
                continue
            }
            when (message) {
                is ClientMessage.CreateRoom -> {
                    val created = manager.createRoom()
                    created.join(message.profile, connection)?.let { room = created; playerId = it }
                }
                is ClientMessage.JoinRoom -> {
                    val target = manager.find(message.roomCode)
                    if (target == null || !RoomCodes.isValid(RoomCodes.normalize(message.roomCode))) {
                        connection.send(ServerMessage.Error(ErrorCode.ROOM_NOT_FOUND, "No room with code ${message.roomCode}"))
                    } else {
                        target.join(message.profile, connection)?.let { room = target; playerId = it }
                    }
                }
                is ClientMessage.Resume -> {
                    val target = manager.find(message.roomCode)
                    if (target == null) {
                        connection.send(ServerMessage.Error(ErrorCode.SESSION_EXPIRED, "Room ${message.roomCode} no longer exists"))
                    } else if (target.resume(message.playerId, message.sessionToken, connection)) {
                        room = target
                        playerId = message.playerId
                    }
                }
                else -> connection.send(ServerMessage.Error(ErrorCode.INVALID_REQUEST, "Create or join a room first"))
            }
        }
    } catch (e: Exception) {
        log.debug("Connection error", e)
    } finally {
        val currentRoom = room
        val currentPlayer = playerId
        if (currentRoom != null && currentPlayer != null) currentRoom.disconnect(currentPlayer, connection)
        connection.close()
    }
}

/** Queues outgoing messages so a slow socket never blocks a room. */
private class WebSocketConnection(private val session: DefaultWebSocketServerSession) : ClientConnection {
    private val outbox = Channel<ServerMessage>(capacity = OUTBOX_CAPACITY)

    override var lastSyncedState: GameStatePayload? = null

    private val writer = session.launch {
        try {
            for (message in outbox) {
                session.send(Frame.Text(GameJson.encodeToString(ServerMessage.serializer(), message)))
            }
            session.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        } catch (_: Exception) {
            // Socket already gone.
        }
    }

    override fun send(message: ServerMessage) {
        if (outbox.trySend(message).isFailure && !outbox.isClosedForSend) {
            log.warn("Dropping slow client")
            close()
        }
    }

    override fun close() {
        outbox.close()
    }

    private companion object {
        const val OUTBOX_CAPACITY = 512
    }
}
