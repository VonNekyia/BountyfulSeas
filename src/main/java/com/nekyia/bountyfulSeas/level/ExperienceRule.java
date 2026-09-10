package com.nekyia.bountyfulSeas.level;

/**
 * What a milestone is worth.
 *
 * <pre>  experience = perStep · milestone step · the fish's level  </pre>
 *
 * <p>Both factors matter. The step means a fish's tenth milestone is worth ten
 * times its first, which is right because it took a thousand catches rather than
 * one. The fish's level means a hard fish pays better than an easy one, so there
 * is a reason to go after what has just been unlocked instead of staying in the
 * shallows with something that bites every cast.
 *
 * @param perStep experience for the first milestone of a level one fish
 */
public record ExperienceRule(long perStep) {

    /**
     * @param step      which milestone it is, counting from 1
     * @param fishLevel the level the fish is locked behind
     */
    public long forMilestone(int step, int fishLevel) {
        return perStep * Math.max(1, step) * Math.max(LevelCurve.FIRST_LEVEL, fishLevel);
    }
}
