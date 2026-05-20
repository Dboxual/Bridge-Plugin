package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.match.BridgeMatch;
import com.thebridge.match.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class BridgeKitListener implements Listener {

    private final TheBridgePlugin plugin;

    public BridgeKitListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Golden apple override ─────────────────────────────────────────────────
    //
    // Let vanilla consume the apple normally (handles item removal reliably).
    // One tick later, override health to max and absorption to 4.0 (2 yellow
    // hearts) so the Bridge-specific effect takes hold.  Vanilla Regen II is
    // harmless once health is already full.

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOLDEN_APPLE) return;
        Player player = event.getPlayer();
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;

        // Wait 1 tick so vanilla has finished applying its effects before we override.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getState() == MatchState.ENDED) return;
            player.setHealth(20.0);
            player.setAbsorptionAmount(4.0);
        }, 1L);
    }

    // ── Arrow regeneration ────────────────────────────────────────────────────
    //
    // Shooting the arrow triggers a 3.5-second regen in BridgeMatch.
    // Picking up a dropped arrow is allowed only when the player has 0 arrows;
    // doing so cancels any pending regen so the count stays at 1.

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Arrow)) return;
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;
        match.scheduleArrowRegen(player.getUniqueId());
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getType() != Material.ARROW) return;
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null) return;
        MatchState s = match.getState();
        if (s != MatchState.COUNTDOWN && s != MatchState.ACTIVE && s != MatchState.RESETTING) return;

        if (player.getInventory().contains(Material.ARROW)) {
            // Already at the 1-arrow cap — block the pickup.
            event.setCancelled(true);
            return;
        }
        // Player has 0 arrows — allow pickup and cancel pending regen (arrow is back).
        match.cancelArrowRegen(player.getUniqueId());
    }
}
