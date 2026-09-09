package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Fish;

import java.util.random.RandomGenerator;

/**
 * A fish that has actually been pulled out, with the size it turned out to be.
 *
 * @param length rolled from the fish's maximum along the length curve, in cm
 */
public record Catch(Fish fish, double length) {

    /**
     * Rolls a size for this fish.
     *
     * <p>A definition gives one number, the most this fish ever reaches. Everything
     * below it comes from {@link LengthCurve}, which is shaped like the length
     * composition of a real stock rather than spread evenly: most catches middling,
     * a good one now and then, and the maximum only once in a very long while.
     */
    public static Catch roll(Fish fish, LengthCurve curve, RandomGenerator random) {
        boolean outsized = fish.rarity() != null && fish.rarity().outsized();
        double length = outsized
                ? curve.rollOutsized(fish.maxLength(), random)
                : curve.roll(fish.maxLength(), random);
        return new Catch(fish, length);
    }
}
