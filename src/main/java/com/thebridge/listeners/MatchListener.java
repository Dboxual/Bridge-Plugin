package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.match.BridgeMatch;
import com.thebridge.match.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public class MatchListener implements Listener {

    private final TheBridgePlugin plugin;

    public MatchListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        Location dest = plugin.getMatchManager().consumePendingReturn(uid);
        if (dest == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.getInventory().clear();
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            player.setNoDamageTicks(0);
            player.setInvulnerable(false);
            plugin.getMatchManager().markPluginTeleport(uid);
            player.teleport(dest);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getMatchManager().clearPluginTeleport(uid));
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handle(event.getPlayer());
    }

    // Fires when a player changes worlds (e.g. /warp, cross-world teleport, etc.)
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (plugin.getMatchManager().isPluginTeleporting(uid)) return;

        // If the player just arrived in the arena's world they are joining, not leaving.
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match != null) {
            Arena arena = match.getArena();
            World arenaWorld = arena.getPos1() != null ? arena.getPos1().getWorld() : null;
            if (arenaWorld != null && arenaWorld.equals(player.getWorld())) return;
        }

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

        // Only forfeit on teleport during active play — not during countdowns or end sequences.
        if (match.getState() != MatchState.ACTIVE) return;

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

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getMatchManager().getMatch(player) == null) return;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null) return;
        Location spawn = match.getTeamSpawn(player.getUniqueId());
        if (spawn != null) event.setRespawnLocation(spawn);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            BridgeMatch current = plugin.getMatchManager().getMatch(player);
            if (current != null) current.respawnPlayer(player.getUniqueId());
        }, 1L);
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
