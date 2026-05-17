package com.thebridge.arena;

import org.bukkit.Location;

/**
 * Represents a single Bridge arena.
 *
 * All location fields are nullable — null means not yet configured.
 * Use isFullyConfigured() to check whether the arena has everything
 * it needs before enabling it for play.
 *
 * The state field drives future game-logic phases. Infrastructure code
 * (e.g. SchematicManager) checks it to guard against double-resets.
 */
public class Arena {

    private final String id;           // unique, lowercase, immutable
    private ArenaState state = ArenaState.DISABLED;
    private boolean enabled = false;

    // Team spawns — where players teleport at match start
    private Location redSpawn;
    private Location blueSpawn;

    // Waiting area spawn — where players stand before the match starts
    private Location lobbySpawn;

    // Goal regions — player stepping here scores a point (or triggers win)
    private Location redGoal;
    private Location blueGoal;

    // Reset region corners — defines the FAWE paste/copy area
    private Location pos1;
    private Location pos2;

    // Name of the .schem file (defaults to arena id, can be overridden)
    private String schematicName;

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

    // ── Schematic ─────────────────────────────────────────────────────────────

    public String getSchematicName() { return schematicName; }
    public void setSchematicName(String schematicName) { this.schematicName = schematicName; }

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
