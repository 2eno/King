package eu.zipf.zeno.monstermayor.server

import eu.zipf.zeno.monstermayor.core.engine.GameEngine
import eu.zipf.zeno.monstermayor.core.net.RoomCodes
import kotlinx.coroutines.CoroutineScope
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Creates rooms with unique 4-letter join codes and closes abandoned ones. */
class RoomManager(
    private val scope: CoroutineScope,
    private val config: RoomConfig = RoomConfig(),
    private val random: Random = SecureRandom().asKotlinRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val engineFactory: () -> GameEngine = { GameEngine(SecureRandom().asKotlinRandom()) },
) {
    private val rooms = ConcurrentHashMap<String, Room>()

    val roomCount: Int get() = rooms.size

    fun createRoom(): Room {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = (1..RoomCodes.LENGTH).map { RoomCodes.ALPHABET[random.nextInt(RoomCodes.ALPHABET.length)] }.joinToString("")
            var created: Room? = null
            rooms.computeIfAbsent(code) {
                Room(code, scope, config, engineFactory, clock, onClosed = { closed -> rooms.remove(closed.code, closed) })
                    .also { created = it }
            }
            created?.let { return it }
        }
        error("Could not find a free room code")
    }

    fun find(code: String): Room? = rooms[RoomCodes.normalize(code)]

    /** Closes rooms nobody has been connected to for longer than the configured TTL. */
    suspend fun sweepAbandoned() {
        val now = clock()
        rooms.values
            .filter { room -> room.abandonedSince?.let { now - it >= config.abandonedRoomTtl.inWholeMilliseconds } == true }
            .forEach { it.close() }
    }

    private companion object {
        const val MAX_CODE_ATTEMPTS = 1_000
    }
}
