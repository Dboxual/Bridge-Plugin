package com.thebridge;

import com.thebridge.arena.ArenaManager;
import com.thebridge.commands.BridgeCommand;
import com.thebridge.schematic.SchematicManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TheBridgePlugin extends JavaPlugin {

    private ArenaManager arenaManager;
    private SchematicManager schematicManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.schematicManager = new SchematicManager(this);
        this.arenaManager = new ArenaManager(this);
        arenaManager.loadAll();

        BridgeCommand cmd = new BridgeCommand(this);
        getCommand("bridge").setExecutor(cmd);
        getCommand("bridge").setTabCompleter(cmd);

        getLogger().info("TheBridge v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("TheBridge disabled.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ArenaManager getArenaManager() { return arenaManager; }
    public SchematicManager getSchematicManager() { return schematicManager; }

    /** The Minecraft world name that contains all arenas. Defined in config.yml. */
    public String getWorldName() {
        return getConfig().getString("settings.world", "bridge");
    }
}
