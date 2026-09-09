package com.nekyia.bountyfulSeas.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@link Settings} out of a Bukkit config.
 *
 * <p>The only place in the plugin that knows a config key by name. Values that
 * make no sense are clamped rather than rejected: a server should not lose its
 * fishing because somebody typed a negative number, and a clamped value with a
 * warning is easier to notice than a stack trace at startup.
 */
public final class SettingsLoader {

    private static final int MAX_SWARMS = 500;
    private static final double MIN_SIZE_SHAPE = 0.1;
    private static final double MAX_SIZE_SMALLEST = 0.95;
    private static final long MIN_ROTATION_MINUTES = 1;

    /** Must stay in step with the {@code rarity-chances} block in config.yml. */
    private static final Map<String, Double> DEFAULT_RARITY_CHANCES = Map.of(
            "uncommon", 75.0,
            "rare", 15.0,
            "misc", 5.0,
            "epic", 4.0,
            "legendary", 0.5,
            "treasure", 0.5,
            "mythic", 0.05,
            "signature", 0.0);

    private SettingsLoader() {
    }

    /**
     * @param problems receives a note for anything that had to be corrected
     */
    public static Settings read(FileConfiguration config, List<String> problems) {
        return new Settings(
                config.getString("water-map.analyzer", ""),
                config.getStringList("water-map.arguments"),
                config.getBoolean("water-map.regenerate-on-start", true),
                database(config),
                swarms(config, problems),
                rarityChances(config, problems),
                enchantments(config, problems),
                sizes(config, problems));
    }

    /**
     * How lengths are spread.
     *
     * <p>Clamped rather than rejected, like everything else here, but the bounds
     * matter more than usual: a shape at or below zero and a smallest at or above
     * one both make the curve meaningless rather than merely odd.
     */
    private static Settings.SizeSettings sizes(FileConfiguration config, List<String> problems) {
        double shape = config.getDouble("sizes.shape", 3);
        if (shape < MIN_SIZE_SHAPE) {
            problems.add("sizes.shape was " + shape + ", which is below " + MIN_SIZE_SHAPE
                    + "; using " + MIN_SIZE_SHAPE);
            shape = MIN_SIZE_SHAPE;
        }

        double smallest = config.getDouble("sizes.smallest", 0.25);
        if (smallest < 0 || smallest >= MAX_SIZE_SMALLEST) {
            problems.add("sizes.smallest was " + smallest + ", which has to be between 0 and "
                    + MAX_SIZE_SMALLEST + "; using 0.25");
            smallest = 0.25;
        }

        long odds = config.getLong("sizes.record-odds", 1_000_000);
        if (odds < 1) {
            problems.add("sizes.record-odds was " + odds + ", which cannot be below 1; using 1");
            odds = 1;
        }
        return new Settings.SizeSettings(shape, smallest, odds);
    }

    private static Settings.EnchantmentSettings enchantments(FileConfiguration config,
                                                             List<String> problems) {
        return new Settings.EnchantmentSettings(
                percent(config, "enchantments.lure.bonus-per-level", 0, problems),
                percent(config, "enchantments.luck-of-the-sea.bonus-per-level", 10, problems),
                percent(config, "enchantments.luck-of-the-fish.bonus-per-level", 10, problems));
    }

    private static double percent(FileConfiguration config, String path, double fallback,
                                  List<String> problems) {
        double value = config.getDouble(path, fallback);
        if (value < 0) {
            problems.add(path + " was " + value + ", which cannot be negative; using 0");
            return 0;
        }
        return value;
    }

    /**
     * The weight of each tier.
     *
     * <p>The base set adds up to a hundred and what an enchantment adds is taken
     * back out of the buffer, so it keeps adding up. Only the tiers present at a
     * spot take part in the draw, so one nobody has written a fish for costs
     * nothing.
     */
    private static Map<String, Double> rarityChances(FileConfiguration config, List<String> problems) {
        // Defaults live here as well as in config.yml, because a tier deleted from
        // the file would otherwise sit at zero - and a zero is silent, so nobody
        // would connect the missing line to the fish that stopped turning up.
        Map<String, Double> chances = new LinkedHashMap<>(DEFAULT_RARITY_CHANCES);

        ConfigurationSection section = config.getConfigurationSection("rarity-chances");
        if (section == null) {
            problems.add("no rarity-chances section, so the built-in tier chances are used");
            return Map.copyOf(chances);
        }

        for (String key : section.getKeys(false)) {
            double chance = section.getDouble(key, 0);
            if (chance < 0) {
                problems.add("rarity-chances." + key + " was " + chance
                        + ", which cannot be negative; using 0");
                chance = 0;
            }
            chances.put(key.toLowerCase(Locale.ROOT), chance);
        }
        return chances;
    }

    private static Settings.DatabaseSettings database(FileConfiguration config) {
        return new Settings.DatabaseSettings(
                config.getBoolean("database.enabled", false),
                config.getString("database.host", "127.0.0.1"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "bountyfulseas"),
                config.getString("database.username", "minecraft"),
                config.getString("database.password", "minecraft"),
                Duration.ofSeconds(Math.max(1, config.getLong("database.cache-seconds", 60))));
    }

    private static Settings.SwarmSettings swarms(FileConfiguration config, List<String> problems) {
        int count = config.getInt("swarms.count", 3);
        if (count < 0) {
            problems.add("swarms.count was " + count + ", which cannot be negative; using 0");
            count = 0;
        } else if (count > MAX_SWARMS) {
            problems.add("swarms.count was " + count + ", which is more than " + MAX_SWARMS
                    + "; using " + MAX_SWARMS);
            count = MAX_SWARMS;
        }

        long minutes = config.getLong("swarms.rotate-minutes", 60);
        if (minutes < MIN_ROTATION_MINUTES) {
            problems.add("swarms.rotate-minutes was " + minutes + ", which is below "
                    + MIN_ROTATION_MINUTES + "; using " + MIN_ROTATION_MINUTES);
            minutes = MIN_ROTATION_MINUTES;
        }

        return new Settings.SwarmSettings(count, Duration.ofMinutes(minutes));
    }
}
