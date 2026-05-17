package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.match.BridgeMatch;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public class MatchListener implements Listener {

    private final TheBridgePlugin plugin;

    public MatchListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handle(event.getPlayer());
    }

    // Fires when a player changes worlds (e.g. /warp, cross-world teleport, etc.)
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMatchManager().isPluginTeleporting(player.getUniqueId())) return;
        handle(player);
    }

    // Fires on any teleport — catch same-world teleports that move the player
    // outside the arena region (e.g. /spawn, /home in the same world).
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (plugin.getMatchManager().isPluginTeleporting(uid)) return;

        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null) return;

        Location to = event.getTo();
        if (to == null) return;

        Arena arena = match.getArena();
        if (arena.getPos1() == null) return;

        // Different world → definite forfeit (scheduled so the teleport completes first).
        // Same world but outside arena region → also forfeit.
        boolean leavingWorld = !to.getWorld().equals(arena.getPos1().getWorld());
        boolean leavingRegion = !leavingWorld && arena.hasRegion() && !arena.isInsideArena(to);

        if (leavingWorld || leavingRegion) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BridgeMatch current = plugin.getMatchManager().getMatch(player);
                if (current == match) match.onPlayerDisconnect(uid);
            });
        }
    }

    private void handle(Player player) {
        if (plugin.getQueueManager().isQueued(player)) {
            plugin.getQueueManager().leave(player);
        }
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match != null) {
            match.onPlayerDisconnect(player.getUniqueId());
        }
    }
}
