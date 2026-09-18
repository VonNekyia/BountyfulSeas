package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fish.Rarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

/** Picks which fish comes out of the water. */
public final class FishSelector {

    private FishSelector() {
    }

    /**
     * Every fish that could be caught in these conditions, in definition order.
     *
     * <p>Exposed on its own because it answers "why did I not catch that here",
     * which is the question anyone debugging a fish table actually has.
     *
     * @param anglerLevel the level of whoever is fishing; higher fish stay out
     */
    public static List<Fish> candidates(Collection<Fish> fishes, WaterConditions where,
                                        int anglerLevel) {
        List<Fish> candidates = new ArrayList<>();
        for (Fish fish : fishes) {
            // The level gate sits with the other filters rather than after the roll:
            // a fish nobody can catch yet must not take a tier's share of the odds
            // and hand back nothing when it wins.
            if (fish.level() <= anglerLevel && matches(fish, where)) {
                candidates.add(fish);
            }
        }
        return candidates;
    }

    /**
     * Fish that live here but sit above the angler's level.
     *
     * <p>The other half of the candidate list, and the other half of "why did I not
     * catch that here": the fish is not missing, it is simply not yet theirs. Sorted
     * by the level that opens it, because that is the answer.
     */
    public static List<Fish> outOfReach(Collection<Fish> fishes, WaterConditions where,
                                        int anglerLevel) {
        List<Fish> locked = new ArrayList<>();
        for (Fish fish : fishes) {
            if (fish.level() > anglerLevel && matches(fish, where)) {
                locked.add(fish);
            }
        }
        locked.sort(Comparator.comparingInt(Fish::level).thenComparing(Fish::id));
        return List.copyOf(locked);
    }

    /**
     * What each fish's odds are here, most likely first.
     *
     * <p>The same candidates and the same weights the roll uses, so what this
     * reports and what the water actually gives cannot drift apart.
     */
    public static List<Chance> chances(Collection<Fish> fishes, WaterConditions where,
                                       int anglerLevel, ToDoubleFunction<Rarity> chanceOf) {
        return chances(fishes, where, anglerLevel, CatchOdds.of(chanceOf));
    }

    /** The same, with a particular rod in it. */
    public static List<Chance> chances(Collection<Fish> fishes, WaterConditions where,
                                       int anglerLevel, CatchOdds odds) {
        List<Fish> candidates = candidates(fishes, where, anglerLevel);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Taken out of the tier draw entirely and given the top of the roll, so what
        // is left for everything else is what the tiers divide between them.
        List<Chance> chances = new ArrayList<>(candidates.size());
        double spoken = 0;
        for (Fish fish : candidates) {
            if (fish.hasOwnChance()) {
                double percent = ownChance(fish, odds.ownChanceLuck());
                spoken += percent;
                chances.add(new Chance(fish, fish.spawnWeight(), percent));
            }
        }
        double left = Math.max(0, 100 - spoken) / 100;

        Map<Rarity, List<Fish>> byRarity = new EnumMap<>(Rarity.class);
        for (Fish fish : candidates) {
            if (!fish.hasOwnChance()) {
                byRarity.computeIfAbsent(fish.rarity(), key -> new ArrayList<>()).add(fish);
            }
        }
        if (byRarity.isEmpty()) {
            chances.sort(Comparator.comparingDouble(Chance::percent).reversed());
            return List.copyOf(chances);
        }

        Map<Rarity, Double> tiers = tierChances(byRarity.keySet(), odds);
        double tierTotal = 0;
        for (double chance : tiers.values()) {
            tierTotal += chance;
        }
        if (tierTotal <= 0) {
            chances.sort(Comparator.comparingDouble(Chance::percent).reversed());
            return List.copyOf(chances);
        }

        // The real odds: the tier's normalised share, split by spawn weight inside it.
        for (Map.Entry<Rarity, List<Fish>> entry : byRarity.entrySet()) {
            double tierShare = tiers.getOrDefault(entry.getKey(), 0.0) / tierTotal;
            long weightTotal = entry.getValue().stream().mapToLong(Fish::spawnWeight).sum();
            if (tierShare <= 0 || weightTotal <= 0) {
                continue;
            }
            for (Fish fish : entry.getValue()) {
                chances.add(new Chance(fish, fish.spawnWeight(),
                        left * 100.0 * tierShare * fish.spawnWeight() / weightTotal));
            }
        }
        chances.sort(Comparator.comparingDouble(Chance::percent).reversed());
        return List.copyOf(chances);
    }

