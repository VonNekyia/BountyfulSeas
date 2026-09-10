package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fishing.Catch;
import com.nekyia.bountyfulSeas.fishing.LengthCurve;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.swarm.Swarms;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Replaces the vanilla catch with whatever lives in this water.
 *
 * <p>Everything the decision needs comes from somewhere else: the map says what
 * the water is, {@link WaterSpot} says what that means to a fish, and the selector
 * picks one. This class only knows how to ask, and when to leave well alone.
 */
final class FishingListener implements Listener {

    private final Supplier<FishLibrary> fish;
    private final Supplier<WaterMap> waterMap;
    private final Supplier<Swarms> swarms;
    private final Supplier<Settings> settings;
    private final AnglerLevels levels;
    private final CatchRecorder recorder;
    private final Consumer<Player> loadLevel;

    FishingListener(Supplier<FishLibrary> fish, Supplier<WaterMap> waterMap,
                    Supplier<Swarms> swarms, Supplier<Settings> settings,
                    AnglerLevels levels, CatchRecorder recorder, Consumer<Player> loadLevel) {
        this.fish = fish;
        this.waterMap = waterMap;
        this.swarms = swarms;
        this.settings = settings;
        this.levels = levels;
        this.recorder = recorder;
        this.loadLevel = loadLevel;
    }

    /**
     * Puts a joining player's level in memory before they can cast.
     *
     * <p>Here rather than in a listener of its own because this is the only thing
     * that needs it: a bite is answered on the server thread, so the level has to
     * be known by then rather than fetched at that moment.
     */
    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        loadLevel.accept(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        levels.forget(event.getPlayer().getUniqueId());
    }

    /**
     * The configured length curve, rebuilt each catch so a config reload is felt.
     *
     * <p>Built here rather than held in the settings: config knows the numbers,
     * the fishing module knows what they mean, and this is the seam between them.
     */
    private LengthCurve lengthCurve() {
        Settings.SizeSettings sizes = settings.get().sizes();
        return new LengthCurve(sizes.shape(), sizes.smallest(), sizes.oddsOfMax());
    }

    /** Where a landed catch is sent to be counted. */
    @FunctionalInterface
    interface CatchRecorder {
        void record(java.util.UUID player, String fishId, double length);
    }

    /**
     * Runs late and ignores cancelled events, so anything that wanted to stop the
     * catch outright still wins.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            return;
        }

        WaterMap map = waterMap.get();
        if (map == null) {
            return;
        }

        Location hook = event.getHook().getLocation();
        WaterRegion region = map.regionAt(hook.getBlockX(), hook.getBlockZ());
        if (region == null) {
            // Unclassified water: vanilla keeps its catch.
            return;
        }

        WaterSpot spot = WaterSpot.of(region, hook.getWorld(), swarms.get());
        Fish picked = FishSelector.select(fish.get().all(), spot,
                levels.levelOf(event.getPlayer().getUniqueId()),
                RodChances.of(event.getPlayer(), settings.get()), ThreadLocalRandom.current());
        if (picked == null) {
            return;
        }

        ItemStack stack = itemFor(picked);
        if (stack == null) {
            // Without the real item, handing over vanilla's fish beats handing over nothing.
            return;
        }

        Catch landed = Catch.roll(picked, lengthCurve(), ThreadLocalRandom.current());
        caught.setItemStack(stack);
        event.getPlayer().sendMessage(CatchMessage.of(landed, stack));

        recorder.record(event.getPlayer().getUniqueId(), picked.id(), landed.length());
    }

    private ItemStack itemFor(Fish picked) {
        String id = FishNexoItems.nexoIdOf(picked.item());
        return id == null ? null : NexoItemFactory.create(id);
    }
}
