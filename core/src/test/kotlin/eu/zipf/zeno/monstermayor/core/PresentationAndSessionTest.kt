package eu.zipf.zeno.monstermayor.core

import eu.zipf.zeno.monstermayor.core.cards.CardId
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.host.GameHost
import eu.zipf.zeno.monstermayor.core.model.Seat
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.presentation.GamePresenter
import eu.zipf.zeno.monstermayor.core.session.LocalGameSession
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresentationAndSessionTest {

    @Test
    fun `controls are only offered to the local acting player`() {
        val (engine, start) = newGame()
        val mine = GamePresenter.present(start.toPayload(), controlled = setOf("p0"))
        assertTrue(mine.controls.canRoll)
        assertFalse(mine.controls.canToggleHold)
        assertEquals("p0", mine.localActor?.id)

        val theirs = GamePresenter.present(start.toPayload(), controlled = setOf("p1"))
        assertFalse(theirs.controls.canRoll)
        assertNull(theirs.localActor)

        val rolled = GamePresenter.present(engine.ok(start, "p0", GameIntent.RollDice).toPayload(), setOf("p0"))
        assertTrue(rolled.controls.canToggleHold)
        assertTrue(rolled.controls.canResolve)
        assertEquals("Reroll (2)", rolled.controls.rollLabel)
    }

    @Test
    fun `the yield prompt goes to the damaged monster`() {
        val (engine, start) = newGame()
        val attacked = engine.ok(start.inTokyo("p1").withDice(CLAW, ONE, TWO, THREE, ONE, TWO), "p0", GameIntent.ResolveDice)
        val defender = GamePresenter.present(attacked.toPayload(), setOf("p1"))
        val prompt = assertNotNull(defender.yieldPrompt)
        assertEquals("Player 0", prompt.attackerName)
        assertEquals(9, prompt.health)
        assertNull(GamePresenter.present(attacked.toPayload(), setOf("p0")).yieldPrompt)
    }

    @Test
    fun `market marks what the active player can afford`() {
        val (engine, start) = newGame()
        val state = engine.ok(
            start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
                .withPlayer("p0") { it.copy(energyCubes = 4) }
                .copy(cardMarket = listOf(CardId.ARMOR_PLATING, CardId.FRENZY)),
            "p0",
            GameIntent.ResolveDice,
        )
        val ui = GamePresenter.present(state.toPayload(), setOf("p0"))
        assertEquals(listOf(true, false), ui.market.map { it.canBuy })
        assertTrue(ui.controls.canEndTurn)
        assertTrue(ui.controls.canResetMarket)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `local session plays bot turns until a human has to act`() = runTest {
        val engine = GameEngine(Random(3))
        val seats = listOf(
            Seat("human", "Zeno", CharacterId.GIGAZAUR),
            Seat("bot1", "CPU 1", CharacterId.CYBER_KITTY, isBot = true),
            Seat("bot2", "CPU 2", CharacterId.MEGA_MOTH, isBot = true),
        )
        val host = GameHost(engine.newGame("local", seats, firstPlayerIndex = 1), engine)
        val session = LocalGameSession(host, backgroundScope, botStepDelayMillis = 100)
        assertEquals(setOf("human"), session.controlledPlayerIds.value)

        advanceTimeBy(60_000)
        advanceUntilIdle()
        val state = host.state.value
        assertTrue(state.turnPhase == TurnPhase.GAME_OVER || state.actingPlayerId == "human", "bots should hand over to the human")
        if (state.turnPhase != TurnPhase.GAME_OVER) {
            val notice = async(start = CoroutineStart.UNDISPATCHED) { session.notices.first() }
            session.submit("bot1", GameIntent.RollDice)
            assertEquals("That monster is not controlled on this device", notice.await())
        }
    }
}
