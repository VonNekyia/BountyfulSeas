package com.nekyia.bountyfulSeas.level;

import java.util.List;

/**
 * How much experience each level costs, as configured.
 *
 * <p>A plain list: the n-th entry is what it costs to go from level n to n+1. The
 * last level is one past the end of the list.
 */
public final class LevelCurve {

    /** The first level, which everybody has before catching anything. */
    public static final int FIRST_LEVEL = 1;

    /** Indexed by level; slot 0 is unused and {@code FIRST_LEVEL} is always 0. */
    private final long[] thresholds;

    private LevelCurve(long[] thresholds) {
        this.thresholds = thresholds;
    }

    /** @param costs what each level after the first costs, in order; none may be below 1 */
    public static LevelCurve of(List<Long> costs) {
        long[] thresholds = new long[costs.size() + 2];
        for (int index = 0; index < costs.size(); index++) {
            thresholds[FIRST_LEVEL + 1 + index] =
                    thresholds[FIRST_LEVEL + index] + Math.max(1, costs.get(index));
        }
        return new LevelCurve(thresholds);
    }

    /** The last level there is. */
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

    /** What this much experience amounts to. */
    public Progress at(long experience) {
        long earned = Math.max(0, experience);

        int level = FIRST_LEVEL;
        while (level < maxLevel() && earned >= experienceFor(level + 1)) {
            level++;
        }

        long start = experienceFor(level);
        long span = level >= maxLevel() ? 0 : experienceFor(level + 1) - start;
        return new Progress(level, earned, earned - start, span, maxLevel());
    }
}
