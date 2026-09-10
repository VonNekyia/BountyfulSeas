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
     * What each fish's odds are here, most likely first.
     *
     * <p>The same candidates and the same weights the roll uses, so what this
     * reports and what the water actually gives cannot drift apart.
     */
    public static List<Chance> chances(Collection<Fish> fishes, WaterConditions where,
                                       int anglerLevel, ToDoubleFunction<Rarity> chanceOf) {
        List<Fish> candidates = candidates(fishes, where, anglerLevel);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Rarity, List<Fish>> byRarity = new EnumMap<>(Rarity.class);
        for (Fish fish : candidates) {
            byRarity.computeIfAbsent(fish.rarity(), key -> new ArrayList<>()).add(fish);
        }

        double tierTotal = 0;
        for (Rarity rarity : byRarity.keySet()) {
            tierTotal += Math.max(0, chanceOf.applyAsDouble(rarity));
        }
        if (tierTotal <= 0) {
            return List.of();
        }

        // The real odds: the tier's normalised share, split by spawn weight inside it.
        List<Chance> chances = new ArrayList<>(candidates.size());
        for (Map.Entry<Rarity, List<Fish>> entry : byRarity.entrySet()) {
            double tierShare = Math.max(0, chanceOf.applyAsDouble(entry.getKey())) / tierTotal;
            long weightTotal = entry.getValue().stream().mapToLong(Fish::spawnWeight).sum();
            if (tierShare <= 0 || weightTotal <= 0) {
                continue;
            }
            for (Fish fish : entry.getValue()) {
                chances.add(new Chance(fish, fish.spawnWeight(),
                        100.0 * tierShare * fish.spawnWeight() / weightTotal));
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
     * <p>Only tiers actually present here take part, and their chances are
     * normalised against each other. A spot with no legendary simply never rolls
     * one, rather than rolling one and coming up empty.
     *
     * <p>Null is an ordinary outcome, not a failure: water nobody wrote a fish for
     * should hand back vanilla's catch rather than something invented.
     *
     * @param chanceOf the configured chance of a tier, by its config name
     */
    public static Fish select(Collection<Fish> fishes, WaterConditions where, int anglerLevel,
                              ToDoubleFunction<Rarity> chanceOf, RandomGenerator random) {
        List<Fish> candidates = candidates(fishes, where, anglerLevel);
        if (candidates.isEmpty()) {
            return null;
        }

        Map<Rarity, List<Fish>> byRarity = new EnumMap<>(Rarity.class);
        for (Fish fish : candidates) {
            byRarity.computeIfAbsent(fish.rarity(), key -> new ArrayList<>()).add(fish);
        }

        Rarity tier = rollRarity(byRarity.keySet(), chanceOf, random);
        return tier == null ? null : rollWithin(byRarity.get(tier), random);
    }

    /** Draws a tier from those present, weighted by its configured chance. */
    private static Rarity rollRarity(Set<Rarity> present, ToDoubleFunction<Rarity> chanceOf,
                                     RandomGenerator random) {
        double total = 0;
        for (Rarity rarity : present) {
            total += Math.max(0, chanceOf.applyAsDouble(rarity));
        }
        if (total <= 0) {
            // Every present tier is configured to never appear.
            return null;
        }

        double roll = random.nextDouble(total);
        Rarity last = null;
        for (Rarity rarity : present) {
            double chance = Math.max(0, chanceOf.applyAsDouble(rarity));
            if (chance <= 0) {
                continue;
            }
            last = rarity;
            roll -= chance;
            if (roll < 0) {
                return rarity;
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
     * <p>Each trait list is a filter: empty places no restriction, and a populated
     * one needs the spot to match any of its values, not all of them.
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
                && anyOf(fish.conditions(), where.conditions());
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
}
