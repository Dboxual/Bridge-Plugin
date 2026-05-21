package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaState;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class SignListener implements Listener {

    private final TheBridgePlugin plugin;

    public SignListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    // HIGHEST + ignoreCancelled=false: fires after protection plugins (e.g. WorldGuard)
    // and still processes registered Bridge signs even if the event was cancelled.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof org.bukkit.block.Sign)) return;

        boolean debug = plugin.getConfig().getBoolean("settings.debug", false);
        Location clicked = block.getLocation();

        Arena target = null;
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            for (Location sign : arena.getSignLocations()) {
                if (isSameBlock(sign, clicked)) {
                    target = arena;
                    break;
                }
            }
            if (target != null) break;
        }

        if (debug) {
            plugin.getLogger().info("[Bridge] Sign click: block=" + block.getType()
                    + " loc=" + fmtLoc(clicked)
                    + " cancelled=" + event.isCancelled()
                    + " registeredMatch=" + (target != null));
        }

        if (target == null) return;

        if (event.isCancelled() && debug) {
            plugin.getLogger().info("[Bridge] Sign pre-cancelled by protection plugin; "
                    + "processing registered Bridge sign for arena '" + target.getId() + "'.");
        }

        // Always cancel to prevent vanilla sign editing.
        event.setCancelled(true);
        handleSignClick(event.getPlayer(), target, debug);
    }

    private void handleSignClick(Player player, Arena arena, boolean debug) {
        if (!arena.isFullyConfigured()) {
            if (debug) plugin.getLogger().info("[Bridge] Sign denied: arena '" + arena.getId() + "' not fully configured.");
            player.sendMessage(Component.text("§cThis arena is not fully configured yet."));
            return;
        }
        if (!plugin.getSchematicManager().hasSchematic(arena)) {
            if (debug) plugin.getLogger().info("[Bridge] Sign denied: arena '" + arena.getId() + "' has no schematic.");
            player.sendMessage(Component.text("§cThis arena has no saved schematic. Ask an admin to run /bridge save " + arena.getId()));
            return;
        }

        if (plugin.getMatchManager().isInMatch(player)) {
            if (debug) plugin.getLogger().info("[Bridge] Sign denied: " + player.getName() + " already in a match.");
            player.sendMessage(Component.text("§cYou are already in a match."));
            return;
        }

        if (plugin.getQueueManager().isQueued(player)) {
            String queued = plugin.getQueueManager().getQueuedArena(player);
            if (queued.equals(arena.getId())) {
                if (debug) plugin.getLogger().info("[Bridge] Sign: " + player.getName() + " leaving queue for '" + arena.getId() + "'.");
                plugin.getQueueManager().leave(player);
            } else {
                if (debug) plugin.getLogger().info("[Bridge] Sign denied: " + player.getName() + " already queued for '" + queued + "'.");
                player.sendMessage(Component.text("§cYou are already in a queue for §e" + queued + "§c. Leave it first."));
            }
            return;
        }

        if (arena.getState() == ArenaState.IN_GAME || arena.getState() == ArenaState.RESETTING) {
            if (debug) plugin.getLogger().info("[Bridge] Sign denied: arena '" + arena.getId() + "' state=" + arena.getState());
            player.sendMessage(Component.text("§cA match is already in progress in this arena."));
            return;
        }
        if (arena.getState() == ArenaState.DISABLED) {
            if (debug) plugin.getLogger().info("[Bridge] Sign denied: arena '" + arena.getId() + "' is disabled.");
            player.sendMessage(Component.text("§cThis arena is disabled."));
            return;
        }

        if (debug) plugin.getLogger().info("[Bridge] Sign: queuing " + player.getName() + " for arena '" + arena.getId() + "' (state=" + arena.getState() + ").");
        plugin.getQueueManager().join(player, arena);
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private String fmtLoc(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
