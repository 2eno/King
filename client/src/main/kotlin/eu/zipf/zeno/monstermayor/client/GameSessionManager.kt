package eu.zipf.zeno.monstermayor.client

import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.host.GameHost
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import eu.zipf.zeno.monstermayor.core.model.PlayerState
import eu.zipf.zeno.monstermayor.core.model.Seat
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.session.GameSession
import eu.zipf.zeno.monstermayor.core.session.LocalGameSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Persists the authoritative state of an offline game so it can be resumed later. */
interface SavedGameStore {
    suspend fun load(): GameState?
    suspend fun save(state: GameState)
    suspend fun clear()
}

enum class GameMode { LOCAL, ONLINE }

data class MatchResult(
    val gameId: String,
    val mode: GameMode,
    val winnerName: String?,
    val playerNames: List<String>,
    val localPlayerWon: Boolean,
    val turns: Int,
    val finishedAt: Long,
)

fun interface MatchRecorder {
    suspend fun record(result: MatchResult)
}

sealed interface ActiveGame {
    val session: GameSession
    val mode: GameMode

    class Local internal constructor(override val session: LocalGameSession, internal val job: Job) : ActiveGame {
        override val mode = GameMode.LOCAL
    }

    class Online internal constructor(override val session: RemoteGameSession, internal val job: Job) : ActiveGame {
        override val mode = GameMode.ONLINE
    }
}

/**
 * Owns the one game this device is currently showing: an in-process pass-and-play game or an
 * online room. Outlives screens, saves offline games after every move and records results.
 */
class GameSessionManager(
    private val scope: CoroutineScope,
    val onlineClient: OnlineGameClient,
    private val savedGames: SavedGameStore,
    private val recorder: MatchRecorder,
    private val engineFactory: () -> GameEngine = { GameEngine() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val botStepDelayMillis: Long = 650,
) {
    private val _active = MutableStateFlow<ActiveGame?>(null)
    val active: StateFlow<ActiveGame?> = _active.asStateFlow()

    fun startLocalGame(seats: List<Seat>): ActiveGame.Local {
        val engine = engineFactory()
        val state = engine.newGame(gameId = "local-${clock()}", seats = seats)
        return attachLocal(GameHost(state, engine))
    }

    /** Restores the saved offline game, or returns null when there is none. */
    suspend fun resumeLocalGame(): ActiveGame.Local? {
        val state = savedGames.load() ?: return null
        if (state.turnPhase == TurnPhase.GAME_OVER) {
            savedGames.clear()
            return null
        }
        return attachLocal(GameHost(state, engineFactory()))
    }

    /** Binds the game screen to the online room the [onlineClient] is in. */
    fun attachOnlineGame(): ActiveGame.Online {
        (_active.value as? ActiveGame.Online)?.let { return it }
        endActiveGame()
        val job = SupervisorJob(scope.coroutineContext[Job])
        val sessionScope = CoroutineScope(scope.coroutineContext + job)
        val session = RemoteGameSession(onlineClient, sessionScope)
        sessionScope.launch {
            onlineClient.game
                .filterNotNull()
                .filter { it.turnPhase == TurnPhase.GAME_OVER }
                .distinctUntilChangedBy { it.gameId }
                .collect { payload ->
                    val me = onlineClient.identity.value?.playerId
                    recorder.record(payload.toResult(GameMode.ONLINE, setOfNotNull(me)))
                }
        }
        return ActiveGame.Online(session, job).also { _active.value = it }
    }

    fun endActiveGame() {
        _active.value?.let {
            when (it) {
                is ActiveGame.Local -> it.job.cancel()
                is ActiveGame.Online -> it.job.cancel()
            }
        }
        _active.value = null
    }

    /** Leaves the online room. Runs in the manager's scope so it survives the screen that asked. */
    fun leaveOnlineRoom(): Job {
        if (_active.value is ActiveGame.Online) endActiveGame()
        return scope.launch { onlineClient.leave() }
    }

    /** Host only: take the room back to the lobby after a finished game. */
    fun requestRematch(): Job = scope.launch { onlineClient.send(ClientMessage.ReturnToLobby) }

    private fun attachLocal(host: GameHost): ActiveGame.Local {
        endActiveGame()
        val job = SupervisorJob(scope.coroutineContext[Job])
        val sessionScope = CoroutineScope(scope.coroutineContext + job)
        val session = LocalGameSession(host, sessionScope, botStepDelayMillis)
        sessionScope.launch {
            var recorded = false
            host.state.collect { state ->
                if (state.turnPhase != TurnPhase.GAME_OVER) {
                    savedGames.save(state)
                } else if (!recorded) {
                    recorded = true
                    savedGames.clear()
                    recorder.record(state.toPayload().toResult(GameMode.LOCAL, session.controlledPlayerIds.value))
                }
            }
        }
        return ActiveGame.Local(session, job).also { _active.value = it }
    }

    private fun GameStatePayload.toResult(mode: GameMode, localIds: Set<String>): MatchResult {
        val winner: PlayerState? = player(winnerId)
        return MatchResult(
            gameId = gameId,
            mode = mode,
            winnerName = winner?.name,
            playerNames = players.map { it.name },
            localPlayerWon = winner != null && winner.id in localIds,
            turns = turnNumber,
            finishedAt = clock(),
        )
    }
}

/** HTTP client with WebSocket support for the given engine (OkHttp on Android, CIO on the JVM). */
fun createGameHttpClient(engine: HttpClientEngineFactory<*>): HttpClient = HttpClient(engine) {
    install(WebSockets) {
        pingIntervalMillis = 15_000
    }
}
