package com.nekyia.bountyfulSeas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What the bosses' MythicMobs skills need to know about fishing rods, told
 * through scoreboard tags - the whole interface between the two.
 *
 * <ul>
 *   <li>A hook stuck in a holdable mob gives that mob its "held" tag, kept a
 *       moment after the hook lets go: the kraken's tentacles
 *       ({@value #TENTACLE} → {@value #TENTACLE_HELD}) and Nessy herself
 *       ({@value #NESSY} → {@value #NESSY_HELD}). The angler holding the line
 *       carries {@value #HOLDER} for as long.</li>
 *   <li>Nessy's ripples ({@value #RIPPLE}) and the kraken's tentacles are
 *       summoned on the boss itself; this class spreads each one to open water
 *       around it, at least 3 blocks from the others, and marks it
 *       {@value #PLACED}. MythicMobs cannot be trusted with that: its placement
 *       lifts anything summoned on water to the air above, where no water check
 *       passes.</li>
 *   <li>A bobber that lands in one of the placed ripples marks it
 *       {@value #RIPPLE_HOOKED}; the ripple's own skill decides whether it was
 *       the real one.</li>
 *   <li>An angler marked {@value #LINE_CUT} by Nessy's young loses the hook they
 *       are holding her with.</li>
 *   <li>Nessy marked {@value #NECK_SWING} has just swung her neck: every boat
 *       within {@value #SWING_REACH} blocks breaks, and everyone there who is not
 *       under water (eyes in water) dies.</li>
 * </ul>
 */
final class BossHooks implements Listener, Runnable {

    static final String TENTACLE = "kraken_tentakel";
    static final String TENTACLE_HELD = "kraken_gehalten";
    static final String NESSY = "nessy";
    static final String NESSY_HELD = "nessy_gehalten";
    static final String HOLDER = "boss_haelt";
    static final String RIPPLE = "nessy_welle";
    static final String RIPPLE_HOOKED = "nessy_angebissen";
    static final String PLACED = "boss_platziert";
    /** Put on an angler by Nessy's young: their line is bitten through. */
    static final String LINE_CUT = "leine_reisst";
    /** Put on Nessy by her skill the moment the neck swing lands; taken off here. */
    static final String NECK_SWING = "nessy_halsschwung";
    private static final double SWING_REACH = 10;
    /** Kills through any armour; a totem still saves. */
    private static final double SWING_DAMAGE = 1000;

    /** Mob tag → the tag it gets while held. */
    private static final Map<String, String> HOLDABLE = Map.of(TENTACLE, TENTACLE_HELD, NESSY, NESSY_HELD);

    /** How long the tags outlive the hook, in ticks: 1.5 s. */
    private static final long GRACE = 30;
    /** How often the hooks are looked at, in ticks. */
    static final long PERIOD = 5;
    /** How close to a ripple a bobber has to land, in blocks. */
    private static final double RIPPLE_REACH = 2;
    /**
     * How far from where it was summoned a ripple or tentacle may go, in blocks:
     * inside the 19 x 19 of water the bite checked. Tentacles keep clear of the head.
     */
    private static final double RIPPLE_NEAREST = 3;
    private static final double TENTACLE_NEAREST = 4;
    private static final double FARTHEST = 9;
    /** How far apart they stay - so the real ripple has to be told from the rest. */
    private static final double SPACING = 3;
    /**
     * The water surface above the bottom of a water block, in blocks (14 px). Bosses
     * and their parts stand here, so a model's origin is its waterline.
     */
    static final double SURFACE = 0.875;
    private static final int TRIES = 40;

    private final Map<FishHook, Entity> hooks = new HashMap<>();
    /** Held mob or holding player → the tag to take off, and when. */
    private final Map<Entity, Map.Entry<String, Long>> held = new HashMap<>();
    private long now;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHook(ProjectileHitEvent event) {
        if (event.getEntity() instanceof FishHook hook
                && event.getHitEntity() != null
                && heldTagOf(event.getHitEntity()) != null) {
            hooks.put(hook, event.getHitEntity());
        }
    }

    /** Runs every {@link #PERIOD} ticks on the server thread. */
    @Override
    public void run() {
        now += PERIOD;
        for (Iterator<Map.Entry<FishHook, Entity>> it = hooks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<FishHook, Entity> entry = it.next();
            FishHook hook = entry.getKey();
            Entity mob = entry.getValue();
            if (hook.getShooter() instanceof Player angler && angler.removeScoreboardTag(LINE_CUT)) {
                hook.remove();
            }
            if (!hook.isValid() || !mob.isValid() || hook.getHookedEntity() != mob) {
                it.remove();
                continue;
            }
            mark(mob, heldTagOf(mob));
            if (hook.getShooter() instanceof Player angler) {
                mark(angler, HOLDER);
            }
        }
        for (Iterator<Map.Entry<Entity, Map.Entry<String, Long>>> it = held.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Map.Entry<String, Long>> entry = it.next();
            if (entry.getValue().getValue() <= now || !entry.getKey().isValid()) {
                entry.getKey().removeScoreboardTag(entry.getValue().getKey());
                // A bite that came as they let go anyway must not cost them their next line.
                entry.getKey().removeScoreboardTag(LINE_CUT);
                it.remove();
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(Interaction.class, Squid.class, Guardian.class)) {
                if (entity.removeScoreboardTag(NECK_SWING)) {
                    swing(entity);
                    continue;
                }
                if (entity.getScoreboardTags().contains(PLACED)) {
                    continue;
                }
                if (entity.getScoreboardTags().contains(RIPPLE)) {
                    place(entity, RIPPLE_NEAREST);
                } else if (entity.getScoreboardTags().contains(TENTACLE)) {
                    place(entity, TENTACLE_NEAREST);
                }
            }
        }
        markRipples();
    }

    /**
     * Moves a fresh ripple or tentacle to open water around where it was summoned.
     * If the tries run out it stays put - on the boss, which is in water.
     */
    static void place(Entity ripple, double nearest) {
        Location from = ripple.getLocation();
        World world = from.getWorld();
        int y = from.getBlockY();
        // Summoned into the air just above the water: the water block is one lower.
        if (from.getBlock().getType() != Material.WATER
                && from.clone().add(0, -1, 0).getBlock().getType() == Material.WATER) {
            y--;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double distance = random.nextDouble(nearest, FARTHEST);
            Location to = new Location(world,
                    Math.floor(from.getX() + Math.cos(angle) * distance) + 0.5, y + SURFACE,
                    Math.floor(from.getZ() + Math.sin(angle) * distance) + 0.5);
            if (to.getBlock().getType() == Material.WATER
                    && !to.clone().add(0, 1, 0).getBlock().isSolid()
                    && clearOfOthers(to, ripple)) {
                ripple.teleport(to);
                ripple.addScoreboardTag(PLACED);
                return;
            }
        }
        ripple.getServer().getLogger().warning("[BountyfulSeas] No open water for " + ripple.getScoreboardTags()
                + " in " + TRIES + " tries around " + from.getBlockX() + " " + y + " " + from.getBlockZ()
                + " (block there: " + world.getBlockAt(from.getBlockX(), y, from.getBlockZ()).getType()
                + "); left on the boss.");
        ripple.teleport(new Location(world, from.getX(), y + SURFACE, from.getZ()));
        ripple.addScoreboardTag(PLACED);
    }

    /** The neck swing lands: boats break first, so who sat in one is not under water. */
    static void swing(Entity nessy) {
        double reach = SWING_REACH * SWING_REACH;
        var near = nessy.getNearbyEntities(SWING_REACH, SWING_REACH, SWING_REACH).stream()
                .filter(entity -> entity.getLocation().distanceSquared(nessy.getLocation()) <= reach)
                .toList();
        for (Entity entity : near) {
            if (entity instanceof Boat boat) {
                boat.eject();
                boat.getWorld().playSound(boat.getLocation(), Sound.BLOCK_WOOD_BREAK, 2, 0.6f);
                boat.remove();
            }
        }
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
                .withCausingEntity(nessy).withDirectEntity(nessy).build();
        for (Entity entity : near) {
            if (!(entity instanceof Player player)) {
                continue;
            }
            if (player.isUnderWater()) {
                player.sendActionBar(Component.text("Der Hals rauscht über dich hinweg.", NamedTextColor.GREEN));
            } else {
                player.damage(SWING_DAMAGE, source);
            }
        }
    }

    private static boolean clearOfOthers(Location to, Entity ripple) {
        for (Entity other : to.getWorld().getNearbyEntities(to, SPACING, SPACING, SPACING)) {
            if (other != ripple && other.getScoreboardTags().contains(PLACED)
                    && other.getLocation().distanceSquared(to) < SPACING * SPACING) {
                return false;
            }
        }
        return true;
    }

    private void mark(Entity entity, String tag) {
        entity.addScoreboardTag(tag);
        held.put(entity, Map.entry(tag, now + GRACE));
    }

    private static String heldTagOf(Entity entity) {
        for (Map.Entry<String, String> holdable : HOLDABLE.entrySet()) {
            if (entity.getScoreboardTags().contains(holdable.getKey())) {
                return holdable.getValue();
            }
        }
        return null;
    }

    /** Bobbers floating in one of Nessy's ripples mark it, once. */
    private static void markRipples() {
        for (World world : Bukkit.getWorlds()) {
            for (FishHook hook : world.getEntitiesByClass(FishHook.class)) {
                if (!hook.isInWater()) {
                    continue;
                }
                for (Entity near : hook.getNearbyEntities(RIPPLE_REACH, RIPPLE_REACH, RIPPLE_REACH)) {
                    if (near.getScoreboardTags().contains(RIPPLE)
                            && near.getScoreboardTags().contains(PLACED)) {
                        near.addScoreboardTag(RIPPLE_HOOKED);
                    }
                }
            }
        }
    }
}
