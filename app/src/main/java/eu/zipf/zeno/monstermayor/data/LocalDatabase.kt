package eu.zipf.zeno.monstermayor.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The single local profile (name, favourite monster, room server). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val favoriteCharacter: String,
    val serverUrl: String,
)

/** The offline pass-and-play game in progress, as JSON of the authoritative GameState. */
@Entity(tableName = "saved_game")
data class SavedGameEntity(
    @PrimaryKey val slot: Int = 0,
    val stateJson: String,
    val savedAt: Long,
)

@Entity(tableName = "match_history")
data class MatchRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val mode: String,
    val winnerName: String?,
    val playerNames: String,
    val localPlayerWon: Boolean,
    val turns: Int,
    val finishedAt: Long,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_game WHERE slot = 0")
    fun observe(): Flow<SavedGameEntity?>

    @Query("SELECT * FROM saved_game WHERE slot = 0")
    suspend fun get(): SavedGameEntity?

    @Upsert
    suspend fun upsert(game: SavedGameEntity)

    @Query("DELETE FROM saved_game")
    suspend fun clear()
}

@Dao
interface MatchHistoryDao {
    @Insert
    suspend fun insert(record: MatchRecordEntity)

    @Query("SELECT * FROM match_history ORDER BY finishedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MatchRecordEntity>>

    @Query("SELECT COUNT(*) FROM match_history")
    fun observeGamesPlayed(): Flow<Int>

    @Query("SELECT COUNT(*) FROM match_history WHERE localPlayerWon = 1")
    fun observeWins(): Flow<Int>
}

@Database(
    entities = [ProfileEntity::class, SavedGameEntity::class, MatchRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MonsterMayorDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun savedGameDao(): SavedGameDao
    abstract fun matchHistoryDao(): MatchHistoryDao
}
