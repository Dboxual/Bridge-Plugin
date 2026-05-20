package com.thebridge.arena;

import com.thebridge.TheBridgePlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of all arenas.
 *
 * Designed for future expansion: the backing map supports any number of arenas.
 * Game-logic stages can inject themselves by querying arenas by state, calling
 * setState(), and passing the arena into match/session managers that don't exist yet.
 */
public class ArenaManager {

    private final TheBridgePlugin plugin;
    private final ArenaStorage storage;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(TheBridgePlugin plugin) {
        this.plugin = plugin;
        this.storage = new ArenaStorage(plugin);
    }

    public void loadAll() {
        arenas.clear();
        for (Arena arena : storage.loadAll()) {
            arenas.put(arena.getId(), arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    public void reloadAll() {
        storage.reload();
        loadAll();
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new arena with the given id.
     * Returns null if an arena with that id already exists.
     */
    public Arena createArena(String id) {
        String normalized = id.toLowerCase();
        if (arenas.containsKey(normalized)) return null;
        Arena arena = new Arena(normalized);
        arenas.put(normalized, arena);
        storage.saveArena(arena);
        return arena;
    }

    /**
     * Removes an arena from memory and storage.
     * Returns false if no arena with that id exists.
     */
    public boolean deleteArena(String id) {
        String normalized = id.toLowerCase();
        if (!arenas.containsKey(normalized)) return false;
        arenas.remove(normalized);
        storage.deleteArena(normalized);
        return true;
    }

    public Optional<Arena> getArena(String id) {
        return Optional.ofNullable(arenas.get(id.toLowerCase()));
    }

    public boolean exists(String id) {
        return arenas.containsKey(id.toLowerCase());
    }

    public Collection<Arena> getAllArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveArena(Arena arena) {
        storage.saveArena(arena);
    }
}
