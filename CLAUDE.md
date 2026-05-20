# TheBridge — Developer Guide

## Project purpose

TheBridge is a Paper 1.21.x minigame plugin for The Bridge (players score by stepping into the opponent's goal region). Stage 2 is complete: full 1v1 match flow including queue signs, loadout, freeze countdowns, goal detection, soft per-round resets (no FAWE), full schematic reset at match end, sidebar scoreboard, and disconnect handling.

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
    BridgeMatch               — single match: countdown, scoring, soft reset, full end-reset, forfeit
  QueueManager                — per-arena player queues; starts match when 2 queued
  WandManager                 — per-player pos1/pos2 selections; showSelectionOutline() draws lime particles
  BridgeCommand               — /bridge admin subcommands + tab completion; setarena uses wand selection to set reset region; removesign unregisters a sign by look-target
  SignListener                — right-click queue sign → join/leave queue
  GoalListener                — PlayerMoveEvent (HIGHEST priority) → freeze enforcement + goal detection
  MatchListener               — PlayerQuit/WorldChange/Teleport → queue leave or match forfeit
  WandListener                — PlayerInteractEvent (pos1/pos2), BlockBreakEvent, BlockDamageEvent
  BridgeKitListener           — PlayerItemConsumeEvent (gapple override), EntityShootBowEvent (arrow regen), EntityPickupItemEvent (block extra arrows)
```

---

## Arena model

`Arena.java` holds every piece of per-arena data:

| Field | Purpose |
|---|---|
| `id` | Unique lowercase identifier; immutable after creation |
| `redSpawn`, `blueSpawn` | Team spawn points used at match start and soft resets |
| `lobbySpawn` | Where players are sent after the match ends |
| `redGoalPos1/2`, `blueGoalPos1/2` | Two-corner regions; scoring fires when a player's block position is inside the opponent's region |
| `redRelease1/2`, `blueRelease1/2` | Two-corner regions defining the floor blocks removed each round to drop players into the arena. If not set, a 3×3 fallback at Y−1 under each spawn is used and a console warning is logged. |
| `pos1`, `pos2` | FAWE region corners for end-of-match save/reset |
| `schematicName` | Filename (without extension); defaults to `id` |
| `enabled` | Whether the arena is open for play |
| `state` | Current lifecycle phase (see `ArenaState`) |
| `signs` | List of registered queue sign locations |

Each `Location` stores its own world. There is no global arena world.

`isFullyConfigured()` returns true when every required location is set. `hasRegion()` checks only pos1/pos2.

---

## ArenaState lifecycle

```
DISABLED → WAITING → IN_GAME → RESETTING (FAWE end-of-match) → WAITING (loop)
```

On startup, arenas that are fully configured and have a saved schematic are set to `WAITING`. Signs are only clickable in `WAITING` state.

---

## Match flow

1. Two players right-click a queue sign → `QueueManager.join()` → `MatchManager.createMatch()` → `BridgeMatch.start()`
2. `start()`: captures spawn floor blocks, clears inventories, teleports to spawns, gives loadout, freezes players, runs 5-second countdown → `state = ACTIVE`
3. `GoalListener.onMove` fires at HIGHEST priority on every move event:
   - If player is frozen → redirect XYZ back to FROM (preserve yaw/pitch), return
   - If XYZ unchanged (rotation only) → return early
   - If state is not ACTIVE → return
   - Otherwise: `match.onGoalEntered(player, event.getFrom(), event.getTo())`
   - **Do NOT reinstate `hasChangedBlock()`** — it causes fast-falling players to skip over the goal region entirely.
4. `onGoalEntered(Player, Location from, Location to)`: calls `touchesOpponentGoal()` which sweeps the player's 3×3 XZ footprint across every Y block from `floor(from.Y)` to `floor(to.Y)` (cap 16). First hit → score. Silent on miss (no logging). Logs once to console on score.
5. If no win: `softReset()` — teleports both to spawn, heals, restores floor, re-gives loadout, freezes, 3-second countdown, drops floor → `state = ACTIVE`
6. If win: `endMatch()` — announces winner with title+sound, clears inventories, teleports to lobby, clears arena entities, removes match, then async FAWE reset → `state = WAITING`
7. Disconnect: `MatchListener` → `match.onPlayerDisconnect()` → `endMatch(opponent)`

---

## Reset system

Two completely separate systems:

### Soft reset (between rounds)
- No FAWE operation
- Triggered from `BridgeMatch.softReset()` after a non-winning goal
- Sequence: teleport → heal/clear effects → restore release zone → give loadout → freeze → 3-second countdown → `clearReleaseZones()` → unfreeze → ACTIVE
- Captured `BlockSnapshot` list is built once at match start; re-placed before each round; set to AIR after countdown

### Match-start release
- At the end of the initial 5-second countdown, `clearReleaseZones()` is also called (same as soft-reset end).
- **Do not remove this call** — without it, players stand on solid blocks for the entire match.

### Full reset (match end only)
- FAWE `resetArena()` async paste at `clipboard.getOrigin()`
- Triggered from `BridgeMatch.endMatch()` after the winning goal or forfeit
- Arena state: `IN_GAME` → `RESETTING` (during paste) → `WAITING` (after paste)

**Soft reset and full reset must remain completely separate.**

---

## Loadout

Given at match start and on every soft reset (`giveLoadout(UUID, Team)`):

| Slot | Item |
|---|---|
| 0 | Iron Sword |
| 1 | Bow |
| 2 | 32× Team-colored Terracotta |
| 3 | 3× Golden Apple |
| 4 | Diamond Pickaxe (Efficiency II) |
| 8 | 1× Arrow |
| Armor | Dyed leather (RED or BLUE) |

Players also receive 20 HP, 20 food, 20 saturation on each load.

`giveLoadout` always calls `cancelArrowRegen(uid)` first — the fresh kit includes 1 arrow so any pending regen would cause a double-stack.

## Kit mechanics

### Golden apple override (`BridgeKitListener.onConsume`)
`PlayerItemConsumeEvent` is cancelled for `GOLDEN_APPLE` when the player is in `ACTIVE` state. The item is manually consumed (1 removed from the hand stack) and Bridge-specific effects applied: `setHealth(20.0)` + `setAbsorptionAmount(4.0)`. Vanilla Regeneration II and slow-absorption tick do not fire. Outside an active Bridge match the event is not touched.

### Arrow regeneration
- `BridgeMatch.arrowRegenTasks: Map<UUID, BukkitRunnable>` — pending regen tasks keyed by player.
- `scheduleArrowRegen(UUID)` — cancels any existing task, schedules a 70-tick (3.5 s) delayed task that gives back 1 arrow if the player has none and the match has not ended.
- `cancelArrowRegen(UUID)` — called from `giveLoadout` to prevent double-stacking when kit is refreshed.
- `cancelAllArrowRegens()` — called from `endMatch` to clean up all pending tasks.
- `BridgeKitListener.onPickupItem` cancels `EntityPickupItemEvent` for arrows in any match phase (COUNTDOWN / ACTIVE / RESETTING) so floor arrows cannot be picked up.

---

## Release zone mechanic

`captureReleaseZones()` runs at match start. If `arena.hasRedRelease()` / `hasBlueRelease()` is true, the full configured cuboid is snapshotted block-by-block into `redReleaseSnapshot` / `blueReleaseSnapshot`. Otherwise the 3×3 fallback at Y−1 under each spawn is used and a `WARNING` is logged to console.

`clearReleaseZones()` is called in **two places**:
1. At the end of the initial match-start countdown (inside `startMatchCountdown` onFinish).
2. At the end of each soft-reset countdown (inside `startSoftCountdown` onFinish).

`restoreReleaseZones()` is called at the start of `softReset()` so the floor is solid before players land.

The schematic reset at match end restores them permanently via FAWE.

Commands: `/bridge setredrelease <arena>` and `/bridge setbluerelease <arena>` — use the Bridge wand to select the region (same flow as `/bridge setredgoal`), then run the command. Stored in `Arena` as `redRelease1/2`, `blueRelease1/2`. Persisted in `arenas.yml` under `red-release-1/2`, `blue-release-1/2`.

## Freeze mechanic

`BridgeMatch` maintains a `frozenPlayers: Set<UUID>`. While a UUID is in this set, `GoalListener.onMove` redirects any XYZ change back to `event.getFrom()` (preserving yaw/pitch). Gravity still applies — only voluntary movement is blocked. The freeze is used during both the match-start countdown and the soft-reset countdown.

---

## Debug mode

`settings.debug: false` in `config.yml`. When `true`, release zone messages (CLEARED / RESTORED / SKIPPED) are also broadcast in-game to all online `bridge.admin` players. Console always receives these messages regardless. Goal miss/cooldown events are never logged (they fire too frequently with the always-on listener).

## Scoreboard

Created in `setupScoreboard()` at match start; assigned to both players via `player.setScoreboard(sb)`. Updated in `updateScoreboard()` after each goal. Cleared (players set to main scoreboard) in `endMatch()`.

`objective.numberFormat(NumberFormat.blank())` (Paper 1.21 API — `io.papermc.paper.scoreboard.numbers.NumberFormat`) hides the numeric scores on the right side of the sidebar. Do not remove this call or the numbers reappear.

Layout (sidebar, no right-side numbers):
```
The Bridge   ← gold bold title
Arena: <id>
<Red name>: <score>
<Blue name>: <score>
First to <N>
```

---

## SchematicManager — reset system detail

**Save flow:**
1. Derive world from `arena.getPos1().getWorld()`.
2. Compute `min`/`max` block vectors from `pos1`/`pos2`.
3. Copy region into `BlockArrayClipboard` (origin = min point).
4. Write to `<schematics>/<name>.schem` using `BuiltInClipboardFormat.FAST`.

**Reset flow:**
1. Read `.schem`; format auto-detected via `ClipboardFormats.findByFile()`.
2. Paste at `clipboard.getOrigin()` (the saved min point) with `ignoreAirBlocks(false)`.

Both operations use `CompletableFuture.runAsync`. Callbacks return to main thread via `Bukkit.getScheduler().runTask()`.

---

## ArenaStorage

All arenas stored in `arenas.yml` under `arenas.<id>`. Location fields: `world/x/y/z/yaw/pitch`. Signs: list of `{world, x, y, z}` maps. Null fields omitted. Unknown worlds on load produce null Locations with a warning.

---

## `/bridge setarena` — wand-based reset region

`/bridge setarena <arena>` saves the current Bridge wand selection (pos1/pos2) as the arena's FAWE reset region (stored in `arena.pos1` / `arena.pos2`). This is the recommended replacement for the legacy `/bridge setpos1` + `/bridge setpos2` workflow. Both legacy commands are retained and unchanged.

Flow: run `/bridge wand` → left-click corner 1 → right-click corner 2 → `/bridge setarena <arena>` → `/bridge save <arena>`.

Validation: both wand positions must be set and in the same world.

---

## Build rules

Gradle 8.x is **not compatible with Java 25**. All builds use `build.sh`.

```bash
# Required jars in libs/:
#   paper-api.jar, fawe-bukkit.jar, adventure-api.jar, adventure-key.jar,
#   examination-api.jar, guava.jar, bungeecord-chat.jar, jetbrains-annotations.jar
# Copy from Pinpoint/libs/ + place FAWE as fawe-bukkit.jar

bash build.sh
# Output: build/TheBridge-1.2.4.jar
```

Classpath separator is auto-detected: `;` on Windows (Git Bash/MSYS), `:` on macOS/Linux. `build.gradle.kts` exists for IDE dependency resolution only.

---

## Workflow rules

Before ending any work session:

1. Increment the version in `plugin.yml`, `build.sh`, and `build.gradle.kts`.
2. Run `bash build.sh` and confirm the jar is produced with no errors.
3. Update `CHANGELOG.md` with a dated entry.
4. Commit all modified source files and the new jar together.
