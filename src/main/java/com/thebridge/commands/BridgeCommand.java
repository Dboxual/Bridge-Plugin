package com.thebridge.commands;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaState;
import com.thebridge.match.BridgeMatch;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class BridgeCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§6Bridge§8] §r";

    private final TheBridgePlugin plugin;

    public BridgeCommand(TheBridgePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("bridge.admin")) {
            sender.sendMessage(PREFIX + "§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create"       -> handleCreate(sender, args);
            case "delete"       -> handleDelete(sender, args);
            case "list"         -> handleList(sender);
            case "setredspawn"  -> handleSetLoc(sender, args, Field.RED_SPAWN);
            case "setbluespawn" -> handleSetLoc(sender, args, Field.BLUE_SPAWN);
            case "setlobby"     -> handleSetLoc(sender, args, Field.LOBBY_SPAWN);
            case "setredgoal"      -> handleSetGoalRegion(sender, args, true);
            case "setbluegoal"     -> handleSetGoalRegion(sender, args, false);
            case "setredrelease"   -> handleSetReleaseRegion(sender, args, true);
            case "setbluerelease"  -> handleSetReleaseRegion(sender, args, false);
            case "setarena"        -> handleSetArena(sender, args);
            case "setvoidlevel"    -> handleSetVoidLevel(sender, args);
            case "setpos1"         -> handleSetLoc(sender, args, Field.POS1);
            case "setpos2"         -> handleSetLoc(sender, args, Field.POS2);
            case "wand"         -> handleWand(sender);
            case "selection"    -> handleSelection(sender);
            case "setsign"      -> handleSetSign(sender, args);
            case "showgoals"    -> handleShowGoals(sender, args);
            case "save"         -> handleSave(sender, args);
            case "reset"        -> handleReset(sender, args);
            case "debug"        -> handleDebug(sender, args);
            default             -> sendUsage(sender);
        }
        return true;
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "create <arena>"); return; }
        String id = args[1].toLowerCase();
        if (plugin.getArenaManager().exists(id)) {
            sender.sendMessage(PREFIX + "§cArena '§e" + id + "§c' already exists.");
            return;
        }
        plugin.getArenaManager().createArena(id);
        sender.sendMessage(PREFIX + "§aArena '§e" + id + "§a' created. "
                + "Use §b/bridge wand§a to select goal regions, then §b/bridge set*§a for spawns.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "delete <arena>"); return; }
        String id = args[1].toLowerCase();
        if (!plugin.getArenaManager().deleteArena(id)) {
            sender.sendMessage(PREFIX + "§cArena '§e" + id + "§c' not found.");
            return;
        }
        sender.sendMessage(PREFIX + "§aArena '§e" + id + "§a' deleted. §7(Schematic not removed.)");
    }

    private void handleList(CommandSender sender) {
        Collection<Arena> all = plugin.getArenaManager().getAllArenas();
        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + "§7No arenas configured.");
            return;
        }
        sender.sendMessage(PREFIX + "§eArenas §7(" + all.size() + ")§e:");
        for (Arena arena : all) {
            String configured = arena.isFullyConfigured() ? "§aconfigured" : "§cincomplete";
            String schematic  = plugin.getSchematicManager().hasSchematic(arena) ? "§aschematic" : "§7no schematic";
            String goals      = arena.hasRedGoal() && arena.hasBlueGoal() ? "§agoals" : "§cno goals";
            String release    = arena.hasRedRelease() && arena.hasBlueRelease() ? "§arelease" : "§eno release";
            sender.sendMessage("  §7• §f" + arena.getId()
                    + " §8[" + configured + "§8] [" + schematic + "§8] [" + goals + "§8] [" + release + "§8]");
        }
    }

    private void handleSetLoc(CommandSender sender, String[] args, Field field) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command.");
            return;
        }
        if (args.length < 2) { usage(sender, args[0] + " <arena>"); return; }

        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        Location loc = player.getLocation();

        switch (field) {
            case RED_SPAWN      -> arena.setRedSpawn(loc);
            case BLUE_SPAWN     -> arena.setBlueSpawn(loc);
            case LOBBY_SPAWN    -> arena.setLobbySpawn(loc);
            case POS1           -> arena.setPos1(loc);
            case POS2           -> arena.setPos2(loc);
        }

        plugin.getArenaManager().saveArena(arena);
        sender.sendMessage(PREFIX + "§a" + field.label + " set for §e" + arena.getId()
                + "§a at §7(" + fmtBlock(loc) + ")§a.");
    }

    private void handleSetGoalRegion(CommandSender sender, String[] args, boolean red) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        if (args.length < 2) { usage(sender, args[0] + " <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        UUID uid = player.getUniqueId();
        if (!plugin.getWandManager().hasSelection(uid)) {
            sender.sendMessage(PREFIX + "§cNo wand selection. Run §e/bridge wand§c, then left/right-click two corner blocks.");
            return;
        }

        Location p1 = plugin.getWandManager().getPos1(uid);
        Location p2 = plugin.getWandManager().getPos2(uid);

        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            sender.sendMessage(PREFIX + "§cBoth corners must be in the same world.");
            return;
        }

        if (red) {
            arena.setRedGoalPos1(p1);
            arena.setRedGoalPos2(p2);
            sender.sendMessage(PREFIX + "§cRed §rgoal region set for §e" + arena.getId()
                    + "§r: §7(" + fmtBlock(p1) + ")§r → §7(" + fmtBlock(p2) + ")§r.");
        } else {
            arena.setBlueGoalPos1(p1);
            arena.setBlueGoalPos2(p2);
            sender.sendMessage(PREFIX + "§9Blue §rgoal region set for §e" + arena.getId()
                    + "§r: §7(" + fmtBlock(p1) + ")§r → §7(" + fmtBlock(p2) + ")§r.");
        }
        plugin.getArenaManager().saveArena(arena);
    }

    private void handleSetVoidLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        if (args.length < 2) { usage(sender, "setvoidlevel <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;
        int y = (int) player.getLocation().getY();
        arena.setVoidLevel(y);
        plugin.getArenaManager().saveArena(arena);
        sender.sendMessage(PREFIX + "§aVoid level set to §eY=" + y + "§a for arena §e" + arena.getId() + "§a.");
    }

    private void handleSetReleaseRegion(CommandSender sender, String[] args, boolean red) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        if (args.length < 2) { usage(sender, args[0] + " <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        UUID uid = player.getUniqueId();
        if (!plugin.getWandManager().hasSelection(uid)) {
            sender.sendMessage(PREFIX + "§cNo wand selection. Run §e/bridge wand§c, then left/right-click two corner blocks.");
            return;
        }

        Location p1 = plugin.getWandManager().getPos1(uid);
        Location p2 = plugin.getWandManager().getPos2(uid);

        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            sender.sendMessage(PREFIX + "§cBoth corners must be in the same world.");
            return;
        }

        if (red) {
            arena.setRedRelease1(p1);
            arena.setRedRelease2(p2);
            sender.sendMessage(PREFIX + "§cRed §rrelease zone set for §e" + arena.getId()
                    + "§r: §7(" + fmtBlock(p1) + ")§r → §7(" + fmtBlock(p2) + ")§r.");
        } else {
            arena.setBlueRelease1(p1);
            arena.setBlueRelease2(p2);
            sender.sendMessage(PREFIX + "§9Blue §rrelease zone set for §e" + arena.getId()
                    + "§r: §7(" + fmtBlock(p1) + ")§r → §7(" + fmtBlock(p2) + ")§r.");
        }
        plugin.getArenaManager().saveArena(arena);
    }

    private void handleSetArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        if (args.length < 2) { usage(sender, "setarena <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        UUID uid = player.getUniqueId();
        if (!plugin.getWandManager().hasSelection(uid)) {
            sender.sendMessage(PREFIX + "§cNo wand selection. Run §e/bridge wand§c, then left/right-click two corner blocks.");
            return;
        }

        Location p1 = plugin.getWandManager().getPos1(uid);
        Location p2 = plugin.getWandManager().getPos2(uid);

        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            sender.sendMessage(PREFIX + "§cBoth corners must be in the same world.");
            return;
        }

        arena.setPos1(p1);
        arena.setPos2(p2);
        plugin.getArenaManager().saveArena(arena);

        int sx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1;
        int sy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1;
        int sz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1;
        sender.sendMessage(PREFIX + "§aArena region set for §e" + arena.getId()
                + "§r: §7(" + fmtBlock(p1) + ")§r → §7(" + fmtBlock(p2) + ")§r "
                + "§7(" + sx + "×" + sy + "×" + sz + ", " + (sx * sy * sz) + " blocks)§a.");
        sender.sendMessage(PREFIX + "§7Run §b/bridge save §e" + arena.getId() + "§7 to snapshot the region.");
    }

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        player.getInventory().addItem(plugin.getWandManager().createWand());
        sender.sendMessage(PREFIX + "§aGiven §6Bridge Setup Wand§a. "
                + "§7Left-click = pos1, right-click = pos2. "
                + "Then run §b/bridge setarena§7, §b/bridge setredgoal§7, §b/bridge setbluegoal§7, "
                + "§b/bridge setredrelease§7, or §b/bridge setbluerelease§7.");
    }

    private void handleSelection(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        UUID uid = player.getUniqueId();
        Location p1 = plugin.getWandManager().getPos1(uid);
        Location p2 = plugin.getWandManager().getPos2(uid);

        sender.sendMessage(PREFIX + "§e=== Wand Selection ===");
        sender.sendMessage("§7Pos1: " + (p1 != null ? "§f" + fmtLocFull(p1) : "§cnot set"));
        sender.sendMessage("§7Pos2: " + (p2 != null ? "§f" + fmtLocFull(p2) : "§cnot set"));

        if (p1 == null || p2 == null) {
            sender.sendMessage("§7Valid: §cfalse §7— need both pos1 and pos2");
            return;
        }
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            sender.sendMessage("§7Valid: §cfalse §7— positions are in different worlds");
            return;
        }

        int sx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1;
        int sy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1;
        int sz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1;
        sender.sendMessage("§7World: §f" + p1.getWorld().getName());
        sender.sendMessage("§7Size:  §f" + sx + "x" + sy + "x" + sz + " §7(" + (sx * sy * sz) + " blocks)");
        sender.sendMessage("§7Valid: §atrue");
        plugin.getWandManager().showSelectionOutline(player);
    }

    private void handleSetSign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        if (args.length < 2) { usage(sender, "setsign <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getState() instanceof Sign)) {
            sender.sendMessage(PREFIX + "§cLook at a sign block to register it."); return;
        }
        arena.addSign(target.getLocation());
        plugin.getArenaManager().saveArena(arena);
        plugin.getQueueManager().updateSigns(arena);
        sender.sendMessage(PREFIX + "§aSign registered for arena §e" + arena.getId() + "§a.");
    }

    private void handleShowGoals(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cOnly players can use this command."); return;
        }
        if (args.length < 2) { usage(sender, "showgoals <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        if (!arena.hasRedGoal() && !arena.hasBlueGoal()) {
            sender.sendMessage(PREFIX + "§cNo goal regions defined for this arena."); return;
        }

        sender.sendMessage(PREFIX + "§eShowing goal regions for §b10§e seconds...");

        Particle.DustOptions redDust  = new Particle.DustOptions(Color.RED,  1.5f);
        Particle.DustOptions blueDust = new Particle.DustOptions(Color.BLUE, 1.5f);

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= 40) { cancel(); return; }
                if (arena.hasRedGoal()) {
                    outlineRegion(arena.getRedGoalPos1().getWorld(),
                            arena.getRedGoalPos1(), arena.getRedGoalPos2(), redDust);
                }
                if (arena.hasBlueGoal()) {
                    outlineRegion(arena.getBlueGoalPos1().getWorld(),
                            arena.getBlueGoalPos1(), arena.getBlueGoalPos2(), blueDust);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void handleSave(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "save <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        if (!arena.hasRegion()) {
            sender.sendMessage(PREFIX + "§cArena region not set. Use §e/bridge wand§c to select the region, then §e/bridge setarena §b" + arena.getId() + "§c.");
            return;
        }

        List<String> worldMismatch = getWorldMismatch(arena);
        if (!worldMismatch.isEmpty()) {
            sender.sendMessage(PREFIX + "§cAll arena locations must be in the same world. Mismatched: §e"
                    + String.join(", ", worldMismatch));
            return;
        }

        sender.sendMessage(PREFIX + "§eSaving '§b" + arena.getId() + "§e'...");

        plugin.getSchematicManager().saveArena(arena).whenComplete((ok, err) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    sender.sendMessage(PREFIX + "§cSave failed: §7" + cause.getMessage());
                    plugin.getLogger().warning("[Bridge] Save failed for " + arena.getId() + ": " + cause.getMessage());
                } else {
                    sender.sendMessage(PREFIX + "§aArena '§b" + arena.getId()
                            + "§a' saved as §b" + arena.getSchematicName() + ".schem§a.");
                }
            })
        );
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "reset <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        if (!plugin.getSchematicManager().hasSchematic(arena)) {
            sender.sendMessage(PREFIX + "§cNo schematic for '§e" + arena.getId() + "§c'. Run §b/bridge save§c first.");
            return;
        }
        if (arena.getState() == ArenaState.RESETTING) {
            sender.sendMessage(PREFIX + "§cArena '§e" + arena.getId() + "§c' is already resetting.");
            return;
        }

        ArenaState previousState = arena.getState();
        arena.setState(ArenaState.RESETTING);
        sender.sendMessage(PREFIX + "§eResetting '§b" + arena.getId() + "§e'...");

        plugin.getSchematicManager().resetArena(arena).whenComplete((ok, err) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                arena.setState(previousState);
                if (err != null) {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    sender.sendMessage(PREFIX + "§cReset failed: §7" + cause.getMessage());
                    plugin.getLogger().warning("[Bridge] Reset failed for " + arena.getId() + ": " + cause.getMessage());
                } else {
                    sender.sendMessage(PREFIX + "§aArena '§b" + arena.getId() + "§a' reset successfully.");
                }
            })
        );
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "debug <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        sender.sendMessage(PREFIX + "§e=== Debug: " + arena.getId() + " ===");
        sender.sendMessage("§7State: §f" + arena.getState()
                + "  Enabled: §f" + arena.isEnabled()
                + "  Configured: §f" + arena.isFullyConfigured());
        sender.sendMessage("§7World: §f" + (arena.getWorld() != null ? arena.getWorld().getName() : "not set"));
        sender.sendMessage("§7Pos1: §f" + fmtLocFull(arena.getPos1()));
        sender.sendMessage("§7Pos2: §f" + fmtLocFull(arena.getPos2()));
        sender.sendMessage("§7Red spawn:  §f" + fmtLocFull(arena.getRedSpawn()));
        sender.sendMessage("§7Blue spawn: §f" + fmtLocFull(arena.getBlueSpawn()));
        sender.sendMessage("§7Lobby:      §f" + fmtLocFull(arena.getLobbySpawn()));
        sender.sendMessage("§7Red goal:     §f" + fmtLocFull(arena.getRedGoalPos1())  + " §7→ §f" + fmtLocFull(arena.getRedGoalPos2()));
        sender.sendMessage("§7Blue goal:    §f" + fmtLocFull(arena.getBlueGoalPos1()) + " §7→ §f" + fmtLocFull(arena.getBlueGoalPos2()));
        sender.sendMessage("§7Red release:  §f" + fmtLocFull(arena.getRedRelease1())  + " §7→ §f" + fmtLocFull(arena.getRedRelease2())
                + (arena.hasRedRelease()  ? "" : " §e(fallback 3×3)"));
        sender.sendMessage("§7Blue release: §f" + fmtLocFull(arena.getBlueRelease1()) + " §7→ §f" + fmtLocFull(arena.getBlueRelease2())
                + (arena.hasBlueRelease() ? "" : " §e(fallback 3×3)"));
        sender.sendMessage("§7Void level: §f" + (arena.hasVoidLevel() ? "Y=" + arena.getVoidLevel() : "not set"));
        sender.sendMessage("§7Schematic: §f" + arena.getSchematicName() + ".schem"
                + "  Exists: §f" + plugin.getSchematicManager().hasSchematic(arena));
        sender.sendMessage("§7Path: §f" + plugin.getSchematicManager().getSchematicFile(arena).getAbsolutePath());

        BridgeMatch match = plugin.getMatchManager().getMatchByArena(arena.getId());
        if (match != null) {
            Player red  = Bukkit.getPlayer(match.getRedPlayer());
            Player blue = Bukkit.getPlayer(match.getBluePlayer());
            sender.sendMessage("§7Match: §aACTIVE §7(state=" + match.getState() + ")");
            sender.sendMessage("§7  §cRed§7:  §f" + (red  != null ? red.getName()  : match.getRedPlayer())
                    + " §7score=§c" + match.getRedScore());
            sender.sendMessage("§7  §9Blue§7: §f" + (blue != null ? blue.getName() : match.getBluePlayer())
                    + " §7score=§9" + match.getBlueScore());
        } else {
            sender.sendMessage("§7Match: §7none");
        }
    }

    // ── Particle helpers ──────────────────────────────────────────────────────

    private void outlineRegion(World world, Location p1, Location p2, Particle.DustOptions dust) {
        if (world == null || p1 == null || p2 == null) return;
        double step = 0.5;
        double x1 = Math.min(p1.getBlockX(), p2.getBlockX());
        double x2 = Math.max(p1.getBlockX(), p2.getBlockX()) + 1;
        double y1 = Math.min(p1.getBlockY(), p2.getBlockY());
        double y2 = Math.max(p1.getBlockY(), p2.getBlockY()) + 1;
        double z1 = Math.min(p1.getBlockZ(), p2.getBlockZ());
        double z2 = Math.max(p1.getBlockZ(), p2.getBlockZ()) + 1;
        for (double x = x1; x <= x2; x += step) {
            spawnDust(world, x, y1, z1, dust); spawnDust(world, x, y2, z1, dust);
            spawnDust(world, x, y1, z2, dust); spawnDust(world, x, y2, z2, dust);
        }
        for (double y = y1; y <= y2; y += step) {
            spawnDust(world, x1, y, z1, dust); spawnDust(world, x2, y, z1, dust);
            spawnDust(world, x1, y, z2, dust); spawnDust(world, x2, y, z2, dust);
        }
        for (double z = z1; z <= z2; z += step) {
            spawnDust(world, x1, y1, z, dust); spawnDust(world, x2, y1, z, dust);
            spawnDust(world, x1, y2, z, dust); spawnDust(world, x2, y2, z, dust);
        }
    }

    private void spawnDust(World world, double x, double y, double z, Particle.DustOptions dust) {
        world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
    }

    // ── World-mismatch helper ─────────────────────────────────────────────────

    private List<String> getWorldMismatch(Arena arena) {
        World ref = arena.getPos1() != null ? arena.getPos1().getWorld() : null;
        if (ref == null) return List.of();
        List<String> bad = new ArrayList<>();
        checkWorld(bad, ref, arena.getPos2(),            "pos2");
        checkWorld(bad, ref, arena.getRedSpawn(),        "red-spawn");
        checkWorld(bad, ref, arena.getBlueSpawn(),       "blue-spawn");
        checkWorld(bad, ref, arena.getLobbySpawn(),      "lobby-spawn");
        checkWorld(bad, ref, arena.getRedGoalPos1(),     "red-goal-pos1");
        checkWorld(bad, ref, arena.getRedGoalPos2(),     "red-goal-pos2");
        checkWorld(bad, ref, arena.getBlueGoalPos1(),    "blue-goal-pos1");
        checkWorld(bad, ref, arena.getBlueGoalPos2(),    "blue-goal-pos2");
        return bad;
    }

    private void checkWorld(List<String> bad, World ref, Location loc, String name) {
        if (loc != null && loc.getWorld() != null && !ref.equals(loc.getWorld())) bad.add(name);
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("bridge.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("create", "delete", "list",
                    "setredspawn", "setbluespawn", "setlobby",
                    "setredgoal", "setbluegoal",
                    "setredrelease", "setbluerelease",
                    "setarena", "setvoidlevel",
                    "setpos1", "setpos2",
                    "wand", "selection", "setsign", "showgoals",
                    "save", "reset", "debug"), args[0]);
        }
        // wand and selection take no second argument
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")
                && !args[0].equalsIgnoreCase("wand")
                && !args[0].equalsIgnoreCase("selection")) {
            return plugin.getArenaManager().getAllArenas().stream()
                    .map(Arena::getId)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Arena resolveArena(CommandSender sender, String id) {
        Optional<Arena> opt = plugin.getArenaManager().getArena(id);
        if (opt.isEmpty()) {
            sender.sendMessage(PREFIX + "§cArena '§e" + id + "§c' not found.");
            return null;
        }
        return opt.get();
    }

    private void usage(CommandSender sender, String sub) {
        sender.sendMessage(PREFIX + "§cUsage: §e/bridge " + sub);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eTheBridge — admin commands:");
        sender.sendMessage("  §b/bridge create §f<arena>         §7Create a new arena");
        sender.sendMessage("  §b/bridge delete §f<arena>         §7Delete an arena");
        sender.sendMessage("  §b/bridge list                    §7List all arenas");
        sender.sendMessage("  §b/bridge setredspawn §f<arena>    §7Set red spawn at your location");
        sender.sendMessage("  §b/bridge setbluespawn §f<arena>   §7Set blue spawn at your location");
        sender.sendMessage("  §b/bridge setlobby §f<arena>       §7Set lobby/waiting spawn");
        sender.sendMessage("  §b/bridge wand                    §7Get the goal-region selection wand");
        sender.sendMessage("  §b/bridge selection               §7Show current wand selection (pos1/pos2/size)");
        sender.sendMessage("  §b/bridge setredgoal §f<arena>        §7Set red goal region (wand selection)");
        sender.sendMessage("  §b/bridge setbluegoal §f<arena>       §7Set blue goal region (wand selection)");
        sender.sendMessage("  §b/bridge showgoals §f<arena>         §7Show goal regions with particles (10s)");
        sender.sendMessage("  §b/bridge setredrelease §f<arena>        §7Set red release zone from wand selection");
        sender.sendMessage("  §b/bridge setbluerelease §f<arena>       §7Set blue release zone from wand selection");
        sender.sendMessage("  §b/bridge setarena §f<arena>             §7Set reset region from wand selection §a(recommended)");
        sender.sendMessage("  §b/bridge setvoidlevel §f<arena>         §7Set void Y level at your current position");
        sender.sendMessage("  §b/bridge setpos1 §f<arena>              §7Set reset region corner 1 §7(legacy)");
        sender.sendMessage("  §b/bridge setpos2 §f<arena>              §7Set reset region corner 2 §7(legacy)");
        sender.sendMessage("  §b/bridge setsign §f<arena>        §7Register the sign you're looking at");
        sender.sendMessage("  §b/bridge save §f<arena>           §7Save arena region as schematic");
        sender.sendMessage("  §b/bridge reset §f<arena>          §7Restore arena from schematic");
        sender.sendMessage("  §b/bridge debug §f<arena>          §7Dump full arena/match status");
    }

    private String fmtBlock(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private String fmtLocFull(Location loc) {
        if (loc == null) return "not set";
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return world + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private List<String> filter(List<String> opts, String prefix) {
        return opts.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }

    // ── Field enum ────────────────────────────────────────────────────────────

    private enum Field {
        RED_SPAWN("Red spawn"),
        BLUE_SPAWN("Blue spawn"),
        LOBBY_SPAWN("Lobby spawn"),
        POS1("Pos1"),
        POS2("Pos2");

        final String label;
        Field(String label) { this.label = label; }
    }
}
