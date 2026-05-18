# Changelog

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
