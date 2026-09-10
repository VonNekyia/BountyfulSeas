package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.api.StatsApi;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.level.ExperienceRule;
import com.nekyia.bountyfulSeas.level.LevelCurve;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.FishStats;
import com.nekyia.bountyfulSeas.stats.Milestone;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * What level each angler is, kept where the fishing event can read it.
 *
 * <p>Experience is not stored. It is worked out from the catch totals that are
 * already kept: every milestone a player has passed is worth what the rule says,
 * and the sum of those is their experience. Nothing to write, nothing that can
 * drift out of step with the counts it is derived from, and no second table.
 *
 * <p>The cost is that working it out reads a player's whole row set, which is a
 * database call - and a bite has to be answered on the server thread. So the
 * answer is kept in memory: refreshed when a player joins and again after each
 * catch, both off the server thread, and only ever read from here.
 */
final class AnglerLevels {

    private final Map<UUID, Progress> known = new ConcurrentHashMap<>();

    private final Supplier<FishLibrary> fish;
    private final Supplier<StatsApi> stats;
    private final Supplier<LevelCurve> curve;
    private final Supplier<ExperienceRule> rule;
    private final BooleanSupplier tracked;

    /**
     * @param tracked whether catches are being stored at all; without a database
     *                there are no totals, so there is nothing to derive a level
     *                from and the gate would shut every fish away forever
     */
    AnglerLevels(Supplier<FishLibrary> fish, Supplier<StatsApi> stats,
                 Supplier<LevelCurve> curve, Supplier<ExperienceRule> rule,
                 BooleanSupplier tracked) {
        this.fish = fish;
        this.stats = stats;
        this.curve = curve;
        this.rule = rule;
        this.tracked = tracked;
    }

    /** The level to gate fish behind. Safe on the server thread; never blocks. */
    int levelOf(UUID player) {
        return progressOf(player).level();
    }

    /** What is known about a player right now, without going to look. */
    Progress progressOf(UUID player) {
        if (!tracked.getAsBoolean()) {
            // No totals means no progress. Rather than lock everyone out of every
            // fish, the gate simply stands open.
            return curve.get().at(Long.MAX_VALUE);
        }
        Progress progress = known.get(player);
        return progress != null ? progress : curve.get().at(0);
    }

    /**
     * Reads a player's totals and works out where they stand.
     *
     * <p>Blocks on the database. Call it off the server thread.
     */
    Progress refresh(UUID player) {
        if (!tracked.getAsBoolean()) {
            return progressOf(player);
        }
        Progress progress = curve.get().at(experienceOf(stats.get().player(player)));
        known.put(player, progress);
        return progress;
    }

    /** Drops what is held for a player who has left. */
    void forget(UUID player) {
        known.remove(player);
    }

    /**
     * The experience a set of catch totals is worth.
     *
     * <p>Every milestone passed on every fish, each priced by the rule. A fish that
     * has since been deleted from the config contributes nothing, which is the
     * honest answer: its level is no longer known.
     */
    private long experienceOf(Map<String, FishStats> totals) {
        FishLibrary library = fish.get();
        ExperienceRule rule = this.rule.get();

        long experience = 0;
        for (FishStats caught : totals.values()) {
            Fish definition = library.get(caught.fishId());
            if (definition == null) {
                continue;
            }
            for (Milestone milestone : Milestone.values()) {
                if (caught.catches() >= milestone.required()) {
                    experience += rule.forMilestone(milestone.step(), definition.level());
                }
            }
        }
        return experience;
    }
}
