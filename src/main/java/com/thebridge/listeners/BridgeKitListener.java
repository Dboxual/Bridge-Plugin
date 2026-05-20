package com.thebridge.listeners;

import com.thebridge.TheBridgePlugin;
import com.thebridge.match.BridgeMatch;
import com.thebridge.match.MatchState;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class BridgeKitListener implements Listener {

    private final TheBridgePlugin plugin;

    public BridgeKitListener(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Golden apple override ─────────────────────────────────────────────────
    //
    // During an active Bridge match, eating a golden apple gives instant full
    // health + 2 absorption hearts instead of the vanilla Regeneration + Absorption
    // potion effects.  The vanilla event is cancelled and the item is consumed
    // manually so the player doesn't keep the apple.

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOLDEN_APPLE) return;
        Player player = event.getPlayer();
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;

        // Cancel vanilla effects (Regeneration II + slow Absorption tick).
        event.setCancelled(true);

        // Manually consume 1 apple — vanilla consumption was suppressed.
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off  = player.getInventory().getItemInOffHand();
        if (main.getType() == Material.GOLDEN_APPLE) {
            if (main.getAmount() > 1) main.setAmount(main.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
        } else if (off.getType() == Material.GOLDEN_APPLE) {
            if (off.getAmount() > 1) off.setAmount(off.getAmount() - 1);
            else player.getInventory().setItemInOffHand(null);
        }

        // Instant full health + 2 absorption hearts (4 HP).
        player.setHealth(20.0);
        player.setAbsorptionAmount(4.0);
    }

    // ── Arrow regeneration ────────────────────────────────────────────────────
    //
    // Each Bridge player has at most 1 arrow at a time.  Shooting it schedules a
    // 3.5-second regen back in BridgeMatch.  The regen is cancelled if the player
    // receives a fresh loadout (soft reset, respawn) before the delay fires.

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Arrow)) return;
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null || match.getState() != MatchState.ACTIVE) return;
        match.scheduleArrowRegen(player.getUniqueId());
    }

    // Prevent picking up stray arrows during any active match phase so players
    // cannot accumulate more than 1 arrow from the arena floor.
    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getItem().getItemStack().getType() != Material.ARROW) return;
        BridgeMatch match = plugin.getMatchManager().getMatch(player);
        if (match == null) return;
        MatchState s = match.getState();
        if (s == MatchState.COUNTDOWN || s == MatchState.ACTIVE || s == MatchState.RESETTING) {
            event.setCancelled(true);
        }
    }
}
