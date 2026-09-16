package com.nekyia.bountyfulSeas.level;

/**
 * How much experience each level costs, worked out from the roster itself.
 *
 * <pre>  to reach level n: share · (what one finished fish pays) · (fish open below n)  </pre>
 *
 * <p>Nothing here is tuned by hand. The last level is the highest level any fish is
 * locked behind, and every threshold is a share of what the fish already open are
 * worth - so a fish added to a category, or moved to another level, moves the curve
 * with it and there is nothing left to keep in step.
 *
 * <p>Held as a table rather than a formula because the shape is not a formula: it
 * bends wherever the roster does, and the last few levels ask for a larger share.
 */
public final class LevelCurve {

    /** The first level, which everybody has before catching anything. */
    public static final int FIRST_LEVEL = 1;

    /** Indexed by level; slot 0 is unused and {@code FIRST_LEVEL} is always 0. */
    private final long[] thresholds;

    private LevelCurve(long[] thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * Derives the curve from the levels the roster's fish are locked behind.
     *
     * @param fishLevels one entry per catchable fish, in any order
     */
    public static LevelCurve from(int[] fishLevels, ExperienceRule rule, CompletionRule demand) {
        int maxLevel = FIRST_LEVEL;
        for (int level : fishLevels) {
            maxLevel = Math.max(maxLevel, level);
        }

        // How many fish are open at each level, counting everything at or below it.
        int[] open = new int[maxLevel + 1];
        for (int level : fishLevels) {
            if (level >= FIRST_LEVEL && level <= maxLevel) {
                open[level]++;
            }
        }
        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            open[level] += open[level - 1];
        }

        long finished = rule.upTo(demand.completedAtStep());
        long[] thresholds = new long[maxLevel + 1];
        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            thresholds[level] = Math.round(
                    demand.shareFor(level, maxLevel) * finished * open[level - 1]);
            // A level that opened nothing new, or a share that fell, must still not
            // make the next level cheaper than the one already passed.
            thresholds[level] = Math.max(thresholds[level], thresholds[level - 1]);
        }
        return new LevelCurve(thresholds);
    }

    /** The last level there is, which is the highest any fish is locked behind. */
    public int maxLevel() {
        return thresholds.length - 1;
    }

    /** Total experience needed to have reached a level. */
    public long experienceFor(int level) {
        if (level <= FIRST_LEVEL) {
            return 0;
        }
        return thresholds[Math.min(level, maxLevel())];
    }

    /**
     * What this much experience amounts to.
     *
     * <p>Walked upwards rather than searched, because the table is short and this
     * runs only when a player's totals change.
     */
    public Progress at(long experience) {
        long earned = Math.max(0, experience);

        int level = FIRST_LEVEL;
        while (level < maxLevel() && earned >= experienceFor(level + 1)) {
            level++;
        }

        long start = experienceFor(level);
        long span = level >= maxLevel() ? 0 : experienceFor(level + 1) - start;
        return new Progress(level, earned, earned - start, span);
    }
}
