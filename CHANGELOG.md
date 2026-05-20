# Changelog

## v1.3.2 — 2026-05-19
### Added — XP bar arrow-regen countdown timer

The arrow regen system now drives the XP bar as a visual countdown:
- When a player shoots their arrow, the XP bar immediately fills to 1.0 (full) and drains smoothly to 0 over 70 ticks (3.5 s) as the regen counts down.
- When the arrow returns, the XP bar is restored to the player's real XP level and progress.
- **Single combined task:** the previous `runTaskLater(70L)` is replaced by a `runTaskTimer(1L, 1L)` that decrements `exp` by `1/70` each tick (`p.setExp(ticksLeft / 70.0f)`), then gives the arrow and restores XP on tick 70.
- Player's real XP (`level` + `exp`) is saved to `savedXpLevels` / `savedXpProgress` on timer start and restored on any cancellation path: `cancelArrowRegen` (loadout refresh, arrow pickup), `cancelAllArrowRegens` (match end), or natural timer completion.
- XP is **not** touched outside Bridge matches.

---

## v1.3.1 — 2026-05-19
### Fixed — golden apple effects not applying; arrow pickup blocked for players with 0 arrows

**Golden apple fix (`BridgeKitListener.onConsume`):**
- Root cause: the event was cancelled (preventing vanilla item consumption) AND the apple was manually removed. In Paper 1.21 `getItemInMainHand()` returns the actual stack reference but the two-step cancel+remove approach was fighting vanilla, leaving the apple in inventory with no effects applied.
- Fix: no longer cancel the consume event — vanilla handles item removal reliably. A 1-tick delayed `runTaskLater` then sets `health=20.0` and `absorptionAmount=4.0` (2 yellow absorption hearts) after vanilla has finished. Vanilla Regen II is still active but harmless at full health.

**Arrow pickup fix (`BridgeKitListener.onPickupItem`):**
- Root cause: all arrow pickups were blocked unconditionally during any match phase, preventing players from ever retrieving dropped arrows.
- Fix: pickup is now allowed when the player currently has 0 arrows. If they pick one up, `match.cancelArrowRegen(uid)` is called so the pending regen task doesn't stack a second arrow on top. Pickup is still blocked when the player already has 1+ arrows (cap enforcement).

---

## v1.3.0 — 2026-05-19
### Added — Bridge kit mechanics (golden apple override, arrow regen, diamond pickaxe)

**Golden apple override (`BridgeKitListener`):**
- Eating a `GOLDEN_APPLE` during an active Bridge match cancels the vanilla event (Regeneration II + slow Absorption tick) and instead instantly sets the player to full health and gives 2 absorption hearts (4 HP via `setAbsorptionAmount(4.0)`).
- The item is manually consumed (1 removed from whichever hand holds it) so the player doesn't keep the apple.
- Players outside an active Bridge match see normal vanilla behavior.

**Arrow regeneration:**
- Each Bridge player is capped at 1 arrow. `BridgeKitListener.onShootBow` (`EntityShootBowEvent`) detects when an arrow is fired during an active match and calls `BridgeMatch.scheduleArrowRegen(UUID)`.
- `scheduleArrowRegen` schedules a 70-tick (3.5 s) delayed task. When it fires, if the player is still in the match and has no arrows, 1 arrow is added to their inventory.
- `cancelArrowRegen(UUID)` is called inside `giveLoadout` — a soft reset or respawn already gives a fresh arrow so the regen must be cancelled to prevent stacking above 1.
- `cancelAllArrowRegens()` is called at the top of `endMatch` to clean up all pending tasks.
- `BridgeKitListener.onPickupItem` (`EntityPickupItemEvent`) cancels arrow pickups during COUNTDOWN, ACTIVE, and RESETTING phases so floor arrows cannot be collected.

**Updated kit (`giveLoadout`):**
- Added Diamond Pickaxe with Efficiency II at slot 4.
- Full slot layout: 0 Iron Sword, 1 Bow, 2 Team Blocks (×32), 3 Golden Apple (×3), 4 Diamond Pickaxe (Eff II), 8 Arrow (×1), armor dyed leather.

---

## v1.2.9 — 2026-05-18
### Fixed — Multiverse-Inventories timing: survival inventory no longer wiped on match join

