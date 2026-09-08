package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Fish;

import java.util.random.RandomGenerator;

/**
 * A fish that has actually been pulled out, with the size it turned out to be.
 *
 * @param length rolled between the fish's min and max length
 */
public record Catch(Fish fish, double length) {

    /** How much of the range a mythic is confined to, measured down from the top. */
    private static final double MYTHIC_TOP_BAND = 0.10;

    /**
     * Rolls a size for this fish.
     *
     * <p>Uniform between the bounds. Real fish are not uniformly sized, but a
     * definition only gives two numbers, and inventing a curve between them would
     * be making up data the config never supplied.
     *
     * <p>A mythic is the exception: it rolls in the top tenth of its range, so one
     * is always a specimen rather than sometimes a runt.
     */
    public static Catch roll(Fish fish, RandomGenerator random) {
        double low = fish.minLength();
        double high = fish.maxLength();

        if (fish.rarity() != null && fish.rarity().outsized()) {
            low = high - (high - low) * MYTHIC_TOP_BAND;
        }
        return new Catch(fish, between(low, high, random));
    }

    private static double between(double min, double max, RandomGenerator random) {
        return max <= min ? min : random.nextDouble(min, max);
    }
}
