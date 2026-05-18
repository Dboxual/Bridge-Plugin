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
| 8 | 1× Arrow |
| Armor | Dyed leather (red or blue) |

---

## Commands

### Admin commands (`bridge.admin` — default: OP)

| Command | Description |
|---|---|
| `/bridge create <arena>` | Create a new arena |
| `/bridge delete <arena>` | Delete an arena |
| `/bridge list` | List all arenas with status |
| `/bridge setredspawn <arena>` | Set red team spawn at your location |
| `/bridge setbluespawn <arena>` | Set blue team spawn at your location |
| `/bridge setlobby <arena>` | Set lobby/waiting spawn at your location |
| `/bridge wand` | Get the Bridge Setup Wand (left-click block=pos1, right-click block=pos2) |
| `/bridge selection` | Show current wand selection: pos1, pos2, world, dimensions |
| `/bridge setredgoal <arena>` | Set red goal region from wand selection |
| `/bridge setbluegoal <arena>` | Set blue goal region from wand selection |
| `/bridge showgoals <arena>` | Visualise goal regions with particles for 10 s |
| `/bridge setredrelease <arena>` | Set red release zone from wand selection |
| `/bridge setbluerelease <arena>` | Set blue release zone from wand selection |
| `/bridge setvoidlevel <arena>` | Set void Y level at your current position |
| `/bridge debug <arena>` | Dump full arena and match status |
| `/bridge setarena <arena>` | Set reset region from wand selection (recommended) |
| `/bridge setpos1 <arena>` | Set reset region corner 1 at your location (legacy) |
| `/bridge setpos2 <arena>` | Set reset region corner 2 at your location (legacy) |
| `/bridge setsign <arena>` | Register the sign you are looking at as a queue sign |
| `/bridge removesign <arena>` | Unregister the sign you are looking at (clears text, keeps block) |
| `/bridge save <arena>` | Save the arena region as a schematic |
| `/bridge reset <arena>` | Restore the arena from its saved schematic |

---

## Arena setup flow

1. `/bridge create <arena>` — create the arena entry.
2. Stand at each spawn and run the corresponding `set*` command. The world is detected automatically.
3. **Goal regions:** run `/bridge wand` to receive the wand.
   - **Left-click** a block → pos1 set.
   - **Right-click** a block → pos2 set.
   - When both corners are selected, lime-green particles outline the cuboid.
   - Run `/bridge selection` to inspect dimensions and verify the selection.
   - Run `/bridge setredgoal <arena>` — saves the wand selection as the red goal region.
   - Repeat with `/bridge setbluegoal <arena>` for blue.
   - Use `/bridge showgoals <arena>` to overlay goal regions with red/blue particles.
4. **Release zones:** use `/bridge wand` to select the floor region that should open each round.
   - Left-click corner 1, right-click corner 2.
   - `/bridge setredrelease <arena>` — saves the wand selection as red's release zone.
   - Repeat the wand selection, then `/bridge setbluerelease <arena>` for blue.
   - If not configured, a 3×3 fallback at Y−1 under each spawn is used (a console warning is logged).
5. **Reset region:** use `/bridge wand` to select the full arena region (all blocks that will be restored at match end).
   - Left-click corner 1, right-click corner 2.
   - `/bridge setarena <arena>` — saves the wand selection as the reset region.
   - *(Legacy alternative: stand at each corner and run `/bridge setpos1 <arena>` then `/bridge setpos2 <arena>`)*
6. `/bridge save <arena>` — snapshot the region as a schematic.
7. Place a sign, look at it, `/bridge setsign <arena>` — sign updates automatically.
8. Players right-click the sign to join. Two players → match starts.

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
# Output: build/TheBridge-1.2.5.jar
```
