package com.nekyia.bountyfulSeas.level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How much of what is already open has to be worked through, at fixed points.
 *
 * <p>An anchor prices one level off the roster: a share of what every fish unlocked
 * below it is worth. Adding a fish raises every anchor above it by itself, which is
 * why no level cost is written down anywhere.
 *
 * <p>Only every fifth level or so is anchored. The levels in between are smoothed
 * between the anchors rather than priced the same way, because pricing each one off
 * the roster made the steps jump about with wherever the fish happened to land -
 * one level costing 270 and the next 810 says nothing to a player.
 *
 * <p>What counts as a finished fish climbs from anchor to anchor. That number is
 * what decides whether the curve is playable: milestones are paid flat but cost
 * exponentially, so the fourth is twenty catches, the sixth a hundred and the tenth
 * a thousand.
 */
public record CompletionRule(List<Anchor> anchors) {

    /**
     * One priced level.
     *
     * @param level           the level this prices
     * @param completedAtStep the milestone at which a fish counts as done
     * @param share           the fraction of that to have earned across what is open
     */
    public record Anchor(int level, int completedAtStep, double share) {
    }

    public CompletionRule {
        if (anchors.isEmpty()) {
            throw new IllegalArgumentException("a level curve needs at least one anchor");
        }
        List<Anchor> sorted = new ArrayList<>(anchors);
        sorted.sort(Comparator.comparingInt(Anchor::level));
        anchors = List.copyOf(sorted);
    }

    /** The last anchor, whose terms carry on past it if the roster reaches further. */
    public Anchor last() {
        return anchors.get(anchors.size() - 1);
    }
}
