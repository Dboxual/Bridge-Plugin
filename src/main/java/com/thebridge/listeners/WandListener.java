package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class WandListener implements Listener {

    private final TheBridgePlugin plugin;

    public WandListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    // ── pos1 (left-click) and pos2 (right-click) ──────────────────────────────
    //
    // In survival mode: LEFT_CLICK_BLOCK fires on first click before any block
    // damage. BlockDamageEvent is cancelled so no crack animation plays and
    // BlockBreakEvent never fires (block never breaks server-side).
    //
    // In creative mode: LEFT_CLICK_BLOCK also fires first. BlockBreakEvent fires
    // immediately after (instant break). The isSameBlock check in onBlockBreak
    // detects that pos1 was just set to this block and skips the duplicate message.

    @EventHandler(priority = EventPriority.HIGHEST)
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
            sendFeedback(player, "Pos1", loc);
            showIfComplete(player);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getWandManager().setPos2(player.getUniqueId(), loc);
            sendFeedback(player, "Pos2", loc);
            showIfComplete(player);
        }
    }

    // Creative-mode safety net: blocks break instantly so this fires even when
    // LEFT_CLICK_BLOCK was already handled. The isSameBlock guard prevents a
    // duplicate message when onInteract already set pos1 to this block.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getWandManager().isWand(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);

        Location loc = event.getBlock().getLocation();
        Location current = plugin.getWandManager().getPos1(player.getUniqueId());
        if (isSameBlock(current, loc)) return; // already set by onInteract this tick

        plugin.getWandManager().setPos1(player.getUniqueId(), loc);
        sendFeedback(player, "Pos1", loc);
        showIfComplete(player);
    }

    // Prevent crack animations in survival mode while holding the wand.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (plugin.getWandManager().isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendFeedback(Player player, String posLabel, Location loc) {
        player.sendMessage(
            Component.text("[Wand] ", NamedTextColor.GREEN)
                .append(Component.text(posLabel, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" → " + fmtLoc(loc), NamedTextColor.WHITE))
        );
    }

    private void showIfComplete(Player player) {
        if (plugin.getWandManager().hasSelection(player.getUniqueId())) {
            plugin.getWandManager().showSelectionOutline(player);
        }
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    private String fmtLoc(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return world + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }
}
