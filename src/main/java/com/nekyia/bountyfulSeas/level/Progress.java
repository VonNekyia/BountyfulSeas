package com.nekyia.bountyfulSeas.level;

/**
 * Where a player stands, worked out from the experience they have.
 *
 * @param level      the level reached
 * @param experience everything earned so far
 * @param intoLevel  how much of the current level is behind them
 * @param span       how much this level is worth in total, 0 at the cap
 * @param maxLevel   the last level there is, carried along so that anything holding
 *                   a standing knows where the ladder ends without asking the curve
 */
public record Progress(int level, long experience, long intoLevel, long span, int maxLevel) {

    /** Whether there is no level left to reach. */
    public boolean capped() {
        return span <= 0;
    }

    /** What is still missing before the next level, 0 at the cap. */
    public long remaining() {
        return capped() ? 0 : span - intoLevel;
    }

    /** How far through the current level, 0 to 1, and 1 at the cap. */
    public double fraction() {
        return capped() ? 1 : (double) intoLevel / span;
    }
}
