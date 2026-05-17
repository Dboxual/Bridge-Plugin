package com.thebridge.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single Bridge arena.
 *
 * All location fields are nullable — null means not yet configured.
 * Each location stores its own world, so arenas can exist in any world
 * independently. Use getWorld() to retrieve the arena's world from pos1.
 *
 * Use isFullyConfigured() before enabling an arena for play.
 * The state field drives match lifecycle phases.
 */
public class Arena {

    private final String id;           // unique, lowercase, immutable
    private ArenaState state = ArenaState.DISABLED;
    private boolean enabled = false;

    // Team spawns — where players teleport at match start
    private Location redSpawn;
    private Location blueSpawn;

    // Waiting area spawn — where players stand before/after the match
    private Location lobbySpawn;

    // Goals — player stepping on opponent's goal scores a point
    private Location redGoal;
    private Location blueGoal;

    // Reset region corners — defines the FAWE paste/copy area
    private Location pos1;
    private Location pos2;

    // Name of the .schem file (defaults to arena id)
    private String schematicName;

    // Queue signs registered to this arena
    private final List<Location> signs = new ArrayList<>();

    public Arena(String id) {
        this.id = id.toLowerCase();
        this.schematicName = this.id;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    public String getId() { return id; }

    // ── State ─────────────────────────────────────────────────────────────────

    public ArenaState getState() { return state; }
    public void setState(ArenaState state) { this.state = state; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // ── Locations ─────────────────────────────────────────────────────────────

    public Location getRedSpawn() { return redSpawn; }
    public void setRedSpawn(Location redSpawn) { this.redSpawn = redSpawn; }

    public Location getBlueSpawn() { return blueSpawn; }
    public void setBlueSpawn(Location blueSpawn) { this.blueSpawn = blueSpawn; }

    public Location getLobbySpawn() { return lobbySpawn; }
    public void setLobbySpawn(Location lobbySpawn) { this.lobbySpawn = lobbySpawn; }

    public Location getRedGoal() { return redGoal; }
    public void setRedGoal(Location redGoal) { this.redGoal = redGoal; }

    public Location getBlueGoal() { return blueGoal; }
    public void setBlueGoal(Location blueGoal) { this.blueGoal = blueGoal; }

    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }

    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }

    // ── World convenience ─────────────────────────────────────────────────────

    /** Returns the world this arena lives in, derived from pos1. Null if pos1 is not set. */
    public World getWorld() {
        return pos1 != null ? pos1.getWorld() : null;
    }

    // ── Schematic ─────────────────────────────────────────────────────────────

    public String getSchematicName() { return schematicName; }
    public void setSchematicName(String schematicName) { this.schematicName = schematicName; }

    // ── Signs ─────────────────────────────────────────────────────────────────

    public List<Location> getSignLocations() { return Collections.unmodifiableList(signs); }

    public void addSign(Location loc) {
        signs.add(loc);
    }

    public void removeSign(Location loc) {
        signs.removeIf(s -> s.getWorld() != null
                && s.getWorld().equals(loc.getWorld())
                && s.getBlockX() == loc.getBlockX()
                && s.getBlockY() == loc.getBlockY()
                && s.getBlockZ() == loc.getBlockZ());
    }

    public void setSignLocations(List<Location> locs) {
        signs.clear();
        signs.addAll(locs);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /** True when every required location has been set. */
    public boolean isFullyConfigured() {
        return redSpawn != null && blueSpawn != null && lobbySpawn != null
                && redGoal != null && blueGoal != null
                && pos1 != null && pos2 != null;
    }

    /** True when both region corners are set (needed for save/reset). */
    public boolean hasRegion() {
        return pos1 != null && pos2 != null;
    }

    @Override
    public String toString() {
        return "Arena[id=" + id + ", state=" + state + ", enabled=" + enabled + "]";
    }
}
