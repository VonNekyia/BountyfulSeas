package com.nekyia.bountyfulSeas.level;

import java.util.Map;

/**
 * What a catch is worth.
 *
 * <p>Every catch pays its tier's value. The first of each kind pays it several
 * times over, because finding something new is the thing worth chasing, and a
 * fish in the top share of its lengths pays a bonus on top. Milestones still pay,
 * but little next to the catches that reach them: they mark a collection, they
 * are not what levels anybody.
 *
 * <p>Everything but the size bonus follows from the catch counts, so it is worked
 * out rather than stored and a changed number here reprices everyone at once. The
 * size bonus cannot be: only the longest catch is kept, so it is added up as it
 * happens.
 *
 * @param perCatch             experience for one catch, by tier config name
 * @param firstCatchMultiplier how many catches' worth the first of a kind pays
 * @param bigFishBonus         share of a catch's value added when it is big
 * @param bigFishShare         what counts as big: the longest this share of catches
 * @param perMilestoneStep     experience for the first milestone; the n-th pays n times it
 */
public record ExperienceRule(Map<String, Long> perCatch, long firstCatchMultiplier,
                             double bigFishBonus, double bigFishShare, long perMilestoneStep) {

    public ExperienceRule {
        perCatch = Map.copyOf(perCatch);
    }

    /** What one catch of this tier pays, before any bonus. */
    public long forCatch(String tier) {
        return Math.max(0, perCatch.getOrDefault(tier, 0L));
    }

    /** What a big one of this tier adds. */
    public long bigBonus(String tier) {
        return Math.round(forCatch(tier) * bigFishBonus);
    }

    /** @param step which milestone it is, counting from 1 */
    public long forMilestone(int step) {
        return perMilestoneStep * Math.max(1, step);
    }

    /** What the catch that brought a fish's count to {@code catches} paid, size bonus aside. */
    public long forNthCatch(String tier, long catches) {
        return forCatch(tier) * (catches == 1 ? Math.max(1, firstCatchMultiplier) : 1);
    }

    /**
     * What one fish's totals are worth, the stored size bonuses aside.
     *
     * @param milestonesPassed how many milestones the count has reached
     */
    public long forTotals(String tier, long catches, int milestonesPassed) {
        if (catches <= 0) {
            return 0;
        }
        long each = forCatch(tier);
        long experience = catches * each + (Math.max(1, firstCatchMultiplier) - 1) * each;
        for (int step = 1; step <= milestonesPassed; step++) {
            experience += forMilestone(step);
        }
        return experience;
    }
}
