package eu.zipf.zeno.monstermayor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.zipf.zeno.monstermayor.ui.components.CharacterPicker
import eu.zipf.zeno.monstermayor.ui.components.MonsterToken
import eu.zipf.zeno.monstermayor.ui.theme.GameColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onPlayLocal: () -> Unit,
    onPlayOnline: () -> Unit,
    onOpenGame: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HomeEffect.OpenGame -> onOpenGame()
                is HomeEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }
    HomeScreen(state, snackbar, viewModel::onIntent, onPlayLocal, onPlayOnline)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onIntent: (HomeIntent) -> Unit,
    onPlayLocal: () -> Unit,
    onPlayOnline: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Text fields keep their own state so typing never waits for the database round trip.
    var nameField by rememberSaveable { mutableStateOf<String?>(null) }
    var serverField by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            MonsterToken(state.favoriteCharacter, size = 96.dp)
            Text("MONSTER MAYOR", style = MaterialTheme.typography.displaySmall, color = GameColors.Neon)
            Text(
                "Smash the city, rule Tokyo, be the first to 20 ★ or the last monster standing.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = nameField ?: state.name,
                onValueChange = {
                    nameField = it
                    onIntent(HomeIntent.ChangeName(it))
                },
                label = { Text("Your monster name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CharacterPicker(
                selected = state.favoriteCharacter,
                taken = emptySet(),
                onPick = { onIntent(HomeIntent.ChangeFavorite(it)) },
            )

            if (state.hasSavedGame) {
                FilledTonalButton(onClick = { onIntent(HomeIntent.ResumeLocalGame) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Resume saved game")
                }
            }
            Button(onClick = onPlayLocal, modifier = Modifier.fillMaxWidth()) { Text("Pass & play / vs CPU") }
            Button(onClick = onPlayOnline, modifier = Modifier.fillMaxWidth()) { Text("Play online") }

            StatsCard(state)

            TextButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "Hide server settings" else "Server settings")
            }
            if (showSettings) {
                OutlinedTextField(
                    value = serverField ?: state.serverUrl,
                    onValueChange = {
                        serverField = it
                        onIntent(HomeIntent.ChangeServerUrl(it))
                    },
                    label = { Text("Room server (ws://host:8080/ws)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatsCard(state: HomeUiState) {
    val stats = state.stats
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your record", style = MaterialTheme.typography.titleMedium)
            Text("${stats.gamesPlayed} games · ${stats.wins} wins")
            stats.recent.forEach { match ->
                Row {
                    Text(if (match.won) "🏆 " else "• ")
                    Text(
                        "${match.winnerName ?: "Draw"} won (${match.mode.lowercase()}, ${match.turns} turns)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

