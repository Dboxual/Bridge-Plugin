# TheBridge

A Paper 1.21.x plugin for running The Bridge minigame — 1v1 bridge-crossing with goal scoring, soft per-round resets, and queue signs.

---

## Requirements

| Requirement | Version |
|---|---|
| Paper | 1.21.x |
| Java | 21+ |
| FastAsyncWorldEdit | 2.x (Paper build) |

The plugin **will not load** unless FAWE is installed on the server.

---

## How it works

- Arenas work in **any world** — the world is derived automatically from the location you're standing in when you run each `set*` command.
- Players right-click a registered **queue sign** to join or leave the queue for an arena.
- When 2 players are queued, the match starts automatically after a countdown.
- Players score by entering the **opponent's goal region** (a configurable cuboid area — Bedrock-friendly).
- After each point a **soft reset** runs: players are teleported back to spawn, healed, re-given loadout, frozen for a 3-second countdown, then the spawn platform drops away so they fall naturally into the arena. No schematic paste runs mid-match — bridges built during the round persist.
- First to **5 points** wins. A **full FAWE schematic reset** runs at match end, restoring the arena completely. Both players are sent to `lobbySpawn`.
- A player disconnecting mid-match forfeits to their opponent.

### Standard loadout (given at match start and after each soft reset)

| Slot | Item |
|---|---|
| 0 | Iron Sword |
| 1 | Bow |
| 2 | 32× Colored Terracotta (team color) |
| 3 | 3× Golden Apple |
| 4 | Diamond Pickaxe (Efficiency II) |
| 8 | 1× Arrow |
| Armor | Dyed leather (red or blue) |

### Kit mechanics

**Golden apple** — eating a golden apple during a Bridge match gives instant full health and 2 absorption hearts (4 HP) instead of the vanilla potion effects. Outside a Bridge match the vanilla behavior is unchanged.

**Arrow regeneration** — each player is capped at 1 arrow. Shooting it schedules a 3.5-second regen; the arrow returns automatically unless the player receives a fresh loadout first (soft reset, death respawn). Stray arrows on the arena floor cannot be picked up during a match.

---

## Commands

### Admin commands (`bridge.admin` — default: OP)

| Command | Description |
|---|---|
| `/bridge create <arena>` | Create a new arena (auto-saved; setup commands work immediately) |
| `/bridge delete <arena>` | Delete an arena |
| `/bridge list` | List all arenas with enabled/configured/schematic status |
| `/bridge enable <arena>` | Open arena to players |
| `/bridge disable <arena>` | Close arena to players (blocked during active matches) |
| `/bridge setlobby <arena>` | Set lobby/waiting spawn at your location |
| `/bridge setredspawn <arena>` | Set red team spawn at your location |
| `/bridge setbluespawn <arena>` | Set blue team spawn at your location |
| `/bridge wand` | Get the Bridge Setup Wand (left-click block=pos1, right-click block=pos2) |
| `/bridge selection` | Show current wand selection: pos1, pos2, world, dimensions |
| `/bridge setredgoal <arena>` | Set red goal region from wand selection |
| `/bridge setbluegoal <arena>` | Set blue goal region from wand selection |
| `/bridge showgoals <arena>` | Visualise goal regions with particles for 10 s |
| `/bridge setredrelease <arena>` | Set red release zone from wand selection |
| `/bridge setbluerelease <arena>` | Set blue release zone from wand selection |
| `/bridge setarena <arena>` | Set full reset region from wand selection |
| `/bridge setvoidlevel <arena>` | Set void Y level at your current position |
| `/bridge save <arena>` | Snapshot the reset region as a schematic (required before enable) |
| `/bridge setsign <arena>` | Register the sign you are looking at as a queue sign |
| `/bridge removesign <arena>` | Unregister the sign you are looking at (clears text, keeps block) |
| `/bridge reload` | Reload config and all arena data from disk (blocked during active matches) |
| `/bridge reset <arena>` | Restore the arena from its saved schematic |
| `/bridge debug <arena>` | Dump full arena and match status |
| `/bridge setpos1 <arena>` | Set reset region corner 1 at your location (legacy) |
| `/bridge setpos2 <arena>` | Set reset region corner 2 at your location (legacy) |

---

## Arena setup flow

All `set*` commands auto-save to `arenas.yml` immediately. No manual config editing is required.

1. `/bridge create <arena>` — register the arena.
2. `/bridge setlobby <arena>` — stand at the lobby and run.
3. `/bridge setredspawn <arena>` — stand at red spawn and run.
4. `/bridge setbluespawn <arena>` — stand at blue spawn and run.
5. **Wand-based regions:** run `/bridge wand` to receive the setup wand.
   - **Left-click** a block → pos1. **Right-click** a block → pos2.
   - `/bridge setredgoal <arena>` — saves wand selection as red goal.
   - `/bridge setbluegoal <arena>` — saves wand selection as blue goal.
   - `/bridge setredrelease <arena>` — saves wand selection as red release zone.
   - `/bridge setbluerelease <arena>` — saves wand selection as blue release zone.
   - `/bridge setarena <arena>` — saves wand selection as the full reset region (required for save).
   - Use `/bridge showgoals <arena>` to verify goal placement with particles.
6. `/bridge setvoidlevel <arena>` — stand at the void threshold and run (optional but recommended).
7. `/bridge save <arena>` — snapshot the reset region as a schematic.
8. Place a sign, look at it, `/bridge setsign <arena>` — registers it as a queue sign.
9. `/bridge enable <arena>` — opens the arena to players. Players right-click the sign to queue.

To take an arena offline: `/bridge disable <arena>`. To reopen: `/bridge enable <arena>`. The enabled state persists across server restarts.

### Release zone requirement

The release zones define the floor blocks removed each round to drop players into the arena. The blocks are captured as a snapshot at match start, restored before each round so players land safely, then set to AIR after the countdown. **The full FAWE schematic reset at match end restores them permanently.**

If no zone is configured, the plugin falls back to a 3×3 area at Y−1 under each spawn and logs a warning. Use `/bridge debug <arena>` to verify zone status.

---

## Reset design

| When | Type | What happens |
|---|---|---|
| After each point | Soft reset | Players teleported to spawn, healed, loadout refreshed, 3-second countdown, spawn floor removed — players fall in. No FAWE paste. |
| After match ends | Full reset | FAWE schematic paste restores the entire arena region. |

---

## Configuration

```yaml
settings:
  schematics-folder: schematics  # Subfolder inside plugin data dir
  countdown-seconds: 5           # Seconds before a match starts
  points-to-win: 5               # Points needed to win a match
```

---

## Configuration

```yaml
settings:
  schematics-folder: schematics
  countdown-seconds: 5
  points-to-win: 5
  debug: false   # set true to receive release-zone debug messages in-game
```

## Building

Gradle is present for IDE support. Actual compilation uses `build.sh` (Java 25 / Gradle 8.x incompatibility).

```bash
# 1. Copy shared libs from Pinpoint/libs and place FAWE:
cp ../Pinpoint/libs/*.jar libs/
# Copy FastAsyncWorldEdit for Paper 1.21.x into libs/ as fawe-bukkit.jar

# 2. Build
bash build.sh
# Output: build/TheBridge-1.3.9.jar
```
