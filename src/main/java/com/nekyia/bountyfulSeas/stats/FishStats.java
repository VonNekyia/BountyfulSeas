package com.nekyia.bountyfulSeas.stats;

/**
 * What has been caught of one fish, already aggregated.
 *
 * <p>Records are kept as running totals rather than as a log of every catch: a
 * server fishing for a year would otherwise hold millions of rows to answer
 * questions that only ever need the count and four extremes.
 *
 * @param catches  how many have been caught
 * @param longest  longest ever caught, in cm
 * @param shortest shortest ever caught, in cm
 */
public record FishStats(
        String fishId,
        long catches,
        double longest,
        double shortest
) {

    /** Nothing caught yet, so every extreme is still undefined. */
    public static FishStats none(String fishId) {
        return new FishStats(fishId, 0, 0, 0);
    }

    public boolean caught() {
        return catches > 0;
    }
}
