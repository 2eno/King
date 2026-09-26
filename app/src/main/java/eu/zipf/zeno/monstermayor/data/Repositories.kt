package eu.zipf.zeno.monstermayor.data

import eu.zipf.zeno.monstermayor.client.MatchRecorder
import eu.zipf.zeno.monstermayor.client.MatchResult
import eu.zipf.zeno.monstermayor.client.SavedGameStore
import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameState
import eu.zipf.zeno.monstermayor.core.net.GameJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Profile(
    val name: String,
    val favoriteCharacter: CharacterId,
    val serverUrl: String,
)

class ProfileRepository(
    private val dao: ProfileDao,
    private val defaultServerUrl: String,
) {
    private val writeLock = Mutex()

    val profile: Flow<Profile> = dao.observe().map { it.toProfile() }

    suspend fun current(): Profile = dao.get().toProfile()

    /** Read-modify-write, serialized so quick successive edits (typing) apply in order. */
    suspend fun update(transform: (Profile) -> Profile) = writeLock.withLock {
        val updated = transform(current())
        dao.upsert(
            ProfileEntity(
                name = updated.name,
                favoriteCharacter = updated.favoriteCharacter.name,
                serverUrl = updated.serverUrl,
            ),
        )
    }

    private fun ProfileEntity?.toProfile(): Profile = Profile(
        name = this?.name ?: "",
        favoriteCharacter = this?.favoriteCharacter
            ?.let { stored -> CharacterId.entries.firstOrNull { it.name == stored } }
            ?: CharacterId.GIGAZAUR,
        serverUrl = this?.serverUrl?.takeIf { it.isNotBlank() } ?: defaultServerUrl,
    )
}

class SavedGameRepository(private val dao: SavedGameDao) : SavedGameStore {
    val hasSavedGame: Flow<Boolean> = dao.observe().map { it != null }

    override suspend fun load(): GameState? = dao.get()?.let { entity ->
        runCatching { GameJson.decodeFromString(GameState.serializer(), entity.stateJson) }.getOrNull()
    }

    override suspend fun save(state: GameState) {
        dao.upsert(SavedGameEntity(stateJson = GameJson.encodeToString(GameState.serializer(), state), savedAt = System.currentTimeMillis()))
    }

    override suspend fun clear() = dao.clear()
}

data class MatchSummary(
    val mode: String,
    val winnerName: String?,
    val players: List<String>,
    val won: Boolean,
    val turns: Int,
    val finishedAt: Long,
)

data class PlayerStats(val gamesPlayed: Int, val wins: Int, val recent: List<MatchSummary>)

class MatchHistoryRepository(private val dao: MatchHistoryDao) : MatchRecorder {
    val stats: Flow<PlayerStats> = combine(
        dao.observeGamesPlayed(),
        dao.observeWins(),
        dao.observeRecent(limit = 5),
    ) { played, wins, recent ->
        PlayerStats(
            gamesPlayed = played,
            wins = wins,
            recent = recent.map {
                MatchSummary(it.mode, it.winnerName, it.playerNames.split('|'), it.localPlayerWon, it.turns, it.finishedAt)
            },
        )
    }

    override suspend fun record(result: MatchResult) {
        dao.insert(
            MatchRecordEntity(
                gameId = result.gameId,
                mode = result.mode.name,
                winnerName = result.winnerName,
                playerNames = result.playerNames.joinToString("|"),
                localPlayerWon = result.localPlayerWon,
                turns = result.turns,
                finishedAt = result.finishedAt,
            ),
        )
    }
}
