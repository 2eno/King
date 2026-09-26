package eu.zipf.zeno.monstermayor.core.engine

import eu.zipf.zeno.monstermayor.core.cards.CardCatalog
import eu.zipf.zeno.monstermayor.core.cards.CardId
import eu.zipf.zeno.monstermayor.core.cards.CardType
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.DiceFace
import eu.zipf.zeno.monstermayor.core.model.GameEvent
import eu.zipf.zeno.monstermayor.core.model.GameRules
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.PlayerState
import eu.zipf.zeno.monstermayor.core.model.Seat
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.model.VpReason
import kotlin.random.Random

/** Rule helpers shared by the engine, bots and the UI. */
object RuleBook {
    fun cardCost(player: PlayerState, card: CardId): Int =
        (CardCatalog[card].cost - if (player.has(CardId.BARGAIN_HUNTER)) 1 else 0).coerceAtLeast(0)

    fun diceCount(rules: GameRules, player: PlayerState): Int =
        rules.diceCount + if (player.has(CardId.EXTRA_CLAW)) 1 else 0

    fun rerolls(rules: GameRules, player: PlayerState): Int =
        rules.rerolls + if (player.has(CardId.OVERCLOCKED_BRAIN)) 1 else 0

    /** Victory points a set of dice scores from number faces (triples and extra matches). */
    fun numberPoints(dice: List<DiceFace>, player: PlayerState? = null): Int =
        DiceFace.NUMBERS.sumOf { face ->
            val count = dice.count { it == face }
            if (count < 3) 0 else {
                val gourmet = if (face == DiceFace.ONE && player?.has(CardId.GOURMET) == true) 2 else 0
                face.numberValue!! + (count - 3) + gourmet
            }
        }
}

/**
 * Deterministic turn state machine. Given the same state, intent and random sequence it always
 * produces the same result. It never mutates its input; only the host calls it.
 *
 * Phases that need no player input (RESOLVE_DICE_PHASE, END_TURN_PHASE) are stepped through inside
 * a single [apply] call, so callers only ever observe ROLL, TOKYO_YIELD, BUY_CARDS and GAME_OVER.
 */
class GameEngine(private val random: Random = Random.Default) {

    fun newGame(
        gameId: String,
        seats: List<Seat>,
        rules: GameRules = GameRules(),
        firstPlayerIndex: Int? = null,
    ): GameState {
        require(seats.size in rules.minPlayers..rules.maxPlayers) {
            "A game needs ${rules.minPlayers}-${rules.maxPlayers} players, got ${seats.size}"
        }
        require(seats.map { it.playerId }.toSet().size == seats.size) { "Player ids must be unique" }
        require(seats.map { it.characterId }.toSet().size == seats.size) { "Each monster can only be picked once" }
        val deck = CardId.entries.shuffled(random)
        val first = firstPlayerIndex ?: random.nextInt(seats.size)
        require(first in seats.indices) { "Invalid first player index $first" }
        val initial = GameState(
            gameId = gameId,
            rules = rules,
            players = seats.map {
                PlayerState(
                    id = it.playerId,
                    name = it.name,
                    characterId = it.characterId,
                    health = rules.startingHealth,
                    maxHealth = rules.maxHealth,
                    isBot = it.isBot,
                )
            },
            currentPlayerIndex = first,
            cardMarket = deck.take(rules.marketSize),
            deck = deck.drop(rules.marketSize),
        )
        return Transaction(initial).apply { startTurn(first) }.commit()
    }

    fun apply(state: GameState, actorId: String, intent: GameIntent): EngineResult {
        if (state.turnPhase == TurnPhase.GAME_OVER) return EngineResult.Rejected("The game is over")
        val actingPlayerId = state.actingPlayerId
        if (actorId != actingPlayerId) {
            val name = state.player(actingPlayerId ?: "")?.name ?: "another monster"
            return EngineResult.Rejected("Waiting for $name")
        }
        val tx = Transaction(state)
        val error = when (intent) {
            GameIntent.RollDice -> tx.roll()
            is GameIntent.ToggleHold -> tx.toggleHold(intent.dieIndex)
            GameIntent.ResolveDice -> tx.resolveDice()
            is GameIntent.TokyoDecision -> tx.tokyoDecision(intent.yieldTokyo)
            is GameIntent.BuyCard -> tx.buyCard(intent.marketIndex)
            GameIntent.ResetMarket -> tx.resetMarket()
            GameIntent.EndTurn -> tx.endTurn()
        }
        if (error != null) return EngineResult.Rejected(error)
        if (!tx.checkGameOver() && !tx.state.currentPlayer.isAlive) {
            // The active monster knocked itself out (e.g. Tank Parade): its turn ends immediately.
            tx.finishTurn()
        }
        return EngineResult.Accepted(tx.commit(), tx.events.toList())
    }

