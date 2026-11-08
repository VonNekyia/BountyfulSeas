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
 * <p>Above {@code tailFrom} of the maximum the curve changes. Left to itself it
 * squeezes everything from one in a thousand to one in a million into the last
 * centimetre, so a rare fish and a record look the same on the scale. From there up
 * every further centimetre is instead equally harder - the odds fall by the same
 * factor per cm - so the top end is spread out and big catches can be told apart.
 *
 * @param oddsOfMax one catch in this many is the maximum length
 * @param tailFrom  the fraction of the maximum where the even tail begins; 1 for none
 */
public record LengthCurve(double shape, double smallest, long oddsOfMax, double tailFrom) {

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

    /** The shortest a catch of this fish can be. */
    public double shortest(double max) {
        return round(max * smallest);
    }

    /**
     * How often a catch is at least this long, as a fraction of all catches.
     *
     * <p>The roll read backwards, so what this reports and what the water gives
     * cannot disagree. At the maximum it is exactly the record odds.
     */
    public double chanceOfAtLeast(double max, double length) {
        double floor = max * smallest;
        if (length <= floor) {
            return 1;
        }
        if (length > max) {
            return 0;
        }
        return Math.clamp(beyond((length - floor) / (max - floor)), 0, 1);
    }

    /**
     * The same for a catch of a given tier: an outsized one is only ever drawn from
     * the top band, so its odds are counted within that band rather than overall.
     */
    public double chanceOfAtLeast(double max, double length, boolean outsized) {
        double chance = chanceOfAtLeast(max, length);
        return outsized ? Math.min(1, chance / topBand()) : chance;
    }

    /** Whether a length is in the even tail, where every further cm is equally harder. */
    public boolean inTail(double max, double length) {
        return tailFrom < 1 && length >= max * tailFrom;
    }

    /** The length that only this fraction of catches reaches or beats. */
    public double lengthBeatenBy(double max, double chance) {
        return lengthAt(max, Math.clamp(chance, 0, 1));
    }

    /**
     * The length a roll of {@code u} lands on.
     *
     * <p>Worked in fractions of the range rather than in centimetres, so the same
     * numbers serve every fish and only the last line knows about cm.
     */
    private double lengthAt(double max, double u) {
        // 0 at the smallest the gear retains, 1 at the maximum, and beyond it only
        // for the one roll in a million that overshoots - which is then clamped.
        double climbed = climbedAt(u);

        double floor = max * smallest;
        return Math.min(max, round(floor + (max - floor) * climbed));
    }

    /**
     * Two decimals, always downwards.
     *
     * <p>Downwards rather than to the nearest on purpose. Rounding to the nearest
     * would let everything from half a hundredth below the maximum come up reading
     * as the maximum, which quietly widens the record: for a 22 cm fish that top
     * step held 1.09 catches in a million rather than 1. Truncating means a catch
     * reads as the maximum only when it genuinely reached it.
     */
    private static double round(double length) {
        return Math.floor(length * PRECISION) / PRECISION;
    }

    /** The share of catches that climb past this fraction of the range. */
    private double beyond(double climbed) {
        double knee = knee();
        if (climbed <= knee) {
            return Math.pow(1 - climbed * (1 - reach()), shape);
        }
        double atKnee = Math.pow(1 - knee * (1 - reach()), shape);
        return atKnee * Math.pow(recordShare() / atKnee, (climbed - knee) / (1 - knee));
    }

    /** {@link #beyond} read backwards: how far a roll of {@code u} climbs. */
    private double climbedAt(double u) {
        double knee = knee();
        double atKnee = Math.pow(1 - knee * (1 - reach()), shape);
        if (knee >= 1 || u >= atKnee) {
            return (1 - Math.pow(u, 1 / shape)) / (1 - reach());
        }
        if (u <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return knee + (1 - knee) * Math.log(u / atKnee) / Math.log(recordShare() / atKnee);
    }

    /** Where the even tail starts, as a fraction of the range; 1 or more for none. */
    private double knee() {
        return (tailFrom - smallest) / (1 - smallest);
    }

    private double recordShare() {
        return 1.0 / Math.max(1, oddsOfMax);
    }

    /** How close to the asymptote the maximum sits, which is what fixes the odds. */
    private double reach() {
        return Math.pow(1.0 / Math.max(1, oddsOfMax), 1 / shape);
    }

    /** The roll below which a length lands in the top band. */
    private double topBand() {
        return beyond(1 - MYTHIC_BAND);
    }
}
