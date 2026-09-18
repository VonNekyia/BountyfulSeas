package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Rarity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * What a particular rod makes of the configured odds.
 *
 * <p>Three things travel together because the draw needs all three, and letting
 * them drift apart is how a report and a catch come to disagree.
 *
 * <p>{@code bare} is the same table with no rod in it, and it is not redundant. A
 * tier with nothing to offer at a spot hands its share to junk, and what it hands
 * over has to be what it was worth <em>before</em> the enchantment: otherwise a rod
 * that raises the odds of a legendary raises the odds of a boot in water that has
 * no legendary in it, which is the opposite of what the angler paid for.
 *
 * <p>The enchanted side is asked per spot rather than once, because what a rod is
 * worth depends on what lives there. An enchantment that lifts the rarest fish is
 * worth nothing in water that holds none - and, just as importantly, must cost
 * nothing there either: the everyday tiers pay for what the rod adds, and they
 * should not be paying for a legendary that was never on offer.
 *
 * @param onRod         each tier's weight with the rod applied, given what is here
 * @param bare          each tier's weight as configured, with no rod
 * @param ownChanceLuck what the rod does to a fish that carries its own chance
 */
public record CatchOdds(TierWeights onRod, ToDoubleFunction<Rarity> bare, double ownChanceLuck) {

    /** What the tiers on offer at one spot are worth, once a rod is taken into account. */
    @FunctionalInterface
    public interface TierWeights {

        /** @param present the tiers with something to offer here */
        Map<Rarity, Double> at(Set<Rarity> present);
    }

    /** The configured odds, for anything asking what the water is worth in itself. */
    public static CatchOdds of(ToDoubleFunction<Rarity> chances) {
        return new CatchOdds(present -> {
            Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
            for (Rarity rarity : present) {
                weights.put(rarity, Math.max(0, chances.applyAsDouble(rarity)));
            }
            return weights;
        }, chances, 1);
    }

    double bareWeightOf(Rarity rarity) {
        return Math.max(0, bare.applyAsDouble(rarity));
    }
}
