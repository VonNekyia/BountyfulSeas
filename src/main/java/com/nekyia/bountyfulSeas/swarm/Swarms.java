package com.nekyia.bountyfulSeas.swarm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Where the swarming fish are right now.
 *
 * <p>A swarm is one fish gathered in one water region. There are a fixed number of
 * them, and every so often they all move somewhere else, so a swarming fish is
 * only catchable where its swarm currently is.
 *
 * <p>Works purely in ids - a fish id and a region id - so it knows nothing about
 * fish definitions or the water map, and can be reasoned about on its own.
 *
 * <p>Read from the server thread while a catch is decided and written from a timer,
 * so the placement is swapped atomically rather than edited in place.
 */
public final class Swarms {

    /** Region id to the fish swarming in it. Replaced wholesale on every move. */
    private volatile Map<Integer, String> placement = Map.of();

    /**
     * Scatters the swarms afresh.
     *
     * <p>Every swarm gets a region of its own, so two swarms never share a spot
     * and the count is honest. Fewer regions than swarms simply means fewer swarms.
     *
     * @param swarmingFish ids of the fish that swarm; none means no swarms
     * @param regions      the water regions a swarm may appear in
     * @param count        how many swarms to place
     * @return how many were actually placed
     */
    public int scatter(List<String> swarmingFish, int[] regions, int count, RandomGenerator random) {
        if (swarmingFish.isEmpty() || regions.length == 0 || count <= 0) {
            placement = Map.of();
            return 0;
        }

        int wanted = Math.min(count, regions.length);
        Map<Integer, String> next = new HashMap<>(wanted);

        // Partial Fisher-Yates: only as many regions as are needed get shuffled,
        // which matters when a scanned world holds tens of thousands of them.
        int[] pool = regions.clone();
        for (int i = 0; i < wanted; i++) {
            int pick = i + random.nextInt(pool.length - i);
            int region = pool[pick];
            pool[pick] = pool[i];
            pool[i] = region;

            next.put(region, swarmingFish.get(random.nextInt(swarmingFish.size())));
        }

        placement = Map.copyOf(next);
        return next.size();
    }

    /** Whether a swarm of this fish is in this region right now. */
    public boolean isSwarming(String fishId, int regionId) {
        return fishId.equals(placement.get(regionId));
    }

    /** The fish swarming in this region, or null when none is. */
    public String fishAt(int regionId) {
        return placement.get(regionId);
    }

    /** Every region currently holding a swarm. */
    public Set<Integer> regions() {
        return placement.keySet();
    }

    public int size() {
        return placement.size();
    }

    /** Clears every swarm, used when there is no map to place them on. */
    public void clear() {
        placement = Map.of();
    }
}
