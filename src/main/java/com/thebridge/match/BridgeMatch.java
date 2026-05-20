package com.thebridge.match;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Players in this set cannot change their XYZ position (rotation still allowed).
    private final Set<UUID> frozenPlayers = new HashSet<>();

    private BukkitRunnable voidCheckTask = null;

    // Pending arrow-regen tasks keyed by player UUID.  Each task updates the XP bar
    // every tick as a countdown timer, then gives back 1 arrow after 70 ticks (3.5 s).
    // Cancelled whenever a fresh loadout is given (kit already includes 1 arrow).
    private final Map<UUID, BukkitRunnable> arrowRegenTasks = new HashMap<>();
    private final Map<UUID, Integer>        savedXpLevels   = new HashMap<>();
    private final Map<UUID, Float>          savedXpProgress = new HashMap<>();

    // Snapshots of the configured release zone blocks, captured at match start.
    // Restored before each round (so players land on solid ground) then cleared
    // (set to AIR) after the countdown so players fall naturally into the arena.
    // Falls back to a 3×3 platform at Y−1 under spawn if no zone is configured.
    private List<BlockSnapshot> redReleaseSnapshot  = new ArrayList<>();
    private List<BlockSnapshot> blueReleaseSnapshot = new ArrayList<>();

    // Sidebar scoreboard — assigned to both players for the duration of the match.
    private Scoreboard scoreboard;
    private Objective  objective;

    // Immutable snapshot of one block's position and material.
    private record BlockSnapshot(World world, int x, int y, int z, BlockData data) {}

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

        captureReleaseZones();

        Player redP  = Bukkit.getPlayer(redPlayer);
        Player blueP = Bukkit.getPlayer(bluePlayer);
        plugin.getLogger().info("[Bridge] Match start — red: "
                + (redP  != null ? redP.getName()  : redPlayer)  + " → " + fmtLoc(arena.getRedSpawn())
                + "  blue: "
                + (blueP != null ? blueP.getName() : bluePlayer) + " → " + fmtLoc(arena.getBlueSpawn()));

        // Teleport to bridge-world lobby first so Multiverse-Inventories can switch per-world
        // inventories before we clear or assign anything. Loadout and countdown happen 2 ticks
        // later, by which point Multiverse has replaced the survival inventory with the bridge inventory.
        Location lobby = arena.getLobbySpawn();
        if (lobby != null) {
            teleportSafe(redPlayer, lobby);
            teleportSafe(bluePlayer, lobby);
        } else {
            teleportSafe(redPlayer, arena.getRedSpawn());
            teleportSafe(bluePlayer, arena.getBlueSpawn());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == MatchState.ENDED) return;

            // Safety guard: confirm the world switch completed before touching inventory.
            World bridgeWorld = arena.getRedSpawn() != null ? arena.getRedSpawn().getWorld() : null;
            for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && bridgeWorld != null && !p.getWorld().equals(bridgeWorld)) {
                    debugAdmin("World mismatch after lobby teleport, retrying: " + p.getName());
                    teleportSafe(uid, uid.equals(redPlayer) ? arena.getRedSpawn() : arena.getBlueSpawn());
                }
            }

            giveLoadout(redPlayer, Team.RED);
            giveLoadout(bluePlayer, Team.BLUE);

            teleportSafe(redPlayer, arena.getRedSpawn());
            teleportSafe(bluePlayer, arena.getBlueSpawn());

            // Defensive re-teleport 1 tick later in case the initial teleport was dropped.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == MatchState.ENDED) return;
                teleportSafe(redPlayer, arena.getRedSpawn());
                teleportSafe(bluePlayer, arena.getBlueSpawn());
            }, 1L);

            freezePlayer(redPlayer);
            freezePlayer(bluePlayer);

            setupScoreboard();
            startVoidCheck();

            broadcast(Component.text("§aMatch starting! §cRed §rvs §9Blue §rin arena §e" + arena.getId()));
            startMatchCountdown(() -> {
                clearReleaseZones();
                unfreezePlayer(redPlayer);
                unfreezePlayer(bluePlayer);
                state = MatchState.ACTIVE;
            });
        }, 2L);
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    // Called by GoalListener with both the FROM and TO positions from the move event.
    // We sweep the full movement path (from.Y → to.Y) and check a 3×3 XZ footprint
    // at each Y level so that:
    //   • players walking into the goal register reliably
    //   • players falling through the goal at high speed cannot skip past it
    //   • edge cases where the player centre is just outside the region still score
    public void onGoalEntered(Player player, Location from, Location to) {
        if (state != MatchState.ACTIVE) return;

        UUID uid = player.getUniqueId();

        // Cooldown — silent, prevents rapid re-fire while softReset() teleports the player.
        long now = System.currentTimeMillis();
        Long lastTime = lastGoalTime.get(uid);
        if (lastTime != null && now - lastTime < GOAL_COOLDOWN_MS) return;

        boolean isRed  = uid.equals(redPlayer);
        boolean isBlue = uid.equals(bluePlayer);
        if (!isRed && !isBlue) return;

        if (!touchesOpponentGoal(isRed, from, to)) return;

        // ── Goal confirmed ────────────────────────────────────────────────────
        lastGoalTime.put(uid, now);

        Team scoringTeam;
        UUID opponentUid;

        if (isRed) {
            redScore++;
            scoringTeam = Team.RED;
            opponentUid = bluePlayer;
        } else {
            blueScore++;
            scoringTeam = Team.BLUE;
            opponentUid = redPlayer;
        }

        plugin.getLogger().info("[Bridge] GOAL: " + player.getName()
                + " (" + scoringTeam + ") → " + redScore + "-" + blueScore);

        updateScoreboard();
        broadcastGoal(player, opponentUid, scoringTeam);

        if (redScore >= pointsToWin) {
            endMatch(redPlayer);
        } else if (blueScore >= pointsToWin) {
            endMatch(bluePlayer);
        } else {
            softReset();
        }
    }

    // Sweep the player's 3×3 XZ footprint across every Y block between from and to.
    // Returns true the moment any block in that footprint intersects the opponent's
    // goal region.  Capped at 16 Y levels to guard against extreme fall distances.
    private boolean touchesOpponentGoal(boolean isRed, Location from, Location to) {
        World world = to.getWorld();
        if (world == null) return false;

        int footX = to.getBlockX();
        int footZ = to.getBlockZ();
        int minY  = Math.min((int) Math.floor(from.getY()), (int) Math.floor(to.getY()));
        int maxY  = Math.min(
                Math.max((int) Math.floor(from.getY()), (int) Math.floor(to.getY())),
                minY + 16);

        // Reuse a single Location object to avoid allocating one per loop iteration.
        Location check = new Location(world, 0, 0, 0);

        for (int y = minY; y <= maxY; y++) {
            check.setY(y);
            for (int dx = -1; dx <= 1; dx++) {
                check.setX(footX + dx);
                for (int dz = -1; dz <= 1; dz++) {
                    check.setZ(footZ + dz);
                    boolean hit = isRed
                            ? arena.isInsideBlueGoal(check)
                            : arena.isInsideRedGoal(check);
                    if (hit) return true;
                }
            }
        }
        return false;
    }

    public void onPlayerDisconnect(UUID uid) {
        if (state == MatchState.ENDED) return;
        UUID winner = uid.equals(redPlayer) ? bluePlayer : redPlayer;
        broadcast(Component.text("§cA player disconnected — opponent wins by forfeit."));
        endMatch(winner);
    }

    // ── Soft reset (between rounds) ───────────────────────────────────────────
    //
    // No schematic paste. Players are repositioned to spawns, healed, re-given
    // loadout, frozen for a 3-second countdown, then the configured release zone
    // blocks are removed so players fall into the arena naturally.

    private void softReset() {
        state = MatchState.RESETTING;
        cancelCountdown();

        teleportSafe(redPlayer, arena.getRedSpawn());
        teleportSafe(bluePlayer, arena.getBlueSpawn());

        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) clearEffects(p);
        }

        // Put release zone blocks back so players land on solid ground.
        restoreReleaseZones();

        giveLoadout(redPlayer, Team.RED);
        giveLoadout(bluePlayer, Team.BLUE);

        freezePlayer(redPlayer);
        freezePlayer(bluePlayer);

        startSoftCountdown(() -> {
            if (state == MatchState.ENDED) return;
            // Open the gates — players fall into the arena.
            clearReleaseZones();
            unfreezePlayer(redPlayer);
            unfreezePlayer(bluePlayer);
            state = MatchState.ACTIVE;
        });
    }

    // ── Match end ─────────────────────────────────────────────────────────────

    public void endMatch(UUID winnerUid) {
        if (state == MatchState.ENDED) return;
        state = MatchState.ENDED;
        cancelCountdown();
        cancelAllArrowRegens();
        if (voidCheckTask != null) { voidCheckTask.cancel(); voidCheckTask = null; }

        unfreezePlayer(redPlayer);
        unfreezePlayer(bluePlayer);

        Player winner = Bukkit.getPlayer(winnerUid);
        UUID loserUid = winnerUid.equals(redPlayer) ? bluePlayer : redPlayer;
        Player loser  = Bukkit.getPlayer(loserUid);

        String winnerName = winner != null ? winner.getName()
                : (winnerUid.equals(redPlayer) ? "Red" : "Blue");

        broadcast(Component.text("§6" + winnerName + " §ewins! Final: §c" + redScore + " §e- §9" + blueScore));

        if (winner != null) {
            winner.showTitle(Title.title(
                    Component.text("VICTORY!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("Score: " + redScore + " - " + blueScore, NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(800))
            ));
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        if (loser != null) {
            loser.showTitle(Title.title(
                    Component.text("DEFEAT", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(winnerName + " wins " + redScore + " - " + blueScore, NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(800))
            ));
            loser.playSound(loser.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.0f);
        }

        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                // Clear game kit while still in bridge_world so Multiverse-Inventories saves
                // an empty bridge inventory. The survival inventory is restored automatically
                // by Multiverse on the player's next world switch back to survival_world.
                p.getInventory().clear();
                debugAdmin("Inventory CLEARED (game kit removed): player=" + p.getName());
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (arena.getLobbySpawn() != null) teleportSafe(uid, arena.getLobbySpawn());
            }
        }

        scoreboard = null;
        objective  = null;
        clearArenaEntities();
        plugin.getMatchManager().removeMatch(this);

        // Full schematic reset — FAWE async, then arena returns to WAITING.
        arena.setState(ArenaState.RESETTING);
        plugin.getQueueManager().updateSigns(arena);

        plugin.getSchematicManager().resetArena(arena).whenComplete((v, ex) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ex != null) {
                        plugin.getLogger().severe("[Bridge] End-of-match reset failed for '"
                                + arena.getId() + "': " + ex.getMessage());
                    }
                    arena.setState(ArenaState.WAITING);
                    plugin.getQueueManager().updateSigns(arena);
                })
        );
    }

    // ── Freeze mechanics ──────────────────────────────────────────────────────

    public void freezePlayer(UUID uid)   { frozenPlayers.add(uid); }
    public void unfreezePlayer(UUID uid) { frozenPlayers.remove(uid); }
    public boolean isFrozen(UUID uid)    { return frozenPlayers.contains(uid); }

    // ── Release zone snapshot system ──────────────────────────────────────────
    //
    // Snapshots are built once at match start. During each soft reset:
    //   1. restoreReleaseZones() — re-places the captured blocks so the floor exists.
    //   2. clearReleaseZones()   — sets them all to AIR so players fall through.
    //
    // If a team has no configured release region the 3×3 fallback is used and a
    // warning is logged so the admin knows to configure the zones.

    private void captureReleaseZones() {
        if (arena.hasRedRelease()) {
            redReleaseSnapshot = captureRegion(arena.getRedRelease1(), arena.getRedRelease2());
        } else {
            redReleaseSnapshot = captureFloor3x3(arena.getRedSpawn());
            plugin.getLogger().warning("[Bridge] Arena '" + arena.getId()
                    + "' has no red release zone — using 3×3 fallback under red spawn. "
                    + "Run /bridge setredrelease <arena> to configure it.");
        }
        plugin.getLogger().info("[Bridge] Captured " + redReleaseSnapshot.size()
                + " red release blocks for arena '" + arena.getId() + "'.");

        if (arena.hasBlueRelease()) {
            blueReleaseSnapshot = captureRegion(arena.getBlueRelease1(), arena.getBlueRelease2());
        } else {
            blueReleaseSnapshot = captureFloor3x3(arena.getBlueSpawn());
            plugin.getLogger().warning("[Bridge] Arena '" + arena.getId()
                    + "' has no blue release zone — using 3×3 fallback under blue spawn. "
                    + "Run /bridge setbluerelease <arena> to configure it.");
        }
        plugin.getLogger().info("[Bridge] Captured " + blueReleaseSnapshot.size()
                + " blue release blocks for arena '" + arena.getId() + "'.");
    }

    private List<BlockSnapshot> captureRegion(Location p1, Location p2) {
        List<BlockSnapshot> list = new ArrayList<>();
        if (p1 == null || p2 == null || p1.getWorld() == null) return list;
        World w = p1.getWorld();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX()), maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY()), maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ()), maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    list.add(new BlockSnapshot(w, x, y, z, w.getBlockAt(x, y, z).getBlockData().clone()));
        return list;
    }

    private List<BlockSnapshot> captureFloor3x3(Location spawn) {
        List<BlockSnapshot> list = new ArrayList<>();
        if (spawn == null || spawn.getWorld() == null) return list;
        World w = spawn.getWorld();
        int bx = spawn.getBlockX(), by = spawn.getBlockY() - 1, bz = spawn.getBlockZ();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                list.add(new BlockSnapshot(w, bx + dx, by, bz + dz,
                        w.getBlockAt(bx + dx, by, bz + dz).getBlockData().clone()));
        return list;
    }

    private void restoreReleaseZones() {
        restoreSnapshot(redReleaseSnapshot);
        restoreSnapshot(blueReleaseSnapshot);
        debugAdmin("Release RESTORED: arena=" + arena.getId()
                + " red=" + redReleaseSnapshot.size() + " blue=" + blueReleaseSnapshot.size() + " blocks");
    }

    private void clearReleaseZones() {
        clearAndDebug("RED",  redReleaseSnapshot,  arena.hasRedRelease());
        clearAndDebug("BLUE", blueReleaseSnapshot, arena.hasBlueRelease());
    }

    private void clearAndDebug(String team, List<BlockSnapshot> snapshot, boolean hasConfigured) {
        if (snapshot.isEmpty()) {
            String reason = hasConfigured
                    ? "region saved 0 blocks — verify /bridge setredrelease / setbluerelease selection"
                    : "no release zone configured — run /bridge setredrelease or setbluerelease";
            debugAdmin("Release CLEAR SKIPPED: arena=" + arena.getId() + " team=" + team + " — " + reason);
            return;
        }
        String world = snapshot.get(0).world().getName();
        clearSnapshot(snapshot);
        debugAdmin("Release CLEARED: arena=" + arena.getId()
                + " team=" + team + " world=" + world + " blocks=" + snapshot.size());
    }

    private void restoreSnapshot(List<BlockSnapshot> snapshots) {
        for (BlockSnapshot s : snapshots)
            s.world().getBlockAt(s.x(), s.y(), s.z()).setBlockData(s.data().clone(), false);
    }

    private void clearSnapshot(List<BlockSnapshot> snapshots) {
        for (BlockSnapshot s : snapshots)
            s.world().getBlockAt(s.x(), s.y(), s.z()).setType(Material.AIR, false);
    }

    // ── Loadout ───────────────────────────────────────────────────────────────

    private void giveLoadout(UUID uid, Team team) {
        // Cancel any pending arrow regen — the fresh kit already includes 1 arrow.
        cancelArrowRegen(uid);

        Player p = Bukkit.getPlayer(uid);
        if (p == null) return;
        p.getInventory().clear();
        debugAdmin("Inventory CLEARED (loadout): player=" + p.getName() + " team=" + team);

        Material blockMat   = team == Team.RED ? Material.RED_TERRACOTTA   : Material.BLUE_TERRACOTTA;
        Color    armorColor = team == Team.RED ? Color.fromRGB(180, 20, 20) : Color.fromRGB(20, 20, 200);

        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pickMeta = pick.getItemMeta();
        pickMeta.addEnchant(Enchantment.EFFICIENCY, 2, true);
        pick.setItemMeta(pickMeta);

        p.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        p.getInventory().setItem(1, new ItemStack(Material.BOW));
        p.getInventory().setItem(2, new ItemStack(blockMat, 32));
        p.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 3));
        p.getInventory().setItem(4, pick);
        p.getInventory().setItem(8, new ItemStack(Material.ARROW, 1));

        p.getInventory().setHelmet(dyeArmor(new ItemStack(Material.LEATHER_HELMET),     armorColor));
        p.getInventory().setChestplate(dyeArmor(new ItemStack(Material.LEATHER_CHESTPLATE), armorColor));
        p.getInventory().setLeggings(dyeArmor(new ItemStack(Material.LEATHER_LEGGINGS),   armorColor));
        p.getInventory().setBoots(dyeArmor(new ItemStack(Material.LEATHER_BOOTS),       armorColor));

        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        debugAdmin("Loadout APPLIED: player=" + p.getName() + " team=" + team + " arena=" + arena.getId());
    }

    private ItemStack dyeArmor(ItemStack item, Color color) {
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    private void clearEffects(Player player) {
        for (PotionEffect fx : player.getActivePotionEffects()) {
            player.removePotionEffect(fx.getType());
        }
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        player.setFreezeTicks(0);
        player.setNoDamageTicks(0);
        player.setAbsorptionAmount(0.0);
        player.setVelocity(new Vector(0, 0, 0));
    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    private void setupScoreboard() {
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective  = scoreboard.registerNewObjective("bridge", Criteria.DUMMY,
                Component.text("The Bridge", NamedTextColor.GOLD, TextDecoration.BOLD));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Hide the numeric scores on the right side of the sidebar.
        this.objective.numberFormat(NumberFormat.blank());
        updateScoreboard();
        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.setScoreboard(scoreboard);
        }
    }

    private void updateScoreboard() {
        if (objective == null) return;
        for (String entry : new ArrayList<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }
        Player red  = Bukkit.getPlayer(redPlayer);
        Player blue = Bukkit.getPlayer(bluePlayer);
        String redName  = red  != null ? red.getName()  : "Red";
        String blueName = blue != null ? blue.getName() : "Blue";

        objective.getScore("§7Arena: §e" + arena.getId()).setScore(6);
        objective.getScore("§8§r").setScore(5);
        objective.getScore("§c" + redName  + ": §f" + redScore).setScore(4);
        objective.getScore("§9" + blueName + ": §f" + blueScore).setScore(3);
        objective.getScore("§8§r ").setScore(2);
        objective.getScore("§7First to §e" + pointsToWin).setScore(1);
    }

    // ── Countdowns ────────────────────────────────────────────────────────────

    private void startMatchCountdown(Runnable onFinish) {
        cancelCountdown();
        int seconds = plugin.getConfig().getInt("settings.countdown-seconds", 5);
        int[] tick = {seconds};

        countdownTask = new BukkitRunnable() {
            @Override public void run() {
                if (state == MatchState.ENDED) { cancel(); return; }
                if (tick[0] > 0) {
                    sendTitle(
                        Component.text(String.valueOf(tick[0]), NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.text("Match starting...", NamedTextColor.GRAY)
                    );
                    sendActionBar(Component.text("Stand by! " + tick[0] + "s", NamedTextColor.YELLOW));
                    playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    tick[0]--;
                } else {
                    sendTitle(Component.text("FIGHT!", NamedTextColor.GREEN, TextDecoration.BOLD), Component.empty());
                    playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    cancel();
                    countdownTask = null;
                    onFinish.run();
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void startSoftCountdown(Runnable onFinish) {
        cancelCountdown();
        int[] tick = {3};

        countdownTask = new BukkitRunnable() {
            @Override public void run() {
                if (state == MatchState.ENDED) { cancel(); return; }
                if (tick[0] > 0) {
                    sendTitle(
                        Component.text(String.valueOf(tick[0]), NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("Next round...", NamedTextColor.GRAY)
                    );
                    playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, tick[0] == 1 ? 1.4f : 0.9f);
                    tick[0]--;
                } else {
                    sendTitle(Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD), Component.empty());
                    playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    cancel();
                    countdownTask = null;
                    onFinish.run();
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
    }

    // ── Arena cleanup ─────────────────────────────────────────────────────────

    private void clearArenaEntities() {
        if (!arena.hasRegion()) return;
        World world = arena.getWorld();
        if (world == null) return;
        world.getEntities().stream()
                .filter(e -> !(e instanceof Player))
                .filter(e -> arena.isInsideArena(e.getLocation()))
                .forEach(Entity::remove);
    }

    // ── Safe teleport ─────────────────────────────────────────────────────────

    private void teleportSafe(UUID uid, Location loc) {
        if (loc == null) return;
        plugin.getMatchManager().markPluginTeleport(uid);
        Player p = Bukkit.getPlayer(uid);
        if (p != null) p.teleport(loc);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMatchManager().clearPluginTeleport(uid));
    }

    // ── Broadcast / title / sound helpers ─────────────────────────────────────

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

    private void sendTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO));
        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.showTitle(t);
        }
    }

    private void playSound(Sound sound, float volume, float pitch) {
        for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private void broadcastGoal(Player scorer, UUID opponentUid, Team scoringTeam) {
        Player opponent = Bukkit.getPlayer(opponentUid);
        String scoreLine = "§c" + redScore + " §f- §9" + blueScore;

        scorer.showTitle(Title.title(
                Component.text("GOAL!", scoringTeam.color, TextDecoration.BOLD),
                Component.text("Score: " + scoreLine, NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(500))
        ));
        scorer.sendMessage(Component.text("§a+1 point! Score: " + scoreLine));
        scorer.playSound(scorer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        if (opponent != null) {
            NamedTextColor oppColor = scoringTeam == Team.RED ? NamedTextColor.RED : NamedTextColor.BLUE;
            opponent.showTitle(Title.title(
                    Component.text(scorer.getName() + " scored!", oppColor, TextDecoration.BOLD),
                    Component.text("Score: " + scoreLine, NamedTextColor.WHITE),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(500))
            ));
            opponent.sendMessage(Component.text("§c" + scorer.getName() + " scored! Score: " + scoreLine));
            opponent.playSound(opponent.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    // ── Void check ────────────────────────────────────────────────────────────

    private void startVoidCheck() {
        if (!arena.hasVoidLevel()) return;
        voidCheckTask = new BukkitRunnable() {
            @Override public void run() {
                if (state == MatchState.ENDED) { cancel(); return; }
                if (state != MatchState.ACTIVE) return;
                int voidY = arena.getVoidLevel();
                for (UUID uid : new UUID[]{redPlayer, bluePlayer}) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p == null || !p.isOnline()) continue;
                    if (p.getLocation().getY() <= voidY) {
                        handleVoidFall(uid);
                    }
                }
            }
        };
        voidCheckTask.runTaskTimer(plugin, 5L, 5L);
    }

    private void handleVoidFall(UUID uid) {
        Player p = Bukkit.getPlayer(uid);
        if (p == null) return;
        Team team = uid.equals(redPlayer) ? Team.RED : Team.BLUE;
        Location spawn = uid.equals(redPlayer) ? arena.getRedSpawn() : arena.getBlueSpawn();
        plugin.getLogger().info("[Bridge] " + p.getName() + " fell below void Y=" + arena.getVoidLevel()
                + " — respawning at " + team + " spawn " + fmtLoc(spawn));
        p.setFallDistance(0f);
        clearEffects(p);
        teleportSafe(uid, spawn);
        giveLoadout(uid, team);
    }

    // ── Death respawn support ─────────────────────────────────────────────────

    public Location getTeamSpawn(UUID uid) {
        if (uid.equals(redPlayer)) return arena.getRedSpawn();
        if (uid.equals(bluePlayer)) return arena.getBlueSpawn();
        return null;
    }

    public Team getTeam(UUID uid) {
        if (uid.equals(redPlayer)) return Team.RED;
        if (uid.equals(bluePlayer)) return Team.BLUE;
        return null;
    }

    public void respawnPlayer(UUID uid) {
        if (state == MatchState.ENDED) return;
        Player p = Bukkit.getPlayer(uid);
        if (p == null) return;
        Team team = getTeam(uid);
        if (team == null) return;
        plugin.getLogger().info("[Bridge] Respawning " + p.getName() + " as " + team
                + " in arena '" + arena.getId() + "'.");
        giveLoadout(uid, team);
        p.setNoDamageTicks(0);
    }

    // ── Arrow regeneration + XP bar countdown ────────────────────────────────
    //
    // A single repeating task (1-tick period) handles both concerns:
    //   • XP bar drains from 1.0 → ~0.0 over 70 ticks as a visual timer.
    //   • After 70 ticks the arrow is given back and the real XP is restored.
    // Cancelling the task at any point (loadout refresh, pickup, match end)
    // also restores the player's real XP level and progress.

    public void scheduleArrowRegen(UUID uid) {
        cancelArrowRegen(uid); // cancel previous timer and restore XP bar

        Player starter = Bukkit.getPlayer(uid);
        if (starter != null) {
            savedXpLevels.put(uid, starter.getLevel());
            savedXpProgress.put(uid, starter.getExp());
            starter.setLevel(0);
            starter.setExp(1.0f); // full bar = arrow just shot, countdown begins
        }

        final int[] ticksLeft = {70};
        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                if (state == MatchState.ENDED) {
                    cancel(); arrowRegenTasks.remove(uid); restoreXpBar(uid); return;
                }
                Player p = Bukkit.getPlayer(uid);
                if (p == null) {
                    cancel(); arrowRegenTasks.remove(uid); restoreXpBar(uid); return;
                }

                ticksLeft[0]--;
                if (ticksLeft[0] > 0) {
                    p.setExp(ticksLeft[0] / 70.0f);
                    return;
                }

                // 70 ticks elapsed — give arrow back and restore XP bar.
                cancel();
                arrowRegenTasks.remove(uid);
                if (!p.getInventory().contains(Material.ARROW)) {
                    p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                    debugAdmin("Arrow REGEN: player=" + p.getName());
                }
                restoreXpBar(uid);
            }
        };
        arrowRegenTasks.put(uid, task);
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public void cancelArrowRegen(UUID uid) {
        BukkitRunnable existing = arrowRegenTasks.remove(uid);
        if (existing != null) { existing.cancel(); restoreXpBar(uid); }
    }

    private void cancelAllArrowRegens() {
        List<UUID> pending = new ArrayList<>(arrowRegenTasks.keySet());
        for (UUID uid : pending) cancelArrowRegen(uid);
    }

    private void restoreXpBar(UUID uid) {
        Integer level = savedXpLevels.remove(uid);
        Float   exp   = savedXpProgress.remove(uid);
        Player p = Bukkit.getPlayer(uid);
        if (p == null) return;
        p.setLevel(level != null ? level : 0);
        p.setExp(exp   != null ? exp   : 0.0f);
    }

    // ── Admin debug helper ────────────────────────────────────────────────────
    //
    // Always logs to console.  In-game messages to admins are only sent when
    // settings.debug is true in config.yml so they don't spam during normal play.

    private void debugAdmin(String msg) {
        plugin.getLogger().info("[Bridge] " + msg);
        if (!plugin.getConfig().getBoolean("settings.debug", false)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("bridge.admin")) {
                p.sendMessage("§8[§6Bridge§8] §7" + msg);
            }
        }
    }

    // ── Location format helper ────────────────────────────────────────────────

    private String fmtLoc(Location loc) {
        if (loc == null) return "null";
        String w = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return w + "(" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Arena getArena()      { return arena; }
    public UUID getRedPlayer()   { return redPlayer; }
    public UUID getBluePlayer()  { return bluePlayer; }
    public int getRedScore()     { return redScore; }
    public int getBlueScore()    { return blueScore; }
    public MatchState getState() { return state; }
}
