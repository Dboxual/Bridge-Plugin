package com.thebridge.queue;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaManager;
import com.thebridge.arena.ArenaState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QueueManager {

    private final TheBridgePlugin plugin;
    private final Map<String, List<UUID>> queues = new HashMap<>();
    private final Map<UUID, String> playerQueue = new HashMap<>();

    public QueueManager(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void join(Player player, Arena arena) {
        String id = arena.getId();
        UUID uid = player.getUniqueId();

        queues.computeIfAbsent(id, k -> new ArrayList<>()).add(uid);
        playerQueue.put(uid, id);
        player.sendMessage(Component.text("Joined queue for §e" + id + "§r. Position: " + getQueueSize(id) + "/2"));
        updateSigns(arena);

        List<UUID> queue = queues.get(id);
        if (queue.size() >= 2) {
            UUID red = queue.remove(0);
            UUID blue = queue.remove(0);
            playerQueue.remove(red);
            playerQueue.remove(blue);
            plugin.getMatchManager().createMatch(arena, red, blue);
            updateSigns(arena);
        }
    }

    public void leave(Player player) {
        UUID uid = player.getUniqueId();
        String id = playerQueue.remove(uid);
        if (id == null) return;

        List<UUID> queue = queues.get(id);
        if (queue != null) queue.remove(uid);

        player.sendMessage(Component.text("Left queue for §e" + id + "§r."));

        plugin.getArenaManager().getArena(id).ifPresent(this::updateSigns);
    }

    public boolean isQueued(Player player) {
        return playerQueue.containsKey(player.getUniqueId());
    }

    public String getQueuedArena(Player player) {
        return playerQueue.get(player.getUniqueId());
    }

    public int getQueueSize(String arenaId) {
        List<UUID> q = queues.get(arenaId);
        return q == null ? 0 : q.size();
    }

    public void updateSigns(Arena arena) {
        boolean inGame = arena.getState() == ArenaState.IN_GAME || arena.getState() == ArenaState.RESETTING;
        int size = getQueueSize(arena.getId());

        Component line1 = Component.text("[Bridge]", NamedTextColor.GOLD);
        Component line2 = Component.text(arena.getId(), NamedTextColor.WHITE);
        Component line3 = inGame
                ? Component.text("In Game", NamedTextColor.RED)
                : Component.text(size + "/2", NamedTextColor.GREEN);
        Component line4 = inGame
                ? Component.empty()
                : Component.text("Click to Join", NamedTextColor.YELLOW);

        for (Location loc : arena.getSignLocations()) {
            if (loc.getWorld() == null) continue;
            Block block = loc.getBlock();
            if (!(block.getState() instanceof Sign sign)) continue;
            var side = sign.getSide(Side.FRONT);
            side.line(0, line1);
            side.line(1, line2);
            side.line(2, line3);
            side.line(3, line4);
            sign.update();
        }
    }

    public void updateAllSigns() {
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            updateSigns(arena);
        }
    }
}
