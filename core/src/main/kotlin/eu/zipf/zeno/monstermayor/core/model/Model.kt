package eu.zipf.zeno.monstermayor.core.model

import eu.zipf.zeno.monstermayor.core.cards.CardId
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The six faces of a standard black die. */
@Serializable
enum class DiceFace {
    ONE, TWO, THREE, ATTACK, HEAL, ENERGY;

    /** Face value for number faces, `null` for symbol faces. */
    val numberValue: Int?
        get() = when (this) {
            ONE -> 1
            TWO -> 2
            THREE -> 3
            else -> null
        }

    companion object {
        val NUMBERS = listOf(ONE, TWO, THREE)

        fun random(random: Random): DiceFace = entries[random.nextInt(entries.size)]
    }
}

/** Playable monsters. Each monster can be picked by at most one player per game. */
@Serializable
enum class CharacterId(val displayName: String, val argbColor: Long) {
    GIGAZAUR("GigaZaur", 0xFF4CAF50),
    CYBER_KITTY("CyberKitty", 0xFFEC407A),
    MEGA_MOTH("Mega Moth", 0xFFFFB300),
    ROBO_RHINO("Robo Rhino", 0xFF78909C),
    SLIME_SULTAN("Slime Sultan", 0xFF7E57C2),
    TITAN_TOAD("Titan Toad", 0xFF26A69A),
}

@Serializable
enum class BoardLocation { OUTSIDE_TOKYO, TOKYO_CITY, TOKYO_BAY }

@Serializable
enum class TurnPhase {
    /** Active player rolls and holds dice (1 roll + rerolls). */
    ROLL_PHASE,

    /** Dice are being scored. Resolved atomically by the host; never waits for input. */
    RESOLVE_DICE_PHASE,

    /** Damaged monsters in Tokyo decide, one by one, whether to yield. */
    TOKYO_YIELD_PHASE,

    /** Active player may buy cards or wipe the market. */
    BUY_CARDS_PHASE,

    /** End-of-turn effects and turn hand-over. Resolved atomically by the host. */
    END_TURN_PHASE,

    GAME_OVER,
}

@Serializable
data class PlayerState(
    val id: String,
    val name: String,
    val characterId: CharacterId,
    val health: Int = GameRules.DEFAULT_STARTING_HEALTH,
    val maxHealth: Int = GameRules.DEFAULT_MAX_HEALTH,
    val victoryPoints: Int = 0,
    val energyCubes: Int = 0,
    val location: BoardLocation = BoardLocation.OUTSIDE_TOKYO,
    val isAlive: Boolean = true,
    val cards: List<CardId> = emptyList(),
    val isBot: Boolean = false,
) {
    val inTokyo: Boolean get() = location != BoardLocation.OUTSIDE_TOKYO

    fun has(card: CardId): Boolean = card in cards
}

/** Tunable rule constants. Defaults follow the classic board game. */
@Serializable
data class GameRules(
    val victoryPointsToWin: Int = 20,
    val startingHealth: Int = DEFAULT_STARTING_HEALTH,
    val maxHealth: Int = DEFAULT_MAX_HEALTH,
    val diceCount: Int = 6,
    val rerolls: Int = 2,
    val marketSize: Int = 3,
    val marketResetCost: Int = 2,
    val tokyoEntryVictoryPoints: Int = 1,
    val tokyoStartTurnVictoryPoints: Int = 2,
    val tokyoBayMinPlayers: Int = 5,
    val minPlayers: Int = 2,
    val maxPlayers: Int = 6,
) {
    companion object {
        const val DEFAULT_STARTING_HEALTH = 10
        const val DEFAULT_MAX_HEALTH = 10
    }
}

/** A player taking part in a new game. */
@Serializable
data class Seat(
    val playerId: String,
    val name: String,
    val characterId: CharacterId,
    val isBot: Boolean = false,
)