    /** Mutable working copy of a [GameState] for the duration of one intent. */
    private inner class Transaction(var state: GameState) {
        val events = mutableListOf<GameEvent>()

        private val rules get() = state.rules
        private val current get() = state.currentPlayer

        fun commit(): GameState = state.copy(
            revision = state.revision + 1,
            log = (state.log + events).takeLast(GameState.LOG_CAPACITY),
        )

        // region intents

        fun roll(): String? {
            if (state.turnPhase != TurnPhase.ROLL_PHASE) return "You can only roll during the roll phase"
            if (!state.hasRolled) {
                val count = RuleBook.diceCount(rules, current)
                state = state.copy(
                    dice = List(count) { DiceFace.random(random) },
                    diceHeld = List(count) { false },
                    hasRolled = true,
                    rollSequence = state.rollSequence + 1,
                )
            } else {
                if (state.rerollsRemaining <= 0) return "No rerolls left"
                if (state.diceHeld.all { it }) return "Every die is held"
                state = state.copy(
                    dice = state.dice.mapIndexed { i, face -> if (state.diceHeld[i]) face else DiceFace.random(random) },
                    rerollsRemaining = state.rerollsRemaining - 1,
                    rollSequence = state.rollSequence + 1,
                )
            }
            events += GameEvent.DiceRolled(current.id, state.dice)
            return null
        }

        fun toggleHold(index: Int): String? {
            if (state.turnPhase != TurnPhase.ROLL_PHASE || !state.hasRolled) return "Roll the dice first"
            if (index !in state.dice.indices) return "There is no die #$index"
            if (state.rerollsRemaining <= 0) return "No rerolls left"
            state = state.copy(diceHeld = state.diceHeld.mapIndexed { i, held -> if (i == index) !held else held })
            return null
        }

        fun resolveDice(): String? {
            if (state.turnPhase != TurnPhase.ROLL_PHASE) return "Dice can only be resolved during the roll phase"
            if (!state.hasRolled) return "Roll the dice first"
            state = state.copy(turnPhase = TurnPhase.RESOLVE_DICE_PHASE)

            val me = current
            val dice = state.dice
            fun count(face: DiceFace) = dice.count { it == face }

            // 1. Numbers: triples score their face value, each extra matching die +1.
            gainVictoryPoints(me.id, RuleBook.numberPoints(dice, me), VpReason.DICE)
            if (me.has(CardId.COMPLETE_DESTRUCTION) && DiceFace.entries.all { count(it) > 0 }) {
                gainVictoryPoints(me.id, 9, VpReason.CARD)
            }

            // 2. Energy.
            val energy = count(DiceFace.ENERGY)
            if (energy > 0) gainEnergy(me.id, energy + if (me.has(CardId.BATTERY_PACK)) 1 else 0)

            // 3. Heal, only outside Tokyo.
            val hearts = count(DiceFace.HEAL)
            if (hearts > 0 && !current.inTokyo) {
                heal(me.id, hearts + if (me.has(CardId.REGENERATING_HIDE)) 1 else 0)
            }

            // 4. Attack: outside hits everyone inside Tokyo, inside hits everyone outside.
            val damagedInTokyo = mutableListOf<String>()
            val claws = count(DiceFace.ATTACK)
            if (claws > 0) {
                val fromTokyo = current.inTokyo
                val damage = claws +
                    (if (me.has(CardId.SPIKED_TAIL)) 1 else 0) +
                    (if (fromTokyo && me.has(CardId.TOKYO_TUNNELS)) 1 else 0)
                if (me.has(CardId.ALPHA_MONSTER)) gainVictoryPoints(me.id, 1, VpReason.CARD)
                val targets = state.alivePlayers
                    .filter { it.id != me.id && it.inTokyo != fromTokyo }
                    .sortedBy { it.location.ordinal }
                for (target in targets) {
                    val dealt = damage(target.id, damage, sourceId = me.id)
                    if (!fromTokyo && dealt > 0 && player(target.id).isAlive) damagedInTokyo += target.id
                }
            }

            normalizeTokyo()
            if (checkGameOver()) return null

            val askToYield = damagedInTokyo.filter { player(it).isAlive && player(it).inTokyo }
            if (askToYield.isNotEmpty()) {
                state = state.copy(
                    turnPhase = TurnPhase.TOKYO_YIELD_PHASE,
                    pendingYieldPlayerIds = askToYield,
                    attackerId = me.id,
                )
            } else {
                enterBuyPhase()
            }
            return null
        }

        fun tokyoDecision(yieldTokyo: Boolean): String? {
            if (state.turnPhase != TurnPhase.TOKYO_YIELD_PHASE) return "Nobody has to decide about Tokyo right now"
            val decidingId = state.pendingYieldPlayerIds.first()
            if (yieldTokyo) leaveTokyo(decidingId, yielded = true) else events += GameEvent.StayedInTokyo(decidingId)
            state = state.copy(pendingYieldPlayerIds = state.pendingYieldPlayerIds.drop(1))
            if (state.pendingYieldPlayerIds.isEmpty()) {
                state = state.copy(attackerId = null)
                enterBuyPhase()
            }
            return null
        }

        fun buyCard(index: Int): String? {
            if (state.turnPhase != TurnPhase.BUY_CARDS_PHASE) return "Cards can only be bought after resolving dice"
            val card = state.cardMarket.getOrNull(index) ?: return "There is no card in that slot"
            val me = current
            val cost = RuleBook.cardCost(me, card)
            if (me.energyCubes < cost) return "Not enough energy (${me.energyCubes}/$cost)"

            updatePlayer(me.id) { it.copy(energyCubes = it.energyCubes - cost) }
            replaceMarketSlot(index)
            events += GameEvent.CardBought(me.id, card, cost)
            when (CardCatalog[card].type) {
                CardType.KEEP -> {
                    updatePlayer(me.id) { it.copy(cards = it.cards + card) }
                    if (card == CardId.EVEN_BIGGER) {
                        updatePlayer(me.id) { it.copy(maxHealth = it.maxHealth + 2) }
                        heal(me.id, 2)
                    }
                }
                CardType.DISCARD -> {
                    resolveDiscardCard(me.id, card)
                    state = state.copy(discardPile = state.discardPile + card)
                }
            }
            if (me.has(CardId.NEWS_TEAM)) gainVictoryPoints(me.id, 1, VpReason.CARD)
            normalizeTokyo()
            return null
        }

        fun resetMarket(): String? {
            if (state.turnPhase != TurnPhase.BUY_CARDS_PHASE) return "The market can only be wiped after resolving dice"
            val me = current
            if (me.energyCubes < rules.marketResetCost) return "Wiping the market costs ${rules.marketResetCost} energy"
            if (state.deck.isEmpty() && state.discardPile.isEmpty()) return "There are no cards left to draw"
            updatePlayer(me.id) { it.copy(energyCubes = it.energyCubes - rules.marketResetCost) }
            val old = state.cardMarket
            state = state.copy(cardMarket = emptyList())
            repeat(rules.marketSize) { drawCard()?.let { state = state.copy(cardMarket = state.cardMarket + it) } }
            state = state.copy(discardPile = state.discardPile + old)
            events += GameEvent.MarketReset(me.id)
            return null
        }

        fun endTurn(): String? {
            if (state.turnPhase != TurnPhase.BUY_CARDS_PHASE) return "Resolve your dice before ending the turn"
            finishTurn()
            return null
        }

        // endregion

        // region turn flow

        fun startTurn(index: Int) {
            val player = state.players[index]
            state = state.copy(
                currentPlayerIndex = index,
                turnPhase = TurnPhase.ROLL_PHASE,
                dice = emptyList(),
                diceHeld = emptyList(),
                hasRolled = false,
                rerollsRemaining = RuleBook.rerolls(rules, player),
                pendingYieldPlayerIds = emptyList(),
                attackerId = null,
                dealtDamageThisTurn = false,
            )
            events += GameEvent.TurnStarted(player.id, state.turnNumber)
            if (player.inTokyo) {
                gainVictoryPoints(player.id, rules.tokyoStartTurnVictoryPoints, VpReason.TOKYO_START)
                if (player.has(CardId.METRO_MUNCHER)) gainVictoryPoints(player.id, 1, VpReason.CARD)
            }
        }

        fun finishTurn() {
            state = state.copy(turnPhase = TurnPhase.END_TURN_PHASE)
            val id = current.id
            if (current.isAlive) {
                if (current.has(CardId.ENERGY_HOARDER)) gainVictoryPoints(id, current.energyCubes / 6, VpReason.CARD)
                if (current.has(CardId.HERBIVORE) && !state.dealtDamageThisTurn) gainVictoryPoints(id, 1, VpReason.CARD)
                if (current.has(CardId.UNDERDOG)) {
                    val others = state.alivePlayers.filter { it.id != id }
                    if (others.isNotEmpty() && others.all { it.victoryPoints > current.victoryPoints }) {
                        gainVictoryPoints(id, 1, VpReason.CARD)
                    }
                }
            }
            if (checkGameOver()) return

            val extraTurn = state.extraTurnPending && current.isAlive
            if (extraTurn) events += GameEvent.ExtraTurn(id)
            val next = if (extraTurn) state.currentPlayerIndex else nextAlivePlayerIndex()
            state = state.copy(turnNumber = state.turnNumber + 1, extraTurnPending = false)
            startTurn(next)
            checkGameOver()
        }

        private fun enterBuyPhase() {
            fillTokyoWithActivePlayer()
            state = state.copy(turnPhase = TurnPhase.BUY_CARDS_PHASE)
        }

        private fun nextAlivePlayerIndex(): Int {
            val size = state.players.size
            for (step in 1..size) {
                val index = (state.currentPlayerIndex + step) % size
                if (state.players[index].isAlive) return index
            }
            return state.currentPlayerIndex
        }

        /** Ends the game on "last monster standing", "everyone is dead" or "someone reached the VP goal". */
        fun checkGameOver(): Boolean {
            if (state.turnPhase == TurnPhase.GAME_OVER) return true
            val alive = state.alivePlayers
            if (alive.isEmpty()) {
                state = state.copy(turnPhase = TurnPhase.GAME_OVER, isDraw = true, pendingYieldPlayerIds = emptyList())
                events += GameEvent.GameDrawn
                return true
            }
            val winner = if (alive.size == 1) {
                alive.single()
            } else {
                val reached = alive.filter { it.victoryPoints >= rules.victoryPointsToWin }
                reached.firstOrNull { it.id == current.id } ?: reached.maxByOrNull { it.victoryPoints }
            } ?: return false
            state = state.copy(turnPhase = TurnPhase.GAME_OVER, winnerId = winner.id, pendingYieldPlayerIds = emptyList())
            events += GameEvent.GameWon(winner.id)
            return true
        }

        // endregion

        // region Tokyo

        /** The active monster must move into an empty Tokyo slot (City first, then Bay). */
        private fun fillTokyoWithActivePlayer() {
            val me = current
            if (!me.isAlive || me.inTokyo) return
            when {
                state.occupantOf(BoardLocation.TOKYO_CITY) == null -> enterTokyo(me.id, BoardLocation.TOKYO_CITY)
                state.tokyoBayEnabled && state.occupantOf(BoardLocation.TOKYO_BAY) == null ->
                    enterTokyo(me.id, BoardLocation.TOKYO_BAY)
            }
        }

        /** Tokyo Bay closes once fewer than [GameRules.tokyoBayMinPlayers] monsters are alive. */
        private fun normalizeTokyo() {
            if (state.tokyoBayEnabled) return
            val bay = state.occupantOf(BoardLocation.TOKYO_BAY) ?: return
            if (state.occupantOf(BoardLocation.TOKYO_CITY) == null) {
                updatePlayer(bay.id) { it.copy(location = BoardLocation.TOKYO_CITY) }
                events += GameEvent.EnteredTokyo(bay.id, BoardLocation.TOKYO_CITY)
            } else {
                leaveTokyo(bay.id, yielded = false)
            }
        }

        private fun enterTokyo(id: String, location: BoardLocation) {
            updatePlayer(id) { it.copy(location = location) }
            events += GameEvent.EnteredTokyo(id, location)
            gainVictoryPoints(id, rules.tokyoEntryVictoryPoints, VpReason.TOKYO_ENTRY)
        }

        private fun leaveTokyo(id: String, yielded: Boolean) {
            val from = player(id).location
            if (from == BoardLocation.OUTSIDE_TOKYO) return
            updatePlayer(id) { it.copy(location = BoardLocation.OUTSIDE_TOKYO) }
            events += GameEvent.LeftTokyo(id, from, yielded)
        }

        // endregion

        // region cards

        private fun resolveDiscardCard(id: String, card: CardId) {
            val others = { state.alivePlayers.filter { it.id != id } }
            when (card) {
                CardId.SKYSCRAPER_SNACK -> gainVictoryPoints(id, 4, VpReason.CARD)
                CardId.MONORAIL_MUNCH -> gainVictoryPoints(id, 2, VpReason.CARD)
                CardId.POWER_GRID_FEAST -> gainEnergy(id, 9)
                CardId.FIELD_MEDICS -> heal(id, 2)
                CardId.REACTOR_SNACK -> {
                    gainVictoryPoints(id, 2, VpReason.CARD)
                    heal(id, 3)
                }
                CardId.REFINERY_BLAST -> {
                    gainVictoryPoints(id, 2, VpReason.CARD)
                    others().forEach { damage(it.id, 3, sourceId = id) }
                }
                CardId.MASS_EVACUATION -> others().forEach { loseVictoryPoints(it.id, 5) }
                CardId.FRENZY -> state = state.copy(extraTurnPending = true)
                CardId.TANK_PARADE -> {
                    gainVictoryPoints(id, 4, VpReason.CARD)
                    damage(id, 3, sourceId = null)
                }
                else -> error("$card is not a discard card")
            }
        }

        private fun drawCard(): CardId? {
            if (state.deck.isEmpty() && state.discardPile.isNotEmpty()) {
                state = state.copy(deck = state.discardPile.shuffled(random), discardPile = emptyList())
            }
            val card = state.deck.firstOrNull() ?: return null
            state = state.copy(deck = state.deck.drop(1))
            return card
        }

        private fun replaceMarketSlot(index: Int) {
            val replacement = drawCard()
            state = state.copy(
                cardMarket = if (replacement != null) {
                    state.cardMarket.toMutableList().also { it[index] = replacement }
                } else {
                    state.cardMarket.filterIndexed { i, _ -> i != index }
                },
            )
        }

        // endregion

        // region player stat changes

        private fun player(id: String): PlayerState = state.player(id) ?: error("Unknown player $id")

        private fun updatePlayer(id: String, transform: (PlayerState) -> PlayerState) {
            state = state.copy(players = state.players.map { if (it.id == id) transform(it) else it })
        }

        private fun gainVictoryPoints(id: String, amount: Int, reason: VpReason) {
            if (amount <= 0 || !player(id).isAlive) return
            updatePlayer(id) { it.copy(victoryPoints = it.victoryPoints + amount) }
            events += GameEvent.VictoryPointsGained(id, amount, reason)
        }

        private fun loseVictoryPoints(id: String, amount: Int) {
            val lost = amount.coerceAtMost(player(id).victoryPoints)
            if (lost <= 0) return
            updatePlayer(id) { it.copy(victoryPoints = it.victoryPoints - lost) }
            events += GameEvent.VictoryPointsLost(id, lost)
        }

        private fun gainEnergy(id: String, amount: Int) {
            if (amount <= 0) return
            updatePlayer(id) { it.copy(energyCubes = it.energyCubes + amount) }
            events += GameEvent.EnergyGained(id, amount)
        }

        private fun heal(id: String, amount: Int) {
            val target = player(id)
            val healed = amount.coerceAtMost(target.maxHealth - target.health)
            if (!target.isAlive || healed <= 0) return
            updatePlayer(id) { it.copy(health = it.health + healed) }
            events += GameEvent.Healed(id, healed)
        }

        /** Applies damage and returns how much was actually dealt (0 if prevented). */
        private fun damage(id: String, amount: Int, sourceId: String?): Int {
            val target = player(id)
            if (!target.isAlive || amount <= 0) return 0
            if (amount == 1 && target.has(CardId.ARMOR_PLATING)) return 0
            val health = target.health - amount
            if (sourceId != null && sourceId == current.id && sourceId != id) {
                state = state.copy(dealtDamageThisTurn = true)
            }
            events += GameEvent.Damaged(id, amount, sourceId)
            if (health <= 0) {
                updatePlayer(id) {
                    it.copy(health = 0, isAlive = false, location = BoardLocation.OUTSIDE_TOKYO)
                }
                events += GameEvent.Eliminated(id)
            } else {
                updatePlayer(id) { it.copy(health = health) }
            }
            return amount
        }

        // endregion
    }
}
