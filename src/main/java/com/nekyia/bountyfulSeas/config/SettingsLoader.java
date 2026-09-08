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
    private static final long MIN_ROTATION_MINUTES = 1;

    /** Must stay in step with the {@code rarity-chances} block in config.yml. */
    private static final Map<String, Double> DEFAULT_RARITY_CHANCES = Map.of(
            "common", 75.0,
            "rare", 15.0,
            "trash", 5.0,
            "epic", 2.0,
            "misc", 2.0,
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
                enchantments(config, problems));
    }

    private static Settings.EnchantmentSettings enchantments(FileConfiguration config,
                                                             List<String> problems) {
        return new Settings.EnchantmentSettings(
                percent(config, "enchantments.lure.bonus-per-level", 0, problems),
                percent(config, "enchantments.luck-of-the-sea.bonus-per-level", 10, problems),
                percent(config, "enchantments.luck-of-the-fish.bonus-per-level", 10, problems),
                config.getString("enchantments.luck-of-the-fish.key",
                        "bountyfulseas:luck_of_the_fish"));
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
     * Relative chances per tier.
     *
     * <p>Relative on purpose: they are normalised against whatever is actually
     * available at a spot, so they do not have to add up to a hundred and a tier
     * nobody has written a fish for costs nothing.
     */
    private static Map<String, Double> rarityChances(FileConfiguration config, List<String> problems) {
        // Defaults live here as well as in config.yml, because a config written
        // before this section existed would otherwise leave every tier at zero -
        // and a zero everywhere means nothing is ever caught, with nothing said.
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
