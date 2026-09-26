package eu.zipf.zeno.monstermayor.core.model

import eu.zipf.zeno.monstermayor.core.cards.CardId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Something that happened in the game. Used for the event feed and UI animations. */
@Serializable
sealed interface GameEvent {
    @Serializable
    @SerialName("turn_started")
    data class TurnStarted(val playerId: String, val turnNumber: Int) : GameEvent

    @Serializable
    @SerialName("dice_rolled")
    data class DiceRolled(val playerId: String, val faces: List<DiceFace>) : GameEvent

    @Serializable
    @SerialName("vp_gained")
    data class VictoryPointsGained(val playerId: String, val amount: Int, val reason: VpReason) : GameEvent

    @Serializable
    @SerialName("vp_lost")
    data class VictoryPointsLost(val playerId: String, val amount: Int) : GameEvent

    @Serializable
    @SerialName("energy_gained")
    data class EnergyGained(val playerId: String, val amount: Int) : GameEvent

    @Serializable
    @SerialName("healed")
    data class Healed(val playerId: String, val amount: Int) : GameEvent

    @Serializable
    @SerialName("damaged")
    data class Damaged(val playerId: String, val amount: Int, val sourcePlayerId: String?) : GameEvent

    @Serializable
    @SerialName("eliminated")
    data class Eliminated(val playerId: String) : GameEvent

    @Serializable
    @SerialName("entered_tokyo")
    data class EnteredTokyo(val playerId: String, val location: BoardLocation) : GameEvent

    @Serializable
    @SerialName("left_tokyo")
    data class LeftTokyo(val playerId: String, val location: BoardLocation, val yielded: Boolean) : GameEvent

    @Serializable
    @SerialName("stayed_in_tokyo")
    data class StayedInTokyo(val playerId: String) : GameEvent

    @Serializable
    @SerialName("card_bought")
    data class CardBought(val playerId: String, val card: CardId, val cost: Int) : GameEvent

    @Serializable
    @SerialName("market_reset")
    data class MarketReset(val playerId: String) : GameEvent

    @Serializable
    @SerialName("extra_turn")
    data class ExtraTurn(val playerId: String) : GameEvent

    @Serializable
    @SerialName("game_won")
    data class GameWon(val playerId: String) : GameEvent

    @Serializable
    @SerialName("game_drawn")
    data object GameDrawn : GameEvent
}

@Serializable
enum class VpReason { DICE, TOKYO_ENTRY, TOKYO_START, CARD }
