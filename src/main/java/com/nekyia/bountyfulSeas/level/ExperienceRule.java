package com.nekyia.bountyfulSeas.level;

/**
 * What a milestone is worth.
 *
 * <pre>  experience = perStep · milestone step  </pre>
 *
 * <p>Flat: the second milestone pays twice the first, the tenth ten times it. With
 * the shipped number that reads 10, 20, 30 and so on up to 100, which is the whole
 * table anyone has to remember.
 *
 * <p>Deliberately not scaled by the fish's level. A hard fish is already harder to
 * catch; paying more for it as well would make the early roster worthless the
 * moment anything new opened, and the level curve derives its thresholds from this
 * same rule, so a second factor here would distort both ends at once.
 *
 * @param perStep experience for the first milestone of any fish
 */
public record ExperienceRule(long perStep) {

    /** @param step which milestone it is, counting from 1 */
    public long forMilestone(int step) {
        return perStep * Math.max(1, step);
    }

    /**
     * What one fish pays for its first so many milestones.
     *
     * <p>The measure of how far through a fish somebody is, which is what the level
     * curve asks its thresholds in.
     */
    public long upTo(int steps) {
        long taken = Math.max(0, steps);
        return perStep * taken * (taken + 1) / 2;
    }
}
