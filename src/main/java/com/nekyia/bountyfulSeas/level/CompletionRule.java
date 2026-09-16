package com.nekyia.bountyfulSeas.level;

import java.util.List;

/**
 * How much of what is already open has to be worked through before the next level.
 *
 * <p>A level's cost is not a number somebody picked. It is a share of what every
 * fish unlocked so far is worth, so adding a fish to the roster raises every later
 * level by itself and nothing has to be retuned by hand.
 *
 * <p>What counts as having finished a fish climbs as the levels do, in bands that
 * split the levels there are into equal slices. That is the number that decides
 * whether the whole thing is playable: milestones are paid flat but cost
 * exponentially - the tenth takes a thousand catches - so measuring completion
 * against all ten would put level 2 at five hundred catches of every starter fish.
 *
 * <p>Equal slices rather than fixed level numbers, so the bands follow the roster
 * too. Three bands over fifteen levels is five levels each; grow the roster to
 * eighteen and it becomes six, with nothing to edit.
 */
public record CompletionRule(List<Band> bands) {

    /**
     * One slice of the levels, and what it asks for.
     *
     * @param completedAtStep the milestone at which a fish counts as done
     * @param share           the fraction of that to have earned before the next level
     */
    public record Band(int completedAtStep, double share) {
    }

    public CompletionRule {
        bands = List.copyOf(bands);
        if (bands.isEmpty()) {
            throw new IllegalArgumentException("a level curve needs at least one band");
        }
    }

    /** The band a level falls in, given where the roster ends. */
    public Band bandFor(int level, int maxLevel) {
        if (maxLevel <= LevelCurve.FIRST_LEVEL) {
            return bands.get(0);
        }
        int slice = (int) ((long) (level - LevelCurve.FIRST_LEVEL) * bands.size() / maxLevel);
        return bands.get(Math.clamp(slice, 0, bands.size() - 1));
    }
}
