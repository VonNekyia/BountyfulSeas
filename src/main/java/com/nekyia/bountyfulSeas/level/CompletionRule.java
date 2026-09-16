package com.nekyia.bountyfulSeas.level;

/**
 * How much of what is already open has to be worked through before the next level.
 *
 * <p>A level's cost is not a number somebody picked. It is a share of what every
 * fish unlocked so far is worth, so adding a fish to the roster raises every later
 * level by itself and nothing has to be retuned by hand.
 *
 * <p>{@code completedAtStep} is what counts as having finished a fish, and it is
 * the number that decides whether the whole thing is playable. Milestones are paid
 * flat but cost exponentially - the tenth takes a thousand catches - so measuring
 * completion against all ten would put level 2 at five hundred catches of every
 * starter fish. Against the fifth, which is fifty catches, the same share reads as
 * twenty catches each.
 *
 * @param completedAtStep the milestone at which a fish counts as done
 * @param share           the fraction of that to have earned before the next level
 * @param lateShare       the same for the last few levels, which should bite harder
 * @param lateLevels      how many levels at the top the harder share applies to
 */
public record CompletionRule(int completedAtStep, double share, double lateShare, int lateLevels) {

    /** The share that applies to reaching a level, given where the roster ends. */
    public double shareFor(int level, int maxLevel) {
        return level > maxLevel - lateLevels ? lateShare : share;
    }
}
