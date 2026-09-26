package eu.zipf.zeno.monstermayor.core.engine

import eu.zipf.zeno.monstermayor.core.cards.CardCatalog
import eu.zipf.zeno.monstermayor.core.cards.CardId
import eu.zipf.zeno.monstermayor.core.model.DiceFace
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.PlayerState
import eu.zipf.zeno.monstermayor.core.model.TurnPhase

/**
 * A simple, deterministic heuristic player. Used for CPU opponents and to keep online games moving
 * when a player disconnects. Returns one intent at a time; call it again after applying the intent.
 */
object BotPolicy {

    fun nextIntent(state: GameState, playerId: String): GameIntent? {
        if (state.actingPlayerId != playerId) return null
        val me = state.player(playerId) ?: return null
        return when (state.turnPhase) {
            TurnPhase.ROLL_PHASE -> rollDecision(state, me)
            TurnPhase.TOKYO_YIELD_PHASE -> GameIntent.TokyoDecision(yieldTokyo = me.health <= YIELD_AT_HEALTH)
            TurnPhase.BUY_CARDS_PHASE -> buyDecision(state, me)
            else -> null
        }
    }

    private const val YIELD_AT_HEALTH = 5

    private fun rollDecision(state: GameState, me: PlayerState): GameIntent {
        if (!state.hasRolled) return GameIntent.RollDice
        if (state.rerollsRemaining <= 0) return GameIntent.ResolveDice
        val wanted = wantedDice(state, me)
        // Fix the first die whose hold state differs from what we want, one toggle per intent.
        val toggle = state.dice.indices.firstOrNull { state.diceHeld[it] != wanted[it] }
        return when {
            toggle != null -> GameIntent.ToggleHold(toggle)
            wanted.all { it } -> GameIntent.ResolveDice
            else -> GameIntent.RollDice
        }
    }

    private fun wantedDice(state: GameState, me: PlayerState): List<Boolean> {
        val dice = state.dice
        val bestNumber = DiceFace.NUMBERS
            .map { face -> face to dice.count { it == face } }
            .filter { (_, count) -> count >= 2 }
            .maxByOrNull { (face, count) -> count * 10 + face.numberValue!! }
            ?.first
        val tokyoOccupied = state.players.any { it.isAlive && it.inTokyo && it.id != me.id }
        val keepAttack = me.inTokyo || me.health >= 6 || !tokyoOccupied
        val keepHeal = !me.inTokyo && me.health < me.maxHealth - 2
        return dice.map { face ->
            when (face) {
                DiceFace.ENERGY -> true
                DiceFace.ATTACK -> keepAttack
                DiceFace.HEAL -> keepHeal
                else -> face == bestNumber
            }
        }
    }

    private fun buyDecision(state: GameState, me: PlayerState): GameIntent {
        val affordable = state.cardMarket.withIndex()
            .filter { (_, card) -> RuleBook.cardCost(me, card) <= me.energyCubes }
            .filter { (_, card) -> worthBuying(state, me, card) }
            .maxByOrNull { (_, card) -> CardCatalog[card].cost }
        return affordable?.let { GameIntent.BuyCard(it.index) } ?: GameIntent.EndTurn
    }

    private fun worthBuying(state: GameState, me: PlayerState, card: CardId): Boolean = when (card) {
        // Hurting yourself is only worth it with health to spare.
        CardId.TANK_PARADE -> me.health > 5
        // Healing cards are wasted at full health.
        CardId.FIELD_MEDICS -> me.health < me.maxHealth - 1
        CardId.MASS_EVACUATION -> state.alivePlayers.any { it.id != me.id && it.victoryPoints >= 5 }
        else -> true
    }
}
