package com.nekyia.bountyfulSeas;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catches somebody has arranged in advance, for testing.
 *
 * <p>One shot each: the next bite hands over what was asked for and the
 * arrangement is gone. Anything longer lived would be a way to quietly break a
 * server rather than a way to look at one.
 *
 * @see DebugCommand
 */
final class ForcedCatches {

    /**
     * @param fishId the fish to hand over
     * @param length the length to give it, or null to roll one as usual
     */
    record Forced(String fishId, Double length) {
    }

    private final Map<UUID, Forced> waiting = new ConcurrentHashMap<>();

    void set(UUID player, String fishId, Double length) {
        waiting.put(player, new Forced(fishId, length));
    }

    /** What was arranged, removing it. Null when nothing was. */
    Forced take(UUID player) {
        return waiting.remove(player);
    }

    /** What is arranged, leaving it in place. Null when nothing is. */
    Forced peek(UUID player) {
        return waiting.get(player);
    }

    /** Cancels an arrangement. True when there was one. */
    boolean clear(UUID player) {
        return waiting.remove(player) != null;
    }
}
