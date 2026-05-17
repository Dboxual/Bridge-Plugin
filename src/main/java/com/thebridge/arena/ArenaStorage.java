package com.thebridge.arena;

import com.thebridge.TheBridgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Reads and writes arena data to arenas.yml.
 *
 * Format:
 *   arenas:
 *     <id>:
 *       enabled: false
 *       schematic-name: <id>
 *       red-spawn: { world, x, y, z, yaw, pitch }
 *       ...
 */
public class ArenaStorage {

    private final TheBridgePlugin plugin;
    private final File file;
    private FileConfiguration config;

    public ArenaStorage(TheBridgePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        reload();
    }

    private void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create arenas.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void flush() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas.yml", e);
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public List<Arena> loadAll() {
        List<Arena> result = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) return result;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Arena arena = deserializeArena(id, s);
            result.add(arena);
        }
        return result;
    }

    private Arena deserializeArena(String id, ConfigurationSection s) {
        Arena arena = new Arena(id);
        arena.setEnabled(s.getBoolean("enabled", false));
        arena.setSchematicName(s.getString("schematic-name", id));
        arena.setRedSpawn(readLocation(s, "red-spawn"));
        arena.setBlueSpawn(readLocation(s, "blue-spawn"));
        arena.setLobbySpawn(readLocation(s, "lobby-spawn"));
        arena.setRedGoal(readLocation(s, "red-goal"));
        arena.setBlueGoal(readLocation(s, "blue-goal"));
        arena.setPos1(readLocation(s, "pos1"));
        arena.setPos2(readLocation(s, "pos2"));
        return arena;
    }

    // ── Save / Delete ─────────────────────────────────────────────────────────

    public void saveArena(Arena arena) {
        String base = "arenas." + arena.getId();
        config.set(base + ".enabled", arena.isEnabled());
        config.set(base + ".schematic-name", arena.getSchematicName());
        writeLocation(base + ".red-spawn", arena.getRedSpawn());
        writeLocation(base + ".blue-spawn", arena.getBlueSpawn());
        writeLocation(base + ".lobby-spawn", arena.getLobbySpawn());
        writeLocation(base + ".red-goal", arena.getRedGoal());
        writeLocation(base + ".blue-goal", arena.getBlueGoal());
        writeLocation(base + ".pos1", arena.getPos1());
        writeLocation(base + ".pos2", arena.getPos2());
        flush();
    }

    public void deleteArena(String id) {
        config.set("arenas." + id, null);
        flush();
    }

    // ── Location helpers ──────────────────────────────────────────────────────

    private void writeLocation(String path, Location loc) {
        if (loc == null) {
            config.set(path, null);
            return;
        }
        config.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : plugin.getWorldName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw",   (double) loc.getYaw());
        config.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location readLocation(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null || !s.contains("x")) return null;
        String worldName = s.getString("world", plugin.getWorldName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                s.getDouble("x"),
                s.getDouble("y"),
                s.getDouble("z"),
                (float) s.getDouble("yaw", 0),
                (float) s.getDouble("pitch", 0)
        );
    }
}
