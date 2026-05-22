package com.thebridge;

import com.thebridge.arena.Arena;
import com.thebridge.arena.ArenaManager;
import com.thebridge.arena.ArenaState;
import com.thebridge.commands.BridgeCommand;
import com.thebridge.listeners.BridgeKitListener;
import com.thebridge.listeners.GoalListener;
import com.thebridge.listeners.MatchListener;
import com.thebridge.listeners.SignListener;
import com.thebridge.listeners.WandListener;
import com.thebridge.match.MatchManager;
import com.thebridge.queue.QueueManager;
import com.thebridge.schematic.SchematicManager;
import com.thebridge.wand.WandManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class TheBridgePlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private SchematicManager schematicManager;
    private MatchManager matchManager;
    private QueueManager queueManager;
    private WandManager wandManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.schematicManager = new SchematicManager(this);
        this.arenaManager     = new ArenaManager(this);
        this.matchManager     = new MatchManager(this);
        this.queueManager     = new QueueManager(this);
        this.wandManager      = new WandManager(this);

        arenaManager.loadAll();

        for (Arena arena : arenaManager.getAllArenas()) {
            if (arena.isFullyConfigured() && schematicManager.hasSchematic(arena)) {
                arena.setState(ArenaState.WAITING);
            }
        }

        BridgeCommand cmd = new BridgeCommand(this);
        getCommand("bridge").setExecutor(cmd);
        getCommand("bridge").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new SignListener(this),     this);
        getServer().getPluginManager().registerEvents(new GoalListener(this),     this);
        getServer().getPluginManager().registerEvents(new MatchListener(this),    this);
        getServer().getPluginManager().registerEvents(new WandListener(this),     this);
        getServer().getPluginManager().registerEvents(new BridgeKitListener(this), this);

        queueManager.updateAllSigns();

        for (String line : getStatusLines()) getLogger().info(line);
    }

    @Override
    public void onDisable() {
        getLogger().info("TheBridge disabled.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ArenaManager getArenaManager()     { return arenaManager; }
    public SchematicManager getSchematicManager() { return schematicManager; }
    public MatchManager getMatchManager()     { return matchManager; }
    public QueueManager getQueueManager()     { return queueManager; }
    public WandManager getWandManager()       { return wandManager; }

    public List<String> getStatusLines() {
        int arenaCount = arenaManager.getAllArenas().size();
        int signCount  = arenaManager.getAllArenas().stream()
                .mapToInt(a -> a.getSignLocations().size())
                .sum();
        boolean debug  = getConfig().getBoolean("settings.debug", false);
        String cfgPath = new File(getDataFolder(), "config.yml").getAbsolutePath();
        return List.of(
                "TheBridge v" + getPluginMeta().getVersion(),
                "Config: " + cfgPath,
                "debug: " + debug,
                "SignListener: registered",
                "Arenas loaded: " + arenaCount,
                "Registered signs: " + signCount
        );
    }
}
