package com.nekyia.bountyfulSeas.api;

import com.nekyia.bountyfulSeas.stats.FishRecord;
import com.nekyia.bountyfulSeas.stats.FishStats;
import com.nekyia.bountyfulSeas.stats.PlayerTotal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The read surface over catch totals.
 *
 * <p>Meant to be the one way anything outside this plugin asks what has been
 * caught, so a ranking built later needs no storage code of its own.
 *
 * <p>Calls may reach the database and block. Call them off the server thread.
 */
public interface StatsApi {

    /** One player's totals for one fish, never null. */
    FishStats player(UUID player, String fishId);

    /** Everything one player has caught, keyed by fish id. */
    Map<String, FishStats> player(UUID player);

    /** Server-wide totals for one fish, never null. */
    FishStats server(String fishId);

    /** Server-wide totals for every fish anyone has caught. */
    Map<String, FishStats> server();

    /** The record holder for every fish anyone has landed, keyed by fish id. */
    Map<String, FishRecord> records();

    /** Where one player stands on every fish they have landed, keyed by fish id. */
    Map<String, Integer> places(UUID player);

    /** The players with the most of one fish, best first. */
    List<PlayerTotal> top(String fishId, int limit);

    /** Forgets what is cached for one player, after their totals change. */
    void invalidate(UUID player);
}
