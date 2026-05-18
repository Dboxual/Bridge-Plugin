package com.thebridge.wand;

import com.thebridge.TheBridgePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WandManager {

    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public WandManager(TheBridgePlugin plugin) {
        this.wandKey = new NamespacedKey(plugin, "setup_wand");
    }

    // ── Item ──────────────────────────────────────────────────────────────────

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("Bridge Setup Wand", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Left-click block → pos1", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click block → pos2", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Then: /bridge setredgoal or setbluegoal", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    // ── Selection storage ─────────────────────────────────────────────────────

    public void setPos1(UUID uid, Location loc) {
        selections.computeIfAbsent(uid, k -> new Location[2])[0] = loc.getBlock().getLocation();
    }

    public void setPos2(UUID uid, Location loc) {
        selections.computeIfAbsent(uid, k -> new Location[2])[1] = loc.getBlock().getLocation();
    }

    public Location getPos1(UUID uid) {
        Location[] sel = selections.get(uid);
        return sel != null ? sel[0] : null;
    }

    public Location getPos2(UUID uid) {
        Location[] sel = selections.get(uid);
        return sel != null ? sel[1] : null;
    }

    public boolean hasSelection(UUID uid) {
        return getPos1(uid) != null && getPos2(uid) != null;
    }

    public void clearSelection(UUID uid) {
        selections.remove(uid);
    }

    // ── Visual feedback ───────────────────────────────────────────────────────

    /**
     * Draws the cuboid outline of the current selection for only the given player.
     * Uses lime-green DUST particles along all 12 edges of the bounding box.
     * Call only when hasSelection(player.getUniqueId()) is true.
     */
    public void showSelectionOutline(Player player) {
        UUID uid = player.getUniqueId();
        Location p1 = getPos1(uid);
        Location p2 = getPos2(uid);
        if (p1 == null || p2 == null || p1.getWorld() == null) return;
        if (!p1.getWorld().equals(p2.getWorld())) return;

        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.5f);

        double step = 0.5;
        double x1 = Math.min(p1.getBlockX(), p2.getBlockX());
        double x2 = Math.max(p1.getBlockX(), p2.getBlockX()) + 1;
        double y1 = Math.min(p1.getBlockY(), p2.getBlockY());
        double y2 = Math.max(p1.getBlockY(), p2.getBlockY()) + 1;
        double z1 = Math.min(p1.getBlockZ(), p2.getBlockZ());
        double z2 = Math.max(p1.getBlockZ(), p2.getBlockZ()) + 1;

        // 4 edges along X axis (at y1/y2, z1/z2 corners)
        for (double x = x1; x <= x2; x += step) {
            dp(player, x, y1, z1, dust); dp(player, x, y2, z1, dust);
            dp(player, x, y1, z2, dust); dp(player, x, y2, z2, dust);
        }
        // 4 edges along Y axis (at x1/x2, z1/z2 corners)
        for (double y = y1; y <= y2; y += step) {
            dp(player, x1, y, z1, dust); dp(player, x2, y, z1, dust);
            dp(player, x1, y, z2, dust); dp(player, x2, y, z2, dust);
        }
        // 4 edges along Z axis (at x1/x2, y1/y2 corners)
        for (double z = z1; z <= z2; z += step) {
            dp(player, x1, y1, z, dust); dp(player, x2, y1, z, dust);
            dp(player, x1, y2, z, dust); dp(player, x2, y2, z, dust);
        }
    }

    private void dp(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
    }
}
