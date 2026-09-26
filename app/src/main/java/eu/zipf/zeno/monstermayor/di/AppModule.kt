package eu.zipf.zeno.monstermayor.di

import androidx.room.Room
import eu.zipf.zeno.monstermayor.BuildConfig
import eu.zipf.zeno.monstermayor.client.GameSessionManager
import eu.zipf.zeno.monstermayor.client.OnlineGameClient
import eu.zipf.zeno.monstermayor.client.createGameHttpClient
import eu.zipf.zeno.monstermayor.data.MatchHistoryRepository
import eu.zipf.zeno.monstermayor.data.MonsterMayorDatabase
import eu.zipf.zeno.monstermayor.data.ProfileRepository
import eu.zipf.zeno.monstermayor.data.SavedGameRepository
import eu.zipf.zeno.monstermayor.ui.game.GameViewModel
import eu.zipf.zeno.monstermayor.ui.home.HomeViewModel
import eu.zipf.zeno.monstermayor.ui.online.OnlineViewModel
import eu.zipf.zeno.monstermayor.ui.setup.LocalSetupViewModel
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Process-wide scope for work that must outlive screens (online connection, local game host). */
class ApplicationScope(val scope: CoroutineScope)

val appModule = module {
    single { ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }

    single {
        Room.databaseBuilder(androidContext(), MonsterMayorDatabase::class.java, "monster-mayor.db").build()
    }
    single { get<MonsterMayorDatabase>().profileDao() }
    single { get<MonsterMayorDatabase>().savedGameDao() }
    single { get<MonsterMayorDatabase>().matchHistoryDao() }

    single { ProfileRepository(get(), BuildConfig.DEFAULT_SERVER_URL) }
    single { SavedGameRepository(get()) }
    single { MatchHistoryRepository(get()) }

    single { createGameHttpClient(OkHttp) }
    single { OnlineGameClient(get(), get<ApplicationScope>().scope) }
    single {
        GameSessionManager(
            scope = get<ApplicationScope>().scope,
            onlineClient = get(),
            savedGames = get<SavedGameRepository>(),
            recorder = get<MatchHistoryRepository>(),
        )
    }

    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { LocalSetupViewModel(get(), get()) }
    viewModel { OnlineViewModel(get(), get()) }
    viewModel { GameViewModel(get()) }
}
