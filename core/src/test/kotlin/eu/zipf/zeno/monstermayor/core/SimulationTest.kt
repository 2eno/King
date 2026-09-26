package eu.zipf.zeno.monstermayor.core

import eu.zipf.zeno.monstermayor.core.engine.BotPolicy
import eu.zipf.zeno.monstermayor.core.engine.EngineResult
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Plays many complete bot-vs-bot games and checks the state machine invariants after every step. */
class SimulationTest {

    @Test
    fun `bot games always finish and never break invariants`() {
        var draws = 0
        for (seed in 0 until 400) {
            val players = 2 + seed % 5
            val engine = GameEngine(Random(seed))
            var state = engine.newGame("sim-$seed", seats(players, bots = true))
            var steps = 0
            while (state.turnPhase != TurnPhase.GAME_OVER) {
                val actor = assertNotNull(state.actingPlayerId)
                val intent = assertNotNull(BotPolicy.nextIntent(state, actor), "bot had no move in ${state.turnPhase}")
                state = when (val result = engine.apply(state, actor, intent)) {
                    is EngineResult.Accepted -> result.state
                    is EngineResult.Rejected -> fail("seed $seed: bot intent $intent rejected: ${result.reason}")
                }
                checkInvariants(state, seed)
                if (++steps > 20_000) fail("seed $seed did not finish")
            }
            if (state.isDraw) draws++ else {
                val winner = state.player(state.winnerId!!)!!
                assertTrue(winner.isAlive, "seed $seed: dead winner")
                assertTrue(winner.victoryPoints >= 20 || state.alivePlayers.size == 1, "seed $seed: undeserved win")
            }
        }
        assertTrue(draws < 400)
    }

    private fun checkInvariants(state: GameState, seed: Int) {
        val context = "seed $seed rev ${state.revision}"
        for (p in state.players) {
            assertTrue(p.health in 0..p.maxHealth, "$context: ${p.id} health ${p.health}")
            assertTrue(p.victoryPoints >= 0 && p.energyCubes >= 0, "$context: negative stats for ${p.id}")
            assertEquals(p.isAlive, p.health > 0, "$context: ${p.id} alive flag mismatch")
            if (!p.isAlive) assertEquals(BoardLocation.OUTSIDE_TOKYO, p.location, "$context: dead monster in Tokyo")
        }
        assertTrue(state.players.count { it.location == BoardLocation.TOKYO_CITY } <= 1, "$context: crowded City")
        assertTrue(state.players.count { it.location == BoardLocation.TOKYO_BAY } <= 1, "$context: crowded Bay")
        if (!state.tokyoBayEnabled) assertTrue(state.occupantOf(BoardLocation.TOKYO_BAY) == null, "$context: Bay should be closed")
        assertTrue(state.turnPhase != TurnPhase.RESOLVE_DICE_PHASE && state.turnPhase != TurnPhase.END_TURN_PHASE, "$context: transient phase leaked")
        if (state.turnPhase != TurnPhase.GAME_OVER) {
            assertTrue(state.currentPlayer.isAlive, "$context: dead monster is taking a turn")
        }
        if (state.turnPhase == TurnPhase.TOKYO_YIELD_PHASE) {
            assertTrue(state.pendingYieldPlayerIds.isNotEmpty(), "$context: nobody pending")
            assertTrue(state.pendingYieldPlayerIds.all { state.player(it)!!.inTokyo }, "$context: pending player not in Tokyo")
        }
        val cardsInPlay = state.deck.size + state.cardMarket.size + state.discardPile.size + state.players.sumOf { it.cards.size }
        assertEquals(26, cardsInPlay, "$context: cards were lost or duplicated")
        assertTrue(state.cardMarket.size <= state.rules.marketSize)
    }
}
