package eu.zipf.zeno.monstermayor.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.zipf.zeno.monstermayor.client.GameSessionManager
import eu.zipf.zeno.monstermayor.client.LocalSetupIntent
import eu.zipf.zeno.monstermayor.client.LocalSetupReducer
import eu.zipf.zeno.monstermayor.client.LocalSetupState
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.data.ProfileRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalSetupViewModel(
    profiles: ProfileRepository,
    private val sessions: GameSessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalSetupState.initial("", CharacterId.GIGAZAUR))
    val state: StateFlow<LocalSetupState> = _state.asStateFlow()

    private val _gameStarted = Channel<Unit>(Channel.CONFLATED)
    val gameStarted: Flow<Unit> = _gameStarted.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = profiles.current()
            _state.value = LocalSetupState.initial(profile.name, profile.favoriteCharacter)
        }
    }

    fun onIntent(intent: LocalSetupIntent) = _state.update { LocalSetupReducer.reduce(it, intent) }

    fun start() {
        val setup = _state.value
        if (!setup.canStart) return
        sessions.startLocalGame(setup.toSeats())
        _gameStarted.trySend(Unit)
    }
}
