package com.nekyia.bountyfulSeas.fishing;

import java.util.random.RandomGenerator;

/**
 * How long a caught fish turns out to be.
 *
 * <p>The shape is the one fisheries science uses for the length composition of a
 * real stock, from Beverton and Holt: numbers fall away exponentially with age
 * while length approaches an asymptote, so the two together give
 *
 * <pre>  P(L &gt; l) = ((L∞ - l) / (L∞ - Lc))^n  </pre>
 *
 * <p>for fish above the smallest that gear retains, {@code Lc}. The exponent
 * {@code n} is the mortality-to-growth ratio Z/K: how many fish die for every bit
 * of growing the survivors do. Real stocks sit around 1.5 to 3, and the higher it
 * goes the fewer fish live long enough to get big.
 *
 * <p>Inverting it gives the roll:
 *
 * <pre>  L = L∞ - (L∞ - Lc) · U^(1/n),   U uniform in [0, 1)  </pre>
 *
 * <p>Two things are pinned rather than left to the curve. The asymptote is placed
 * so that the configured maximum is reached exactly at the record odds - the
 * maximum is not where the fish stop growing, it is the length only one catch in a
 * million reaches. And nothing is rolled below {@code Lc}, because a hook does not
 * bring up fry: that is gear selectivity, the same reason the formula above starts
 * at {@code Lc} rather than at zero.
 *
 * @param shape     Z/K, the mortality-to-growth ratio; higher means big ones are rarer
 * @param smallest  the shortest catch, as a fraction of the fish's maximum
 * @param oddsOfMax one catch in this many is the maximum length
 */
public record LengthCurve(double shape, double smallest, long oddsOfMax) {

    /** How much of the range a mythic is confined to, measured down from the top. */
    private static final double MYTHIC_BAND = 0.10;

    /** Lengths are a measurement, not a float. Two decimals is what a scale reads. */
    private static final double PRECISION = 100;

    /**
     * Rolls a length for a fish that reaches {@code max} at most.
     *
     * @param max the fish's maximum length, in cm
     */
    public double roll(double max, RandomGenerator random) {
        return lengthAt(max, random.nextDouble());
    }

    /**
     * Rolls a length in the top tenth of what the fish reaches.
     *
     * <p>For a mythic, so that one is always a specimen rather than sometimes a
     * runt. It can still come up at the maximum, only far sooner than one in a
     * million - the band it draws from is that much narrower.
     */
    public double rollOutsized(double max, RandomGenerator random) {
        return lengthAt(max, random.nextDouble() * topBand());
    }

    /**
     * The length a roll of {@code u} lands on.
     *
     * <p>Worked in fractions of the range rather than in centimetres, so the same
     * numbers serve every fish and only the last line knows about cm.
     */
    private double lengthAt(double max, double u) {
        double reach = Math.pow(1.0 / Math.max(1, oddsOfMax), 1 / shape);

        // 0 at the smallest the gear retains, 1 at the maximum, and beyond it only
        // for the one roll in a million that overshoots - which is then clamped.
        double climbed = (1 - Math.pow(u, 1 / shape)) / (1 - reach);

        double shortest = max * smallest;
        double length = shortest + (max - shortest) * climbed;
        return Math.min(max, Math.round(length * PRECISION) / PRECISION);
    }

    /** The roll below which a length lands in the top band. */
    private double topBand() {
        double reach = Math.pow(1.0 / Math.max(1, oddsOfMax), 1 / shape);
        return Math.pow(1 - (1 - MYTHIC_BAND) * (1 - reach), shape);
    }
}
