# Changelog

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
