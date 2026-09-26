package eu.zipf.zeno.monstermayor.core

import eu.zipf.zeno.monstermayor.core.cards.CardId
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.engine.GameIntent.ResolveDice
import eu.zipf.zeno.monstermayor.core.engine.GameIntent.RollDice
import eu.zipf.zeno.monstermayor.core.engine.GameIntent.TokyoDecision
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.BoardLocation.OUTSIDE_TOKYO
import eu.zipf.zeno.monstermayor.core.model.BoardLocation.TOKYO_BAY
import eu.zipf.zeno.monstermayor.core.model.BoardLocation.TOKYO_CITY
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameEvent
import eu.zipf.zeno.monstermayor.core.model.Seat
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    // region setup & rolling

    @Test
    fun `new game deals a market of three and starts in the roll phase`() {
        val (_, state) = newGame(players = 4)
        assertEquals(3, state.cardMarket.size)
        assertEquals(CardId.entries.size - 3, state.deck.size)
        assertEquals(TurnPhase.ROLL_PHASE, state.turnPhase)
        assertEquals("p0", state.currentPlayer.id)
        assertEquals(2, state.rerollsRemaining)
        assertFalse(state.hasRolled)
        assertTrue(state.players.all { it.health == 10 && it.victoryPoints == 0 && it.energyCubes == 0 && !it.inTokyo })
    }

    @Test
    fun `new game validates player count and unique monsters`() {
        val engine = GameEngine(Random(1))
        assertFailsWith<IllegalArgumentException> { engine.newGame("g", seats(1)) }
        assertFailsWith<IllegalArgumentException> { engine.newGame("g", seats(6) + Seat("p6", "Extra", CharacterId.GIGAZAUR)) }
        val clash = seats(2).let { listOf(it[0], it[1].copy(characterId = it[0].characterId)) }
        assertFailsWith<IllegalArgumentException> { engine.newGame("g", clash) }
    }

    @Test
    fun `first roll rolls all dice and each reroll only rerolls unheld dice`() {
        val (engine, start) = newGame()
        var state = engine.ok(start, "p0", RollDice)
        assertEquals(6, state.dice.size)
        assertEquals(2, state.rerollsRemaining)
        val firstFace = state.dice[0]

        state = engine.ok(state, "p0", GameIntent.ToggleHold(0))
        assertTrue(state.diceHeld[0])
        repeat(2) {
            state = engine.ok(state, "p0", RollDice)
            assertEquals(firstFace, state.dice[0])
        }
        assertEquals(0, state.rerollsRemaining)
        assertEquals("No rerolls left", engine.rejected(state, "p0", RollDice))
        assertEquals(3, state.rollSequence)
    }

    @Test
    fun `rerolling with every die held is rejected`() {
        val (engine, start) = newGame()
        var state = engine.ok(start, "p0", RollDice)
        for (i in 0 until 6) state = engine.ok(state, "p0", GameIntent.ToggleHold(i))
        assertEquals("Every die is held", engine.rejected(state, "p0", RollDice))
    }

    @Test
    fun `only the acting player may act`() {
        val (engine, state) = newGame()
        assertTrue(engine.rejected(state, "p1", RollDice).startsWith("Waiting for"))
        assertEquals("Roll the dice first", engine.rejected(state, "p0", ResolveDice))
        assertEquals("Cards can only be bought after resolving dice", engine.rejected(state, "p0", GameIntent.BuyCard(0)))
    }

    // endregion

    // region dice resolution

    @Test
    fun `triples score face value and extra matches score one each`() {
        val (engine, start) = newGame()
        val base = start.inTokyo("p1")
        assertEquals(2, engine.ok(base.withDice(TWO, TWO, TWO, ONE, ONE, THREE), "p0", ResolveDice).p("p0").victoryPoints)
        assertEquals(4, engine.ok(base.withDice(THREE, THREE, THREE, THREE, ONE, TWO), "p0", ResolveDice).p("p0").victoryPoints)
        assertEquals(4, engine.ok(base.withDice(ONE, ONE, ONE, ONE, ONE, ONE), "p0", ResolveDice).p("p0").victoryPoints)
        assertEquals(0, engine.ok(base.withDice(ONE, ONE, TWO, TWO, THREE, THREE), "p0", ResolveDice).p("p0").victoryPoints)
    }

    @Test
    fun `energy faces give energy cubes`() {
        val (engine, start) = newGame()
        val state = engine.ok(start.inTokyo("p1").withDice(BOLT, BOLT, BOLT, ONE, TWO, THREE), "p0", ResolveDice)
        assertEquals(3, state.p("p0").energyCubes)
    }

    @Test
    fun `hearts heal outside Tokyo only and never above max health`() {
        val (engine, start) = newGame()
        val hurt = start.inTokyo("p1").withPlayer("p0") { it.copy(health = 5) }
        assertEquals(7, engine.ok(hurt.withDice(HEART, HEART, ONE, TWO, THREE, ONE), "p0", ResolveDice).p("p0").health)
        val nearlyFull = start.inTokyo("p1").withPlayer("p0") { it.copy(health = 9) }
        assertEquals(10, engine.ok(nearlyFull.withDice(HEART, HEART, HEART, ONE, TWO, THREE), "p0", ResolveDice).p("p0").health)
        val inTokyo = start.inTokyo("p0").withPlayer("p0") { it.copy(health = 5) }
        assertEquals(5, engine.ok(inTokyo.withDice(HEART, HEART, ONE, TWO, THREE, ONE), "p0", ResolveDice).p("p0").health)
    }

    @Test
    fun `when Tokyo is empty the active monster enters Tokyo City and scores one`() {
        val (engine, start) = newGame()
        val state = engine.ok(start.withDice(CLAW, CLAW, ONE, TWO, THREE, ONE), "p0", ResolveDice)
        assertEquals(TOKYO_CITY, state.p("p0").location)
        assertEquals(1, state.p("p0").victoryPoints)
        assertTrue(state.players.filter { it.id != "p0" }.all { it.health == 10 })
        assertEquals(TurnPhase.BUY_CARDS_PHASE, state.turnPhase)
        assertEquals("p0", state.toPayload().tokyoCityOccupantId)
    }

    @Test
    fun `attacking from outside damages Tokyo and the occupant may yield`() {
        val (engine, start) = newGame()
        val attacked = engine.ok(start.inTokyo("p1").withDice(CLAW, CLAW, ONE, TWO, THREE, ONE), "p0", ResolveDice)
        assertEquals(8, attacked.p("p1").health)
        assertEquals(10, attacked.p("p2").health)
        assertEquals(TurnPhase.TOKYO_YIELD_PHASE, attacked.turnPhase)
        assertEquals("p1", attacked.toPayload().pendingYieldPlayerId)
        assertEquals("p1", attacked.actingPlayerId)
        assertTrue(engine.rejected(attacked, "p0", GameIntent.EndTurn).startsWith("Waiting for"))

        val yielded = engine.ok(attacked, "p1", TokyoDecision(yieldTokyo = true))
        assertEquals(OUTSIDE_TOKYO, yielded.p("p1").location)
        assertEquals(TOKYO_CITY, yielded.p("p0").location)
        assertEquals(1, yielded.p("p0").victoryPoints)
        assertEquals(TurnPhase.BUY_CARDS_PHASE, yielded.turnPhase)
        assertNull(yielded.toPayload().pendingYieldPlayerId)

        val stayed = engine.ok(attacked, "p1", TokyoDecision(yieldTokyo = false))
        assertEquals(TOKYO_CITY, stayed.p("p1").location)
        assertEquals(OUTSIDE_TOKYO, stayed.p("p0").location)
        assertEquals(0, stayed.p("p0").victoryPoints)
    }

    @Test
    fun `attacking from Tokyo damages every monster outside without a yield prompt`() {
        val (engine, start) = newGame(players = 4)
        val state = engine.ok(start.inTokyo("p0").withDice(CLAW, CLAW, CLAW, ONE, TWO, THREE), "p0", ResolveDice)
        assertEquals(listOf(10, 7, 7, 7), state.players.map { it.health })
        assertEquals(TurnPhase.BUY_CARDS_PHASE, state.turnPhase)
    }

    @Test
    fun `destroying the Tokyo occupant lets the attacker move in`() {
        val (engine, start) = newGame()
        val state = engine.ok(
            start.inTokyo("p1").withPlayer("p1") { it.copy(health = 2) }.withDice(CLAW, CLAW, ONE, TWO, THREE, ONE),
            "p0",
            ResolveDice,
        )
        assertFalse(state.p("p1").isAlive)
        assertEquals(OUTSIDE_TOKYO, state.p("p1").location)
        assertEquals(TOKYO_CITY, state.p("p0").location)
        assertTrue(state.log.any { it == GameEvent.Eliminated("p1") })
    }

    @Test
    fun `starting a turn in Tokyo scores two`() {
        val (engine, start) = newGame(players = 2)
        var state = engine.ok(start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE), "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.EndTurn)
        assertEquals("p1", state.currentPlayer.id)
        assertEquals(2, state.p("p1").victoryPoints)
        assertEquals(2, state.turnNumber)
    }

    @Test
    fun `eliminated monsters are skipped in turn order`() {
        val (engine, start) = newGame(players = 3)
        var state = start.withPlayer("p1") { it.copy(isAlive = false, health = 0) }.inTokyo("p2")
        state = engine.ok(state.withDice(ONE, TWO, THREE, ONE, TWO, THREE), "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.EndTurn)
        assertEquals("p2", state.currentPlayer.id)
    }

    // endregion

    // region winning

    @Test
    fun `reaching twenty victory points wins immediately`() {
        val (engine, start) = newGame()
        val state = engine.ok(
            start.inTokyo("p1").withPlayer("p0") { it.copy(victoryPoints = 18) }.withDice(TWO, TWO, TWO, ONE, HEART, BOLT),
            "p0",
            ResolveDice,
        )
        assertEquals(TurnPhase.GAME_OVER, state.turnPhase)
        assertEquals("p0", state.winnerId)
        assertEquals("The game is over", engine.rejected(state, "p0", GameIntent.EndTurn))
    }

    @Test
    fun `last monster standing wins`() {
        val (engine, start) = newGame(players = 2)
        val state = engine.ok(
            start.inTokyo("p1").withPlayer("p1") { it.copy(health = 1) }.withDice(CLAW, ONE, TWO, THREE, ONE, TWO),
            "p0",
            ResolveDice,
        )
        assertEquals(TurnPhase.GAME_OVER, state.turnPhase)
        assertEquals("p0", state.winnerId)
    }

    @Test
    fun `a monster that destroys itself hands the win to the survivor`() {
        val (engine, start) = newGame(players = 2)
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(health = 2, energyCubes = 10) }
            .copy(cardMarket = listOf(CardId.TANK_PARADE, CardId.FRENZY, CardId.GOURMET))
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        assertFalse(state.p("p0").isAlive)
        assertEquals("p1", state.winnerId)
    }

    @Test
    fun `self destruction with several survivors passes the turn`() {
        val (engine, start) = newGame(players = 3)
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(health = 2, energyCubes = 10) }
            .copy(cardMarket = listOf(CardId.TANK_PARADE, CardId.FRENZY, CardId.GOURMET))
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        assertEquals("p1", state.currentPlayer.id)
        assertEquals(TurnPhase.ROLL_PHASE, state.turnPhase)
    }

    // endregion

    // region Tokyo Bay

    @Test
    fun `with five monsters the Bay opens and fills after the City`() {
        val (engine, start) = newGame(players = 5)
        assertTrue(start.tokyoBayEnabled)
        val state = engine.ok(start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE), "p0", ResolveDice)
        assertEquals(TOKYO_BAY, state.p("p0").location)
        assertEquals("p0", state.toPayload().tokyoBayOccupantId)
    }

    @Test
    fun `an outside attack hits City and Bay and both are asked in order`() {
        val (engine, start) = newGame(players = 5)
        var state = engine.ok(
            start.inTokyo("p2", TOKYO_BAY).inTokyo("p1", TOKYO_CITY).withDice(CLAW, ONE, TWO, THREE, ONE, TWO),
            "p0",
            ResolveDice,
        )
        assertEquals(listOf("p1", "p2"), state.pendingYieldPlayerIds)
        state = engine.ok(state, "p1", TokyoDecision(yieldTokyo = true))
        assertEquals("p2", state.actingPlayerId)
        state = engine.ok(state, "p2", TokyoDecision(yieldTokyo = true))
        assertEquals(TOKYO_CITY, state.p("p0").location)
        assertNull(state.occupantOf(TOKYO_BAY))
        assertEquals(1, state.p("p0").victoryPoints)
    }

    @Test
    fun `the Bay closes when only four monsters remain`() {
        val (engine, start) = newGame(players = 5)
        val state = engine.ok(
            start.inTokyo("p0", TOKYO_CITY).inTokyo("p1", TOKYO_BAY)
                .withPlayer("p3") { it.copy(health = 1) }
                .withDice(CLAW, ONE, TWO, THREE, ONE, TWO),
            "p0",
            ResolveDice,
        )
        assertFalse(state.p("p3").isAlive)
        assertFalse(state.tokyoBayEnabled)
        assertEquals(OUTSIDE_TOKYO, state.p("p1").location)
        assertNotEquals(TOKYO_BAY, state.p("p0").location)
    }

    // endregion

    // region cards

    @Test
    fun `buying a keep card spends energy and refills the slot`() {
        val (engine, start) = newGame()
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(energyCubes = 5) }
            .copy(cardMarket = listOf(CardId.ARMOR_PLATING, CardId.FRENZY, CardId.GOURMET))
        state = engine.ok(state, "p0", ResolveDice)
        val nextInDeck = state.deck.first()
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        assertEquals(1, state.p("p0").energyCubes)
        assertEquals(listOf(CardId.ARMOR_PLATING), state.p("p0").cards)
        assertEquals(listOf(nextInDeck, CardId.FRENZY, CardId.GOURMET), state.cardMarket)
        assertTrue(engine.rejected(state, "p0", GameIntent.BuyCard(1)).startsWith("Not enough energy"))
    }

    @Test
    fun `wiping the market costs two energy and draws three new cards`() {
        val (engine, start) = newGame()
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE).withPlayer("p0") { it.copy(energyCubes = 2) }
        state = engine.ok(state, "p0", ResolveDice)
        val old = state.cardMarket
        val expected = state.deck.take(3)
        state = engine.ok(state, "p0", GameIntent.ResetMarket)
        assertEquals(expected, state.cardMarket)
        assertEquals(old, state.discardPile)
        assertEquals(0, state.p("p0").energyCubes)
        assertEquals("Wiping the market costs 2 energy", engine.rejected(state, "p0", GameIntent.ResetMarket))
    }

    @Test
    fun `armor plating ignores a single point of damage`() {
        val (engine, start) = newGame()
        val state = engine.ok(
            start.inTokyo("p1").withPlayer("p1") { it.copy(cards = listOf(CardId.ARMOR_PLATING)) }
                .withDice(CLAW, ONE, TWO, THREE, ONE, TWO),
            "p0",
            ResolveDice,
        )
        assertEquals(10, state.p("p1").health)
        // Undamaged monsters are not asked to yield.
        assertEquals(TurnPhase.BUY_CARDS_PHASE, state.turnPhase)
    }

    @Test
    fun `frenzy grants another turn`() {
        val (engine, start) = newGame()
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(energyCubes = 7) }
            .copy(cardMarket = listOf(CardId.FRENZY, CardId.GOURMET, CardId.HERBIVORE))
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        state = engine.ok(state, "p0", GameIntent.EndTurn)
        assertEquals("p0", state.currentPlayer.id)
        assertTrue(state.log.any { it == GameEvent.ExtraTurn("p0") })
        assertTrue(CardId.FRENZY in state.discardPile)
    }

    @Test
    fun `extra claw and overclocked brain change dice count and rerolls`() {
        val (engine, start) = newGame(players = 2)
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(cards = listOf(CardId.EXTRA_CLAW, CardId.OVERCLOCKED_BRAIN)) }
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.EndTurn)
        state = engine.ok(state.withDice(ONE, TWO, THREE, ONE, TWO, THREE), "p1", ResolveDice)
        state = engine.ok(state, "p1", GameIntent.EndTurn)
        assertEquals(3, state.rerollsRemaining)
        state = engine.ok(state, "p0", RollDice)
        assertEquals(7, state.dice.size)
    }

    @Test
    fun `even bigger raises max health and heals`() {
        val (engine, start) = newGame()
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(energyCubes = 4, health = 9) }
            .copy(cardMarket = listOf(CardId.EVEN_BIGGER, CardId.GOURMET, CardId.HERBIVORE))
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        assertEquals(12, state.p("p0").maxHealth)
        assertEquals(11, state.p("p0").health)
    }

    @Test
    fun `refinery blast damages everyone else without a yield prompt`() {
        val (engine, start) = newGame()
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(energyCubes = 6) }
            .copy(cardMarket = listOf(CardId.REFINERY_BLAST, CardId.GOURMET, CardId.HERBIVORE))
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        assertEquals(listOf(10, 7, 7), state.players.map { it.health })
        assertEquals(2, state.p("p0").victoryPoints)
        assertEquals(TurnPhase.BUY_CARDS_PHASE, state.turnPhase)
    }

    @Test
    fun `bargain hunter makes cards cheaper and news team pays per purchase`() {
        val (engine, start) = newGame()
        var state = start.inTokyo("p1").withDice(ONE, TWO, THREE, ONE, TWO, THREE)
            .withPlayer("p0") { it.copy(energyCubes = 2, cards = listOf(CardId.BARGAIN_HUNTER, CardId.NEWS_TEAM)) }
            .copy(cardMarket = listOf(CardId.MONORAIL_MUNCH, CardId.GOURMET, CardId.HERBIVORE))
        state = engine.ok(state, "p0", ResolveDice)
        state = engine.ok(state, "p0", GameIntent.BuyCard(0))
        assertEquals(0, state.p("p0").energyCubes)
        assertEquals(3, state.p("p0").victoryPoints) // 2 from Monorail Munch + 1 from News Team
    }

    // endregion

    @Test
    fun `every accepted intent bumps the revision`() {
        val (engine, start) = newGame()
        val next = engine.ok(start, "p0", RollDice)
        assertEquals(start.revision + 1, next.revision)
        assertEquals(BoardLocation.entries.size, 3)
    }
}
