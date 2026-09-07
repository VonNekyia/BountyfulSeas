package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fish.FishLoadResult;
import com.nekyia.bountyfulSeas.fish.FishLoader;
import com.nekyia.bountyfulSeas.fish.FishProblem;
import com.nekyia.bountyfulSeas.nexo.BlueprintResult;
import com.nekyia.bountyfulSeas.nexo.NexoBlueprintWriter;
import com.nekyia.bountyfulSeas.pl3xmap.WaterOverlay;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterMapGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class BountyfulSeas extends JavaPlugin {

    private static final String FISH_FOLDER = "fishes";
    private static final String NEXO_PLUGIN_FOLDER = "Nexo";
    private static final String WATER_MAP_FILE = "water_regions.bin";
    private static final String ROOT_COMMAND = "bs";
    private static final String OVERLAY_LABEL = "Fishing Water";

    private FishLibrary fish = FishLibrary.empty();
    private WaterMap waterMap;

    /**
     * Bukkit calls onLoad on every plugin before it enables any of them, which is
     * what lets the Nexo blueprint be written while Nexo is still loading. Doing
     * this in onEnable would be a restart too late for the items to register.
     */
    @Override
    public void onLoad() {
        loadFish();
        writeNexoBlueprints();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadWaterMap();
        getServer().getPluginManager().registerEvents(
                new FishingListener(this::fish, this::waterMap), this);

        PluginCommand root = getCommand(ROOT_COMMAND);
        if (root != null) {
            BountyfulSeasCommand executor = new BountyfulSeasCommand(
                    new DebugCommand(this::fish, this::waterMap), this::regenerateWaterMap);
            root.setExecutor(executor);
            root.setTabCompleter(executor);
        }
        publishWaterOverlay();
        getLogger().log(Level.INFO, "Ready with {0} fish.", fish.size());
    }

    /**
     * Draws the scanned water onto Pl3xMap, when Pl3xMap is there.
     *
     * <p>Done once at enable, because the water map is a file that only changes
     * when somebody rescans the world.
     */
    void publishWaterOverlay() {
        if (waterMap == null || !WaterOverlay.available()) {
            return;
        }

        String worldName = getServer().getWorlds().getFirst().getName();
        try {
            int markers = WaterOverlay.publish(worldName, OVERLAY_LABEL, WaterMapAreas.from(waterMap));
            if (markers < 0) {
                getLogger().log(Level.WARNING, "Pl3xMap does not map world {0}, so no water layer was drawn.",
                        worldName);
            } else {
                getLogger().log(Level.INFO, "Drew {0} water markers on Pl3xMap for {1}.",
                        new Object[]{markers, worldName});
            }
        } catch (RuntimeException failure) {
            getLogger().log(Level.WARNING, "Could not draw the water layer: {0}", failure.toString());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    /** The fish that loaded cleanly. Never null, possibly empty. */
    public FishLibrary fish() {
        return fish;
    }

    /** The scanned water map, or null when none has been supplied yet. */
    public WaterMap waterMap() {
        return waterMap;
    }

    /**
     * Reads {@code water_regions.bin} from the plugin folder.
     *
     * <p>Absent is a normal state, not an error: the map is produced offline by a
     * separate tool, so a server can be running long before anyone has scanned the
     * world. Without it the plugin simply leaves vanilla fishing alone.
     */
    public void loadWaterMap() {
        Path file = getDataFolder().toPath().resolve(WATER_MAP_FILE);
        if (!Files.isRegularFile(file)) {
            getLogger().log(Level.WARNING,
                    "No {0} in the plugin folder, so vanilla fishing is left alone. "
                            + "Generate one with the water map tool and restart.", WATER_MAP_FILE);
            waterMap = null;
            return;
        }

        try {
            waterMap = WaterMap.read(file);
            getLogger().log(Level.INFO, "Water map loaded: {0} regions, sea level {1}.",
                    new Object[]{waterMap.regionCount(), waterMap.seaLevel()});
        } catch (IOException exception) {
            waterMap = null;
            getLogger().log(Level.SEVERE, "Could not read {0}: {1}",
                    new Object[]{WATER_MAP_FILE, exception.getMessage()});
        }
    }

    /**
     * Rescans the world and reloads the map, reporting progress to whoever asked.
     *
     * <p>The scan runs off the server thread because it reads the whole world and
     * takes seconds; only the reload and the redraw come back to the main thread,
     * because that is where the map and the overlay live.
     */
    public void regenerateWaterMap(CommandSender sender) {
        String configured = getConfig().getString("water-map.analyzer", "");
        if (configured == null || configured.isBlank()) {
            sender.sendMessage(Component.text(
                    "No analyzer configured. Set water-map.analyzer in the BountyfulSeas config.yml "
                            + "to the water-analyzer executable, then try again.", NamedTextColor.RED));
            return;
        }

        Path analyzer = Path.of(configured);
        Path world = getServer().getWorlds().getFirst().getWorldFolder().toPath();
        Path output = getDataFolder().toPath();
        List<String> arguments = getConfig().getStringList("water-map.arguments");

        sender.sendMessage(Component.text("Scanning the world, this can take a while...",
                NamedTextColor.GRAY));

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            WaterMapGenerator.Result result =
                    WaterMapGenerator.run(analyzer, world, output, arguments);

            getServer().getScheduler().runTask(this, () -> {
                if (!result.ok()) {
                    sender.sendMessage(Component.text("Water map not regenerated: " + result.message(),
                            NamedTextColor.RED));
                    getLogger().log(Level.WARNING, "Water map regeneration failed: {0}", result.message());
                    return;
                }

                loadWaterMap();
                publishWaterOverlay();

                sender.sendMessage(Component.text("Water map regenerated: "
                        + (waterMap == null ? "0" : waterMap.regionCount()) + " regions.",
                        NamedTextColor.GREEN));
                sender.sendMessage(Component.text("  scanned " + result.scanned(), NamedTextColor.DARK_GRAY));
            });
        });
    }

    /**
     * Reads the fish folder and reports every rejected definition to the console.
     * A broken file costs its own fish and nothing else.
     */
    public void loadFish() {
        Path folder = getDataFolder().toPath().resolve(FISH_FOLDER);
        if (!Files.isDirectory(folder)) {
            saveDefaultCategories();
        }

        FishLoadResult result = FishLoader.load(folder);
        fish = result.library();

        if (result.hasProblems()) {
            getLogger().log(Level.SEVERE, "{0} fish definition(s) were rejected:", result.problems().size());
            for (FishProblem problem : result.problems()) {
                getLogger().log(Level.SEVERE, "  {0}", problem);
            }
        }

        getLogger().log(Level.INFO, "Loaded {0} fish.", fish.size());
    }

    /**
     * Writes the shipped example categories on first start.
     *
     * <p>The jar is walked rather than kept as a list in code, so adding a category
     * file to the plugin resources ships it without anyone having to remember to
     * register it here.
     */
    private void saveDefaultCategories() {
        try (JarFile jar = new JarFile(getFile())) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith(FISH_FOLDER + "/"))
                    .filter(name -> name.endsWith(".yaml") || name.endsWith(".yml"))
                    .forEach(name -> saveResource(name, false));
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not write the example fish categories: {0}",
                    exception.getMessage());
        }
    }

    /** Gives every fish a Nexo item stub, without touching the ones that exist. */
    private void writeNexoBlueprints() {
        Path nexoFolder = getDataFolder().toPath().getParent().resolve(NEXO_PLUGIN_FOLDER);
        if (!Files.isDirectory(nexoFolder)) {
            getLogger().info("Nexo is not installed, skipping fish item blueprints.");
            return;
        }

        BlueprintResult result = NexoBlueprintWriter.write(nexoFolder, FishNexoItems.from(fish.all()));

        if (result.skipped()) {
            getLogger().log(Level.WARNING, "Fish item blueprints skipped: {0}", result.note());
            return;
        }
        if (result.created().isEmpty()) {
            return;
        }

        getLogger().log(Level.INFO, "Wrote {0} new fish item blueprint(s) to {1}: {2}",
                new Object[]{result.created().size(), NexoBlueprintWriter.TARGET, String.join(", ", result.created())});
        getLogger().info("Give them their textures in the Nexo pack, then run /nexo reload pack.");
    }
}
