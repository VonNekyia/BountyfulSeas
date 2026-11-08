package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fish.TierKinds;
import com.nekyia.bountyfulSeas.fishing.Catch;
import com.nekyia.bountyfulSeas.fishing.LengthCurve;
import com.nekyia.bountyfulSeas.fishing.CatchOdds;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.swarm.Swarms;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;
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

    /** How long the angler is warned before a boss comes up, in ticks: 5 s. */
    private static final long BOSS_WARNING_TICKS = 100;

    private final Supplier<FishLibrary> fish;
    private final Supplier<WaterMap> waterMap;
    private final Supplier<Swarms> swarms;
    private final Supplier<Settings> settings;
    private final Supplier<TierKinds> kinds;
    private final AnglerLevels levels;
    private final ForcedCatches forced;
    private final CatchRecorder recorder;
    private final Consumer<Player> loadLevel;
    private final BossLoot kraken;
    private final BossLoot nessy;

    FishingListener(Supplier<FishLibrary> fish, Supplier<WaterMap> waterMap,
                    Supplier<Swarms> swarms, Supplier<Settings> settings,
                    Supplier<TierKinds> kinds, AnglerLevels levels, ForcedCatches forced,
                    CatchRecorder recorder, Consumer<Player> loadLevel, BossLoot kraken, BossLoot nessy) {
        this.fish = fish;
        this.waterMap = waterMap;
        this.swarms = swarms;
        this.settings = settings;
        this.kinds = kinds;
        this.levels = levels;
        this.forced = forced;
        this.recorder = recorder;
        this.loadLevel = loadLevel;
        this.kraken = kraken;
        this.nessy = nessy;
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
        return new LengthCurve(sizes.shape(), sizes.smallest(), sizes.oddsOfMax(), sizes.tailFrom());
    }

    /** Where a landed catch is sent to be counted. */
    @FunctionalInterface
    interface CatchRecorder {
        /** @param odds the chance of this exact catch, 1 when it was arranged rather than rolled */
        void record(UUID player, String fishId, double length, double odds);
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
        // An arranged bite skips the roll but nothing else: the water still has to
        // be fishable and the item still has to exist, so what comes up is a real
        // catch rather than something conjured past the parts being tested.
        ForcedCatches.Forced arranged = forced.take(event.getPlayer().getUniqueId());
        int level = levels.levelOf(event.getPlayer().getUniqueId());
        CatchOdds rod = RodChances.oddsFor(event.getPlayer(), settings.get(), kinds.get());
        // Without bait, nothing rare or better is in the water at all.
        boolean baited = BaitBucket.hasBait(event.getPlayer());
        Collection<Fish> pool = baited ? fish.get().all()
                : fish.get().all().stream().filter(entry -> !entry.needsBait()).toList();
        if (arranged == null && baited && bossBites(spot, level, hook, event.getPlayer())) {
            event.setCancelled(true);
            event.getHook().remove();
            BaitBucket.consume(event.getPlayer());
            return;
        }
        Fish picked = arranged == null
                ? FishSelector.select(pool, spot, level, rod, ThreadLocalRandom.current())
                : fish.get().get(arranged.fishId());
        if (picked == null) {
            return;
        }

        ItemStack stack = itemFor(picked);
        if (stack == null) {
            // Without the real item, handing over vanilla's fish beats handing over nothing.
            return;
        }

        Catch landed = arranged != null && arranged.length() != null
                ? new Catch(picked, arranged.length())
                : Catch.roll(picked, lengthCurve(), ThreadLocalRandom.current());
        // How likely exactly this was: the fish biting here, with this rod, times a
        // length at least this long. An arranged catch was not rolled, so it has none.
        LengthCurve curve = lengthCurve();
        double odds = 1;
        if (arranged == null) {
            double share = FishSelector.chances(pool, spot, level, rod).stream()
                    .filter(chance -> chance.fish().id().equals(picked.id()))
                    .mapToDouble(chance -> chance.percent() / 100)
                    .findFirst().orElse(1);
            odds = picked.isCatchMarker() ? share
                    : share * curve.chanceOfAtLeast(picked.maxLength(), landed.length(),
                            picked.rarity().outsized());
        }
        boolean tail = arranged == null && !picked.isCatchMarker()
                && curve.inTail(picked.maxLength(), landed.length());

        caught.setItemStack(stack);
        event.getPlayer().sendMessage(CatchMessage.of(landed, stack, tail ? odds : 1));
        if (baited && arranged == null) {
            BaitBucket.consume(event.getPlayer());
        }

        recorder.record(event.getPlayer().getUniqueId(), picked.id(), landed.length(), odds);
    }

    /**
     * Whether a boss takes the bait instead of a fish, and if so, spawns it: the
     * kraken in the ocean, Nessy in lakes.
     *
     * <p>Only ever one of each at a time: a second bite while one is up is just a
     * fish. Spawned through the MythicMobs console command, so this plugin needs
     * no MythicMobs on its classpath and runs without it.
     */
    private boolean bossBites(WaterSpot spot, int level, Location hook, Player angler) {
        return switch (spot.waterType()) {
            case OCEAN -> bites(settings.get().kraken(), kraken, level, hook, angler,
                    "Etwas Gewaltiges hat angebissen", "... bring dich in Sicherheit!",
                    Sound.ENTITY_ELDER_GUARDIAN_CURSE);
            case LAKE -> bites(settings.get().nessy(), nessy, level, hook, angler,
                    "Etwas Uraltes hat angebissen", "... und der See wird ganz still.",
                    Sound.BLOCK_BELL_RESONATE);
            default -> false;
        };
    }

    private boolean bites(Settings.BossSettings boss, BossLoot loot, int level, Location hook, Player angler,
                          String title, String subtitle, Sound sound) {
        if (boss.chance() <= 0 || level < boss.minLevel()
                || ThreadLocalRandom.current().nextDouble() >= boss.chance()
                || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return false;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(loot.boss().headTag())) {
                    return false;
                }
            }
        }
        if (!openWater(hook)) {
            return false;
        }
        // A warning first, so whoever hooked it can still cut and run.
        angler.showTitle(Title.title(
                Component.text(title, NamedTextColor.DARK_AQUA, TextDecoration.BOLD),
                Component.text(subtitle, NamedTextColor.GRAY)));
        angler.playSound(angler.getLocation(), sound, 1f, 0.5f);
        Location at = hook.clone();
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(FishingListener.class), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn " + boss.mob() + " 1 "
                    + at.getWorld().getName() + "," + (at.getBlockX() + 0.5) + ","
                    + (at.getBlockY() + BossHooks.SURFACE) + "," + (at.getBlockZ() + 0.5));
            loot.spawned(at, angler.getUniqueId());
        }, BOSS_WARNING_TICKS);
        return true;
    }

    /**
     * Whether there is room for a boss here: about 20 by 20 blocks of water at
     * least four deep around the bobber, which is where the fight takes place.
     */
    private static boolean openWater(Location hook) {
        World world = hook.getWorld();
        int y = hook.getBlockY();
        int samples = 0;
        int wet = 0;
        for (int dx = -9; dx <= 9; dx += 3) {
            for (int dz = -9; dz <= 9; dz += 3) {
                samples++;
                int x = hook.getBlockX() + dx;
                int z = hook.getBlockZ() + dz;
                if (world.getBlockAt(x, y, z).getType() == Material.WATER
                        && world.getBlockAt(x, y - 3, z).getType() == Material.WATER) {
                    wet++;
                }
            }
        }
        return wet >= samples * 0.9;
    }

    private ItemStack itemFor(Fish picked) {
        String id = FishNexoItems.nexoIdOf(picked.item());
        return id == null ? null : NexoItemFactory.create(id);
    }
}
