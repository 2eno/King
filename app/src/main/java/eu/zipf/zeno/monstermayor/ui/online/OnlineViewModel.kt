package eu.zipf.zeno.monstermayor.ui.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.zipf.zeno.monstermayor.client.ConnectionState
import eu.zipf.zeno.monstermayor.client.GameSessionManager
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.LobbyState
import eu.zipf.zeno.monstermayor.core.net.PlayerProfile
import eu.zipf.zeno.monstermayor.core.net.RoomCodes
import eu.zipf.zeno.monstermayor.core.net.RoomStatus
import eu.zipf.zeno.monstermayor.data.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnlineUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val lobby: LobbyState? = null,
    val myPlayerId: String? = null,
    val joinCode: String = "",
) {
    val inRoom: Boolean get() = lobby != null
    val isHost: Boolean get() = lobby != null && lobby.hostPlayerId == myPlayerId
    val mySeat get() = lobby?.seats?.firstOrNull { it.playerId == myPlayerId }
    val isBusy: Boolean get() = connection == ConnectionState.Connecting && lobby == null
    val canJoin: Boolean get() = RoomCodes.isValid(RoomCodes.normalize(joinCode)) && !isBusy
    val takenCharacters: Set<CharacterId> get() = lobby?.seats?.map { it.characterId }?.toSet().orEmpty()
}

sealed interface OnlineIntent {
    data object HostRoom : OnlineIntent
    data class ChangeJoinCode(val code: String) : OnlineIntent
    data object JoinRoom : OnlineIntent
    data object ToggleReady : OnlineIntent
    data class PickCharacter(val characterId: CharacterId) : OnlineIntent
    data object AddBot : OnlineIntent
    data class Kick(val playerId: String) : OnlineIntent
    data object StartGame : OnlineIntent
    data object Leave : OnlineIntent
}

class OnlineViewModel(
    private val profiles: ProfileRepository,
    private val sessions: GameSessionManager,
) : ViewModel() {
    private val client = sessions.onlineClient
    private val joinCode = MutableStateFlow("")

    val state: StateFlow<OnlineUiState> = combine(client.connection, client.lobby, client.identity, joinCode) { connection, lobby, identity, code ->
        OnlineUiState(connection, lobby, identity?.playerId, code)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnlineUiState())

    val notices: SharedFlow<String> = client.notices

    /** Emits once each time the room's game starts, so the screen can open the game. */
    val gameStarted: Flow<Unit> = client.lobby
        .map { it?.status == RoomStatus.IN_GAME }
        .distinctUntilChanged()
        .filter { it }
        .map { sessions.attachOnlineGame(); Unit }

    fun onIntent(intent: OnlineIntent) {
        viewModelScope.launch {
            when (intent) {
                OnlineIntent.HostRoom -> {
                    val profile = profiles.current()
                    client.createRoom(profile.serverUrl, profile.toPlayerProfile())
                }
                is OnlineIntent.ChangeJoinCode ->
                    joinCode.value = intent.code.uppercase().filter { it in RoomCodes.ALPHABET }.take(RoomCodes.LENGTH)
                OnlineIntent.JoinRoom -> {
                    val profile = profiles.current()
                    client.joinRoom(profile.serverUrl, joinCode.value, profile.toPlayerProfile())
                }
                OnlineIntent.ToggleReady -> client.send(ClientMessage.SetReady(!(state.value.mySeat?.isReady ?: false)))
                is OnlineIntent.PickCharacter -> client.send(ClientMessage.SelectCharacter(intent.characterId))
                OnlineIntent.AddBot -> client.send(ClientMessage.AddBot)
                is OnlineIntent.Kick -> client.send(ClientMessage.KickPlayer(intent.playerId))
                OnlineIntent.StartGame -> client.send(ClientMessage.StartGame)
                OnlineIntent.Leave -> sessions.leaveOnlineRoom()
            }
        }
    }

    private fun eu.zipf.zeno.monstermayor.data.Profile.toPlayerProfile() =
        PlayerProfile(name.ifBlank { "Monster" }, favoriteCharacter)
}
