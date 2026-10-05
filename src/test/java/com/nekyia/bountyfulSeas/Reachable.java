package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.config.*;
import com.nekyia.bountyfulSeas.fish.*;
import com.nekyia.bountyfulSeas.fishing.*;
import com.nekyia.bountyfulSeas.water.*;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Can every fish actually be caught in this world?
 *
 * <p>Sweeps every scanned region against every weather and time of day, and asks
 * the real selector - not a copy of its rules - whether each fish is in the draw
 * and at what odds. A fish that no region admits is content that cannot be reached,
 * which no amount of fishing in game would reveal except by its absence.
 */
public class Reachable {

    /** The spot, with a swarm forced on or off. */
    record Spot(WaterSpot water, boolean swarming) implements WaterConditions {
        public WaterType waterType() { return water.waterType(); }
        public Terrain terrain() { return water.terrain(); }
        public Vegetation vegetation() { return water.vegetation(); }
        public Depth depth() { return water.depth(); }
        public Set<Modifier> modifiers() { return water.modifiers(); }
        public Set<Condition> conditions() { return water.conditions(); }
        public boolean hasSwarmOf(String fishId) { return swarming; }
    }

    record Best(double percent, int regionId, String conditions, boolean needsSwarm) {}

    public static void main(String[] args) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(Path.of(args[0]).toFile());
        List<String> problems = new ArrayList<>();
        Settings settings = SettingsLoader.read(yaml, problems);
        problems.forEach(p -> System.out.println("CONFIG PROBLEM: " + p));
        TierKinds kinds = TierKinds.of(settings.tiers().objects());

        FishLoadResult loaded = FishLoader.load(Path.of(args[1]), kinds);
        loaded.problems().forEach(p -> System.out.println("FISH PROBLEM: " + p));
        Collection<Fish> all = loaded.library().all();

        WaterMap map = WaterMap.read(Path.of(args[2]));

        Map<Rarity, Double> bare = RodChances.weightsFor(0, 0, 0, settings, kinds);
        ToDoubleFunction<Rarity> bareOf = rarity -> bare.getOrDefault(rarity, 0.0);
        CatchOdds odds = new CatchOdds(
                at -> RodChances.weightsFor(0, 0, 0, settings, kinds, at), bareOf, 1);

        // Every weather a spot can be in. Storm implies rain, as the world reports it.
        Map<String, Set<Condition>> weathers = new LinkedHashMap<>();
        weathers.put("day", EnumSet.of(Condition.DAY));
        weathers.put("day + rain", EnumSet.of(Condition.DAY, Condition.RAIN));
        weathers.put("day + storm", EnumSet.of(Condition.DAY, Condition.RAIN, Condition.STORM));
        weathers.put("night", EnumSet.of(Condition.NIGHT));
        weathers.put("night + rain", EnumSet.of(Condition.NIGHT, Condition.RAIN));
        weathers.put("night + storm", EnumSet.of(Condition.NIGHT, Condition.RAIN, Condition.STORM));

        Map<String, Best> best = new HashMap<>();
        int level = Integer.parseInt(args[3]);

        for (int id = 0; id < map.regionCount(); id++) {
            WaterRegion region = map.region(id);
            for (Map.Entry<String, Set<Condition>> weather : weathers.entrySet()) {
                WaterSpot water = WaterSpot.of(region, weather.getValue(), null);
                for (boolean swarming : new boolean[]{false, true}) {
                    Spot spot = new Spot(water, swarming);
                    for (Chance chance : FishSelector.chances(all, spot, level, odds)) {
                        String fish = chance.fish().id();
                        Best held = best.get(fish);
                        // A fish that bites without a swarm is better news than one
                        // that needs the swarm to be here, even at longer odds.
                        boolean better = held == null
                                || (held.needsSwarm() && !swarming)
                                || (held.needsSwarm() == swarming && chance.percent() > held.percent());
                        if (better) {
                            best.put(fish, new Best(chance.percent(), id, weather.getKey(), swarming));
                        }
                    }
                }
            }
        }

        List<Fish> unreachable = all.stream().filter(f -> !best.containsKey(f.id()))
                .sorted(Comparator.comparing(Fish::id)).toList();

        System.out.printf("%d entries against %d regions, 6 weathers, swarm on and off,"
                + " as a level %d angler%n%n", all.size(), map.regionCount(), level);

        System.out.printf("%-32s %-9s %-8s %-14s %s%n",
                "fish", "best odds", "region", "weather", "needs a swarm");
        all.stream().sorted(Comparator.comparingDouble(
                        (Fish f) -> best.containsKey(f.id()) ? best.get(f.id()).percent() : -1)
                        .thenComparing(Fish::id))
                .forEach(fish -> {
                    Best found = best.get(fish.id());
                    if (found == null) {
                        System.out.printf("%-32s %-9s %-8s %-14s %s%n",
                                fish.id(), "NEVER", "-", "-", "-");
                    } else {
                        System.out.printf("%-32s %-9.4f %-8d %-14s %s%n", fish.id(),
                                found.percent(), found.regionId(), found.conditions(),
                                found.needsSwarm() ? "yes" : "no");
                    }
                });

        System.out.printf("%n%d of %d catchable somewhere%n", best.size(), all.size());
        if (!unreachable.isEmpty()) {
            System.out.println("UNREACHABLE: " + unreachable.stream().map(Fish::id).toList());
        }
        // Fails loudly, so this is a check rather than a report nobody reads.
        if (best.size() != all.size()) {
            throw new AssertionError(unreachable.size() + " entries cannot be caught anywhere");
        }
        System.out.println("every entry can be caught somewhere");
    }
}
