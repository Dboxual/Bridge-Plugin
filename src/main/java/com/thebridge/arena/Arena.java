package com.thebridge.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Arena {

    private final String id;
    private ArenaState state = ArenaState.DISABLED;
    private boolean enabled = false;

    private Location redSpawn;
    private Location blueSpawn;
    private Location lobbySpawn;

    // Goals are regions (two corner points) rather than single blocks.
    // Region check uses inclusive block coordinates; generous Y range suits Bedrock players.
    private Location redGoalPos1;
    private Location redGoalPos2;
    private Location blueGoalPos1;
    private Location blueGoalPos2;

    // Release zone regions — the floor blocks removed each round to drop players into the arena.
    // Defined by two corners (same as goal regions). If not set, a fallback 3×3 platform is used.
    private Location redRelease1;
    private Location redRelease2;
    private Location blueRelease1;
    private Location blueRelease2;

    private Location pos1;
    private Location pos2;

    private String schematicName;

    private Integer voidLevel = null;

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

    public Location getRedGoalPos1() { return redGoalPos1; }
    public void setRedGoalPos1(Location p) { this.redGoalPos1 = p; }

    public Location getRedGoalPos2() { return redGoalPos2; }
    public void setRedGoalPos2(Location p) { this.redGoalPos2 = p; }

    public Location getBlueGoalPos1() { return blueGoalPos1; }
    public void setBlueGoalPos1(Location p) { this.blueGoalPos1 = p; }

    public Location getBlueGoalPos2() { return blueGoalPos2; }
    public void setBlueGoalPos2(Location p) { this.blueGoalPos2 = p; }

    public Location getRedRelease1() { return redRelease1; }
    public void setRedRelease1(Location p) { this.redRelease1 = p; }

    public Location getRedRelease2() { return redRelease2; }
    public void setRedRelease2(Location p) { this.redRelease2 = p; }

    public Location getBlueRelease1() { return blueRelease1; }
    public void setBlueRelease1(Location p) { this.blueRelease1 = p; }

    public Location getBlueRelease2() { return blueRelease2; }
    public void setBlueRelease2(Location p) { this.blueRelease2 = p; }

    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }

    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }

    // ── World convenience ─────────────────────────────────────────────────────

    public World getWorld() {
        return pos1 != null ? pos1.getWorld() : null;
    }

    // ── Schematic ─────────────────────────────────────────────────────────────

    public String getSchematicName() { return schematicName; }
    public void setSchematicName(String schematicName) { this.schematicName = schematicName; }

    // ── Signs ─────────────────────────────────────────────────────────────────

    public List<Location> getSignLocations() { return Collections.unmodifiableList(signs); }

    public void addSign(Location loc) { signs.add(loc); }

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

    // ── Goal region checks ────────────────────────────────────────────────────

    public boolean hasRedGoal()     { return redGoalPos1  != null && redGoalPos2  != null; }
    public boolean hasBlueGoal()    { return blueGoalPos1 != null && blueGoalPos2 != null; }
    public boolean hasRedRelease()  { return redRelease1  != null && redRelease2  != null; }
    public boolean hasBlueRelease() { return blueRelease1 != null && blueRelease2 != null; }

    public boolean isInsideRedGoal(Location loc) {
        return isInsideRegion(loc, redGoalPos1, redGoalPos2);
    }

    public boolean isInsideBlueGoal(Location loc) {
        return isInsideRegion(loc, blueGoalPos1, blueGoalPos2);
    }

    public boolean isInsideArena(Location loc) {
        return isInsideRegion(loc, pos1, pos2);
    }

    private boolean isInsideRegion(Location loc, Location p1, Location p2) {
        if (loc == null || p1 == null || p2 == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().equals(p1.getWorld())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= Math.min(p1.getBlockX(), p2.getBlockX()) && x <= Math.max(p1.getBlockX(), p2.getBlockX())
            && y >= Math.min(p1.getBlockY(), p2.getBlockY()) && y <= Math.max(p1.getBlockY(), p2.getBlockY())
            && z >= Math.min(p1.getBlockZ(), p2.getBlockZ()) && z <= Math.max(p1.getBlockZ(), p2.getBlockZ());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public boolean isFullyConfigured() {
        return redSpawn != null && blueSpawn != null && lobbySpawn != null
                && hasRedGoal() && hasBlueGoal()
                && pos1 != null && pos2 != null;
    }

    public boolean hasRegion() {
        return pos1 != null && pos2 != null;
    }

    public Integer getVoidLevel() { return voidLevel; }
    public void setVoidLevel(Integer voidLevel) { this.voidLevel = voidLevel; }
    public boolean hasVoidLevel() { return voidLevel != null; }

    @Override
    public String toString() {
        return "Arena[id=" + id + ", state=" + state + ", enabled=" + enabled + "]";
    }
}
