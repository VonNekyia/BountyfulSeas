package com.nekyia.bountyfulSeas.stats;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ready-made {@link CatchStore} implementations. */
public final class CatchStores {

    private CatchStores() {
    }

    /**
     * A store that keeps nothing.
     *
     * <p>Used when no database is configured, so callers never have to hold a null
     * store or ask whether one exists before recording a catch.
     */
    public static CatchStore none() {
        return new CatchStore() {
            @Override
            public long record(UUID player, String fishId, double length) {
                return 0;
            }

            @Override
            public FishStats forPlayer(UUID player, String fishId) {
                return FishStats.none(fishId);
            }

            @Override
            public Map<String, FishStats> forPlayer(UUID player) {
                return Map.of();
            }

            @Override
            public FishStats forServer(String fishId) {
                return FishStats.none(fishId);
            }

            @Override
            public Map<String, FishStats> forServer() {
                return Map.of();
            }

            @Override
            public List<PlayerTotal> topBy(String fishId, int limit) {
                return List.of();
            }
        };
    }
}
