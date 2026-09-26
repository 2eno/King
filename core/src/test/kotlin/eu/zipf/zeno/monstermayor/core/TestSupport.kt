package eu.zipf.zeno.monstermayor.core

import eu.zipf.zeno.monstermayor.core.engine.EngineResult
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.DiceFace
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.model.PlayerState
import eu.zipf.zeno.monstermayor.core.model.Seat
import kotlin.random.Random
import kotlin.test.fail

fun seats(count: Int, bots: Boolean = false): List<Seat> =
    (0 until count).map { Seat("p$it", "Player $it", CharacterId.entries[it], isBot = bots) }

fun newGame(players: Int = 3, seed: Int = 7, engine: GameEngine = GameEngine(Random(seed))): Pair<GameEngine, GameState> =
    engine to engine.newGame("game", seats(players), firstPlayerIndex = 0)

/** Puts fixed dice on the table for the current player, as if they had just rolled. */
fun GameState.withDice(vararg faces: DiceFace): GameState =
    copy(dice = faces.toList(), diceHeld = faces.map { false }, hasRolled = true)

fun GameState.withPlayer(id: String, transform: (PlayerState) -> PlayerState): GameState =
    copy(players = players.map { if (it.id == id) transform(it) else it })

fun GameState.inTokyo(id: String, location: BoardLocation = BoardLocation.TOKYO_CITY): GameState =
    withPlayer(id) { it.copy(location = location) }

fun GameState.p(id: String): PlayerState = player(id) ?: fail("no player $id")

fun GameEngine.ok(state: GameState, actor: String, intent: GameIntent): GameState =
    when (val result = apply(state, actor, intent)) {
        is EngineResult.Accepted -> result.state
        is EngineResult.Rejected -> fail("Expected $intent by $actor to be accepted but was rejected: ${result.reason}")
    }

fun GameEngine.rejected(state: GameState, actor: String, intent: GameIntent): String =
    when (val result = apply(state, actor, intent)) {
        is EngineResult.Accepted -> fail("Expected $intent by $actor to be rejected")
        is EngineResult.Rejected -> result.reason
    }

val ONE = DiceFace.ONE
val TWO = DiceFace.TWO
val THREE = DiceFace.THREE
val CLAW = DiceFace.ATTACK
val HEART = DiceFace.HEAL
val BOLT = DiceFace.ENERGY
