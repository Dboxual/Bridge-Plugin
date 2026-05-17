package com.thebridge.match;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BridgeMatch {

    private final TheBridgePlugin plugin;
    private final Arena arena;
    private final UUID redPlayer;
    private final UUID bluePlayer;

    private int redScore = 0;
    private int blueScore = 0;
    private final int pointsToWin;

    private MatchState state = MatchState.COUNTDOWN;
    private BukkitRunnable countdownTask;

    public BridgeMatch(TheBridgePlugin plugin, Arena arena, UUID redPlayer, UUID bluePlayer) {
        this.plugin = plugin;
        this.arena = arena;
        this.redPlayer = redPlayer;
        this.bluePlayer = bluePlayer;
        this.pointsToWin = plugin.getConfig().getInt("settings.points-to-win", 5);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        arena.setState(ArenaState.IN_GAME);
        plugin.getQueueManager().updateSigns(arena);

        Player red = Bukkit.getPlayer(redPlayer);
        Player blue = Bukkit.getPlayer(bluePlayer);

        if (red != null) {
            red.getInventory().clear();
            red.teleport(arena.getRedSpawn());
        }
        if (blue != null) {
            blue.getInventory().clear();
            blue.teleport(arena.getBlueSpawn());
        }

        broadcast(Component.text("Match starting! §cRed§r vs §9Blue§r in arena §e" + arena.getId() + "§r."));
        startCountdown(() -> state = MatchState.ACTIVE);
    }

    public void onGoalEntered(Player player) {
        if (state != MatchState.ACTIVE) return;

        UUID uid = player.getUniqueId();
        boolean scoredGoal;

        if (uid.equals(redPlayer) && isSameBlock(player.getLocation(), arena.getBlueGoal())) {
            redScore++;
            scoredGoal = true;
            broadcast(Component.text("§cRed §rscored! §c" + redScore + " §r- §9" + blueScore));
        } else if (uid.equals(bluePlayer) && isSameBlock(player.getLocation(), arena.getRedGoal())) {
            blueScore++;
            scoredGoal = true;
            broadcast(Component.text("§9Blue §rscored! §c" + redScore + " §r- §9" + blueScore));
        } else {
            return;
        }

        if (!scoredGoal) return;

        if (redScore >= pointsToWin) {
            endMatch(redPlayer);
        } else if (blueScore >= pointsToWin) {
            endMatch(bluePlayer);
        } else {
            resetAndContinue();
        }
    }

    public void onPlayerDisconnect(UUID uid) {
        UUID winner = uid.equals(redPlayer) ? bluePlayer : redPlayer;
        broadcast(Component.text("A player disconnected — opponent wins by forfeit."));
        endMatch(winner);
    }

    private void resetAndContinue() {
        state = MatchState.RESETTING;
        broadcast(Component.text("Resetting arena..."));
        plugin.getSchematicManager().resetArena(arena).whenComplete((v, ex) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ex != null) {
                        plugin.getLogger().severe("Schematic reset failed: " + ex.getMessage());
                    }
                    Player red = Bukkit.getPlayer(redPlayer);
                    Player blue = Bukkit.getPlayer(bluePlayer);
                    if (red != null) red.teleport(arena.getRedSpawn());
                    if (blue != null) blue.teleport(arena.getBlueSpawn());
                    startCountdown(() -> state = MatchState.ACTIVE);
                })
        );
    }

    public void endMatch(UUID winnerUid) {
        state = MatchState.ENDED;
        cancelCountdown();

        Player winner = Bukkit.getPlayer(winnerUid);
        String winnerName = winner != null ? winner.getName()
                : (winnerUid.equals(redPlayer) ? "Red" : "Blue");
        broadcast(Component.text("§e" + winnerName + " §rwins! Final score: §c" + redScore + " §r- §9" + blueScore));

        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.getInventory().clear();
                if (arena.getLobbySpawn() != null) p.teleport(arena.getLobbySpawn());
            }
        }

        arena.setState(ArenaState.WAITING);
        plugin.getQueueManager().updateSigns(arena);
        plugin.getMatchManager().removeMatch(this);
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void startCountdown(Runnable onFinish) {
        cancelCountdown();
        int seconds = plugin.getConfig().getInt("settings.countdown-seconds", 5);
        int[] tick = {seconds};

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (tick[0] > 0) {
                    Component msg = Component.text(tick[0], NamedTextColor.YELLOW, TextDecoration.BOLD);
                    sendActionBar(msg);
                    tick[0]--;
                } else {
                    sendActionBar(Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD));
                    cancel();
                    countdownTask = null;
                    onFinish.run();
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void broadcast(Component msg) {
        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void sendActionBar(Component msg) {
        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.sendActionBar(msg);
        }
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Arena getArena() { return arena; }
    public UUID getRedPlayer() { return redPlayer; }
    public UUID getBluePlayer() { return bluePlayer; }
    public MatchState getState() { return state; }
}
