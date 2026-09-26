package eu.zipf.zeno.monstermayor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eu.zipf.zeno.monstermayor.ui.game.GameRoute
import eu.zipf.zeno.monstermayor.ui.home.HomeRoute
import eu.zipf.zeno.monstermayor.ui.online.OnlineRoute
import eu.zipf.zeno.monstermayor.ui.setup.LocalSetupRoute
import eu.zipf.zeno.monstermayor.ui.theme.MonsterMayorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonsterMayorTheme {
                MonsterMayorNavHost()
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val LOCAL_SETUP = "local_setup"
    const val ONLINE = "online"
    const val GAME = "game"
}

@Composable
private fun MonsterMayorNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                onPlayLocal = { navController.navigate(Routes.LOCAL_SETUP) },
                onPlayOnline = { navController.navigate(Routes.ONLINE) },
                onOpenGame = { navController.navigate(Routes.GAME) },
            )
        }
        composable(Routes.LOCAL_SETUP) {
            LocalSetupRoute(
                onBack = { navController.popBackStack() },
                onGameStarted = {
                    navController.navigate(Routes.GAME) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.ONLINE) {
            OnlineRoute(
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onGameStarted = {
                    navController.navigate(Routes.GAME) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.GAME) {
            GameRoute(
                onExitToMenu = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onBackToLobby = {
                    if (!navController.popBackStack(Routes.ONLINE, inclusive = false)) {
                        navController.navigate(Routes.ONLINE) { popUpTo(Routes.HOME) }
                    }
                },
            )
        }
    }
}
