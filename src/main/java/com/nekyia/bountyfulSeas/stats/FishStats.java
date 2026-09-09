package com.nekyia.bountyfulSeas.stats;

/**
 * What has been caught of one fish, already aggregated.
 *
 * <p>Records are kept as running totals rather than as a log of every catch: a
 * server fishing for a year would otherwise hold millions of rows to answer a
 * question that only ever needs the count and the best of them.
 *
 * <p>Only the long end is kept. Nobody sets out to land the smallest herring
 * anybody has ever seen, and a shortest column would be a record of bad luck.
 *
 * @param catches how many have been caught
 * @param longest longest ever caught, in cm
 */
public record FishStats(
        String fishId,
        long catches,
        double longest
) {

    /** Nothing caught yet, so there is no best yet either. */
    public static FishStats none(String fishId) {
        return new FishStats(fishId, 0, 0);
    }

    public boolean caught() {
        return catches > 0;
    }
}
