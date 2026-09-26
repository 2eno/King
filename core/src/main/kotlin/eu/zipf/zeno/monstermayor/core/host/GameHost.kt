package eu.zipf.zeno.monstermayor.core.host

import eu.zipf.zeno.monstermayor.core.engine.BotPolicy
import eu.zipf.zeno.monstermayor.core.engine.EngineResult
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single authority over one game. Intents are applied one at a time, in arrival order, and
 * every accepted intent publishes a new state. Used in-process for pass-and-play and by the server
 * for online rooms.
 */
class GameHost(
    initialState: GameState,
    private val engine: GameEngine,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<GameState> = _state.asStateFlow()

    suspend fun submit(actorId: String, intent: GameIntent): EngineResult = mutex.withLock {
        val result = engine.apply(_state.value, actorId, intent)
        if (result is EngineResult.Accepted) _state.value = result.state
        result
    }

    /**
     * Lets [BotPolicy] take the next step for the acting player, but only if the state has not moved
     * on since [expectedRevision] (so a returning human is never overridden by a stale bot move).
     */
    suspend fun autoplay(playerId: String, expectedRevision: Long): EngineResult? = mutex.withLock {
        val state = _state.value
        if (state.revision != expectedRevision || state.actingPlayerId != playerId) return@withLock null
        val intent = BotPolicy.nextIntent(state, playerId) ?: return@withLock null
        val result = engine.apply(state, playerId, intent)
        if (result is EngineResult.Accepted) _state.value = result.state
        result
    }
}
