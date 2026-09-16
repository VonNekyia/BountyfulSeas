package com.nekyia.bountyfulSeas.level;

import java.util.ArrayList;
import java.util.List;

/**
 * How much experience each level costs, worked out from the roster itself.
 *
 * <p>Every fifth level or so is anchored to what the roster is worth there: a share
 * of what one finished fish pays, times the fish open below it. The levels between
 * two anchors are smoothed, each step a little larger than the one before and the
 * first no larger than the last step before it, so the climb is felt as a climb
 * rather than as a wall at whichever level a band happened to begin.
 *
 * <p>Nothing here is tuned by hand. The last level is the highest level any fish is
 * locked behind, and a fish added to a category or moved between levels retunes the
 * curve by itself.
 *
 * <p>Held as a table rather than a formula because the shape is not a formula: it
 * bends wherever the anchors do. Every threshold sits on a multiple of what a
 * milestone pays, because that is the only kind of number a player can stand on.
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

        long[] thresholds = new long[maxLevel + 1];
        long total = 0;
        double step = 0;
        int at = FIRST_LEVEL;

        for (CompletionRule.Anchor anchor : anchorsUpTo(demand, maxLevel)) {
            int span = anchor.level() - at;
            if (span <= 0) {
                continue;
            }
            long target = Math.max(total, onGrid(
                    anchor.share() * rule.upTo(anchor.completedAtStep()) * open[anchor.level() - 1],
                    rule.perStep()));

            // Steps grow by a fixed amount each level, starting from the last step
            // taken, and have to add up to exactly what the anchor asks. Where that
            // would mean shrinking steps - an anchor cheaper than the pace already
            // set - the span is split evenly instead, which is the flattest honest
            // answer and still lands on the anchor.
            long gain = target - total;
            double growth = 2 * (gain - span * step) / ((double) span * (span + 1));
            if (growth < 0) {
                step = (double) gain / span;
                growth = 0;
            }

            double running = total;
            for (int level = at + 1; level <= anchor.level(); level++) {
                step += growth;
                running += step;
                thresholds[level] = onGrid(running, rule.perStep());
            }
            // The anchor is the number that matters; rounding must not drift off it.
            thresholds[anchor.level()] = target;
            total = target;
            at = anchor.level();
        }

        for (int level = FIRST_LEVEL + 1; level <= maxLevel; level++) {
            thresholds[level] = Math.max(thresholds[level], thresholds[level - 1]);
        }
        return new LevelCurve(thresholds);
    }

    /**
     * The nearest experience a player can actually stand on.
     *
     * <p>Every milestone pays a multiple of the step, so every total anybody can
     * hold is one too. A threshold of 108 would really be 110: the bar would never
     * fill, it would jump past. Snapping says what is meant.
     */
    private static long onGrid(double experience, long perStep) {
        if (perStep <= 0) {
            return Math.round(experience);
        }
        return Math.round(experience / perStep) * perStep;
    }

    /**
     * The anchors that fall inside the roster, with the last one carried up to the
     * top level if the roster reaches past everything configured.
     */
    private static List<CompletionRule.Anchor> anchorsUpTo(CompletionRule demand, int maxLevel) {
        List<CompletionRule.Anchor> kept = new ArrayList<>();
        for (CompletionRule.Anchor anchor : demand.anchors()) {
            if (anchor.level() > FIRST_LEVEL && anchor.level() < maxLevel) {
                kept.add(anchor);
            }
        }
        if (maxLevel > FIRST_LEVEL) {
            CompletionRule.Anchor top = demand.last();
            kept.add(new CompletionRule.Anchor(maxLevel, top.completedAtStep(), top.share()));
        }
        return kept;
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
