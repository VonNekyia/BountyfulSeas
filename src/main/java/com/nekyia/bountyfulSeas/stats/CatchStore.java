package com.nekyia.bountyfulSeas.stats;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where catch totals are kept.
 *
 * <p>The port to storage. Whatever implements this may be a database or a stub;
 * nothing above it learns which, so a server without a database still runs with
 * everything but the numbers.
 *
 * <p>Implementations are called off the server thread and must be safe for
 * concurrent use.
 */
public interface CatchStore {

    /**
     * Adds one catch to a player's totals and to the server's.
     *
     * @return how many of that fish the player has now, so a milestone crossing
     *         can be spotted without asking a second time
     */
    long record(UUID player, String fishId, double length);

    /** One player's totals for one fish, never null. */
    FishStats forPlayer(UUID player, String fishId);

    /** Everything one player has caught, keyed by fish id. */
    Map<String, FishStats> forPlayer(UUID player);

    /** Server-wide totals for one fish, never null. */
    FishStats forServer(String fishId);

    /** Server-wide totals for every fish anyone has caught. */
    Map<String, FishStats> forServer();

    /**
     * The players with the most catches of one fish, best first.
     *
     * <p>Here for the ranking that is meant to be built elsewhere, so that work
     * needs no new storage code.
     */
    List<PlayerTotal> topBy(String fishId, int limit);
}
