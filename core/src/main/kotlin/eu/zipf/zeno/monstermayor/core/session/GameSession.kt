package eu.zipf.zeno.monstermayor.core.session

import eu.zipf.zeno.monstermayor.core.engine.EngineResult
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.host.GameHost
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the game screen talks to, whether the authority is on this device or on a server. */
interface GameSession {
    /** Latest public game state; null until the first snapshot arrives. */
    val state: StateFlow<GameStatePayload?>

    /** Players this device may act for (all local humans in pass-and-play, one player online). */
    val controlledPlayerIds: StateFlow<Set<String>>

    /** Human-readable rejections and notices. */
    val notices: SharedFlow<String>

    suspend fun submit(actorId: String, intent: GameIntent)
}

/**
 * Offline pass-and-play: the [GameHost] runs in-process, every human seat is controlled by this
 * device and bot seats are played automatically.
 */
class LocalGameSession(
    val host: GameHost,
    scope: CoroutineScope,
    private val botStepDelayMillis: Long = 650,
) : GameSession {

    override val state: StateFlow<GameStatePayload?> = host.state
        .map { it.toPayload() }
        .stateIn(scope, SharingStarted.Eagerly, host.state.value.toPayload())

    private val _controlled = MutableStateFlow(host.state.value.players.filterNot { it.isBot }.map { it.id }.toSet())
    override val controlledPlayerIds: StateFlow<Set<String>> = _controlled.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** Exposed so the app can persist every accepted state (resume after process death). */
    val authoritativeState: StateFlow<GameState> get() = host.state

    private val botDriver: Job = scope.launch {
        host.state.collectLatest { current ->
            val actor = current.actingPlayerId ?: return@collectLatest
            if (current.player(actor)?.isBot == true) {
                delay(botStepDelayMillis)
                host.autoplay(actor, current.revision)
            }
        }
    }

    override suspend fun submit(actorId: String, intent: GameIntent) {
        if (actorId !in _controlled.value) {
            _notices.emit("That monster is not controlled on this device")
            return
        }
        val result = host.submit(actorId, intent)
        if (result is EngineResult.Rejected) _notices.emit(result.reason)
    }

    fun close() = botDriver.cancel()
}
