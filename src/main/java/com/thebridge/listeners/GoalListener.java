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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null) return;

        // Freeze: allow head rotation but cancel any XYZ position change.
        if (match.isFrozen(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to   = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location frozen = from.clone();
                frozen.setYaw(to.getYaw());
                frozen.setPitch(to.getPitch());
                event.setTo(frozen);
            }
            return;
        }

        // Goal detection — only fires when the player crosses a block boundary.
        // Pass event.getTo() because player.getLocation() is still the FROM position
        // during a move event; using FROM would always miss the goal region.
        if (!event.hasChangedBlock()) return;
        if (match.getState() != MatchState.ACTIVE) return;
        match.onGoalEntered(player, event.getTo());
    }
}
