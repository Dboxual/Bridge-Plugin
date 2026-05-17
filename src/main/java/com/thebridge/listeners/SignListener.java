package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaState;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class SignListener implements Listener {

    private final TheBridgePlugin plugin;

    public SignListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof org.bukkit.block.Sign)) return;

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

        if (target == null) return;

        event.setCancelled(true);
        handleSignClick(event.getPlayer(), target);
    }

    private void handleSignClick(Player player, Arena arena) {
        if (!arena.isFullyConfigured()) {
            player.sendMessage(Component.text("§cThis arena is not fully configured yet."));
            return;
        }
        if (!plugin.getSchematicManager().hasSchematic(arena)) {
            player.sendMessage(Component.text("§cThis arena has no saved schematic. Ask an admin to run /bridge save " + arena.getId()));
            return;
        }

        if (plugin.getMatchManager().isInMatch(player)) {
            player.sendMessage(Component.text("§cYou are already in a match."));
            return;
        }

        if (plugin.getQueueManager().isQueued(player)) {
            String queued = plugin.getQueueManager().getQueuedArena(player);
            if (queued.equals(arena.getId())) {
                plugin.getQueueManager().leave(player);
            } else {
                player.sendMessage(Component.text("§cYou are already in a queue for §e" + queued + "§c. Leave it first."));
            }
            return;
        }

        if (arena.getState() == ArenaState.IN_GAME || arena.getState() == ArenaState.RESETTING) {
            player.sendMessage(Component.text("§cA match is already in progress in this arena."));
            return;
        }
        if (arena.getState() == ArenaState.DISABLED) {
            player.sendMessage(Component.text("§cThis arena is disabled."));
            return;
        }

        plugin.getQueueManager().join(player, arena);
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
