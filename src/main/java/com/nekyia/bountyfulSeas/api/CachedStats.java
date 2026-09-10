package com.nekyia.bountyfulSeas.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nekyia.bountyfulSeas.stats.CatchStore;
import com.nekyia.bountyfulSeas.stats.FishRecord;
import com.nekyia.bountyfulSeas.stats.FishStats;
import com.nekyia.bountyfulSeas.stats.PlayerTotal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link StatsApi} that answers from memory where it can.
 *
 * <p>Opening the guide asks for one player's whole table at once, and a ranking
 * asks the same server-wide question for everybody. Both are read far more often
 * than they change, so they are cached and the database is left alone.
 *
 * <p>A player's own entry is dropped the moment they catch something, so their
 * guide is never behind. Server-wide figures expire on a timer instead: they move
 * with every catch by anyone, and a ranking that is a minute old is worth more
 * than one that costs a query per viewer.
 *
 * <p>Everything here is fetched whole rather than a row at a time. A guide screen
 * asks about a category at once, so the alternative would be a round trip per fish
 * per viewer - which is exactly the shape of load a server notices.
 */
public final class CachedStats implements StatsApi {

    /** One key, because the server-wide table is fetched whole. */
    private static final String SERVER_KEY = "server";

    private final CatchStore store;

    private final Cache<UUID, Map<String, FishStats>> players;
    private final Cache<String, Map<String, FishStats>> server;
    private final Cache<String, List<PlayerTotal>> rankings;

    /** The whole record board, fetched in one query and shared by every viewer. */
    private final Cache<String, Map<String, FishRecord>> records;

    /** Where each player stands, dropped with their totals the moment they catch. */
    private final Cache<UUID, Map<String, Integer>> places;

    public CachedStats(CatchStore store, Duration serverTtl, int maxPlayers) {
        this.store = store;

        this.players = Caffeine.newBuilder()
                .maximumSize(maxPlayers)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();

        this.server = Caffeine.newBuilder()
                .expireAfterWrite(serverTtl)
                .maximumSize(1)
                .build();

        this.rankings = Caffeine.newBuilder()
                .expireAfterWrite(serverTtl)
                .maximumSize(256)
                .build();

        this.records = Caffeine.newBuilder()
                .expireAfterWrite(serverTtl)
                .maximumSize(1)
                .build();

        this.places = Caffeine.newBuilder()
                .maximumSize(maxPlayers)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    @Override
    public FishStats player(UUID player, String fishId) {
        FishStats stats = player(player).get(fishId);
        return stats != null ? stats : FishStats.none(fishId);
    }

    @Override
    public Map<String, FishStats> player(UUID player) {
        return players.get(player, store::forPlayer);
    }

    @Override
    public FishStats server(String fishId) {
        FishStats stats = server().get(fishId);
        return stats != null ? stats : FishStats.none(fishId);
    }

    @Override
    public Map<String, FishStats> server() {
        return server.get(SERVER_KEY, key -> store.forServer());
    }

    @Override
    public Map<String, FishRecord> records() {
        return records.get(SERVER_KEY, key -> store.records());
    }

    @Override
    public Map<String, Integer> places(UUID player) {
        return places.get(player, store::placesOf);
    }

    @Override
    public List<PlayerTotal> top(String fishId, int limit) {
        return rankings.get(fishId + "#" + limit, key -> store.topBy(fishId, limit));
    }

    @Override
    public void invalidate(UUID player) {
        players.invalidate(player);
        places.invalidate(player);
        // The server totals moved too, but they are shared, so they are left to
        // expire rather than rebuilt for every catch on a busy server.
    }
}
