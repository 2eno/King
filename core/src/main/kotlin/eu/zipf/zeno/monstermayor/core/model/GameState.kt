package eu.zipf.zeno.monstermayor.core.model

import eu.zipf.zeno.monstermayor.core.cards.CardId
import kotlinx.serialization.Serializable

/**
 * Full authoritative game state, owned by the host. It contains hidden information (deck order)
 * and is never sent over the network; see [toPayload] for the public projection.
 *
 * Tokyo occupancy has a single source of truth: [PlayerState.location].
 */
@Serializable
data class GameState(
    val gameId: String,
    val rules: GameRules,
    val players: List<PlayerState>,
    val currentPlayerIndex: Int = 0,
    val turnPhase: TurnPhase = TurnPhase.ROLL_PHASE,
    val turnNumber: Int = 1,
    val dice: List<DiceFace> = emptyList(),
    val diceHeld: List<Boolean> = emptyList(),
    val hasRolled: Boolean = false,
    val rerollsRemaining: Int = rules.rerolls,
    val rollSequence: Int = 0,
    val cardMarket: List<CardId> = emptyList(),
    val deck: List<CardId> = emptyList(),
    val discardPile: List<CardId> = emptyList(),
    /** Queue of monsters that still have to answer "yield Tokyo?"; the head is asked first. */
    val pendingYieldPlayerIds: List<String> = emptyList(),
    val attackerId: String? = null,
    val dealtDamageThisTurn: Boolean = false,
    val extraTurnPending: Boolean = false,
    val winnerId: String? = null,
    val isDraw: Boolean = false,
    val revision: Long = 0,
    val log: List<GameEvent> = emptyList(),
) {
    val currentPlayer: PlayerState get() = players[currentPlayerIndex]

    val alivePlayers: List<PlayerState> get() = players.filter { it.isAlive }

    val tokyoBayEnabled: Boolean get() = alivePlayers.size >= rules.tokyoBayMinPlayers

    fun occupantOf(location: BoardLocation): PlayerState? =
        players.firstOrNull { it.isAlive && it.location == location }

    fun player(id: String): PlayerState? = players.firstOrNull { it.id == id }

    /** The player whose input the game is currently waiting for, or null if the game is over. */
    val actingPlayerId: String?
        get() = when (turnPhase) {
            TurnPhase.GAME_OVER -> null
            TurnPhase.TOKYO_YIELD_PHASE -> pendingYieldPlayerIds.firstOrNull()
            else -> currentPlayer.id
        }

    fun toPayload(recentEventCount: Int = RECENT_EVENTS): GameStatePayload = GameStatePayload(
        gameId = gameId,
        currentTurnPlayerId = currentPlayer.id,
        turnPhase = turnPhase,
        diceValues = dice,
        diceHeld = diceHeld,
        rerollsRemaining = rerollsRemaining,
        players = players,
        tokyoCityOccupantId = occupantOf(BoardLocation.TOKYO_CITY)?.id,
        tokyoBayOccupantId = occupantOf(BoardLocation.TOKYO_BAY)?.id,
        cardMarket = cardMarket,
        pendingYieldPlayerId = if (turnPhase == TurnPhase.TOKYO_YIELD_PHASE) pendingYieldPlayerIds.firstOrNull() else null,
        revision = revision,
        hasRolled = hasRolled,
        rollSequence = rollSequence,
        turnNumber = turnNumber,
        deckSize = deck.size,
        tokyoBayEnabled = tokyoBayEnabled,
        winnerId = winnerId,
        isDraw = isDraw,
        recentEvents = log.takeLast(recentEventCount),
        rules = rules,
    )

    companion object {
        const val RECENT_EVENTS = 20
        const val LOG_CAPACITY = 60
    }
}

/**
 * Public, host-authoritative snapshot synced to every client as JSON. The first eleven fields
 * are the core sync contract; the rest are extensions the UI needs (animations, win screen).
 */
@Serializable
data class GameStatePayload(
    val gameId: String,
    val currentTurnPlayerId: String,
    val turnPhase: TurnPhase,
    val diceValues: List<DiceFace>,
    val diceHeld: List<Boolean>,
    val rerollsRemaining: Int,
    val players: List<PlayerState>,
    val tokyoCityOccupantId: String?,
    val tokyoBayOccupantId: String?,
    val cardMarket: List<CardId>,
    val pendingYieldPlayerId: String?,
    val revision: Long = 0,
    val hasRolled: Boolean = false,
    val rollSequence: Int = 0,
    val turnNumber: Int = 1,
    val deckSize: Int = 0,
    val tokyoBayEnabled: Boolean = false,
    val winnerId: String? = null,
    val isDraw: Boolean = false,
    val recentEvents: List<GameEvent> = emptyList(),
    val rules: GameRules = GameRules(),
) {
    val currentPlayer: PlayerState? get() = player(currentTurnPlayerId)

    fun player(id: String?): PlayerState? = players.firstOrNull { it.id == id }

    val actingPlayerId: String?
        get() = when (turnPhase) {
            TurnPhase.GAME_OVER -> null
            TurnPhase.TOKYO_YIELD_PHASE -> pendingYieldPlayerId
            else -> currentTurnPlayerId
        }
}
