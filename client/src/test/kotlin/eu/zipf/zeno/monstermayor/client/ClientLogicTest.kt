package eu.zipf.zeno.monstermayor.client

import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemorySavedGames : SavedGameStore {
    var state: GameState? = null
    var saves = 0
    override suspend fun load() = state
    override suspend fun save(state: GameState) {
        this.state = state
        saves++
    }
    override suspend fun clear() {
        state = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClientLogicTest {

    @Test
    fun `setup starts with the player against one CPU and keeps monsters unique`() {
        var state = LocalSetupState.initial("Zeno", CharacterId.CYBER_KITTY)
        assertEquals(listOf(false, true), state.seats.map { it.isBot })
        assertTrue(state.canStart)

        repeat(6) { state = LocalSetupReducer.reduce(state, LocalSetupIntent.AddSeat) }
        assertEquals(6, state.seats.size)
        assertFalse(state.canAddSeat)
        assertEquals(6, state.seats.map { it.characterId }.toSet().size)

        state = LocalSetupReducer.reduce(state, LocalSetupIntent.RemoveSeat(5))
        val before = state.seats[1].characterId
        state = LocalSetupReducer.reduce(state, LocalSetupIntent.NextCharacter(1))
        assertTrue(state.seats[1].characterId != before)
        assertEquals(state.seats.size, state.seats.map { it.characterId }.toSet().size)
        assertEquals("CPU ${state.seats[1].characterId.displayName}", state.seats[1].name)

        state = LocalSetupReducer.reduce(state, LocalSetupIntent.ToggleBot(1))
        assertFalse(state.seats[1].isBot)
        assertEquals("Player 2", state.seats[1].name)

        state = LocalSetupReducer.reduce(state, LocalSetupIntent.Rename(1, ""))
        assertEquals("Every monster needs a name", state.problem)

        val allBots = LocalSetupState(state.seats.map { it.copy(isBot = true, name = "x") })
        assertEquals("At least one monster must be played by a human", allBots.problem)
    }

    @Test
    fun `local games are saved after every move and can be resumed`() = runTest {
        val saved = InMemorySavedGames()
        val results = mutableListOf<MatchResult>()
        val manager = GameSessionManager(
            scope = backgroundScope,
            onlineClient = OnlineGameClient(HttpClient(), backgroundScope),
            savedGames = saved,
            recorder = { results += it },
            engineFactory = { GameEngine(Random(9)) },
        )
        val setup = LocalSetupState.initial("Zeno", CharacterId.GIGAZAUR)
        val game = manager.startLocalGame(setup.toSeats())
        runCurrent()
        assertNotNull(saved.state)

        // Let the CPU finish its turn if it goes first.
        advanceTimeBy(30_000)
        runCurrent()
        val human = "local-0"
        if (game.session.host.state.value.actingPlayerId == human) {
            game.session.submit(human, GameIntent.RollDice)
            runCurrent()
            assertTrue(saved.state!!.hasRolled)
        }
        val snapshot = saved.state!!

        manager.endActiveGame()
        assertNull(manager.active.value)
        val resumed = assertNotNull(manager.resumeLocalGame())
        assertEquals(snapshot, resumed.session.host.state.value)
        assertIs<ActiveGame.Local>(manager.active.value)
    }

    @Test
    fun `finished local games are recorded and cleared`() = runTest {
        val saved = InMemorySavedGames()
        val results = mutableListOf<MatchResult>()
        val manager = GameSessionManager(
            scope = backgroundScope,
            onlineClient = OnlineGameClient(HttpClient(), backgroundScope),
            savedGames = saved,
            recorder = { results += it },
            engineFactory = { GameEngine(Random(4)) },
            botStepDelayMillis = 10,
        )
        // Play the human seat with the bot policy until the game ends.
        val game = manager.startLocalGame(LocalSetupState.initial("Zeno", CharacterId.GIGAZAUR).toSeats())
        val host = game.session.host
        var guard = 0
        while (host.state.value.turnPhase != TurnPhase.GAME_OVER && guard++ < 5_000) {
            val state = host.state.value
            if (state.actingPlayerId == "local-0") host.autoplay("local-0", state.revision)
            advanceTimeBy(20)
            runCurrent()
        }
        assertEquals(TurnPhase.GAME_OVER, host.state.value.turnPhase)
        runCurrent()
        assertEquals(1, results.size)
        assertEquals(GameMode.LOCAL, results.single().mode)
        assertNull(saved.state)
        assertNull(manager.resumeLocalGame())
    }
}
