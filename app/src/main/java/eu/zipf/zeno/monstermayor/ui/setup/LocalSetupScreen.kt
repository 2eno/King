package eu.zipf.zeno.monstermayor.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.zipf.zeno.monstermayor.client.LocalSetupIntent
import eu.zipf.zeno.monstermayor.client.LocalSetupState
import eu.zipf.zeno.monstermayor.client.SetupSeat
import eu.zipf.zeno.monstermayor.ui.components.MonsterToken
import eu.zipf.zeno.monstermayor.ui.theme.GameColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocalSetupRoute(onBack: () -> Unit, onGameStarted: () -> Unit, viewModel: LocalSetupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.gameStarted.collect { onGameStarted() } }
    LocalSetupScreen(state, viewModel::onIntent, viewModel::start, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSetupScreen(
    state: LocalSetupState,
    onIntent: (LocalSetupIntent) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pass & play") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                state.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start game (${state.seats.size} monsters)")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Humans share this device and hand it over when it's their move. CPU monsters play by themselves.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            itemsIndexed(state.seats) { index, seat ->
                SeatCard(index, seat, canRemove = state.canRemoveSeat, onIntent = onIntent)
            }
            if (state.canAddSeat) {
                item {
                    OutlinedButton(onClick = { onIntent(LocalSetupIntent.AddSeat) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add monster")
                    }
                }
            }
            item {
                Text(
                    "With 5 or 6 monsters, Tokyo Bay opens as a second spot in Tokyo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SeatCard(index: Int, seat: SetupSeat, canRemove: Boolean, onIntent: (LocalSetupIntent) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonsterToken(
                    seat.characterId,
                    size = 48.dp,
                    modifier = Modifier.clickable { onIntent(LocalSetupIntent.NextCharacter(index)) },
                )
                Text(seat.characterId.displayName, style = MaterialTheme.typography.bodySmall, color = GameColors.Neon)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = seat.name,
                    onValueChange = { onIntent(LocalSetupIntent.Rename(index, it)) },
                    singleLine = true,
                    label = { Text(if (seat.isBot) "CPU name" else "Player name") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CPU", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = seat.isBot, onCheckedChange = { onIntent(LocalSetupIntent.ToggleBot(index)) })
                }
            }
            if (canRemove) {
                IconButton(onClick = { onIntent(LocalSetupIntent.RemoveSeat(index)) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${seat.name}")
                }
            }
        }
    }
}
