package eu.zipf.zeno.monstermayor.core.presentation

import eu.zipf.zeno.monstermayor.core.cards.CardCatalog
import eu.zipf.zeno.monstermayor.core.cards.CardId
import eu.zipf.zeno.monstermayor.core.cards.CardType
import eu.zipf.zeno.monstermayor.core.engine.RuleBook
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.DiceFace
import eu.zipf.zeno.monstermayor.core.model.GameEvent
import eu.zipf.zeno.monstermayor.core.model.GameStatePayload
import eu.zipf.zeno.monstermayor.core.model.PlayerState
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.model.VpReason

data class PlayerUi(
    val id: String,
    val name: String,
    val character: CharacterId,
    val health: Int,
    val maxHealth: Int,
    val victoryPoints: Int,
    val energy: Int,
    val location: BoardLocation,
    val isAlive: Boolean,
    val isBot: Boolean,
    val isCurrentTurn: Boolean,
    val isActing: Boolean,
    val isLocal: Boolean,
    val cards: List<CardUi>,
) {
    val inTokyo: Boolean get() = location != BoardLocation.OUTSIDE_TOKYO
}

data class CardUi(
    val id: CardId,
    val name: String,
    val description: String,
    val type: CardType,
    val cost: Int,
)

data class MarketCardUi(val index: Int, val card: CardUi, val affordable: Boolean, val canBuy: Boolean)

data class DieUi(val index: Int, val face: DiceFace, val held: Boolean)

data class TurnControls(
    val canRoll: Boolean = false,
    val rollLabel: String = "Roll",
    val canToggleHold: Boolean = false,
    val canResolve: Boolean = false,
    val canBuy: Boolean = false,
    val canResetMarket: Boolean = false,
    val canEndTurn: Boolean = false,
)

data class YieldPrompt(
    val playerId: String,
    val playerName: String,
    val attackerName: String,
    val health: Int,
    val location: BoardLocation,
)

data class GameOverUi(
    val winnerId: String?,
    val winnerName: String?,
    val isDraw: Boolean,
    val localPlayerWon: Boolean,
    val standings: List<PlayerUi>,
)

data class GameUiState(
    val phase: TurnPhase,
    val turnNumber: Int,
    val players: List<PlayerUi>,
    val currentPlayer: PlayerUi,
    /** One of this device's players who must act right now, or null when waiting for someone else. */
    val localActor: PlayerUi?,
    val dice: List<DieUi>,
    val diceSlots: Int,
    val hasRolled: Boolean,
    val rerollsRemaining: Int,
    val rollSequence: Int,
    val controls: TurnControls,
    val market: List<MarketCardUi>,
    val deckSize: Int,
    val marketResetCost: Int,
    val tokyoCity: PlayerUi?,
    val tokyoBay: PlayerUi?,
    val tokyoBayEnabled: Boolean,
    val yieldPrompt: YieldPrompt?,
    val statusText: String,
    val eventFeed: List<String>,
    val gameOver: GameOverUi?,
    val victoryPointsToWin: Int,
)

/** Pure mapping from the synced payload to everything the game screen renders. */
object GamePresenter {

    fun present(payload: GameStatePayload, controlled: Set<String>): GameUiState {
        val acting = payload.actingPlayerId
        val players = payload.players.map { it.toUi(payload, controlled, acting) }
        val byId = players.associateBy { it.id }
        val current = byId.getValue(payload.currentTurnPlayerId)
        val localActor = acting?.let(byId::get)?.takeIf { it.isLocal }
        val activeState = payload.currentPlayer!!
        val myTurn = localActor != null && localActor.id == current.id
        val phase = payload.turnPhase

        val controls = if (!myTurn) {
            TurnControls()
        } else when (phase) {
            TurnPhase.ROLL_PHASE -> TurnControls(
                canRoll = !payload.hasRolled || (payload.rerollsRemaining > 0 && !payload.diceHeld.all { it }),
                rollLabel = if (!payload.hasRolled) "Roll" else "Reroll (${payload.rerollsRemaining})",
                canToggleHold = payload.hasRolled && payload.rerollsRemaining > 0,
                canResolve = payload.hasRolled,
            )
            TurnPhase.BUY_CARDS_PHASE -> TurnControls(
                canBuy = true,
                canResetMarket = activeState.energyCubes >= payload.rules.marketResetCost,
                canEndTurn = true,
            )
            else -> TurnControls()
        }

        val market = payload.cardMarket.mapIndexed { index, id ->
            val cost = RuleBook.cardCost(activeState, id)
            val affordable = activeState.energyCubes >= cost
            MarketCardUi(index, id.toCardUi(cost), affordable, canBuy = controls.canBuy && affordable)
        }

        val yieldPrompt = if (phase == TurnPhase.TOKYO_YIELD_PHASE && localActor != null) {
            YieldPrompt(localActor.id, localActor.name, current.name, localActor.health, localActor.location)
        } else null

        val gameOver = if (phase == TurnPhase.GAME_OVER) {
            val winner = payload.winnerId?.let(byId::get)
            GameOverUi(
                winnerId = winner?.id,
                winnerName = winner?.name,
                isDraw = payload.isDraw,
                localPlayerWon = winner?.isLocal == true,
                standings = players.sortedWith(
                    compareByDescending<PlayerUi> { it.id == winner?.id }
                        .thenByDescending { it.isAlive }
                        .thenByDescending { it.victoryPoints },
                ),
            )
        } else null

        val names = payload.players.associate { it.id to it.name }
        return GameUiState(
            phase = phase,
            turnNumber = payload.turnNumber,
            players = players,
            currentPlayer = current,
            localActor = localActor,
            dice = payload.diceValues.mapIndexed { i, face -> DieUi(i, face, payload.diceHeld.getOrElse(i) { false }) },
            diceSlots = RuleBook.diceCount(payload.rules, activeState),
            hasRolled = payload.hasRolled,
            rerollsRemaining = payload.rerollsRemaining,
            rollSequence = payload.rollSequence,
            controls = controls,
            market = market,
            deckSize = payload.deckSize,
            marketResetCost = payload.rules.marketResetCost,
            tokyoCity = payload.tokyoCityOccupantId?.let(byId::get),
            tokyoBay = payload.tokyoBayOccupantId?.let(byId::get),
            tokyoBayEnabled = payload.tokyoBayEnabled,
            yieldPrompt = yieldPrompt,
            statusText = statusText(payload, current, byId[acting ?: ""], localActor),
            eventFeed = payload.recentEvents.asReversed().mapNotNull { EventText.describe(it, names) },
            gameOver = gameOver,
            victoryPointsToWin = payload.rules.victoryPointsToWin,
        )
    }

