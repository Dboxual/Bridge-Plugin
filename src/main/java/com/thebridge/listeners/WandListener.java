package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class WandListener implements Listener {

    private final TheBridgePlugin plugin;

    public WandListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!plugin.getWandManager().isWand(player.getInventory().getItemInMainHand())) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true);

        Location loc = block.getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getWandManager().setPos1(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos1 set: ", NamedTextColor.GREEN)
                    .append(Component.text(fmtLoc(loc), NamedTextColor.YELLOW)));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getWandManager().setPos2(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos2 set: ", NamedTextColor.GREEN)
                    .append(Component.text(fmtLoc(loc), NamedTextColor.YELLOW)));
        }
    }

    // Cancel block breaks while holding the wand (survival mode protection).
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getWandManager().isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private String fmtLoc(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return world + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