    /**
     * One fish for these conditions, or null when nothing lives here.
     *
     * <p>Two rolls, not one. The tier is drawn first, from the configured chances,
     * and only then is an entry drawn from within it by spawn weight. Rolling in
     * one pass would make a legendary likelier in water that happens to hold three
     * of them, which is the opposite of what a rarity is for.
     *
     * <p>Only tiers actually present here take part. A spot with no legendary
     * simply never rolls one, and that share goes to junk rather than making every
     * other fish here likelier - see {@link #tierChances}.
     *
     * <p>Null is an ordinary outcome, not a failure: water nobody wrote a fish for
     * should hand back vanilla's catch rather than something invented.
     *
     * @param chanceOf the configured chance of a tier, by its config name
     */
    public static Fish select(Collection<Fish> fishes, WaterConditions where, int anglerLevel,
                              ToDoubleFunction<Rarity> chanceOf, RandomGenerator random) {
        return select(fishes, where, anglerLevel, CatchOdds.of(chanceOf), random);
    }

    /** The same, with a particular rod in it. */
    public static Fish select(Collection<Fish> fishes, WaterConditions where, int anglerLevel,
                              CatchOdds odds, RandomGenerator random) {
        List<Fish> candidates = candidates(fishes, where, anglerLevel);
        if (candidates.isEmpty()) {
            return null;
        }

        // A fish with a chance of its own is asked first and on its own terms. Rolled
        // against the whole hundred rather than inside a tier, so a quarter of one per
        // cent means that wherever it lives and whatever else lives there too.
        double roll = random.nextDouble(100);
        for (Fish fish : candidates) {
            if (!fish.hasOwnChance()) {
                continue;
            }
            double percent = ownChance(fish, odds.ownChanceLuck());
            if (roll < percent) {
                return fish;
            }
            roll -= percent;
        }

        Map<Rarity, List<Fish>> byRarity = new EnumMap<>(Rarity.class);
        for (Fish fish : candidates) {
            if (!fish.hasOwnChance()) {
                byRarity.computeIfAbsent(fish.rarity(), key -> new ArrayList<>()).add(fish);
            }
        }
        if (byRarity.isEmpty()) {
            return null;
        }

        Rarity tier = rollRarity(tierChances(byRarity.keySet(), odds), random);
        return tier == null ? null : rollWithin(byRarity.get(tier), random);
    }

    /**
     * What a fish with its own chance is worth on this rod.
     *
     * <p>Never the whole hundred, however enchanted the rod: something has to be
     * left for the water to hand up anything else.
     */
    private static double ownChance(Fish fish, double luck) {
        return Math.min(99, fish.catchChance() * Math.max(1, luck));
    }

