# TheBridge — Developer Guide

## Project purpose

TheBridge is a Paper 1.21.x minigame plugin for The Bridge (players score by crossing a bridge into the opponent's goal area). This repo currently contains Stage 1 only: arena infrastructure — no gameplay logic exists yet.

---

## Architecture

```
TheBridgePlugin               — entry point; wires all managers
  ArenaManager                — in-memory arena registry, CRUD
    ArenaStorage              — YAML persistence (arenas.yml)
    Arena                     — model: spawns, goals, region, state
    ArenaState                — enum: DISABLED, WAITING, STARTING, IN_GAME, RESETTING
  SchematicManager            — FAWE save/restore (async)
  BridgeCommand               — /bridge subcommands + tab completion
```

### Arena model

`Arena.java` holds every piece of per-arena data:

| Field | Purpose |
|---|---|
| `id` | Unique lowercase identifier; immutable after creation |
| `redSpawn`, `blueSpawn` | Team spawn points used at match start |
| `lobbySpawn` | Where players wait before the match |
| `redGoal`, `blueGoal` | Locations that trigger scoring/win events |
| `pos1`, `pos2` | FAWE region corners for save/reset |
| `schematicName` | Filename (without extension); defaults to `id` |
| `enabled` | Whether the arena is open for play |
| `state` | Current lifecycle phase (see `ArenaState`) |

`isFullyConfigured()` returns true only when every required location is set. `hasRegion()` checks only pos1/pos2 (the minimum for save/reset).

### ArenaState lifecycle (current + future)

```
DISABLED → WAITING → STARTING → IN_GAME → RESETTING → WAITING
```

Stage 1 only sets `RESETTING` (during a `/bridge reset`). The other states are reserved for the match-session system in Stage 2.

### SchematicManager — reset system detail

This is the most critical component. The reset system must be reliable because game sessions depend on a clean arena after every match.

**Save flow:**
1. Compute `min` and `max` block vectors from `pos1`/`pos2`.
2. Build a `CuboidRegion` from those vectors.
3. Copy the region into a `BlockArrayClipboard`. The clipboard's origin is automatically set to `region.getMinimumPoint()`.
4. Write to `<schematics>/<name>.schem` using `BuiltInClipboardFormat.FAST`.

**Reset flow:**
1. Read the `.schem` file; format is auto-detected via `ClipboardFormats.findByFile()` (falls back to SPONGE_V2 if detection fails).
2. Paste at `clipboard.getOrigin()` — which is the min block recorded at save time — so the arena lands in exactly the same position.
3. `ignoreAirBlocks(false)` ensures air blocks in the schematic overwrite anything that was built during the match.

**Threading:** Both operations use `CompletableFuture.runAsync`. FAWE manages chunk locking and queuing internally. Callbacks return to the main thread via `Bukkit.getScheduler().runTask()` before sending messages to the command sender.

**Guard rails:**
- `/bridge save` requires `hasRegion()` — both pos1 and pos2 must be set.
- `/bridge reset` requires the `.schem` file to exist — forces admins to save before resetting.
- `/bridge reset` checks `arena.getState() == RESETTING` to prevent a double-reset if one is already in progress.

### ArenaStorage

All arenas are stored in `arenas.yml`. Each arena is a section under `arenas.<id>`. Location fields are written as nested `world/x/y/z/yaw/pitch` values; null fields are omitted (set to null, which removes them from YAML). On load, missing or unknown worlds result in null Locations — the admin must re-run the relevant `set*` command after a world rename.

---

## Build rules

Gradle 8.x is **not compatible with Java 25**. All builds use `build.sh`.

```bash
# Required jars in libs/ (copy from Pinpoint/libs + download FAWE):
#   paper-api.jar, fawe-bukkit.jar, adventure-api.jar, adventure-key.jar,
#   examination-api.jar, guava.jar, bungeecord-chat.jar, jetbrains-annotations.jar

bash build.sh
# Output: build/TheBridge-1.0.0.jar
```

`build.gradle.kts` exists for IDE dependency resolution only.

---

## Adding a new arena field (future stages)

1. Add the field + getter/setter to `Arena.java`.
2. Add read/write calls in `ArenaStorage.loadAll()` / `saveArena()` using the `readLocation` / `writeLocation` helpers (or add a new helper for non-Location types).
3. Add the corresponding `/bridge set*` subcommand in `BridgeCommand.java` — add a new `Field` enum value, a case in the dispatch switch, and a `handleSetLoc` call (or a new handler if it's not a Location type).

## Adding game sessions (Stage 2)

The `ArenaState` enum already has `WAITING`, `STARTING`, `IN_GAME` placeholders. A future `MatchManager` (or `SessionManager`) will:
- Query `arenaManager.getAllArenas()` filtered by `isEnabled()` + `isFullyConfigured()` + state `WAITING`
- Assign players to a session, set state to `STARTING`/`IN_GAME`
- Call `schematicManager.resetArena(arena)` after the session ends, then set state back to `WAITING`

Nothing in Stage 1 needs to change to support this pattern.

---

## Workflow rules

Before ending any work session:

1. Increment the version in `plugin.yml`, `build.sh`, and `build.gradle.kts`.
2. Run `bash build.sh` and confirm the jar is produced with no errors.
3. Update `CHANGELOG.md` with a dated entry.
4. Commit all modified source files and the new jar together.
