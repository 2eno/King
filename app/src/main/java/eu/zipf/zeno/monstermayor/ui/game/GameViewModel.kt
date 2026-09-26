package eu.zipf.zeno.monstermayor.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.zipf.zeno.monstermayor.client.ActiveGame
import eu.zipf.zeno.monstermayor.client.ConnectionState
import eu.zipf.zeno.monstermayor.client.GameMode
import eu.zipf.zeno.monstermayor.client.GameSessionManager
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.presentation.GamePresenter
import eu.zipf.zeno.monstermayor.core.presentation.GameUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the game screen renders: the presented game plus screen-only state. */
data class GameScreenState(
    val game: GameUiState? = null,
    val mode: GameMode = GameMode.LOCAL,
    /** Pass-and-play: name of the human who should take the device before acting. */
    val handOffTo: String? = null,
    val connection: ConnectionState = ConnectionState.Connected,
    val isOnlineHost: Boolean = false,
    /** True when no game is running any more (e.g. the app process was restarted). */
    val noActiveGame: Boolean = false,
)

sealed interface GameScreenIntent {
    data object Roll : GameScreenIntent
    data class ToggleHold(val index: Int) : GameScreenIntent
    data object Resolve : GameScreenIntent
    data class DecideTokyo(val yieldTokyo: Boolean) : GameScreenIntent
    data class Buy(val marketIndex: Int) : GameScreenIntent
    data object WipeMarket : GameScreenIntent
    data object EndTurn : GameScreenIntent
    data object AcknowledgeHandOff : GameScreenIntent
    data object Rematch : GameScreenIntent
    data object Leave : GameScreenIntent
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(private val sessions: GameSessionManager) : ViewModel() {

    private val acknowledgedActor = MutableStateFlow<String?>(null)

    val state: StateFlow<GameScreenState> = sessions.active.flatMapLatest { active ->
        if (active == null) {
            flowOf(GameScreenState(noActiveGame = true))
        } else {
            combine(
                active.session.state,
                active.session.controlledPlayerIds,
                acknowledgedActor,
                if (active is ActiveGame.Online) sessions.onlineClient.connection else flowOf(ConnectionState.Connected),
                if (active is ActiveGame.Online) sessions.onlineClient.lobby else flowOf(null),
            ) { payload, controlled, acknowledged, connection, lobby ->
                val game = payload?.let { GamePresenter.present(it, controlled) }
                val actor = game?.localActor
                val needsHandOff = active.mode == GameMode.LOCAL && controlled.size > 1 &&
                    actor != null && actor.id != acknowledged && game?.gameOver == null
                GameScreenState(
                    game = game,
                    mode = active.mode,
                    handOffTo = if (needsHandOff) actor?.name else null,
                    connection = connection,
                    isOnlineHost = lobby != null && lobby.hostPlayerId == sessions.onlineClient.identity.value?.playerId,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GameScreenState())

    val notices: Flow<String> = sessions.active.flatMapLatest { it?.session?.notices ?: emptyFlow() }

    fun onIntent(intent: GameScreenIntent) {
        val active = sessions.active.value ?: return
        val actor = state.value.game?.localActor?.id
        viewModelScope.launch {
            val gameIntent: GameIntent? = when (intent) {
                GameScreenIntent.Roll -> GameIntent.RollDice
                is GameScreenIntent.ToggleHold -> GameIntent.ToggleHold(intent.index)
                GameScreenIntent.Resolve -> GameIntent.ResolveDice
                is GameScreenIntent.DecideTokyo -> GameIntent.TokyoDecision(intent.yieldTokyo)
                is GameScreenIntent.Buy -> GameIntent.BuyCard(intent.marketIndex)
                GameScreenIntent.WipeMarket -> GameIntent.ResetMarket
                GameScreenIntent.EndTurn -> GameIntent.EndTurn
                GameScreenIntent.AcknowledgeHandOff -> {
                    acknowledgedActor.value = actor
                    null
                }
                GameScreenIntent.Rematch -> {
                    // Only the host resets the room; everyone else just returns to the lobby screen.
                    if (state.value.isOnlineHost) sessions.requestRematch()
                    null
                }
                GameScreenIntent.Leave -> {
                    if (active is ActiveGame.Online) sessions.leaveOnlineRoom() else sessions.endActiveGame()
                    null
                }
            }
            if (gameIntent != null && actor != null) active.session.submit(actor, gameIntent)
        }
    }
}
