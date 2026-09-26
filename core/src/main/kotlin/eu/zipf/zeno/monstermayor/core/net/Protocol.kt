package eu.zipf.zeno.monstermayor.core.net

import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Shared JSON configuration for the wire protocol and local persistence. */
val GameJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

@Serializable
data class PlayerProfile(val name: String, val characterId: CharacterId)

/** Messages sent from a client to the room server over the WebSocket. */
@Serializable
sealed interface ClientMessage {
    @Serializable
    @SerialName("create_room")
    data class CreateRoom(val profile: PlayerProfile) : ClientMessage

    @Serializable
    @SerialName("join_room")
    data class JoinRoom(val roomCode: String, val profile: PlayerProfile) : ClientMessage

    /** Reconnect to a seat after losing the connection. */
    @Serializable
    @SerialName("resume")
    data class Resume(val roomCode: String, val playerId: String, val sessionToken: String) : ClientMessage

    @Serializable
    @SerialName("set_ready")
    data class SetReady(val ready: Boolean) : ClientMessage

    @Serializable
    @SerialName("select_character")
    data class SelectCharacter(val characterId: CharacterId) : ClientMessage

    /** Host only: add a CPU monster to the lobby. */
    @Serializable
    @SerialName("add_bot")
    data object AddBot : ClientMessage

    /** Host only: remove a player or bot from the lobby. */
    @Serializable
    @SerialName("kick")
    data class KickPlayer(val playerId: String) : ClientMessage

    /** Host only. */
    @Serializable
    @SerialName("start_game")
    data object StartGame : ClientMessage

    /** Host only: after a game ends, take everyone back to the lobby for a rematch. */
    @Serializable
    @SerialName("return_to_lobby")
    data object ReturnToLobby : ClientMessage

    @Serializable
    @SerialName("intent")
    data class SubmitIntent(val intent: GameIntent) : ClientMessage

    /** Sent when a delta cannot be applied (revision gap); the server answers with a snapshot. */
    @Serializable
    @SerialName("request_snapshot")
    data object RequestSnapshot : ClientMessage

    @Serializable
    @SerialName("leave")
    data object LeaveRoom : ClientMessage
}

/** Messages sent from the room server to a client. */
@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("joined")
    data class Joined(val roomCode: String, val playerId: String, val sessionToken: String) : ServerMessage

    @Serializable
    @SerialName("lobby")
    data class LobbyUpdate(val lobby: LobbyState) : ServerMessage

    @Serializable
    @SerialName("snapshot")
    data class StateSnapshot(val state: GameStatePayload) : ServerMessage

    /** Top-level fields of [GameStatePayload] that changed between two revisions. */
    @Serializable
    @SerialName("delta")
    data class StateDelta(val baseRevision: Long, val revision: Long, val changes: JsonObject) : ServerMessage

    @Serializable
    @SerialName("intent_rejected")
    data class IntentRejected(val reason: String) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val code: ErrorCode, val message: String) : ServerMessage

    @Serializable
    @SerialName("kicked")
    data object Kicked : ServerMessage
}

@Serializable
enum class ErrorCode { ROOM_NOT_FOUND, ROOM_FULL, GAME_IN_PROGRESS, NOT_HOST, INVALID_REQUEST, CANNOT_START, SESSION_EXPIRED }

@Serializable
enum class RoomStatus { LOBBY, IN_GAME, FINISHED }

@Serializable
data class LobbySeat(
    val playerId: String,
    val name: String,
    val characterId: CharacterId,
    val isReady: Boolean,
    val isHost: Boolean,
    val isBot: Boolean,
    val isConnected: Boolean,
)

@Serializable
data class LobbyState(
    val roomCode: String,
    val hostPlayerId: String,
    val seats: List<LobbySeat>,
    val status: RoomStatus,
    val minPlayers: Int,
    val maxPlayers: Int,
) {
    /** Everyone except the host must be ready; bots always are. */
    val canStart: Boolean
        get() = status == RoomStatus.LOBBY &&
            seats.size in minPlayers..maxPlayers &&
            seats.all { it.isHost || it.isReady }
}

object RoomCodes {
    /** Letters that cannot be confused with digits or each other (no I, O). */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val LENGTH = 4

    fun normalize(raw: String): String = raw.trim().uppercase()

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
