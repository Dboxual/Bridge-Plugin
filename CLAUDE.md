# TheBridge — Developer Guide

## Project purpose

TheBridge is a Paper 1.21.x minigame plugin for The Bridge (players score by stepping on the opponent's goal block). Stage 2 is complete: full 1v1 match flow including queue signs, countdowns, goal detection, arena resets between rounds, and disconnect handling.

---

## Architecture

```
TheBridgePlugin               — entry point; wires all managers + registers listeners
  ArenaManager                — in-memory arena registry, CRUD
    ArenaStorage              — YAML persistence (arenas.yml), includes sign locations
    Arena                     — model: spawns, goal regions, reset region, signs, state
    ArenaState                — enum: DISABLED, WAITING, STARTING, IN_GAME, RESETTING
  SchematicManager            — FAWE async save/restore with console debug logging
  MatchManager                — tracks active BridgeMatch instances; plugin-teleport flag set
    BridgeMatch               — single match: countdown, scoring, reset, forfeit; score cooldown
  QueueManager                — per-arena player queues; starts match when 2 queued
  WandManager                 — per-player pos1/pos2 selections for goal region setup
  BridgeCommand               — /bridge admin subcommands + tab completion
  SignListener                — right-click queue sign → join/leave queue
  GoalListener                — PlayerMoveEvent → goal region detection (block-change only)
  MatchListener               — PlayerQuit/WorldChange/Teleport → queue leave or match forfeit
  WandListener                — left/right-click with wand → pos1/pos2 selection
```

---

## Arena model

`Arena.java` holds every piece of per-arena data:

| Field | Purpose |
|---|---|
| `id` | Unique lowercase identifier; immutable after creation |
| `redSpawn`, `blueSpawn` | Team spawn points used at match start |
| `lobbySpawn` | Where players are sent after the match ends |
| `redGoalPos1/2`, `blueGoalPos1/2` | Two-corner regions; scoring fires when a player's block position is inside the opponent's region |
| `pos1`, `pos2` | FAWE region corners for save/reset |
| `schematicName` | Filename (without extension); defaults to `id` |
| `enabled` | Whether the arena is open for play |
| `state` | Current lifecycle phase (see `ArenaState`) |
| `signs` | List of registered queue sign locations |

Each `Location` stores its own world. There is no global arena world — the plugin works in any world automatically.

`isFullyConfigured()` returns true when every required location is set. `hasRegion()` checks only pos1/pos2.

---

## ArenaState lifecycle

```
DISABLED → WAITING → IN_GAME → RESETTING → WAITING (loop)
                            ↓
                          ENDED (match over) → WAITING
```

On startup, arenas that are fully configured and have a saved schematic are set to `WAITING`. Signs are only clickable in `WAITING` state.

---

## Match flow

1. Two players right-click a queue sign → `QueueManager.join()` → `MatchManager.createMatch()` → `BridgeMatch.start()`
2. `start()`: clears inventories, teleports to spawns, broadcasts start message, runs countdown → `state = ACTIVE`
3. `GoalListener` fires on every block-change move; calls `match.onGoalEntered(player)` if state is ACTIVE
4. `onGoalEntered()`: verifies the player is on the correct goal block (`isSameBlock`), increments score
5. If no win: `resetAndContinue()` — calls `SchematicManager.resetArena()` async, teleports back, runs countdown
6. If win: `endMatch()` — announces winner, clears inventories, teleports both to `lobbySpawn`, sets arena back to `WAITING`
7. Disconnect: `MatchListener` → `match.onPlayerDisconnect()` → `endMatch(opponent)`

---

## SchematicManager — reset system detail

**Save flow:**
1. Derive world from `arena.getPos1().getWorld()` — no config lookup.
2. Compute `min`/`max` block vectors from `pos1`/`pos2`.
3. Copy region into `BlockArrayClipboard` (origin = min point).
4. Write to `<schematics>/<name>.schem` using `BuiltInClipboardFormat.FAST`.

**Reset flow:**
1. Derive world from `arena.getPos1().getWorld()`.
2. Read `.schem`; format auto-detected via `ClipboardFormats.findByFile()`.
3. Paste at `clipboard.getOrigin()` (the saved min point) with `ignoreAirBlocks(false)`.

Both operations use `CompletableFuture.runAsync`. Callbacks return to the main thread via `Bukkit.getScheduler().runTask()`.

---

## ArenaStorage

All arenas stored in `arenas.yml` under `arenas.<id>`. Location fields: `world/x/y/z/yaw/pitch`. Signs: list of `{world, x, y, z}` maps. Null fields omitted. Unknown worlds on load produce null Locations with a warning.

---

## Build rules

Gradle 8.x is **not compatible with Java 25**. All builds use `build.sh`.

```bash
# Required jars in libs/:
#   paper-api.jar, fawe-bukkit.jar, adventure-api.jar, adventure-key.jar,
#   examination-api.jar, guava.jar, bungeecord-chat.jar, jetbrains-annotations.jar
# Copy from Pinpoint/libs/ + place FAWE as fawe-bukkit.jar

bash build.sh
# Output: build/TheBridge-1.1.1.jar
```

Classpath separator is `;` (Windows javac). `build.gradle.kts` exists for IDE dependency resolution only.

---

## Workflow rules

Before ending any work session:

1. Increment the version in `plugin.yml`, `build.sh`, and `build.gradle.kts`.
2. Run `bash build.sh` and confirm the jar is produced with no errors.
3. Update `CHANGELOG.md` with a dated entry.
4. Commit all modified source files and the new jar together.
