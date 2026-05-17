package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.match.BridgeMatch;
import com.thebridge.match.MatchState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class GoalListener implements Listener {

    private final TheBridgePlugin plugin;

    public GoalListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;

        Location to = event.getTo();
        if (to == null) return;

        match.onGoalEntered(player);
    }
}
