package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.stats.FishRecord;
import com.nekyia.bountyfulSeas.stats.FishStats;

import java.util.Map;
import java.util.UUID;

/**
 * Everything the guide needs to know, fetched once before it opens.
 *
 * <p>A menu populates itself on the server thread. Anything it had to look up
 * there would be a database call the whole server waits on, once per viewer and
 * once per screen they click through - so all of it is gathered off-thread, in
 * three cached queries, and handed over whole.
 *
 * @param caught  the viewer's totals, by fish id
 * @param records who holds each fish's record, by fish id
 * @param places  where the viewer stands on each fish, by fish id
 * @param names   names for the record holders, resolved while off the server thread
 */
public record GuideStandings(
        Map<String, FishStats> caught,
        Map<String, FishRecord> records,
        Map<String, Integer> places,
        Map<UUID, String> names
) {

    public GuideStandings {
        caught = Map.copyOf(caught);
        records = Map.copyOf(records);
        places = Map.copyOf(places);
        names = Map.copyOf(names);
    }

    /** What the guide shows when the database could not be reached. */
    public static GuideStandings none() {
        return new GuideStandings(Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** The viewer's totals for one fish, never null. */
    public FishStats statsOf(String fishId) {
        return caught.getOrDefault(fishId, FishStats.none(fishId));
    }

    /**
     * A record holder's name.
     *
     * <p>Falls back to a short form of the id. A player who has never been on this
     * server since the cache was written has no name to give, and holding up the
     * menu to ask Mojang for one would be a poor trade.
     */
    public String nameOf(UUID holder) {
        String name = names.get(holder);
        return name != null ? name : holder.toString().substring(0, 8);
    }

    /** How many people have landed one at all, so a place has something to be out of. */
    public int anglersOn(String fishId) {
        FishRecord record = records.get(fishId);
        return record == null ? 0 : record.anglers();
    }
}
