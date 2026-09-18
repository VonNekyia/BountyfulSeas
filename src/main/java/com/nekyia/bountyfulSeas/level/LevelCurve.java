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
 * <p>A level the config prices but no fish opens at - one held out in front of
 * people while what it gives is still being built - stands outside all of that. It
 * costs exactly what it is priced at, and the levels below it are left alone:
 * smoothing it in would drag the whole curve up to meet it.
 *
 * <p>Nothing here is tuned by hand. The last level is the highest level any fish is
 * locked behind, or the last one the config prices, whichever is further up.
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
        int stocked = FIRST_LEVEL;
        for (int level : fishLevels) {
            stocked = Math.max(stocked, level);
        }
        // A level the config prices counts even when no fish opens there: that is how
        // a level is put in front of people before there is anything behind it.
        int maxLevel = Math.max(stocked, demand.last().level());

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
        for (int level = FIRST_LEVEL + 1; level <= stocked; level++) {
            steps[level] = Math.max(0, asked[level] - asked[level - 1]);
        }
        climb(steps, FIRST_LEVEL + 1, stocked);
        ease(steps, FIRST_LEVEL + 1, stocked);
        lean(steps, FIRST_LEVEL + 1, stocked);

        long[] thresholds = new long[maxLevel + 1];
        long running = 0;
        for (int level = FIRST_LEVEL + 1; level <= stocked; level++) {
            // Snapped step by step rather than at the end: every experience anybody
            // can hold is a multiple of what a milestone pays, so a threshold that is
            // not one would never be landed on, only jumped past. Rounding each step
            // also keeps them climbing, which rounding the totals would not.
            running += onGrid(steps[level], rule.perStep());
            thresholds[level] = running;
        }

        // Past the roster the levels stand on their own. Smoothing them in with the
        // rest would drag the whole curve up to meet them - a level priced at the
        // ninth milestone would make every level below it steeper to keep the climb
        // even - and these are meant to sit apart, not to reshape what came before.
        for (int level = stocked + 1; level <= maxLevel; level++) {
            thresholds[level] = Math.max(onGrid(asked[level], rule.perStep()),
                    thresholds[level - 1] + Math.max(1, rule.perStep()));
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
     * Takes the cliff out of the places where the scale changes milestone.
     *
     * <p>Pooling only answers steps that fall. A step that leaps - the last level
     * asking four times the one before it, because the scale goes from the fifth
     * milestone to the sixth in one move - is left exactly as it was, and that is
     * the one a player actually feels.
     *
     * <p>So no step may be more than a set fraction larger than the one below it.
     * Where one is, the excess is handed back down the curve rather than dropped:
     * the levels before it each take a little more, the leap comes down, and the
     * total is what it was. Handed down repeatedly, because the level that now
     * takes more may itself have become a leap.
     */
    private static void ease(double[] steps, int from, int to) {
        // A third more than the level below is a climb anybody can feel without it
        // reading as a wall. Two levels of that is already a doubling.
        final double most = 1.35;

        // Bounded rather than "until nothing moves": each pass flattens the worst
        // leap by a third, so this is far more passes than the curve ever needs,
        // and a curve that cannot settle still ends up sensible instead of hanging.
        for (int pass = 0; pass < 200; pass++) {
            boolean moved = false;
            for (int at = to; at > from; at--) {
                double allowed = most * steps[at - 1];
                if (steps[at] <= allowed) {
                    continue;
                }
                // Hand back just enough that the pair sits on the limit, keeping
                // what the two of them cost together.
                double handed = (steps[at] - allowed) / (1 + most);
                steps[at] -= handed;
                steps[at - 1] += handed;
                moved = true;
            }
            if (!moved) {
                return;
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
        return new Progress(level, earned, earned - start, span, maxLevel());
    }
}
