package com.nekyia.bountyfulSeas.level;

import java.util.List;

/**
 * How much experience each level costs, worked out from the roster itself.
 *
 * <p>The config says how deep into every open fish a level expects somebody to be -
 * the second milestone by level three, the third by five, and so on. That, times the
 * fish actually open below the level, is what the level would cost.
 *
 * <p>Would, because the raw numbers do not climb evenly. A milestone holds for three
 * levels while the roster grows by four fish and then by two, so the cost per level
 * lurches and sometimes falls. A level that costs less than the one before it is not
 * a level. So the steps are smoothed until they never fall, staying as close to what
 * the config asked for as that allows: the scale is the shape, not the arithmetic.
 *
 * <p>Nothing here is tuned by hand. The last level is the highest level any fish is
 * locked behind, and a fish added to a category or moved between levels retunes the
 * whole curve by itself.
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

        // What the config asks for, before anything is made to behave.
        double[] asked = new double[maxLevel + 1];
        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            asked[level] = depthAt(level, demand, rule) * open[level - 1];
        }

        double[] steps = new double[maxLevel + 1];
        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            steps[level] = Math.max(0, asked[level] - asked[level - 1]);
        }
        climb(steps, FIRST_LEVEL + 1, maxLevel);
        lean(steps, FIRST_LEVEL + 1, maxLevel);

        long[] thresholds = new long[maxLevel + 1];
        long running = 0;
        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            // Snapped step by step rather than at the end: every experience anybody
            // can hold is a multiple of what a milestone pays, so a threshold that is
            // not one would never be landed on, only jumped past. Rounding each step
            // also keeps them climbing, which rounding the totals would not.
            running += onGrid(steps[level], rule.perStep());
            thresholds[level] = running;
        }
        return new LevelCurve(thresholds);
    }

    /**
     * How deep into one fish a level expects somebody to be.
     *
     * <p>Read off the anchors, and between two of them read across: a config that
     * names every level says exactly what it wants, and one that names every fifth
     * still gives a slope rather than a staircase.
     */
    private static double depthAt(int level, CompletionRule demand, ExperienceRule rule) {
        List<CompletionRule.Anchor> anchors = demand.anchors();

        CompletionRule.Anchor below = null;
        CompletionRule.Anchor above = null;
        for (CompletionRule.Anchor anchor : anchors) {
            if (anchor.level() <= level) {
                below = anchor;
            } else if (above == null) {
                above = anchor;
            }
        }

        if (below != null && below.level() == level) {
            return depth(below, rule);
        }
        if (below == null) {
            // Below the first anchor the scale runs up from nothing, so the earliest
            // levels are not all priced as though they were the first anchor.
            double reach = (double) (level - FIRST_LEVEL) / (above.level() - FIRST_LEVEL);
            return depth(above, rule) * reach;
        }
        if (above == null) {
            return depth(below, rule);
        }
        double across = (double) (level - below.level()) / (above.level() - below.level());
        return depth(below, rule) + across * (depth(above, rule) - depth(below, rule));
    }

    private static double depth(CompletionRule.Anchor anchor, ExperienceRule rule) {
        return anchor.share() * rule.upTo(anchor.completedAtStep());
    }

    /**
     * Makes a run of steps climb, changing them as little as possible.
     *
     * <p>Wherever a step would fall below the one before it, that step and the ones
     * it argues with are replaced by their average - repeatedly, until the run only
     * ever rises. That is the closest climbing sequence there is to the one asked
     * for, and it keeps the sum: what the config wanted the last level to cost in
     * total is still what it costs, only the way there has been made to behave.
     */
    private static void climb(double[] steps, int from, int to) {
        int span = to - from + 1;
        if (span <= 0) {
            return;
        }
        double[] pooled = new double[span];
        int[] held = new int[span];
        int blocks = 0;

        for (int at = from; at <= to; at++) {
            double value = steps[at];
            int count = 1;
            while (blocks > 0 && pooled[blocks - 1] > value) {
                blocks--;
                value = (pooled[blocks] * held[blocks] + value * count) / (held[blocks] + count);
                count += held[blocks];
            }
            pooled[blocks] = value;
            held[blocks] = count;
            blocks++;
        }

        int at = from;
        for (int block = 0; block < blocks; block++) {
            for (int each = 0; each < held[block]; each++) {
                steps[at++] = pooled[block];
            }
        }
    }

    /**
     * Fans out the runs the smoothing left flat, so no two levels cost the same.
     *
     * <p>Smoothing answers a plateau in the scale with a run of identical steps -
     * four levels at 860 each - which is correct arithmetic and a poor level. Each
     * such run is tilted around its own average: same total, but every level asks a
     * little more than the one below it.
     *
     * <p>The tilt is held back wherever it would undo the smoothing, so a run never
     * starts below where the one before it ended.
     */
    private static void lean(double[] steps, int from, int to) {
        // A tenth either side of the average: enough to feel on any level worth
        // levelling, small enough that it never argues with the scale.
        final double spread = 0.20;

        double last = 0;
        int at = from;
        while (at <= to) {
            int end = at;
            while (end + 1 <= to && steps[end + 1] == steps[at]) {
                end++;
            }
            int run = end - at + 1;
            if (run > 1) {
                double flat = steps[at];
                double reach = Math.min(spread * flat, 2 * (flat - last));
                for (int each = 0; each < run; each++) {
                    steps[at + each] = flat + reach * ((double) each / (run - 1) - 0.5);
                }
            }
            last = steps[end];
            at = end + 1;
        }
    }

    /** The nearest step somebody can actually take, which is a multiple of the pay. */
    private static long onGrid(double experience, long perStep) {
        if (perStep <= 0) {
            return Math.round(experience);
        }
        return Math.round(experience / perStep) * perStep;
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
