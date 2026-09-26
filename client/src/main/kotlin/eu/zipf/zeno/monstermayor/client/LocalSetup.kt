package eu.zipf.zeno.monstermayor.client

import eu.zipf.zeno.monstermayor.core.model.CharacterId
import eu.zipf.zeno.monstermayor.core.model.GameRules
import eu.zipf.zeno.monstermayor.core.model.Seat

data class SetupSeat(val name: String, val characterId: CharacterId, val isBot: Boolean)

/** Seat list for an offline pass-and-play game (humans share the device, CPUs play themselves). */
data class LocalSetupState(val seats: List<SetupSeat>) {
    val canAddSeat: Boolean get() = seats.size < RULES.maxPlayers
    val canRemoveSeat: Boolean get() = seats.size > RULES.minPlayers

    val problem: String?
        get() = when {
            seats.size !in RULES.minPlayers..RULES.maxPlayers -> "Pick ${RULES.minPlayers}-${RULES.maxPlayers} monsters"
            seats.none { !it.isBot } -> "At least one monster must be played by a human"
            seats.any { it.name.isBlank() } -> "Every monster needs a name"
            seats.map { it.characterId }.toSet().size != seats.size -> "Each monster can only be picked once"
            else -> null
        }

    val canStart: Boolean get() = problem == null

    fun toSeats(): List<Seat> = seats.mapIndexed { index, seat ->
        Seat(playerId = "local-$index", name = seat.name.trim(), characterId = seat.characterId, isBot = seat.isBot)
    }

    companion object {
        private val RULES = GameRules()

        fun initial(profileName: String, favorite: CharacterId): LocalSetupState {
            val cpuCharacter = CharacterId.entries.first { it != favorite }
            return LocalSetupState(
                listOf(
                    SetupSeat(profileName.ifBlank { "Player 1" }, favorite, isBot = false),
                    SetupSeat("CPU ${cpuCharacter.displayName}", cpuCharacter, isBot = true),
                ),
            )
        }
    }
}

sealed interface LocalSetupIntent {
    data object AddSeat : LocalSetupIntent
    data class RemoveSeat(val index: Int) : LocalSetupIntent
    data class Rename(val index: Int, val name: String) : LocalSetupIntent
    data class ToggleBot(val index: Int) : LocalSetupIntent
    data class NextCharacter(val index: Int) : LocalSetupIntent
}

object LocalSetupReducer {
    private const val MAX_NAME = 20

    fun reduce(state: LocalSetupState, intent: LocalSetupIntent): LocalSetupState = when (intent) {
        LocalSetupIntent.AddSeat -> if (!state.canAddSeat) state else {
            val character = CharacterId.entries.first { c -> state.seats.none { it.characterId == c } }
            state.copy(seats = state.seats + SetupSeat("CPU ${character.displayName}", character, isBot = true))
        }
        is LocalSetupIntent.RemoveSeat ->
            if (!state.canRemoveSeat || intent.index !in state.seats.indices) state
            else state.copy(seats = state.seats.filterIndexed { i, _ -> i != intent.index })
        is LocalSetupIntent.Rename -> state.update(intent.index) { it.copy(name = intent.name.take(MAX_NAME)) }
        is LocalSetupIntent.ToggleBot -> state.update(intent.index) { seat ->
            val name = when {
                !seat.isBot -> "CPU ${seat.characterId.displayName}"
                seat.name.startsWith("CPU ") -> "Player ${intent.index + 1}"
                else -> seat.name
            }
            seat.copy(isBot = !seat.isBot, name = name)
        }
        is LocalSetupIntent.NextCharacter -> state.update(intent.index) { seat ->
            val taken = state.seats.filter { it !== seat }.map { it.characterId }.toSet()
            val all = CharacterId.entries
            val start = all.indexOf(seat.characterId)
            val next = (1..all.size).map { all[(start + it) % all.size] }.first { it !in taken }
            val renamed = if (seat.isBot && seat.name == "CPU ${seat.characterId.displayName}") "CPU ${next.displayName}" else seat.name
            seat.copy(characterId = next, name = renamed)
        }
    }

    private fun LocalSetupState.update(index: Int, transform: (SetupSeat) -> SetupSeat): LocalSetupState =
        if (index !in seats.indices) this else copy(seats = seats.mapIndexed { i, s -> if (i == index) transform(s) else s })
}
