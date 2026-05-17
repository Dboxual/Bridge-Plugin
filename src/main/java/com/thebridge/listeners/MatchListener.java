package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.match.BridgeMatch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class MatchListener implements Listener {

    private final TheBridgePlugin plugin;

    public MatchListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getQueueManager().isQueued(event.getPlayer())) {
            plugin.getQueueManager().leave(event.getPlayer());
            return;
        }

        BridgeMatch match = plugin.getMatchManager().getMatch(event.getPlayer());
        if (match != null) {
            match.onPlayerDisconnect(event.getPlayer().getUniqueId());
        }
    }
}
