package eu.zipf.zeno.monstermayor.core.engine

import eu.zipf.zeno.monstermayor.core.model.GameEvent
import eu.zipf.zeno.monstermayor.core.model.GameState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Player intents: the only way to change a game. Validated by [GameEngine]. */
@Serializable
sealed interface GameIntent {
    /** First roll of the turn rolls every die; later rolls reroll the dice that are not held. */
    @Serializable
    @SerialName("roll_dice")
    data object RollDice : GameIntent

    @Serializable
    @SerialName("toggle_hold")
    data class ToggleHold(val dieIndex: Int) : GameIntent

    /** Stop rolling and resolve the current dice. */
    @Serializable
    @SerialName("resolve_dice")
    data object ResolveDice : GameIntent

    /** Answer from the monster named in `pendingYieldPlayerId`. */
    @Serializable
    @SerialName("tokyo_decision")
    data class TokyoDecision(val yieldTokyo: Boolean) : GameIntent

    @Serializable
    @SerialName("buy_card")
    data class BuyCard(val marketIndex: Int) : GameIntent

    @Serializable
    @SerialName("reset_market")
    data object ResetMarket : GameIntent

    @Serializable
    @SerialName("end_turn")
    data object EndTurn : GameIntent
}

sealed interface EngineResult {
    data class Accepted(val state: GameState, val events: List<GameEvent>) : EngineResult
    data class Rejected(val reason: String) : EngineResult
}
