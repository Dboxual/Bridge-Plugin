# TheBridge

A Paper 1.21.x plugin for running The Bridge minigame. This repo contains **Stage 1 only** — arena infrastructure. No gameplay has been implemented yet.

---

## Requirements

| Requirement | Version |
|---|---|
| Paper | 1.21.x |
| Java | 21+ |
| FastAsyncWorldEdit | 2.x (Paper build) |

The plugin **will not load** unless FAWE is installed on the server.

---

## World setup

Create a world named `bridge` on your server. All arenas must be placed inside this world. The world name is configurable in `config.yml` (`settings.world`).

---

## Commands

All commands require the `bridge.admin` permission (default: OP).

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
| `/bridge save <arena>` | Save the arena region as a schematic |
| `/bridge reset <arena>` | Restore the arena from its saved schematic |

---

## Arena setup flow

1. `create` a new arena.
2. Stand at each location and run the appropriate `set*` command.
3. Set `pos1` and `pos2` around the full arena area you want to reset.
4. Run `/bridge save` — this copies the region into `plugins/TheBridge/schematics/<name>.schem`.
5. From this point, `/bridge reset` will fully restore the arena to this saved state.

---

## Configuration

```yaml
settings:
  world: bridge                # World containing all arenas
  schematics-folder: schematics  # Subfolder inside plugin data dir
```

---

## Building

Gradle is present for IDE support. Actual compilation uses `build.sh` (Java 25 / Gradle 8.x incompatibility).

```bash
# 1. Copy shared libs from Pinpoint/libs and download FAWE:
cp ../Pinpoint/libs/*.jar libs/
# Download FastAsyncWorldEdit for Paper 1.21.x, rename to fawe-bukkit.jar

# 2. Build
bash build.sh
# Output: build/TheBridge-1.0.0.jar
```

---

## Stage 2 (planned)

- Match sessions with player join/leave
- Score tracking (goals)
- Kit system
- Spectator support
- Party-based team assignment
- Automatic arena reset between rounds
