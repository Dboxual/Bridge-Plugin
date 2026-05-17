package com.thebridge.schematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.thebridge.TheBridgePlugin;
import com.thebridge.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Handles all FAWE schematic save and restore operations.
 *
 * Save flow:
 *   1. Compute the min/max block vectors from arena pos1/pos2.
 *   2. Copy the region into a BlockArrayClipboard (origin = min point).
 *   3. Write the clipboard to <schematics>/<arenaId>.schem.
 *
 * Reset flow:
 *   1. Read the clipboard from the .schem file (format auto-detected).
 *   2. Paste back at clipboard.getOrigin() — the exact location it was
 *      copied from — so the arena is restored to its original state.
 *
 * Both operations run on a background thread via CompletableFuture.runAsync.
 * FAWE manages its own internal queue and chunk locking, making this safe.
 * Callers receive a CompletableFuture they can chain .whenComplete() on to
 * dispatch success/failure messages back to the main thread.
 */
public class SchematicManager {

    private final TheBridgePlugin plugin;
    private final File schematicsFolder;

    public SchematicManager(TheBridgePlugin plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(
                plugin.getDataFolder(),
                plugin.getConfig().getString("settings.schematics-folder", "schematics"));
        schematicsFolder.mkdirs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public File getSchematicFile(Arena arena) {
        return new File(schematicsFolder, arena.getSchematicName() + ".schem");
    }

    public boolean hasSchematic(Arena arena) {
        return getSchematicFile(arena).exists();
    }

    /**
     * Saves the arena's region (pos1 → pos2) as a schematic file.
     * Runs on a background thread; the returned future completes when done.
     */
    public CompletableFuture<Void> saveArena(Arena arena) {
        if (!arena.hasRegion()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Region not set (run /bridge setpos1 and /bridge setpos2 first)."));
        }

        // Capture world on the main thread — safe to read from async after this
        World world = Bukkit.getWorld(plugin.getWorldName());
        if (world == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("World '" + plugin.getWorldName() + "' is not loaded."));
        }

        Location loc1 = arena.getPos1();
        Location loc2 = arena.getPos2();

        return CompletableFuture.runAsync(() -> {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

            BlockVector3 min = BlockVector3.at(
                    Math.min(loc1.getBlockX(), loc2.getBlockX()),
                    Math.min(loc1.getBlockY(), loc2.getBlockY()),
                    Math.min(loc1.getBlockZ(), loc2.getBlockZ()));
            BlockVector3 max = BlockVector3.at(
                    Math.max(loc1.getBlockX(), loc2.getBlockX()),
                    Math.max(loc1.getBlockY(), loc2.getBlockY()),
                    Math.max(loc1.getBlockZ(), loc2.getBlockZ()));

            CuboidRegion region = new CuboidRegion(weWorld, min, max);
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);

            try (EditSession session = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(weWorld)
                    .maxBlocks(-1)
                    .build()) {

                ForwardExtentCopy copy = new ForwardExtentCopy(
                        session, region, clipboard, region.getMinimumPoint());
                copy.setCopyingEntities(false);
                copy.setCopyingBiomes(false);
                Operations.complete(copy);
            }

            File file = getSchematicFile(arena);
            try (ClipboardWriter writer = BuiltInClipboardFormat.FAST.getWriter(
                    new FileOutputStream(file))) {
                writer.write(clipboard);
            } catch (Exception e) {
                throw new RuntimeException("Failed to write schematic: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Restores the arena from its saved schematic file.
     * Paste origin is clipboard.getOrigin() — the min point saved during /bridge save —
     * so the arena lands exactly where it was originally.
     * Runs on a background thread; the returned future completes when done.
     */
    public CompletableFuture<Void> resetArena(Arena arena) {
        File file = getSchematicFile(arena);
        if (!file.exists()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Schematic '" + arena.getSchematicName() + ".schem' not found. Run /bridge save first."));
        }
        if (!arena.hasRegion()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Region not set — cannot determine paste location."));
        }

        World world = Bukkit.getWorld(plugin.getWorldName());
        if (world == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("World '" + plugin.getWorldName() + "' is not loaded."));
        }

        return CompletableFuture.runAsync(() -> {
            // Auto-detect format so the file can be read regardless of how it was written
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) format = BuiltInClipboardFormat.SPONGE_V2_SCHEMATIC;

            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                clipboard = reader.read();
            } catch (Exception e) {
                throw new RuntimeException("Failed to read schematic: " + e.getMessage(), e);
            }

            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

            try (EditSession session = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(weWorld)
                    .maxBlocks(-1)
                    .build()) {

                // Paste back to the origin that was stored when the schematic was saved.
                // This is the minimum block of the region, so it lands in the exact same spot.
                Operation paste = new ClipboardHolder(clipboard)
                        .createPaste(session)
                        .to(clipboard.getOrigin())
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(paste);
            }
        });
    }
}