    private fun statusText(payload: GameStatePayload, current: PlayerUi, acting: PlayerUi?, localActor: PlayerUi?): String {
        val who = if (localActor != null) "${localActor.name}, you" else current.name
        return when (payload.turnPhase) {
            TurnPhase.ROLL_PHASE -> when {
                !payload.hasRolled -> if (localActor != null) "$who: roll the dice!" else "${current.name} is about to roll"
                payload.rerollsRemaining > 0 -> "${current.name}: hold dice and reroll (${payload.rerollsRemaining} left)"
                else -> "${current.name}: resolve the dice"
            }
            TurnPhase.TOKYO_YIELD_PHASE ->
                if (localActor != null) "$who took a hit in Tokyo. Yield to ${current.name}?"
                else "Waiting for ${acting?.name ?: "a monster"} to decide on Tokyo"
            TurnPhase.BUY_CARDS_PHASE ->
                if (localActor != null) "$who may buy power cards" else "${current.name} is shopping for power cards"
            TurnPhase.RESOLVE_DICE_PHASE, TurnPhase.END_TURN_PHASE -> "${current.name}'s turn"
            TurnPhase.GAME_OVER -> when {
                payload.isDraw -> "Everyone was destroyed. It's a draw!"
                else -> "${payload.player(payload.winnerId)?.name ?: "Someone"} is the new Monster Mayor!"
            }
        }
    }

    private fun PlayerState.toUi(payload: GameStatePayload, controlled: Set<String>, acting: String?) = PlayerUi(
        id = id,
        name = name,
        character = characterId,
        health = health,
        maxHealth = maxHealth,
        victoryPoints = victoryPoints,
        energy = energyCubes,
        location = location,
        isAlive = isAlive,
        isBot = isBot,
        isCurrentTurn = id == payload.currentTurnPlayerId,
        isActing = id == acting,
        isLocal = id in controlled,
        cards = cards.map { it.toCardUi(CardCatalog[it].cost) },
    )

    private fun CardId.toCardUi(cost: Int): CardUi {
        val definition = CardCatalog[this]
        return CardUi(this, definition.name, definition.description, definition.type, cost)
    }
}

/** One-line descriptions for the event feed. */
object EventText {
    fun describe(event: GameEvent, names: Map<String, String>): String? {
        fun n(id: String?) = names[id] ?: "?"
        return when (event) {
            is GameEvent.TurnStarted -> "— Turn ${event.turnNumber}: ${n(event.playerId)} —"
            is GameEvent.DiceRolled -> null
            is GameEvent.VictoryPointsGained -> "${n(event.playerId)} +${event.amount} ★" + when (event.reason) {
                VpReason.DICE -> " from dice"
                VpReason.TOKYO_ENTRY -> " for entering Tokyo"
                VpReason.TOKYO_START -> " for starting in Tokyo"
                VpReason.CARD -> " from a card"
            }
            is GameEvent.VictoryPointsLost -> "${n(event.playerId)} −${event.amount} ★"
            is GameEvent.EnergyGained -> "${n(event.playerId)} +${event.amount} ⚡"
            is GameEvent.Healed -> "${n(event.playerId)} heals ${event.amount} ♥"
            is GameEvent.Damaged ->
                if (event.sourcePlayerId == null || event.sourcePlayerId == event.playerId) "${n(event.playerId)} takes ${event.amount} damage"
                else "${n(event.sourcePlayerId)} hits ${n(event.playerId)} for ${event.amount}"
            is GameEvent.Eliminated -> "💀 ${n(event.playerId)} is destroyed!"
            is GameEvent.EnteredTokyo -> "${n(event.playerId)} enters ${event.location.label}"
            is GameEvent.LeftTokyo ->
                if (event.yielded) "${n(event.playerId)} yields ${event.location.label}"
                else "${n(event.playerId)} leaves ${event.location.label}"
            is GameEvent.StayedInTokyo -> "${n(event.playerId)} stays in Tokyo"
            is GameEvent.CardBought -> "${n(event.playerId)} buys ${CardCatalog[event.card].name}"
            is GameEvent.MarketReset -> "${n(event.playerId)} wipes the card market"
            is GameEvent.ExtraTurn -> "${n(event.playerId)} takes another turn!"
            is GameEvent.GameWon -> "👑 ${n(event.playerId)} wins!"
            GameEvent.GameDrawn -> "Nobody survived."
        }
    }
}

val BoardLocation.label: String
    get() = when (this) {
        BoardLocation.OUTSIDE_TOKYO -> "outside Tokyo"
        BoardLocation.TOKYO_CITY -> "Tokyo City"
        BoardLocation.TOKYO_BAY -> "Tokyo Bay"
    }
