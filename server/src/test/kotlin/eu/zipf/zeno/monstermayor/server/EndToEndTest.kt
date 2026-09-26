package eu.zipf.zeno.monstermayor.server

import eu.zipf.zeno.monstermayor.client.ConnectionState
import eu.zipf.zeno.monstermayor.client.OnlineGameClient
import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.engine.GameIntent
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.net.ClientMessage
import eu.zipf.zeno.monstermayor.core.net.PlayerProfile
import eu.zipf.zeno.monstermayor.core.net.RoomStatus
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Two real clients talking to the real WebSocket endpoint. */
class EndToEndTest {

    @Test
    fun `host creates a room, a friend joins by code, and both see the same game`() = testApplication {
        val serverScope = CoroutineScope(SupervisorJob())
        val manager = RoomManager(
            scope = serverScope,
            config = RoomConfig(botStepDelay = 10.milliseconds),
            engineFactory = { GameEngine(Random(5)) },
        )
        application { monsterMayorModule(manager, sweepAbandonedRooms = false) }
        val http = createClient { install(WebSockets) }
        val clientScope = CoroutineScope(SupervisorJob())
        val alice = OnlineGameClient(http, clientScope)
        val bob = OnlineGameClient(http, clientScope)
        val url = "ws://localhost/ws"

        try {
            withTimeout(20.seconds) {
                alice.createRoom(url, PlayerProfile("Alice", CharacterId.GIGAZAUR))
                val code = alice.lobby.first { it != null }!!.roomCode
                assertEquals(4, code.length)

                bob.joinRoom(url, code.lowercase(), PlayerProfile("Bob", CharacterId.GIGAZAUR))
                alice.lobby.first { it?.seats?.size == 2 }
                bob.connection.first { it == ConnectionState.Connected }
                bob.send(ClientMessage.SetReady(true))
                alice.lobby.first { it?.canStart == true }
                alice.send(ClientMessage.StartGame)

                val start = alice.game.first { it != null }!!
                assertEquals(start, bob.game.first { it != null })
                assertEquals(RoomStatus.IN_GAME, bob.lobby.first { it?.status == RoomStatus.IN_GAME }!!.status)

                val active = if (start.currentTurnPlayerId == alice.identity.value!!.playerId) alice else bob
                active.submit(GameIntent.RollDice)
                val rolled = alice.game.first { it!!.hasRolled }!!
                assertEquals(6, rolled.diceValues.size)
                assertEquals(rolled, bob.game.first { it?.revision == rolled.revision })

                active.submit(GameIntent.ToggleHold(1))
                val held = bob.game.first { it!!.diceHeld.getOrNull(1) == true }!!
                assertEquals(held, alice.game.first { it?.revision == held.revision })

                val bystander = if (active === alice) bob else alice
                bystander.submit(GameIntent.ResolveDice)
                assertTrue(bystander.notices.first().startsWith("Waiting for"))
            }
        } finally {
            clientScope.cancel()
            serverScope.cancel()
        }
    }

    @Test
    fun `joining an unknown room fails cleanly`() = testApplication {
        val serverScope = CoroutineScope(SupervisorJob())
        application { monsterMayorModule(RoomManager(serverScope), sweepAbandonedRooms = false) }
        val clientScope = CoroutineScope(SupervisorJob())
        val client = OnlineGameClient(createClient { install(WebSockets) }, clientScope)
        try {
            withTimeout(10.seconds) {
                client.joinRoom("ws://localhost/ws", "ZZZZ", PlayerProfile("Eve", CharacterId.MEGA_MOTH))
                val failed = client.connection.first { it is ConnectionState.Failed } as ConnectionState.Failed
                assertTrue(failed.reason.contains("ZZZZ"))
            }
        } finally {
            clientScope.cancel()
            serverScope.cancel()
        }
    }
}
