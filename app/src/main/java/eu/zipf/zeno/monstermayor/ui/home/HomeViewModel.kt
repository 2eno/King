package eu.zipf.zeno.monstermayor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.zipf.zeno.monstermayor.client.GameSessionManager
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.data.MatchHistoryRepository
import eu.zipf.zeno.monstermayor.data.PlayerStats
import eu.zipf.zeno.monstermayor.data.ProfileRepository
import eu.zipf.zeno.monstermayor.data.SavedGameRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val name: String = "",
    val favoriteCharacter: CharacterId = CharacterId.GIGAZAUR,
    val serverUrl: String = "",
    val hasSavedGame: Boolean = false,
    val stats: PlayerStats = PlayerStats(0, 0, emptyList()),
)

sealed interface HomeIntent {
    data class ChangeName(val name: String) : HomeIntent
    data class ChangeFavorite(val characterId: CharacterId) : HomeIntent
    data class ChangeServerUrl(val url: String) : HomeIntent
    data object ResumeLocalGame : HomeIntent
}

sealed interface HomeEffect {
    data object OpenGame : HomeEffect
    data class Message(val text: String) : HomeEffect
}

class HomeViewModel(
    private val profiles: ProfileRepository,
    savedGames: SavedGameRepository,
    history: MatchHistoryRepository,
    private val sessions: GameSessionManager,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(profiles.profile, savedGames.hasSavedGame, history.stats) { profile, saved, stats ->
        HomeUiState(profile.name, profile.favoriteCharacter, profile.serverUrl, saved, stats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: HomeIntent) {
        viewModelScope.launch {
            when (intent) {
                is HomeIntent.ChangeName -> profiles.update { it.copy(name = intent.name.take(20)) }
                is HomeIntent.ChangeFavorite -> profiles.update { it.copy(favoriteCharacter = intent.characterId) }
                is HomeIntent.ChangeServerUrl -> profiles.update { it.copy(serverUrl = intent.url.trim()) }
                HomeIntent.ResumeLocalGame ->
                    if (sessions.resumeLocalGame() != null) _effects.send(HomeEffect.OpenGame)
                    else _effects.send(HomeEffect.Message("No saved game to resume"))
            }
        }
    }
}
