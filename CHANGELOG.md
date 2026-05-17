# Changelog

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
