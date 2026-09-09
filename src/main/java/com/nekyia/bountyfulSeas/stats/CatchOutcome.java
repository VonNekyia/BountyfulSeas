package com.nekyia.bountyfulSeas.stats;

/**
 * What one recorded catch turned out to be.
 *
 * <p>Returned by the store because it is the only place that knows what the totals
 * looked like a moment ago. Asking afterwards would be a second round trip and a
 * race: by then the old best has already been overwritten by this very catch.
 *
 * @param catches         how many of that fish the player has now
 * @param beatenOwnBest   the player's previous longest, when this catch beat it,
 *                        otherwise 0
 * @param beatenServerBest the server's previous longest, when this catch beat it,
 *                        otherwise 0
 */
public record CatchOutcome(long catches, double beatenOwnBest, double beatenServerBest) {

    /** Nothing was recorded, so there is nothing to say about it. */
    public static CatchOutcome nothing() {
        return new CatchOutcome(0, 0, 0);
    }

    /**
     * Whether this is the longest that player has ever landed of this fish.
     *
     * <p>A first catch is not a record. It would be true in the arithmetic sense,
     * but announcing one for every fish a player has never caught before turns the
     * message into noise and makes a real record worth less.
     */
    public boolean ownRecord() {
        return beatenOwnBest > 0;
    }

    /** Whether this is the longest anyone on the server has landed of this fish. */
    public boolean serverRecord() {
        return beatenServerBest > 0;
    }
}