**Root cause:** `BridgeMatch.start()` called `p.getInventory().clear()` and `saveInventory()` while the player was still in `survival_world` (the sign world). Multiverse-Inventories had not yet switched the per-world inventory context, so these operations ran on the survival inventory — corrupting or erasing it. The v1.2.8 `SavedInventory` snapshot mechanism made this worse by snapshotting the wrong world's inventory.

**Fix:**
- Removed `SavedInventory` record, `savedInventories` map, and all save/restore helper methods from `BridgeMatch`. Multiverse-Inventories manages cross-world inventory persistence automatically — TheBridge must not interfere.
- `start()` now teleports players to the arena lobby (bridge\_world) **first**, then schedules a 2-tick delayed task. Inside that task, `player.getWorld()` is confirmed to equal the bridge world before `giveLoadout()` clears and assigns kit. This guarantees all inventory operations occur on the bridge-world inventory, never the survival inventory.
- `endMatch()` calls `p.getInventory().clear()` **before** teleporting to lobby — while the player is still in bridge\_world. Multiverse-Inventories then saves the empty bridge inventory and restores the survival inventory automatically on the next world switch.
- If no lobby is configured, the initial teleport goes directly to the arena spawn (also bridge\_world), which still triggers the Multiverse world-inventory switch.

**Debug logging:** `Inventory CLEARED (game kit removed)` is now emitted in `endMatch()` for each online player.

---

## v1.2.8 — 2026-05-18
### Fixed — survival inventory preserved across match sessions

**Root cause:** `BridgeMatch.start()` called `p.getInventory().clear()` without first saving the player's survival inventory, and `endMatch()` called `p.getInventory().clear()` again without ever restoring it. Any items the player had before joining a match were permanently discarded when they left.

