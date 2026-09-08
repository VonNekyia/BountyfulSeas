package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.api.CachedStats;
import com.nekyia.bountyfulSeas.api.StatsApi;
import com.nekyia.bountyfulSeas.database.DatabaseSettings;
import com.nekyia.bountyfulSeas.database.HikariCatchStore;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fish.FishLoadResult;
import com.nekyia.bountyfulSeas.fish.FishLoader;
import com.nekyia.bountyfulSeas.fish.FishProblem;
import com.nekyia.bountyfulSeas.nexo.BlueprintResult;
import com.nekyia.bountyfulSeas.nexo.NexoBlueprintWriter;
import com.nekyia.bountyfulSeas.stats.CatchStore;
import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.config.SettingsLoader;
import com.nekyia.bountyfulSeas.stats.CatchStores;
import com.nekyia.bountyfulSeas.swarm.Swarms;
import com.nekyia.bountyfulSeas.stats.Milestone;
import com.nekyia.bountyfulSeas.pl3xmap.WaterOverlay;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterMapGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
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
    private CatchStore catchStore = CatchStores.none();
    private StatsApi stats;
    private final Swarms swarms = new Swarms();
    private Settings settings;

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
        loadSettings();
        openCatchStore();
        loadWaterMap();
        getServer().getPluginManager().registerEvents(
                new FishingListener(this::fish, this::waterMap, this::swarms,
                        this::settings, this::recordCatch), this);

        PluginCommand root = getCommand(ROOT_COMMAND);
        if (root != null) {
            BountyfulSeasCommand executor = new BountyfulSeasCommand(
                    new DebugCommand(this::fish, this::waterMap, this::swarms, this::settings),
                    this::regenerateWaterMap,
                    new GuideCommand(this, this::fish, this::stats));
            root.setExecutor(executor);
            root.setTabCompleter(executor);
        }
        startSwarms();
        publishWaterOverlay();
        rescanOnStart();
        getLogger().log(Level.INFO, "Ready with {0} fish.", fish.size());
    }

    /**
     * Rescans the world on startup so the map follows the world as it grows.
     *
     * <p>Runs after the server is up rather than during startup: the scan reads
     * every region file, and holding the server on that would delay players joining
     * for no gain. The overlay is drawn once from the old map and redrawn a few
     * seconds later from the new one.
     */
    private void rescanOnStart() {
        if (!settings.rescanOnStart()) {
            return;
        }

        if (!settings.hasAnalyzer()) {
            // Expected on a fresh install, so this is a note and not a complaint.
            getLogger().info("Not rescanning on start: water-map.analyzer is not set in config.yml.");
            return;
        }

        regenerateWaterMap(getServer().getConsoleSender());
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
            int markers = WaterOverlay.publish(worldName, OVERLAY_LABEL, WaterMapAreas.from(waterMap, swarms));
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
        if (catchStore instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception failure) {
                getLogger().log(Level.WARNING, "Could not close the database: {0}", failure.getMessage());
            }
        }
    }

    /** Where the swarming fish are right now. Never null. */
    public Swarms swarms() {
        return swarms;
    }

    /** Everything read out of config.yml. Never null after enable. */
    public Settings settings() {
        return settings;
    }

    private void loadSettings() {
        List<String> problems = new ArrayList<>();
        settings = SettingsLoader.read(getConfig(), problems);
        for (String problem : problems) {
            getLogger().log(Level.WARNING, "config.yml: {0}", problem);
        }
    }

    /**
     * Places the swarms and keeps them moving.
     *
     * <p>Scattered once at startup and again on a timer, so a swarming fish is
     * somewhere different each time the interval passes. The map is redrawn with
     * them, since a swarm that has moved but is still painted gold would send
     * people to the wrong water.
     */
    private void startSwarms() {
        moveSwarms();

        long ticks = Math.max(20L, settings.swarms().rotation().toSeconds() * 20L);
        getServer().getScheduler().runTaskTimer(this, this::moveSwarms, ticks, ticks);
    }

    /** Scatters the swarms afresh and redraws the map to match. */
    private void moveSwarms() {
        if (waterMap == null || waterMap.regionCount() == 0) {
            swarms.clear();
            return;
        }

        List<String> swarming = fish.all().stream()
                .filter(candidate -> candidate.modifiers().contains(Modifier.SWARM))
                .map(Fish::id)
                .toList();

        int[] regions = new int[waterMap.regionCount()];
        for (int id = 0; id < regions.length; id++) {
            regions[id] = id;
        }

        int placed = swarms.scatter(swarming, regions,
                settings.swarms().count(), ThreadLocalRandom.current());

        if (placed > 0) {
            getLogger().log(Level.INFO, "Moved {0} swarm(s).", placed);
        }
        publishWaterOverlay();
    }

    /** The cached read surface over catch totals. Never null. */
    public StatsApi stats() {
        return stats;
    }

    /**
     * Opens the database, or falls back to keeping nothing.
     *
     * <p>A missing or unreachable database is not fatal: fishing works, only the
     * totals and the guide go empty. That keeps a broken database from taking the
     * server's fishing with it.
     */
    private void openCatchStore() {
        Settings.DatabaseSettings db = settings.database();

        if (db.enabled()) {
            DatabaseSettings connection = new DatabaseSettings(
                    db.host(), db.port(), db.database(), db.username(), db.password());
            try {
                catchStore = new HikariCatchStore(connection);
                getLogger().log(Level.INFO, "Catch statistics stored in {0}.", db.database());
            } catch (SQLException | RuntimeException failure) {
                catchStore = CatchStores.none();
                getLogger().log(Level.SEVERE,
                        "Could not reach the database, so no catch statistics will be kept: {0}",
                        failure.getMessage());
            }
        } else {
            getLogger().info("No database configured, so no catch statistics will be kept.");
        }

        stats = new CachedStats(catchStore, db.cacheDuration(), 512);
    }

    /**
     * Writes one catch away, off the server thread.
     *
     * <p>Called from the fishing event, which runs on the main thread, so the
     * database work is handed straight to the scheduler and never made to finish
     * before the player gets their fish.
     */
    private void recordCatch(UUID player, String fishId, double length) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            long catches;
            try {
                catches = catchStore.record(player, fishId, length);
                stats.invalidate(player);
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Could not record a catch of {0}: {1}",
                        new Object[]{fishId, failure.getMessage()});
                return;
            }

            Milestone earned = Milestone.reachedExactly(catches);
            if (earned == null) {
                return;
            }

            // Back to the main thread: the player may have logged off while the
            // write was in flight, and that has to be checked where it cannot change.
            getServer().getScheduler().runTask(this, () -> {
                Player online = getServer().getPlayer(player);
                Fish caught = fish.get(fishId);
                if (online != null && caught != null) {
                    online.sendMessage(CatchMessage.milestone(caught, earned, catches));
                }
            });
        });
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
        if (!settings.hasAnalyzer()) {
            String reason = "water-map.analyzer is not set in config.yml";
            sender.sendMessage(Component.text(
                    "No analyzer configured. Set water-map.analyzer in the BountyfulSeas config.yml "
                            + "to the water-analyzer executable, then try again.", NamedTextColor.RED));
            // Logged as well: a refusal only the player can see is one nobody can
            // debug from the console afterwards.
            getLogger().log(Level.WARNING, "Water map regeneration refused for {0}: {1}",
                    new Object[]{sender.getName(), reason});
            return;
        }

        Path analyzer = Path.of(settings.analyzer());
        Path world = getServer().getWorlds().getFirst().getWorldFolder().toPath();
        Path output = getDataFolder().toPath();
        List<String> arguments = settings.analyzerFlags();

        sender.sendMessage(Component.text("Scanning the world, this can take a while...",
                NamedTextColor.GRAY));
        getLogger().log(Level.INFO, "Water map regeneration started by {0}, scanning {1}",
                new Object[]{sender.getName(), world});

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
                getLogger().log(Level.INFO, "Water map regenerated from {0}: {1}",
                        new Object[]{result.scanned(), result.message()});
            });
        });
    }

    /**
     * Reads the fish folder and reports every rejected definition to the console.
     * A broken file costs its own fish and nothing else.
     */
    public void loadFish() {
        Path folder = getDataFolder().toPath().resolve(FISH_FOLDER);
        saveDefaultCategories();
        saveEnchantmentDatapack();

        FishLoadResult result = FishLoader.load(folder);
        fish = result.library();

        List<FishProblem> rejected = result.problems().stream().filter(FishProblem::fatal).toList();
        List<FishProblem> outdated = result.problems().stream().filter(problem -> !problem.fatal()).toList();

        if (!rejected.isEmpty()) {
            getLogger().log(Level.SEVERE, "{0} fish definition(s) were rejected:", rejected.size());
            for (FishProblem problem : rejected) {
                getLogger().log(Level.SEVERE, "  {0}", problem);
            }
        }
        if (!outdated.isEmpty()) {
            // Loaded anyway, so this is a nudge to tidy the file, not a failure.
            getLogger().log(Level.WARNING, "{0} outdated setting(s), ignored:", outdated.size());
            for (FishProblem problem : outdated) {
                getLogger().log(Level.WARNING, "  {0}", problem);
            }
        }

        getLogger().log(Level.INFO, "Loaded {0} fish.", fish.size());
    }

    /**
     * Lays the Luck of the Fish datapack in the plugin folder, ready to install.
     *
     * <p>Written here rather than straight into the world on purpose. A datapack is
     * the world's business, and an enchantment definition this plugin has never
     * been able to test against a running server is not something to drop into
     * somebody's save unasked. Copy the folder into {@code <world>/datapacks} to
     * turn it on.
     *
     * <p>Nothing here is required: without the enchantment the lookup simply finds
     * nothing and the bonus is never applied.
     */
    private void saveEnchantmentDatapack() {
        saveResource("datapack/pack.mcmeta", false);
        saveResource("datapack/data/bountyfulseas/enchantment/luck_of_the_fish.json", false);
    }

    /**
     * Writes any shipped category the server does not have yet.
     *
     * <p>Runs on every start, not only the first. {@code saveResource} never
     * overwrites, so an edited file is left alone - but a category added to the
     * plugin after a server was set up would otherwise never arrive, and the fish
     * in it would silently not exist.
     *
     * <p>The consequence to know: a shipped category deleted on purpose comes back.
     * Emptying the file is the way to be rid of one.
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
