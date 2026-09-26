# Monster Mayor

A multiplayer Android dice-brawler in the spirit of *King of Tokyo*: up to six monsters roll dice,
smash each other, fight over Tokyo and race to **20 victory points** or to be the **last monster standing**.

- **Offline**: pass & play on one phone, with any mix of humans and CPU monsters. Games auto-save
  after every move and can be resumed.
- **Online**: host a room, share its **4-letter code**, friends join from their phones. The room
  server is authoritative; clients only send intents and receive state.

## Modules

```
core/    Pure Kotlin. Rules engine, turn state machine, cards, bots, wire protocol, delta sync,
         GameSession abstraction and the platform-agnostic GamePresenter. No Android, no I/O.
client/  Pure Kotlin. Reconnecting WebSocket client (Ktor), RemoteGameSession,
         GameSessionManager (active game, autosave, match history hooks), local game setup reducer.
server/  Ktor WebSocket room server: lobbies, ready/host controls, one GameHost per room.
app/     Android app: Jetpack Compose UI, MVI ViewModels, Koin DI, Room persistence.
king/    The original 2018 Java prototype (Play Games RTMP). Not part of the build any more.
```

Dependencies only point downwards: `app -> client -> core`, `server -> core`.

### Architecture

```
            Compose UI  ──intent──▶  ViewModel (MVI)  ──▶  GameSession.submit(actor, GameIntent)
                ▲                        │                          │
                └──── GameScreenState ◀──┴── GamePresenter ◀── StateFlow<GameStatePayload>
                                                                    │
                      ┌─────────────────────────────────────────────┴──────────────────────┐
                      │ LocalGameSession (offline)          RemoteGameSession (online)      │
                      │   GameHost in-process                 OnlineGameClient ◀─ws─▶ Room  │
                      │   + bot driver + autosave (Room)      snapshot + JSON deltas   │    │
                      └──────────────────────────────────────────────────────────── GameHost┘
                                                                                      │
                                                          GameEngine (pure reducer) ◀─┘
```

- **`GameEngine`** is a deterministic reducer: `apply(state, actorId, intent) -> Accepted(state, events) | Rejected(reason)`.
  It validates who may act, never mutates its input, and bumps `revision` on every accepted intent.
  Given a seeded `Random` it is fully reproducible.
- **`GameHost`** serializes intents for one game (mutex) and publishes a `StateFlow<GameState>`.
  The same class is the authority in pass & play and in every online room.
- **`GameState`** is the full authoritative state (it includes the hidden deck order).
  **`GameStatePayload`** is the public projection that is synced to clients.
- **`GamePresenter`** turns a payload and the set of players this device controls into everything
  the game screen needs (enabled controls, affordable cards, yield prompt, status line, event feed).
  It is pure Kotlin and unit tested.

## Rules implemented

Turn phases: `ROLL_PHASE → RESOLVE_DICE_PHASE → (TOKYO_YIELD_PHASE) → BUY_CARDS_PHASE → END_TURN_PHASE → next turn`,
plus `GAME_OVER`. The two phases that need no input (resolve, end turn) are stepped through inside a single
intent, so clients only ever wait in roll, yield, buy or game over.

- 6 dice (`1`, `2`, `3`, `ATTACK`, `HEAL`, `ENERGY`): 1 roll + 2 rerolls, hold any dice between rolls.
- Numbers: three of a kind score the face value, each extra matching die +1 ★.
- Energy: +1 energy cube per face. Heal: +1 health per face, only outside Tokyo, capped at max health.
- Attack from outside Tokyo hits every monster in Tokyo; attack from Tokyo hits every monster outside.
- Damaged monsters in Tokyo are asked one by one (City, then Bay) whether to yield. If they yield, the
  attacker must move in. If Tokyo is empty after resolving, the active monster moves in automatically.
- Entering Tokyo +1 ★, starting a turn in Tokyo +2 ★.
- **Tokyo Bay** opens with 5–6 monsters alive and closes again when only 4 remain.
- Card market: 3 face-up power cards, buy with energy, wipe the market for 2 energy. 26 original cards
  (9 *discard*, 17 *keep*), e.g. *Extra Claw* (7 dice), *Overclocked Brain* (+1 reroll), *Armor Plating*,
  *Frenzy* (extra turn), *Refinery Blast*, *Mass Evacuation*, *Tank Parade* (can knock yourself out).
- Win at 20 ★ (checked immediately after every step) or as the last monster standing. If everyone dies
  at once the game is a draw.
- Rule constants live in `GameRules` (VP goal, dice count, rerolls, market size, …).

`SimulationTest` plays 400 complete bot-vs-bot games (2–6 players) and checks the invariants after every
single step: health bounds, alive flags, at most one monster per Tokyo slot, Bay only with 5+ alive,
no transient phase ever visible, no cards lost or duplicated.

## Multiplayer protocol

One WebSocket per player at `ws://<host>:8080/ws`. Messages are JSON with a `type` discriminator
(see `core/.../net/Protocol.kt`).

| Client → server | Purpose |
| --- | --- |
| `create_room`, `join_room`, `resume` | Enter a room (resume = reclaim your seat with the session token) |
| `set_ready`, `select_character` | Lobby |
| `add_bot`, `kick`, `start_game`, `return_to_lobby` | Host controls |
| `intent` | A `GameIntent`: `roll_dice`, `toggle_hold`, `resolve_dice`, `tokyo_decision`, `buy_card`, `reset_market`, `end_turn` |
| `request_snapshot`, `leave` | |

| Server → client | Purpose |
| --- | --- |
| `joined` | Room code, your player id and a session token for reconnecting |
| `lobby` | Seats, ready flags, host, room status |
| `snapshot` | Full `GameStatePayload` (first sync, after reconnect or on request) |
| `delta` | Only the top-level payload fields that changed, tagged with `baseRevision → revision` |
| `intent_rejected`, `error`, `kicked` | |

Deltas are applied only on top of the exact base revision; on a gap the client asks for a snapshot.
If a player disconnects mid-game their seat is kept: after a 20 s grace period the bot autopilot plays
for them until they come back (`resume`). Players who leave for good are replaced by the autopilot.
Rooms nobody is connected to are closed after 10 minutes.

## Building and running

Requirements: JDK 17+ (the build targets Java 17 bytecode), Android SDK for the app.

```bash
# All JVM modules (engine, client, server) and their tests; no Android SDK needed
./gradlew -PmonsterMayor.skipAndroid=true test

# Run the room server (PORT defaults to 8080)
./gradlew -PmonsterMayor.skipAndroid=true :server:run

# Android app (needs the Android SDK / local.properties)
./gradlew :app:installDebug
```

The app's default server URL is `ws://10.0.2.2:8080/ws`, which is your computer as seen from the
Android emulator. For real phones on the same Wi-Fi, set the server URL under *Server settings* on the
home screen to `ws://<your-computer-ip>:8080/ws`.

## Tech stack

Kotlin 2.2, coroutines/StateFlow, kotlinx.serialization, Ktor 3 (server + client WebSockets),
Jetpack Compose + Material 3, Navigation Compose, Koin, Room (KSP), Gradle 8.14 with a version catalog.
