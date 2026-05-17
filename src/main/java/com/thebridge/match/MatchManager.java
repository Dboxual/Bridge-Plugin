package com.thebridge.match;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MatchManager {

    private final TheBridgePlugin plugin;
    private final Map<UUID, BridgeMatch> playerMatches = new HashMap<>();
    private final Map<String, BridgeMatch> arenaMatches = new HashMap<>();

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

    public boolean isInMatch(Player player) {
        return playerMatches.containsKey(player.getUniqueId());
    }

    public Collection<BridgeMatch> getActiveMatches() {
        return Collections.unmodifiableCollection(arenaMatches.values());
    }
}