    /**
     * What each tier here is really worth, before anything is normalised.
     *
     * <p>A tier with nothing to offer at this spot does not simply drop out and
     * leave the others to share its odds between them. Its share goes to junk: water
     * with no rare fish in it should hand up a boot where the rare would have been,
     * which is the difference between a poor spot and a smaller one.
     *
     * <p>Where there is no junk here either, the share is not drawn at all and what
     * is left normalises against itself - the only honest answer when a spot holds
     * nothing that could stand in.
     *
     * <p>Only the unenchanted weight is handed over. What a rod adds to a tier that
     * is not here is simply not in play: it is neither drawn nor given away.
     */
    public static Map<Rarity, Double> tierChances(Set<Rarity> present, CatchOdds odds) {
        Map<Rarity, Double> tiers = new EnumMap<>(odds.onRod().at(present));
        double forfeited = 0;
        for (Rarity rarity : Rarity.values()) {
            if (!present.contains(rarity)) {
                // What an absent tier hands over is what it was worth before the rod
                // touched it. Handing over the enchanted weight would turn a rod
                // bought for better fish into a rod for more junk, wherever the fish
                // it lifts do not live - and that is most water, for a legendary.
                forfeited += odds.bareWeightOf(rarity);
            }
        }
        if (forfeited > 0 && tiers.containsKey(Rarity.MISC)) {
            tiers.merge(Rarity.MISC, forfeited, Double::sum);
        }
        return tiers;
    }

    /** Draws a tier from those present, weighted by what it is worth here. */
    private static Rarity rollRarity(Map<Rarity, Double> tiers, RandomGenerator random) {
        double total = 0;
        for (double chance : tiers.values()) {
            total += chance;
        }
        if (total <= 0) {
            // Every present tier is configured to never appear.
            return null;
        }

        double roll = random.nextDouble(total);
        Rarity last = null;
        for (Map.Entry<Rarity, Double> entry : tiers.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            last = entry.getKey();
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return last;
    }

    /** Draws one entry from a tier by spawn weight. */
    private static Fish rollWithin(List<Fish> tier, RandomGenerator random) {
        long total = 0;
        for (Fish fish : tier) {
            total += fish.spawnWeight();
        }
        if (total <= 0) {
            return null;
        }

        long roll = random.nextLong(total);
        for (Fish fish : tier) {
            roll -= fish.spawnWeight();
            if (roll < 0) {
                return fish;
            }
        }
        return tier.getLast();
    }

    /**
     * Whether a fish accepts these conditions.
     *
     * <p>Each trait list is a filter: empty places no restriction. Where the list
     * says where a fish lives, any one of its values is enough; where it says what
     * has to be true of the moment - the conditions - every one of them must hold.
     */
    private static boolean matches(Fish fish, WaterConditions where) {
        if (fish.baitLocked() || fish.spawnWeight() <= 0) {
            return false;
        }
        // A swarming fish is somewhere else entirely most of the time.
        if (fish.modifiers().contains(Modifier.SWARM) && !where.hasSwarmOf(fish.id())) {
            return false;
        }
        return fish.allowsWaterType(where.waterType())
                && fish.allowsTerrain(where.terrain())
                && fish.allowsVegetation(where.vegetation())
                && fish.allowsDepth(where.depth())
                && waterModifiers(fish, where)
                && allOf(fish.conditions(), where.conditions());
    }

    /**
     * Only the modifiers that describe water take part in matching.
     *
     * <p>trash and treasure mark what an entry is, not where it is found. Matching
     * them against a spot would exclude every spot, since no water is ever trash.
     */
    private static boolean waterModifiers(Fish fish, WaterConditions where) {
        Set<Modifier> required = fish.modifiers().stream()
                .filter(Modifier::describesWater)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
        return anyOf(required, where.modifiers());
    }

    /** An empty requirement accepts anything; otherwise one shared value is enough. */
    private static <E> boolean anyOf(Set<E> required, Set<E> present) {
        return required.isEmpty() || !Collections.disjoint(required, present);
    }

    /**
     * Every listed one has to hold, not just one of them.
     *
     * <p>Conditions are the one axis where that is the natural reading: a fish that
     * wants night and storm wants both at once, where a fish that lists river and
     * lake is happy in either. Where a trait describes somewhere the fish could be,
     * any of them will do; where it describes what has to be true, all of them must.
     */
    private static <E> boolean allOf(Set<E> required, Set<E> present) {
        return required.isEmpty() || present.containsAll(required);
    }
}
