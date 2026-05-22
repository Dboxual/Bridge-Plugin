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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Reads and writes arena data to arenas.yml.
 *
 * Each location stores its own world name — there is no global arena world.
 * Arenas in different worlds coexist without configuration.
 *
 * Format:
 *   arenas:
 *     <id>:
 *       enabled: false
 *       schematic-name: <id>
 *       red-spawn:       { world, x, y, z, yaw, pitch }
 *       blue-spawn:      { ... }
 *       lobby-spawn:     { ... }
 *       red-goal-pos1:   { world, x, y, z }
 *       red-goal-pos2:   { world, x, y, z }
 *       blue-goal-pos1:  { world, x, y, z }
 *       blue-goal-pos2:  { world, x, y, z }
 *       pos1:            { ... }
 *       pos2:            { ... }
 *       signs:
 *         - { world, x, y, z }
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

    void reload() {
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
            result.add(deserializeArena(id, s));
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
        arena.setRedGoalPos1(readLocation(s, "red-goal-pos1"));
        arena.setRedGoalPos2(readLocation(s, "red-goal-pos2"));
        arena.setBlueGoalPos1(readLocation(s, "blue-goal-pos1"));
        arena.setBlueGoalPos2(readLocation(s, "blue-goal-pos2"));
        arena.setRedRelease1(readLocation(s, "red-release-1"));
        arena.setRedRelease2(readLocation(s, "red-release-2"));
        arena.setBlueRelease1(readLocation(s, "blue-release-1"));
        arena.setBlueRelease2(readLocation(s, "blue-release-2"));
        arena.setPos1(readLocation(s, "pos1"));
        arena.setPos2(readLocation(s, "pos2"));
        arena.setReturnLocation(readLocation(s, "return-location"));
        if (s.contains("void-level")) arena.setVoidLevel(s.getInt("void-level"));
        arena.setSignLocations(readSigns(s));
        if (arena.isEnabled()) arena.setState(ArenaState.WAITING);
        return arena;
    }

    // ── Save / Delete ─────────────────────────────────────────────────────────

    public void saveArena(Arena arena) {
        String base = "arenas." + arena.getId();
        config.set(base + ".enabled", arena.isEnabled());
        config.set(base + ".schematic-name", arena.getSchematicName());
        writeLocation(base + ".red-spawn",      arena.getRedSpawn());
        writeLocation(base + ".blue-spawn",     arena.getBlueSpawn());
        writeLocation(base + ".lobby-spawn",    arena.getLobbySpawn());
        writeLocation(base + ".red-goal-pos1",  arena.getRedGoalPos1());
        writeLocation(base + ".red-goal-pos2",  arena.getRedGoalPos2());
        writeLocation(base + ".blue-goal-pos1", arena.getBlueGoalPos1());
        writeLocation(base + ".blue-goal-pos2", arena.getBlueGoalPos2());
        writeLocation(base + ".red-release-1",  arena.getRedRelease1());
        writeLocation(base + ".red-release-2",  arena.getRedRelease2());
        writeLocation(base + ".blue-release-1", arena.getBlueRelease1());
        writeLocation(base + ".blue-release-2", arena.getBlueRelease2());
        writeLocation(base + ".pos1",            arena.getPos1());
        writeLocation(base + ".pos2",            arena.getPos2());
        writeLocation(base + ".return-location", arena.getReturnLocation());
        config.set(base + ".void-level", arena.hasVoidLevel() ? arena.getVoidLevel() : null);
        writeSigns(base + ".signs", arena.getSignLocations());
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
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : null;
        if (worldName == null) {
            config.set(path, null);
            return;
        }
        config.set(path + ".world", worldName);
        config.set(path + ".x",     loc.getX());
        config.set(path + ".y",     loc.getY());
        config.set(path + ".z",     loc.getZ());
        config.set(path + ".yaw",   (double) loc.getYaw());
        config.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location readLocation(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null || !s.contains("x")) return null;

        String worldName = s.getString("world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' is not loaded — location '" + key + "' skipped.");
            return null;
        }
        return new Location(
                world,
                s.getDouble("x"),
                s.getDouble("y"),
                s.getDouble("z"),
                (float) s.getDouble("yaw", 0),
                (float) s.getDouble("pitch", 0)
        );
    }

    // ── Sign helpers ──────────────────────────────────────────────────────────

    private void writeSigns(String path, List<Location> signs) {
        if (signs.isEmpty()) {
            config.set(path, null);
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Location loc : signs) {
            if (loc.getWorld() == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", loc.getWorld().getName());
            m.put("x", loc.getBlockX());
            m.put("y", loc.getBlockY());
            m.put("z", loc.getBlockZ());
            list.add(m);
        }
        config.set(path, list.isEmpty() ? null : list);
    }

    private List<Location> readSigns(ConfigurationSection s) {
        List<Location> result = new ArrayList<>();
        List<?> raw = s.getList("signs");
        if (raw == null) return result;
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Object wObj = map.get("world");
            Object xObj = map.get("x");
            Object yObj = map.get("y");
            Object zObj = map.get("z");
            if (wObj == null || xObj == null || yObj == null || zObj == null) continue;
            World world = Bukkit.getWorld(wObj.toString());
            if (world == null) continue;
            result.add(new Location(world,
                    ((Number) xObj).intValue(),
                    ((Number) yObj).intValue(),
                    ((Number) zObj).intValue()));
        }
        return result;
    }
}
