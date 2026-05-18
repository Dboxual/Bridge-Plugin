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

        Location from = event.getFrom();
        Location to   = event.getTo();

        // Freeze: allow head rotation but block all XYZ movement.
        if (match.isFrozen(player.getUniqueId())) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location frozen = from.clone();
                frozen.setYaw(to.getYaw());
                frozen.setPitch(to.getPitch());
                event.setTo(frozen);
            }
            return;
        }

        // Skip rotation-only events — no position change means no goal can be entered.
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        if (match.getState() != MatchState.ACTIVE) return;

        // Pass both FROM and TO so onGoalEntered can sweep the full movement path.
        // This catches players falling through the goal region in a single tick.
        match.onGoalEntered(player, from, to);
    }
}
