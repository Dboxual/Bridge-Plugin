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

import java.util.HashMap;
import java.util.Map;
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

    private final Map<UUID, Long> lastGoalTime = new HashMap<>();
    private static final long GOAL_COOLDOWN_MS = 2000;

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

        teleportSafe(redPlayer, arena.getRedSpawn());
        teleportSafe(bluePlayer, arena.getBlueSpawn());

        Player red = Bukkit.getPlayer(redPlayer);
        Player blue = Bukkit.getPlayer(bluePlayer);
        if (red != null) red.getInventory().clear();
        if (blue != null) blue.getInventory().clear();

        broadcast(Component.text("Match starting! §cRed§r vs §9Blue§r in arena §e" + arena.getId() + "§r."));
        startCountdown(() -> state = MatchState.ACTIVE);
    }

    public void onGoalEntered(Player player) {
        if (state != MatchState.ACTIVE) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastGoalTime.get(uid);
        if (lastTime != null && now - lastTime < GOAL_COOLDOWN_MS) return;
        lastGoalTime.put(uid, now);

        Location loc = player.getLocation();

        if (uid.equals(redPlayer) && arena.isInsideBlueGoal(loc)) {
            redScore++;
            broadcast(Component.text("§cRed §rscored! §c" + redScore + " §r- §9" + blueScore));
        } else if (uid.equals(bluePlayer) && arena.isInsideRedGoal(loc)) {
            blueScore++;
            broadcast(Component.text("§9Blue §rscored! §c" + redScore + " §r- §9" + blueScore));
        } else {
            return;
        }

        if (redScore >= pointsToWin) {
            endMatch(redPlayer);
        } else if (blueScore >= pointsToWin) {
            endMatch(bluePlayer);
        } else {
            resetAndContinue();
        }
    }

    public void onPlayerDisconnect(UUID uid) {
        if (state == MatchState.ENDED) return;
        UUID winner = uid.equals(redPlayer) ? bluePlayer : redPlayer;
        broadcast(Component.text("A player left — opponent wins by forfeit."));
        endMatch(winner);
    }

    private void resetAndContinue() {
        state = MatchState.RESETTING;
        cancelCountdown();
        broadcast(Component.text("Resetting arena..."));

        plugin.getSchematicManager().resetArena(arena).whenComplete((v, ex) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (state == MatchState.ENDED) return;
                    if (ex != null) {
                        plugin.getLogger().severe("[Bridge] Schematic reset failed mid-match for '" + arena.getId() + "': " + ex.getMessage());
                    }
                    teleportSafe(redPlayer, arena.getRedSpawn());
                    teleportSafe(bluePlayer, arena.getBlueSpawn());
                    startCountdown(() -> state = MatchState.ACTIVE);
                })
        );
    }

    public void endMatch(UUID winnerUid) {
        if (state == MatchState.ENDED) return;
        state = MatchState.ENDED;
        cancelCountdown();

        Player winner = Bukkit.getPlayer(winnerUid);
        String winnerName = winner != null ? winner.getName()
                : (winnerUid.equals(redPlayer) ? "Red" : "Blue");
        broadcast(Component.text("§e" + winnerName + " §rwins! Final: §c" + redScore + " §r- §9" + blueScore));

        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.getInventory().clear();
                if (arena.getLobbySpawn() != null) {
                    teleportSafe(uid, arena.getLobbySpawn());
                }
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
                if (state == MatchState.ENDED) { cancel(); return; }
                if (tick[0] > 0) {
                    sendActionBar(Component.text(tick[0], NamedTextColor.YELLOW, TextDecoration.BOLD));
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

    // ── Safe teleport ─────────────────────────────────────────────────────────

    // Marks the teleport as plugin-initiated so MatchListener does not treat it as a forfeit.
    private void teleportSafe(UUID uid, Location loc) {
        if (loc == null) return;
        plugin.getMatchManager().markPluginTeleport(uid);
        Player p = Bukkit.getPlayer(uid);
        if (p != null) p.teleport(loc);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMatchManager().clearPluginTeleport(uid));
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

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

    // ── Getters ───────────────────────────────────────────────────────────────

    public Arena getArena() { return arena; }
    public UUID getRedPlayer() { return redPlayer; }
    public UUID getBluePlayer() { return bluePlayer; }
    public int getRedScore() { return redScore; }
    public int getBlueScore() { return blueScore; }
    public MatchState getState() { return state; }
}
