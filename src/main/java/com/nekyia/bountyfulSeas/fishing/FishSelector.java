package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Fish;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
     */
    public static List<Fish> candidates(Collection<Fish> fishes, WaterConditions where) {
        List<Fish> candidates = new ArrayList<>();
        for (Fish fish : fishes) {
            if (matches(fish, where)) {
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
    public static List<Chance> chances(Collection<Fish> fishes, WaterConditions where) {
        List<Fish> candidates = candidates(fishes, where);
        long total = 0;
        for (Fish fish : candidates) {
            total += fish.spawnWeight();
        }
        if (total <= 0) {
            return List.of();
        }

        List<Chance> chances = new ArrayList<>(candidates.size());
        for (Fish fish : candidates) {
            chances.add(new Chance(fish, fish.spawnWeight(), 100.0 * fish.spawnWeight() / total));
        }
        chances.sort(Comparator.comparingDouble(Chance::percent).reversed());
        return List.copyOf(chances);
    }

    /**
     * One fish for these conditions, chosen by spawn weight, or null when nothing
     * lives here.
     *
     * <p>Null is an ordinary outcome, not a failure: water nobody wrote a fish for
     * should hand back vanilla's catch rather than something invented.
     */
    public static Fish select(Collection<Fish> fishes, WaterConditions where, RandomGenerator random) {
        List<Fish> candidates = candidates(fishes, where);
        if (candidates.isEmpty()) {
            return null;
        }

        long total = 0;
        for (Fish fish : candidates) {
            total += fish.spawnWeight();
        }
        if (total <= 0) {
            // Every candidate is disabled, which is a table saying nothing lives here.
            return null;
        }

        long roll = random.nextLong(total);
        for (Fish fish : candidates) {
            roll -= fish.spawnWeight();
            if (roll < 0) {
                return fish;
            }
        }
        return candidates.getLast();
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
        return fish.allowsWaterType(where.waterType())
                && fish.allowsTerrain(where.terrain())
                && fish.allowsVegetation(where.vegetation())
                && fish.allowsDepth(where.depth())
                && anyOf(fish.modifiers(), where.modifiers())
                && anyOf(fish.conditions(), where.conditions());
    }

    /** An empty requirement accepts anything; otherwise one shared value is enough. */
    private static <E> boolean anyOf(Set<E> required, Set<E> present) {
        return required.isEmpty() || !Collections.disjoint(required, present);
    }
}
