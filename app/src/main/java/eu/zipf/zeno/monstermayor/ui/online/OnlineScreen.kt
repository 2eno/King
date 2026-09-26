package eu.zipf.zeno.monstermayor.ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.zipf.zeno.monstermayor.client.ConnectionState
import eu.zipf.zeno.monstermayor.core.net.LobbySeat
import eu.zipf.zeno.monstermayor.core.net.LobbyState
import eu.zipf.zeno.monstermayor.core.net.RoomStatus
import eu.zipf.zeno.monstermayor.ui.components.CharacterPicker
import eu.zipf.zeno.monstermayor.ui.components.MonsterToken
import eu.zipf.zeno.monstermayor.ui.theme.GameColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnlineRoute(onBack: () -> Unit, onGameStarted: () -> Unit, viewModel: OnlineViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.notices.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(viewModel) { viewModel.gameStarted.collect { onGameStarted() } }
    OnlineScreen(state, snackbar, viewModel::onIntent, onBack = {
        if (state.inRoom) viewModel.onIntent(OnlineIntent.Leave)
        onBack()
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(state: OnlineUiState, snackbar: SnackbarHostState, onIntent: (OnlineIntent) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.inRoom) "Room lobby" else "Play online") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ConnectionLine(state.connection)
            val lobby = state.lobby
            if (lobby == null) JoinOrHost(state, onIntent) else Lobby(lobby, state, onIntent)
        }
    }
}

@Composable
private fun ConnectionLine(connection: ConnectionState) {
    val (text, color) = when (connection) {
        ConnectionState.Idle -> return
        ConnectionState.Connecting -> "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.Connected -> "Connected" to GameColors.Energy
        is ConnectionState.Reconnecting -> "Reconnecting (attempt ${connection.attempt})…" to GameColors.Neon
        is ConnectionState.Failed -> connection.reason to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun JoinOrHost(state: OnlineUiState, onIntent: (OnlineIntent) -> Unit) {
    Text(
        "Host a room and share its 4-letter code, or enter a friend's code. Up to 6 monsters per room.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = { onIntent(OnlineIntent.HostRoom) }, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
        Text("Host a new room")
    }
    HorizontalDivider()
    OutlinedTextField(
        value = state.joinCode,
        onValueChange = { onIntent(OnlineIntent.ChangeJoinCode(it)) },
        label = { Text("Room code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 8.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
    FilledTonalButton(onClick = { onIntent(OnlineIntent.JoinRoom) }, enabled = state.canJoin, modifier = Modifier.fillMaxWidth()) {
        Text("Join room")
    }
    if (state.isBusy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(4.dp))
            Spacer(Modifier.width(8.dp))
            Text("Contacting the room server…")
        }
    }
}

@Composable
private fun Lobby(lobby: LobbyState, state: OnlineUiState, onIntent: (OnlineIntent) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = GameColors.Panel)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Room code", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                lobby.roomCode,
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 10.sp),
                color = GameColors.Neon,
            )
            Text(
                when (lobby.status) {
                    RoomStatus.LOBBY -> "${lobby.seats.size}/${lobby.maxPlayers} monsters"
                    RoomStatus.IN_GAME -> "Game in progress"
                    RoomStatus.FINISHED -> "Game over. Waiting for the host to start a rematch"
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }

    lobby.seats.forEach { seat ->
        SeatRow(seat, isMe = seat.playerId == state.myPlayerId, canKick = state.isHost && !seat.isHost && lobby.status == RoomStatus.LOBBY) {
            onIntent(OnlineIntent.Kick(seat.playerId))
        }
    }

    if (lobby.status == RoomStatus.LOBBY) {
        Text("Your monster", style = MaterialTheme.typography.titleMedium)
        CharacterPicker(
            selected = state.mySeat?.characterId,
            taken = state.takenCharacters,
            onPick = { onIntent(OnlineIntent.PickCharacter(it)) },
        )
        Spacer(Modifier.height(4.dp))
        if (state.isHost) {
            OutlinedButton(
                onClick = { onIntent(OnlineIntent.AddBot) },
                enabled = lobby.seats.size < lobby.maxPlayers,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add CPU monster") }
            Button(onClick = { onIntent(OnlineIntent.StartGame) }, enabled = lobby.canStart, modifier = Modifier.fillMaxWidth()) {
                Text(if (lobby.canStart) "Start game" else "Waiting for everyone to be ready")
            }
        } else {
            val ready = state.mySeat?.isReady == true
            Button(onClick = { onIntent(OnlineIntent.ToggleReady) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ready) "Not ready" else "I'm ready!")
            }
        }
    }
    OutlinedButton(onClick = { onIntent(OnlineIntent.Leave) }, modifier = Modifier.fillMaxWidth()) { Text("Leave room") }
}

@Composable
private fun SeatRow(seat: LobbySeat, isMe: Boolean, canKick: Boolean, onKick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonsterToken(seat.characterId, size = 36.dp, dimmed = !seat.isConnected)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(seat.name + if (isMe) " (you)" else "", style = MaterialTheme.typography.labelLarge)
                Text(
                    listOfNotNull(
                        seat.characterId.displayName,
                        "host".takeIf { seat.isHost },
                        "CPU".takeIf { seat.isBot },
                        "offline".takeIf { !seat.isConnected },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (seat.isHost || seat.isReady) "READY" else "…",
                color = if (seat.isHost || seat.isReady) GameColors.Energy else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            if (canKick) {
                IconButton(onClick = onKick) { Icon(Icons.Filled.Close, contentDescription = "Remove ${seat.name}") }
            }
        }
    }
}
