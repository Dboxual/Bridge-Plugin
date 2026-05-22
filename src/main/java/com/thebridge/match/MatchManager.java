package com.thebridge.match;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchManager {

    private final TheBridgePlugin plugin;
    private final Map<UUID, BridgeMatch> playerMatches = new HashMap<>();
    private final Map<String, BridgeMatch> arenaMatches = new HashMap<>();
    private final Set<UUID> pluginTeleporting = new HashSet<>();
    private final Map<UUID, Location> pendingReturns = new HashMap<>();

    public MatchManager(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public BridgeMatch createMatch(Arena arena, UUID red, UUID blue) {
        BridgeMatch match = new BridgeMatch(plugin, arena, red, blue);
        playerMatches.put(red, match);
        playerMatches.put(blue, match);
        arenaMatches.put(arena.getId(), match);
        match.start();
        return match;
    }

    public void removeMatch(BridgeMatch match) {
        playerMatches.remove(match.getRedPlayer());
        playerMatches.remove(match.getBluePlayer());
        arenaMatches.remove(match.getArena().getId());
    }

    public BridgeMatch getMatch(Player player) {
        return playerMatches.get(player.getUniqueId());
    }

    public BridgeMatch getMatchByArena(String arenaId) {
        return arenaMatches.get(arenaId);
    }

    public boolean isInMatch(Player player) {
        return playerMatches.containsKey(player.getUniqueId());
    }

    public Collection<BridgeMatch> getActiveMatches() {
        return Collections.unmodifiableCollection(arenaMatches.values());
    }

    // ── Plugin-teleport tracking ──────────────────────────────────────────────
    // BridgeMatch marks teleports it initiates so MatchListener ignores them.

    public void markPluginTeleport(UUID uid) { pluginTeleporting.add(uid); }
    public void clearPluginTeleport(UUID uid) { pluginTeleporting.remove(uid); }
    public boolean isPluginTeleporting(UUID uid) { return pluginTeleporting.contains(uid); }

    // ── Pending return for players who disconnected mid-match ─────────────────

    public void setPendingReturn(UUID uid, Location loc) { pendingReturns.put(uid, loc); }
    public Location consumePendingReturn(UUID uid) { return pendingReturns.remove(uid); }
}
