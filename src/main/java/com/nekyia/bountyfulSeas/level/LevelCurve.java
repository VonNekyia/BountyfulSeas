package com.nekyia.bountyfulSeas.level;

/**
 * How much experience each level costs.
 *
 * <pre>  total experience to reach level n = base · (n - 1)^steepness  </pre>
 *
 * <p>A power curve rather than a flat cost per level, so that early levels come
 * quickly and later ones are worth working for. With the shipped numbers level 2
 * costs 100 and level 50 costs a little over a hundred thousand.
 *
 * @param base      experience for the second level, and the scale of everything after
 * @param steepness how sharply the cost climbs; 1 would be a straight line
 * @param maxLevel  the last level there is
 */
public record LevelCurve(long base, double steepness, int maxLevel) {

    /** The first level, which everybody has before catching anything. */
    public static final int FIRST_LEVEL = 1;

    /** Total experience needed to have reached a level. */
    public long experienceFor(int level) {
        if (level <= FIRST_LEVEL) {
            return 0;
        }
        int capped = Math.min(level, maxLevel);
        return Math.round(base * Math.pow(capped - FIRST_LEVEL, steepness));
    }

    /**
     * What this much experience amounts to.
     *
     * <p>Walked upwards rather than solved for, because the cap has to hold and a
     * closed form would have to be clamped anyway. The loop runs at most maxLevel
     * times and only when a player's totals change.
     */
    public Progress at(long experience) {
        long earned = Math.max(0, experience);

        int level = FIRST_LEVEL;
        while (level < maxLevel && earned >= experienceFor(level + 1)) {
            level++;
        }

        long start = experienceFor(level);
        long span = level >= maxLevel ? 0 : experienceFor(level + 1) - start;
        return new Progress(level, earned, earned - start, span);
    }
}
