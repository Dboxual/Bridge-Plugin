# TheBridge

A Paper 1.21.x plugin for running The Bridge minigame — 1v1 bridge-crossing with goal scoring, automatic arena resets, and queue signs.

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
- Players score by stepping on the **opponent's goal block**.
- After each point the arena resets via FAWE and a new countdown begins.
- First to **5 points** wins. Both players are sent to `lobbySpawn` when the match ends.
- A player disconnecting mid-match forfeits to their opponent.

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
| `/bridge setredgoal <arena>` | Set red team goal at your location |
| `/bridge setbluegoal <arena>` | Set blue team goal at your location |
| `/bridge setpos1 <arena>` | Set reset region corner 1 at your location |
| `/bridge setpos2 <arena>` | Set reset region corner 2 at your location |
| `/bridge setsign <arena>` | Register the sign you are looking at as a queue sign |
| `/bridge save <arena>` | Save the arena region as a schematic |
| `/bridge reset <arena>` | Restore the arena from its saved schematic |

---

## Arena setup flow

1. `create` a new arena.
2. Stand at each location and run the corresponding `set*` command. The world is saved automatically from wherever you are standing — no world configuration needed.
3. Set `pos1` and `pos2` around the full arena area to be reset between points.
4. Run `/bridge save` — copies the region into `plugins/TheBridge/schematics/<name>.schem`.
5. Place a sign, look at it, and run `/bridge setsign <arena>` to register it as a queue sign.
6. Players can now right-click the sign to join the queue. When 2 players queue, the match begins.

---

## Configuration

```yaml
settings:
  schematics-folder: schematics  # Subfolder inside plugin data dir
  countdown-seconds: 5           # Seconds before a match or reset starts
  points-to-win: 5               # Points needed to win a match
```

---

## Building

Gradle is present for IDE support. Actual compilation uses `build.sh` (Java 25 / Gradle 8.x incompatibility).

```bash
# 1. Copy shared libs from Pinpoint/libs and place FAWE:
cp ../Pinpoint/libs/*.jar libs/
# Copy FastAsyncWorldEdit for Paper 1.21.x into libs/ as fawe-bukkit.jar

# 2. Build
bash build.sh
# Output: build/TheBridge-1.1.0.jar
```
