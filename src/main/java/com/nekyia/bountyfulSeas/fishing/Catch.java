package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Fish;

import java.util.random.RandomGenerator;

/**
 * A fish that has actually been pulled out, with the size it turned out to be.
 *
 * @param weight rolled between the fish's min and max weight
 * @param length rolled between the fish's min and max length
 */
public record Catch(Fish fish, double weight, double length) {

    /**
     * Rolls a size for this fish.
     *
     * <p>Uniform between the bounds. Real fish are not uniformly sized, but a
     * definition only gives two numbers, and inventing a curve between them would
     * be making up data the config never supplied.
     */
    public static Catch roll(Fish fish, RandomGenerator random) {
        return new Catch(fish,
                between(fish.minWeight(), fish.maxWeight(), random),
                between(fish.minLength(), fish.maxLength(), random));
    }

    private static double between(double min, double max, RandomGenerator random) {
        return max <= min ? min : random.nextDouble(min, max);
    }
}
