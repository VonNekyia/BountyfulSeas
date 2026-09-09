package com.nekyia.bountyfulSeas.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Everything the plugin reads out of {@code config.yml}, already checked.
 *
 * <p>Config is read once, in one place, into types the rest of the plugin can use
 * without asking whether a key exists or what its default was. Nothing below this
 * point touches a {@code FileConfiguration}.
 *
 * @param analyzer      path to the water-analyzer executable, or empty when unset
 * @param analyzerFlags extra flags handed to the analyzer
 * @param rescanOnStart whether to rescan the world on every startup
 * @param database      how to reach the statistics database
 * @param swarms        how swarming fish are distributed
 * @param rarityChances relative chance of each tier being rolled, by tier name
 * @param sizes         how rolled lengths are spread between nothing and the maximum
 */
public record Settings(
        String analyzer,
        List<String> analyzerFlags,
        boolean rescanOnStart,
        DatabaseSettings database,
        SwarmSettings swarms,
        Map<String, Double> rarityChances,
        EnchantmentSettings enchantments,
        SizeSettings sizes
) {

    public Settings {
        analyzerFlags = List.copyOf(analyzerFlags);
        rarityChances = Map.copyOf(rarityChances);
    }

    /** The configured chance for a tier, or 0 when it was left out. */
    public double chanceOf(String rarity) {
        return rarityChances.getOrDefault(rarity, 0.0);
    }

    public boolean hasAnalyzer() {
        return analyzer != null && !analyzer.isBlank();
    }

    /**
     * @param enabled      whether statistics are kept at all
     * @param cacheDuration how long server-wide figures may be served from memory
     */
    public record DatabaseSettings(
            boolean enabled,
            String host,
            int port,
            String database,
            String username,
            String password,
            Duration cacheDuration
    ) {
    }

    /**
     * @param count    how many swarms exist at once, across every swarming fish
     * @param rotation how long a swarm stays where it is
     */
    public record SwarmSettings(int count, Duration rotation) {
    }

    /**
     * What a rod's enchantments are worth.
     *
     * <p>Both are a percentage of the tier's <em>base</em> chance per level, not a
     * percentage point. Lure at 10 with three levels makes a rare tier 1.3 times as
     * likely, not thirty points likelier - so the numbers stay sane whatever the
     * base chances are set to.
     *
     * @param lurePerLevel percent added per Lure level, to the same tiers as Luck of the Fish
     * @param luckPerLevel percent added per Luck of the Sea level, to treasure
     * @param fishPerLevel percent added per Luck of the Fish level, to rare, epic and legendary
     */
    public record EnchantmentSettings(
            double lurePerLevel,
            double luckPerLevel,
            double fishPerLevel
    ) {

        /** The multiplier a rod with this much Lure applies. */
        public double lureMultiplier(int level) {
            return 1 + lurePerLevel / 100.0 * Math.max(0, level);
        }

        /** The multiplier a rod with this much Luck of the Sea applies. */
        public double luckMultiplier(int level) {
            return 1 + luckPerLevel / 100.0 * Math.max(0, level);
        }

        /** The multiplier a rod with this much Luck of the Fish applies. */
        public double fishMultiplier(int level) {
            return 1 + fishPerLevel / 100.0 * Math.max(0, level);
        }
    }

    /**
     * The shape of the length roll.
     *
     * <p>Held as plain numbers rather than as the curve itself: config knows what
     * was configured, and the fishing module owns what it means.
     *
     * @param shape     Z/K, the mortality-to-growth ratio; higher means big ones are rarer
     * @param smallest  the shortest catch, as a fraction of the fish's maximum
     * @param oddsOfMax one catch in this many comes up at the maximum length
     */
    public record SizeSettings(double shape, double smallest, long oddsOfMax) {
    }
}
