package com.thebridge.commands;

import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
            case "setredgoal"   -> handleSetLoc(sender, args, Field.RED_GOAL);
            case "setbluegoal"  -> handleSetLoc(sender, args, Field.BLUE_GOAL);
            case "setpos1"      -> handleSetLoc(sender, args, Field.POS1);
            case "setpos2"      -> handleSetLoc(sender, args, Field.POS2);
            case "save"         -> handleSave(sender, args);
            case "reset"        -> handleReset(sender, args);
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
                + "Use §b/bridge set*§a commands to configure it, then §b/bridge save§a.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "delete <arena>"); return; }
        String id = args[1].toLowerCase();
        if (!plugin.getArenaManager().deleteArena(id)) {
            sender.sendMessage(PREFIX + "§cArena '§e" + id + "§c' not found.");
            return;
        }
        sender.sendMessage(PREFIX + "§aArena '§e" + id + "§a' deleted from config. "
                + "§7(Schematic file on disk was not removed.)");
    }

    private void handleList(CommandSender sender) {
        Collection<Arena> all = plugin.getArenaManager().getAllArenas();
        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + "§7No arenas configured. Use §b/bridge create§7.");
            return;
        }
        sender.sendMessage(PREFIX + "§eArenas §7(" + all.size() + ")§e:");
        for (Arena arena : all) {
            String configured = arena.isFullyConfigured() ? "§aconfigured" : "§cincomplete";
            String schematic  = plugin.getSchematicManager().hasSchematic(arena) ? "§aschematic" : "§7no schematic";
            String enabledStr = arena.isEnabled() ? "§aenabled" : "§7disabled";
            sender.sendMessage("  §7• §f" + arena.getId()
                    + " §8[" + configured + "§8] [" + schematic + "§8] [" + enabledStr + "§8]");
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
            case RED_SPAWN  -> arena.setRedSpawn(loc);
            case BLUE_SPAWN -> arena.setBlueSpawn(loc);
            case LOBBY_SPAWN -> arena.setLobbySpawn(loc);
            case RED_GOAL   -> arena.setRedGoal(loc);
            case BLUE_GOAL  -> arena.setBlueGoal(loc);
            case POS1       -> arena.setPos1(loc);
            case POS2       -> arena.setPos2(loc);
        }

        plugin.getArenaManager().saveArena(arena);
        sender.sendMessage(PREFIX + "§a" + field.label + " set for §e" + arena.getId()
                + "§a at §7(" + fmtBlock(loc) + ")§a.");
    }

    private void handleSave(CommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender, "save <arena>"); return; }
        Arena arena = resolveArena(sender, args[1]);
        if (arena == null) return;

        if (!arena.hasRegion()) {
            sender.sendMessage(PREFIX + "§cSet both pos1 and pos2 before saving.");
            return;
        }

        sender.sendMessage(PREFIX + "§eSaving '§b" + arena.getId() + "§e'...");

        plugin.getSchematicManager().saveArena(arena).whenComplete((ok, err) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null) {
                    Throwable cause = err.getCause() != null ? err.getCause() : err;
                    sender.sendMessage(PREFIX + "§cSave failed: §7" + cause.getMessage());
                    plugin.getLogger().warning("Save failed for " + arena.getId() + ": " + cause.getMessage());
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
            sender.sendMessage(PREFIX + "§cNo schematic found for '§e" + arena.getId()
                    + "§c'. Run §b/bridge save§c first.");
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
                    plugin.getLogger().warning("Reset failed for " + arena.getId() + ": " + cause.getMessage());
                } else {
                    sender.sendMessage(PREFIX + "§aArena '§b" + arena.getId() + "§a' reset successfully.");
                }
            })
        );
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("bridge.admin")) return List.of();

        if (args.length == 1) {
            return filter(List.of("create", "delete", "list",
                    "setredspawn", "setbluespawn", "setlobby",
                    "setredgoal", "setbluegoal",
                    "setpos1", "setpos2",
                    "save", "reset"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
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
        sender.sendMessage("  §b/bridge setredgoal §f<arena>     §7Set red goal at your location");
        sender.sendMessage("  §b/bridge setbluegoal §f<arena>    §7Set blue goal at your location");
        sender.sendMessage("  §b/bridge setpos1 §f<arena>        §7Set reset region corner 1");
        sender.sendMessage("  §b/bridge setpos2 §f<arena>        §7Set reset region corner 2");
        sender.sendMessage("  §b/bridge save §f<arena>           §7Save arena region as schematic");
        sender.sendMessage("  §b/bridge reset §f<arena>          §7Restore arena from schematic");
    }

    private String fmtBlock(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private List<String> filter(List<String> opts, String prefix) {
        return opts.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }

    // ── Field enum (avoids a pile of near-identical handler methods) ──────────

    private enum Field {
        RED_SPAWN("Red spawn"),
        BLUE_SPAWN("Blue spawn"),
        LOBBY_SPAWN("Lobby spawn"),
        RED_GOAL("Red goal"),
        BLUE_GOAL("Blue goal"),
        POS1("Pos1"),
        POS2("Pos2");

        final String label;
        Field(String label) { this.label = label; }
    }
}