**Fix — `SavedInventory` snapshot in `BridgeMatch`:**
- New private `SavedInventory` record captures slots 0-35, armor (4 slots), offhand, XP level + progress, health, food, and saturation.
- `saveInventory(Player)` — called in `start()` **before** any clearing. Deep-copies every `ItemStack` so in-game mutations cannot corrupt the snapshot.
- `restoreInventory(UUID)` — called in `endMatch()` in place of the old blind `clear()`. Clears the game kit first, then restores all saved fields. Clamps health to `getMaxHealth()` to guard against attribute changes mid-match.
- If the player is offline when the match ends, a debug message is logged and the saved data is discarded (the server's own persistence handles their inventory state).
- If no snapshot exists for a player (e.g. they joined after match start), the game kit is still cleared but no restore is attempted.

**Debug logging added** (always to console; in-game admin messages gated behind `settings.debug: true`):
- `Inventory SAVED` — player name, filled slot counts, XP level
- `Inventory CLEARED (match start)` — before first teleport
- `Inventory CLEARED (loadout)` — each time `giveLoadout()` runs (includes soft-resets)
- `Loadout APPLIED` — after kit is given, shows player + team + arena
- `Inventory CLEARED (game kit removed)` — in `restoreInventory()` before restoring
- `Inventory RESTORED` — slot counts and XP level after restore
- `Inventory RESTORE SKIPPED` — when player is offline or snapshot is absent

---

## v1.2.7 — 2026-05-18
### Added — `/bridge removesign`

- **New command:** `/bridge removesign <arena>` unregisters the sign the admin is looking at from the arena's sign list.
- The physical sign block is preserved; only its text is cleared (all four lines on both faces set to blank).
- Errors clearly if: the targeted block is not a sign, or the sign is not registered to the specified arena.
- Arena data is saved immediately after removal.
- Tab completion and `/bridge` usage list updated.

## v1.2.6 — 2026-05-18
### Added — `/bridge setarena` wand-based reset region setup

- **New command:** `/bridge setarena <arena>` saves the current Bridge wand selection (pos1/pos2) as the arena's reset region. Mirrors the wand-based flow already used for goals and release zones.
- Validates that both wand corners are set and in the same world before saving.
- Reports the region dimensions (X×Y×Z, total blocks) and reminds the admin to run `/bridge save <arena>` next.
- Updated `/bridge save` error message to reference `/bridge setarena` instead of the raw setpos1/setpos2 commands.
- Updated `/bridge wand` hint message to list `setarena` alongside the other wand commands.
- `/bridge setpos1` and `/bridge setpos2` are retained as legacy/manual alternatives — no behaviour change.
- Tab completion updated; `/bridge` usage list shows `setarena (recommended)` and marks setpos1/setpos2 as `(legacy)`.

## v1.2.5 — 2026-05-18
### Fixed — goal detection rewrite + debug spam removed

#### 1. Goal detection: footprint sweep
**Root cause of remaining scoring failures:** `hasChangedBlock()` only fires once per tick regardless of fall speed. A player falling at terminal velocity (~3.9 blocks/tick) can skip from above the goal region to below it without a single block-change event landing *inside* the region. Additionally, the previous single-point check against `event.getTo()` failed whenever the player's centre was just outside the region boundary but their body overlapped it.

**Fix — `onGoalEntered(Player, Location from, Location to)`:**
- `GoalListener` no longer gates on `hasChangedBlock()`. It fires on every XYZ position change (rotation-only events are still skipped with a single coordinate comparison).
- Both the FROM and TO locations from the move event are forwarded to `onGoalEntered`.
- `touchesOpponentGoal()` sweeps every Y block between `floor(from.Y)` and `floor(to.Y)` (capped at 16 levels), and for each Y checks a **3×3 XZ footprint** centred on the player's feet. The first hit in any of those up-to-144 block positions triggers a score. A single Location object is reused across iterations to avoid allocation overhead.
- Rules unchanged: RED player touching BLUE goal = RED scores; BLUE touching RED = BLUE scores.

#### 2. Debug spam removed
- `Goal MISS` and `Goal COOLDOWN` log lines are gone. With the new always-on listener these would fire ~20 times/second during normal movement.
- Successful goals still log once to console: `[Bridge] GOAL: <name> (RED/BLUE) → N-N`.
- Release zone messages (CLEARED, RESTORED, SKIPPED) still log to console always; in-game admin messages for *all* debug calls are now gated behind `settings.debug: false` in `config.yml`.

#### 3. `settings.debug` config option
Added to `config.yml` (default `false`). When set to `true`, release zone and other admin debug messages are also broadcast in-game to online players with `bridge.admin`.

## v1.2.4 — 2026-05-18
### Fixed — three core gameplay bugs

#### 1. Release zone never opened at match start
- **Root cause:** `clearReleaseZones()` was only called at the end of the soft-reset countdown, never at the end of the initial match-start countdown. Players stood on solid blocks the whole match.
- **Fix:** `clearReleaseZones()` is now called inside `startMatchCountdown`'s `onFinish` callback, immediately before unfreezing players. Floor disappears, players fall into arena.

#### 2. Scoring never registered
- **Root cause:** `onGoalEntered` called `player.getLocation()`, which returns the *from* position during a `PlayerMoveEvent`. The new block (the goal) is at `event.getTo()`, never `player.getLocation()`. Every goal check was against the position the player had *just left*, so it always missed.
- **Fix:** `GoalListener` now passes `event.getTo()` into `onGoalEntered(Player, Location)`. Goal region checks use the TO location.

#### 3. Scoreboard showing right-side numbers (6, 5, 4, 3, 2, 1)
- **Fix:** `objective.numberFormat(NumberFormat.blank())` (Paper 1.21 API) hides all score numbers for the sidebar objective. Scoreboard now shows only arena name, red/blue player scores, and first-to-N.

#### Debug messages added
- **Release:** In-game admin message + console log on every release clear: `Release CLEARED: arena=X team=RED/BLUE world=W blocks=N`. If 0 blocks or no zone configured, prints the exact reason.
- **Release restore:** `Release RESTORED: arena=X red=N blue=N blocks` on each soft-reset restore.
- **Scoring:** In-game admin message on every goal check: `Goal SCORED` (with team and running score) or `Goal MISS` (with team, coordinates, inRedGoal/inBlueGoal flags, and human-readable reason). `Goal COOLDOWN` when the per-player 2-second cooldown blocks a re-score.
- Messages go to server console (`[Bridge] ...`) and to all online players with `bridge.admin` permission.

## v1.2.3 — 2026-05-18
### Fixed — bug-fix patch for the 1v1 match loop

#### A) Release zone debug logging
- Console now logs every capture (`[Bridge] Captured N red/blue release blocks`), every restore, and every clear so the sequence is visible in the server log.
- Fixed stale warning messages that still referenced the removed `/bridge setredrelease1/2` commands — they now reference the correct `/bridge setredrelease <arena>`.

#### B) Spawn assignment debug + defensive re-teleport
- `BridgeMatch.start()` now logs: `[Bridge] Match start — red: <name> → world(x,y,z)  blue: <name> → world(x,y,z)` so spawn-assignment issues are immediately visible.
- Added a 1-tick deferred re-teleport immediately after match start so players reach their correct spawn even if the initial teleport was dropped by the engine on the same tick.

#### C) Void/death level — `/bridge setvoidlevel <arena>`
- New command stores the admin's current Y as the arena's void Y. Shown in `/bridge debug`. Persisted in `arenas.yml` under `void-level`.
- A repeating 5-tick task checks both players during `ACTIVE` state. If a player's Y ≤ void Y they are teleported to their team spawn, effects cleared, loadout restored — match stays ACTIVE with no countdown.

#### D) Death respawn
- `MatchListener` now handles `PlayerDeathEvent` (suppress drops, keep inventory/XP) and `PlayerRespawnEvent` (set respawn location to team spawn). One tick after respawn the loadout is re-given via `BridgeMatch.respawnPlayer()`.

#### E) Scoring debug logging
- `onGoalEntered` now logs on goal-cooldown rejections (with elapsed ms) and on goal-region misses (`isRed`, `isBlue`, `inRedGoal`, `inBlueGoal` flags) so scoring failures are traceable in the server console.

#### F) `/bridge debug` update
- Now shows `Void level: Y=<n>` (or `not set`) between the release zone lines and the schematic line.

## v1.2.2 — 2026-05-18
### Changed — release zone UX aligned with wand system
- **`/bridge setredrelease <arena>`** and **`/bridge setbluerelease <arena>`** replace the four individual corner commands. Both read the current Bridge wand selection (pos1 + pos2) and save the cuboid region as the team's release zone — identical UX to `/bridge setredgoal` and `/bridge setbluegoal`.
- **Removed:** `/bridge setredrelease1`, `/bridge setredrelease2`, `/bridge setbluerelease1`, `/bridge setbluerelease2`.
- Internal storage unchanged: `Arena.redRelease1/2` and `blueRelease1/2` fields, YAML keys `red-release-1/2` and `blue-release-1/2`. Existing arena configs continue to work without migration.
- Wand hint message updated to also mention `/bridge setredrelease` and `/bridge setbluerelease`.

## v1.2.1 — 2026-05-18
### Added — configurable release zones
- **`/bridge setredrelease1 <arena>`** and **`/bridge setredrelease2 <arena>`** — set the two corners of the red team's release zone (the floor blocks removed each round to drop players into the arena). Location is taken from where the admin is standing.
- **`/bridge setbluerelease1 <arena>`** and **`/bridge setbluerelease2 <arena>`** — same for blue team.
- Release zones are cuboid regions defined by two corner locations, stored per-team. Any shape and size the admin selects is supported.

### Changed
- **Soft reset floor mechanic now uses configured release zones.** On match start the full cuboid region is captured (block-by-block snapshot). Before each round the snapshot is restored so players land on solid ground; after the countdown the blocks are set to AIR so players fall through. No schematic paste is involved.
- **3×3 fallback retained.** If a team's release zone is not configured, the old hardcoded 3×3 platform at Y−1 under the spawn is used as a fallback. A warning is logged to the server console at match start identifying which team is missing the zone configuration.
- **`/bridge list`** now shows a `[release]` / `[no release]` tag per arena indicating whether both release zones are configured.
- **`/bridge debug <arena>`** now prints the red and blue release zone corners (or `not set` with a `(fallback 3×3)` note).
- **`Arena`** — four new location fields: `redRelease1`, `redRelease2`, `blueRelease1`, `blueRelease2`. Helper methods `hasRedRelease()` / `hasBlueRelease()`.
- **`ArenaStorage`** — serialises the four new fields under keys `red-release-1`, `red-release-2`, `blue-release-1`, `blue-release-2`. Existing arenas without these keys load cleanly with null values (fallback applies).

## v1.2.0 — 2026-05-18
### Changed — core gameplay loop redesign
- **Soft reset replaces per-point schematic paste.** After each goal, no FAWE operation runs. Players are teleported back to spawns, healed, and re-given their loadout. The spawn platform blocks are restored so players land on solid ground, then removed after the 3-second countdown so players fall naturally into the arena. Bridges and blocks placed during the round remain — the arena resets only once, at match end.
- **Full schematic reset on match end.** FAWE paste now runs only in `endMatch()`. The arena is set to `RESETTING` while the paste runs, then to `WAITING` when done.
- **Standard loadout given at match start and after each soft reset.** Iron sword (slot 0), bow (slot 1), 32 colored terracotta blocks (slot 2), 3 golden apples (slot 3), 1 arrow (slot 8), and full dyed leather armor (red or blue). Players are healed to 20 HP and fed to 20 hunger on each re-load.
- **Player freeze during countdowns.** Players cannot change their XYZ position during the 5-second match-start countdown or the 3-second soft-reset countdown. Head rotation is still permitted. Implemented via `GoalListener.onMove` at `HIGHEST` priority — position changes are redirected back to the player's frozen location; goal detection is suppressed while frozen.
- **Spawn floor gate mechanic.** On match start the blocks at Y−1 under each spawn are captured. On soft reset they are restored (3×3 area), then removed after the countdown so gravity drops players into the arena. Full FAWE reset at match end restores them permanently.
- **Sidebar scoreboard** — assigned to both players at match start. Displays arena name, red/blue player names and current scores, and first-to-N target. Updated after every goal and cleared on match end.
- **Title + sound feedback on goals.** Scorer sees a large coloured "GOAL!" title with current score and hears a level-up sound. Opponent sees the scorer's name, current score, and hears a villager-no sound.
- **Title countdowns.** Match start: large yellow numbers counting down to "FIGHT!" (green). Soft reset: large red numbers 3-2-1 counting down to "GO!" (green). Pitch ramps up on the final soft-reset tick for emphasis.
- **Victory / defeat titles.** Winner sees gold "VICTORY!" with final score + `UI_TOAST_CHALLENGE_COMPLETE` sound. Loser sees red "DEFEAT" with `ENTITY_WITHER_SPAWN` sound.
- **Arena entity clear on match end.** All non-player entities inside the arena region (arrows, dropped items, etc.) are removed before the FAWE reset.
- **`GoalListener` priority raised to `HIGHEST`.** Previously `MONITOR` (could not cancel events). Changed to allow the freeze mechanism to redirect position and to ensure goal detection runs after other plugins have processed the move.

## v1.1.2 — 2026-05-17
### Fixed
- **Wand left-click now reliably sets pos1 in all game modes.**
  - `PlayerInteractEvent.LEFT_CLICK_BLOCK` handles pos1 in survival (fires on first click before block damage).
  - `BlockBreakEvent` is a creative-mode safety net (blocks break instantly, interact event may be skipped); an `isSameBlock` guard prevents a duplicate message when both events fire in the same tick.
  - `BlockDamageEvent` is cancelled while holding the wand — no crack animation plays in survival mode.
- **Two selections are properly independent.** `setPos1` and `setPos2` write to separate array slots; neither overwrites the other. Location is always stored from `block.getLocation()` (clicked block), never from player position.
- **Visual outline after every selection.** When both pos1 and pos2 are set, lime-green DUST particles trace all 12 edges of the cuboid, visible only to the selecting player. Works the same for the first selection after either position is updated.
- **Added `/bridge selection`** — shows pos1, pos2, world name, size (WxHxD and total blocks), and validity. Also re-draws the particle outline.
- **`/bridge setredgoal` / `setbluegoal`** confirmed to read from wand pos1/pos2 (not player location); no logic change needed, bug was entirely in WandListener.
- **Scoring uses proper min/max normalization** (`Math.min`/`Math.max` on all three axes); regions work correctly regardless of which corner was clicked first.

## v1.1.1 — 2026-05-17
### Fixed / Changed
- **Goal regions replace single-block goals.** Each goal is now a selectable cuboid region (two corner blocks). Scoring triggers whenever a player's block position is inside the opponent's goal region. Inclusive block-coordinate check — generous for Bedrock/Geyser players.
- **Per-player 2-second score cooldown.** Prevents duplicate scoring if a player bounces inside the region during a reset countdown.
- **Bridge Setup Wand** (`/bridge wand`) — BLAZE_ROD with a PDC tag. Left-click sets pos1, right-click sets pos2. Selections stored per player; block breaks cancelled while holding the wand.
- **`/bridge setredgoal <arena>` and `/bridge setbluegoal <arena>`** now use the wand selection instead of standing location.
- **`/bridge showgoals <arena>`** — outlines both goal regions with colored dust particles for 10 seconds (red = red goal, blue = blue goal).
- **`/bridge debug <arena>`** — prints arena world, all locations, goal regions, schematic path/existence, current state, and active match details (players, score, match state).
- **Match cleanup hardened.** `PlayerChangedWorldEvent` and `PlayerTeleportEvent` both trigger a forfeit if the player leaves the arena world or teleports outside the arena region while a match is active. Plugin-initiated teleports (spawn, reset, end-of-match lobby) are flagged to avoid false forfeits.
- **`endMatch` double-end guard.** `state == ENDED` check prevents re-entrant calls.
- **`resetAndContinue` end guard.** If the match ends while FAWE is async-resetting, the whenComplete callback is a no-op.
- **FAWE logging.** Console logs on reset start (arena, world, file path), paste origin, and reset complete/failed.
- **`/bridge list`** now shows goal-region status alongside configured/schematic status.
- **Cross-world validation in `/bridge save`** extended to include all four goal region corners.

## v1.1.0 — 2026-05-17
### Added
- **Stage 2: basic 1v1 match flow.**
- `QueueManager` — per-arena player queues; automatically starts a match when 2 players are ready.
- `MatchManager` — tracks active `BridgeMatch` instances; maps players and arenas to their current match.
- `BridgeMatch` — full match lifecycle: countdown → active → score → reset → repeat; ends on first to `points-to-win` points or on disconnect/forfeit.
- `SignListener` — right-clicking a registered queue sign joins or leaves the queue for that arena.
- `GoalListener` — `PlayerMoveEvent` (block-change check) detects when a player steps on the opponent's goal.
- `MatchListener` — `PlayerQuitEvent` handler; queued players are removed from the queue, in-match players forfeit to their opponent.
- `/bridge setsign <arena>` — look at a sign and run this command to register it as a queue sign for an arena. Sign text is updated automatically (`[Bridge] / arena / 0/2 / Click to Join`).
- Action-bar countdown (Adventure API) displayed to both players before each round.
- `bridge.use` permission (default: true) — reserved for future queue gating.

### Changed
- **Removed global world requirement.** The `settings.world` config key is gone. Each arena's world is now derived automatically from the locations set by `set*` commands — the plugin works in any world without extra configuration.
- `SchematicManager.saveArena()` and `resetArena()` now use `arena.getPos1().getWorld()` instead of a config-defined world name.
- `ArenaStorage` sign persistence added — signs survive server restarts.
- Arenas that are fully configured and have a saved schematic are set to `WAITING` state on startup so signs become clickable immediately.
- `config.yml` now includes `countdown-seconds` (default 5) and `points-to-win` (default 5).
- `plugin.yml` version bumped to 1.1.0; `bridge.use` permission added.
- `build.sh` updated to output `TheBridge-1.1.0.jar`; classpath separator fixed to `;` for Windows javac compatibility.

## v1.0.0 — 2026-05-17
### Added
- Initial release — Stage 1: arena infrastructure only.
- `Arena` model with all required fields: red/blue spawns, lobby spawn, red/blue goals, reset region corners (pos1/pos2), schematic name, enabled flag, and `ArenaState` enum for future game-phase tracking.
- `ArenaManager` — in-memory registry with CRUD operations, duplicate-name prevention, and YAML persistence via `ArenaStorage`.
- `ArenaStorage` — reads/writes `arenas.yml`; all location fields serialized as `world/x/y/z/yaw/pitch` sections; null fields are omitted cleanly.
- `SchematicManager` — FAWE-backed async save and restore:
  - `/bridge save` copies the pos1→pos2 region into a `.schem` file using `BuiltInClipboardFormat.FAST`.
  - `/bridge reset` reads the schematic back and pastes it at `clipboard.getOrigin()` (the exact min-corner recorded at save time), fully restoring the arena in place.
  - All I/O runs on a background thread via `CompletableFuture.runAsync`; result callbacks return to the main thread for player messaging.
- `BridgeCommand` — twelve `/bridge` subcommands with tab completion:
  `create`, `delete`, `list`, `setredspawn`, `setbluespawn`, `setlobby`, `setredgoal`, `setbluegoal`, `setpos1`, `setpos2`, `save`, `reset`.
- `config.yml` with `settings.world` (default `bridge`) and `settings.schematics-folder` (default `schematics`).
- Guard rails: duplicate arena prevention, missing-region save check, missing-schematic reset check, double-reset prevention via `RESETTING` state.
