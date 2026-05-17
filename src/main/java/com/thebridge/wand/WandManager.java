package com.thebridge.wand;

import com.thebridge.TheBridgePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("Bridge Setup Wand", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Left-click block: set pos1", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click block: set pos2", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
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

    public void setPos1(UUID uid, Location loc) {
        selections.computeIfAbsent(uid, k -> new Location[2])[0] = loc;
    }

    public void setPos2(UUID uid, Location loc) {
        selections.computeIfAbsent(uid, k -> new Location[2])[1] = loc;
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
}
