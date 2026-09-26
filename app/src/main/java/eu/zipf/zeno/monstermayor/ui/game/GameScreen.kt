package eu.zipf.zeno.monstermayor.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.zipf.zeno.monstermayor.client.ConnectionState
import eu.zipf.zeno.monstermayor.client.GameMode
import eu.zipf.zeno.monstermayor.core.model.BoardLocation
import eu.zipf.zeno.monstermayor.core.model.TurnPhase
import eu.zipf.zeno.monstermayor.core.presentation.GameOverUi
import eu.zipf.zeno.monstermayor.core.presentation.GameUiState
import eu.zipf.zeno.monstermayor.core.presentation.YieldPrompt
import eu.zipf.zeno.monstermayor.ui.components.DiceTray
import eu.zipf.zeno.monstermayor.ui.components.MarketCard
import eu.zipf.zeno.monstermayor.ui.components.MonsterToken
import eu.zipf.zeno.monstermayor.ui.components.PlayerPanel
import eu.zipf.zeno.monstermayor.ui.components.TokyoBoard
import eu.zipf.zeno.monstermayor.ui.theme.GameColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GameRoute(
    onExitToMenu: () -> Unit,
    onBackToLobby: () -> Unit,
    viewModel: GameViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }
    GameScreen(
        state = state,
        snackbar = snackbar,
        onIntent = { intent ->
            viewModel.onIntent(intent)
            if (intent == GameScreenIntent.Leave) onExitToMenu()
            if (intent == GameScreenIntent.Rematch) onBackToLobby()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(state: GameScreenState, snackbar: SnackbarHostState, onIntent: (GameScreenIntent) -> Unit) {
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler { confirmLeave = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Monster Mayor", style = MaterialTheme.typography.titleMedium)
                        state.game?.let {
                            Text(
                                "Turn ${it.turnNumber} · first to ${it.victoryPointsToWin} ★",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { confirmLeave = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Leave game")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val game = state.game
        if (game == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.noActiveGame) {
                        Text("This game is no longer running.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onIntent(GameScreenIntent.Leave) }) { Text("Main menu") }
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Waiting for the game state…")
                    }
                }
            }
        } else {
            GameContent(game, state, onIntent, Modifier.padding(padding))
        }
    }

    val game = state.game
    val prompt = game?.yieldPrompt
    when {
        game?.gameOver != null -> GameOverDialog(game.gameOver!!, state, onIntent)
        state.handOffTo != null -> HandOffDialog(state.handOffTo) { onIntent(GameScreenIntent.AcknowledgeHandOff) }
        prompt != null -> YieldDialog(prompt) { yieldTokyo -> onIntent(GameScreenIntent.DecideTokyo(yieldTokyo)) }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the game?") },
            text = {
                Text(
                    if (state.mode == GameMode.LOCAL) "Your game is saved. You can resume it from the main menu."
                    else "You will leave the room. A CPU will take over your monster.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onIntent(GameScreenIntent.Leave)
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun GameContent(game: GameUiState, state: GameScreenState, onIntent: (GameScreenIntent) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionBanner(state.connection)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(game.players, key = { it.id }) { player ->
                PlayerPanel(player, game.victoryPointsToWin)
            }
        }

        TokyoBoard(game.tokyoCity, game.tokyoBay, game.tokyoBayEnabled, Modifier.padding(horizontal = 12.dp))

        StatusCard(game, Modifier.padding(horizontal = 12.dp))

        DiceTray(
            dice = game.dice,
            slots = game.diceSlots,
            rollSequence = game.rollSequence,
            canToggle = game.controls.canToggleHold,
            onToggle = { onIntent(GameScreenIntent.ToggleHold(it)) },
            modifier = Modifier.padding(top = 8.dp),
        )
        if (game.controls.canToggleHold) {
            Text(
                "Tap dice to hold them before rerolling",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        ActionBar(game, onIntent, Modifier.padding(horizontal = 12.dp))

        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Power cards", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${game.deckSize} in deck", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(game.market, key = { it.card.id }) { card ->
                MarketCard(card, showBuy = game.controls.canBuy, onBuy = { onIntent(GameScreenIntent.Buy(card.index)) })
            }
        }

        EventFeed(game.eventFeed, Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectionBanner(connection: ConnectionState) {
    val text = when (connection) {
        is ConnectionState.Reconnecting -> "Connection lost, reconnecting (attempt ${connection.attempt})…"
        is ConnectionState.Failed -> "Disconnected: ${connection.reason}"
        else -> return
    }
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(GameColors.Health)
            .padding(8.dp),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusCard(game: GameUiState, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (game.localActor != null) GameColors.Neon.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MonsterToken(game.currentPlayer.character, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(game.statusText, style = MaterialTheme.typography.titleMedium)
                if (game.phase == TurnPhase.ROLL_PHASE && game.hasRolled) {
                    Text(
                        "${game.rerollsRemaining} reroll(s) left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(game: GameUiState, onIntent: (GameScreenIntent) -> Unit, modifier: Modifier) {
    val controls = game.controls
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (game.phase) {
            TurnPhase.ROLL_PHASE -> {
                Button(
                    onClick = { onIntent(GameScreenIntent.Roll) },
                    enabled = controls.canRoll,
                    modifier = Modifier.weight(1f),
                ) { Text(controls.rollLabel) }
                FilledTonalButton(
                    onClick = { onIntent(GameScreenIntent.Resolve) },
                    enabled = controls.canResolve,
                    modifier = Modifier.weight(1f),
                ) { Text("Resolve dice") }
            }
            TurnPhase.BUY_CARDS_PHASE -> {
                OutlinedButton(
                    onClick = { onIntent(GameScreenIntent.WipeMarket) },
                    enabled = controls.canResetMarket,
                    modifier = Modifier.weight(1f),
                ) { Text("Wipe market (${game.marketResetCost}⚡)") }
                Button(
                    onClick = { onIntent(GameScreenIntent.EndTurn) },
                    enabled = controls.canEndTurn,
                    modifier = Modifier.weight(1f),
                ) { Text("End turn") }
            }
            else -> Unit
        }
    }
}

@Composable
private fun EventFeed(lines: List<String>, modifier: Modifier) {
    if (lines.isEmpty()) return
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("What happened", style = MaterialTheme.typography.labelLarge, color = GameColors.Neon)
            lines.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun YieldDialog(prompt: YieldPrompt, onDecide: (Boolean) -> Unit) {
    val where = if (prompt.location == BoardLocation.TOKYO_BAY) "Tokyo Bay" else "Tokyo City"
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${prompt.playerName}: yield $where?") },
        text = {
            Text(
                "${prompt.attackerName} hit you. You have ${prompt.health} health left. " +
                    "Yield and ${prompt.attackerName} takes your place, or stay and keep scoring.",
            )
        },
        confirmButton = { Button(onClick = { onDecide(true) }) { Text("Yield") } },
        dismissButton = { OutlinedButton(onClick = { onDecide(false) }) { Text("Stay in Tokyo") } },
    )
}

@Composable
private fun HandOffDialog(name: String, onReady: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Pass the device") },
        text = { Text("Hand the phone to $name.") },
        confirmButton = { Button(onClick = onReady) { Text("I'm $name") } },
    )
}

@Composable
private fun GameOverDialog(result: GameOverUi, state: GameScreenState, onIntent: (GameScreenIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                when {
                    result.isDraw -> "Nobody survived!"
                    result.localPlayerWon -> "You are the Monster Mayor!"
                    else -> "${result.winnerName} wins!"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.standings.forEachIndexed { index, player ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", modifier = Modifier.width(24.dp))
                        MonsterToken(player.character, size = 24.dp, dimmed = !player.isAlive)
                        Spacer(Modifier.width(8.dp))
                        Text(player.name, modifier = Modifier.weight(1f))
                        Text(if (player.isAlive) "${player.victoryPoints} ★" else "destroyed")
                    }
                }
            }
        },
        confirmButton = {
            if (state.mode == GameMode.ONLINE) {
                Button(onClick = { onIntent(GameScreenIntent.Rematch) }) {
                    Text(if (state.isOnlineHost) "Rematch in lobby" else "Back to lobby")
                }
            } else {
                Button(onClick = { onIntent(GameScreenIntent.Leave) }) { Text("Main menu") }
            }
        },
        dismissButton = {
            if (state.mode == GameMode.ONLINE) {
                TextButton(onClick = { onIntent(GameScreenIntent.Leave) }) { Text("Leave room") }
            }
        },
    )
}
