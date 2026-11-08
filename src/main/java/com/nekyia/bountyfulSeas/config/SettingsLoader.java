package com.nekyia.bountyfulSeas.config;

import com.nekyia.bountyfulSeas.fish.Rarity;
import com.nekyia.bountyfulSeas.level.ExperienceRule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /** Must stay in step with the {@code tiers} block in config.yml. */
    private static final Set<String> DEFAULT_FISH_TIERS = Set.of(
            "uncommon", "rare", "epic", "legendary", "mythic", "signature");

    private static final Set<String> DEFAULT_OBJECT_TIERS = Set.of("misc", "treasure");

    /** Must stay in step with the {@code sizes.brackets} list in config.yml. */
    private static final List<Settings.SizeSettings.Bracket> DEFAULT_BRACKETS = List.of(
            new Settings.SizeSettings.Bracket("a record", 0.000001),
            new Settings.SizeSettings.Bracket("1 in 100000", 0.00001),
            new Settings.SizeSettings.Bracket("1 in 10000", 0.0001),
            new Settings.SizeSettings.Bracket("1 in 1000", 0.001),
            new Settings.SizeSettings.Bracket("top 1%", 0.01),
            new Settings.SizeSettings.Bracket("top 10%", 0.10),
            new Settings.SizeSettings.Bracket("above average", 0.50));
    private static final long MIN_ROTATION_MINUTES = 1;

    /** Must stay in step with the {@code rarity-chances} block in config.yml. */
    private static final Map<String, Double> DEFAULT_RARITY_CHANCES = Map.of(
            "uncommon", 75.0,
            "rare", 15.0,
            "misc", 5.0,
            "epic", 4.0,
            "legendary", 0.5,
            "treasure", 0.5,
            "mythic", 0.0,
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
                sizes(config, problems),
                levels(config, problems),
                tiers(config, problems),
                boss(config, "kraken", "Kraken"),
                boss(config, "nessy", "Nessy"));
    }

    private static Settings.BossSettings boss(FileConfiguration config, String key, String mob) {
        return new Settings.BossSettings(
                Math.clamp(config.getDouble(key + ".chance", 0.00004), 0, 1),
                config.getInt(key + ".min-level", 6),
                config.getString(key + ".mob", mob),
                Math.clamp(config.getDouble(key + ".trophy-chance", 0.01), 0, 1));
    }

    /**
     * Which tiers are fish and which are objects.
     *
     * <p>Worth checking properly rather than clamping: a tier in neither list, or
     * in both, changes what the enchantments do to it, and doing that silently
     * would be a balance change nobody asked for.
     */
    private static Settings.TierSettings tiers(FileConfiguration config, List<String> problems) {
        Set<String> fish = names(config, "tiers.fish", DEFAULT_FISH_TIERS);
        Set<String> objects = names(config, "tiers.objects", DEFAULT_OBJECT_TIERS);

        for (String name : fish) {
            if (objects.contains(name)) {
                problems.add("tiers." + name + " is listed as both fish and object; "
                        + "treating it as an object");
            }
        }
        for (String name : DEFAULT_RARITY_CHANCES.keySet()) {
            if (!fish.contains(name) && !objects.contains(name)) {
                problems.add("tiers has no side for " + name + ", so it counts as fish");
            }
        }
        return new Settings.TierSettings(fish, objects);
    }

    private static Set<String> names(FileConfiguration config, String path, Set<String> fallback) {
        List<String> listed = config.getStringList(path);
        if (listed.isEmpty()) {
            return fallback;
        }
        Set<String> names = new LinkedHashSet<>();
        for (String name : listed) {
            names.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /** What a catch of each tier pays when the config leaves one out. */
    private static final Map<String, Long> DEFAULT_CATCH_EXPERIENCE = Map.of(
            "common", 10L,
            "uncommon", 15L,
            "rare", 30L,
            "misc", 2L,
            "epic", 80L,
            "legendary", 300L,
            "treasure", 20L,
            "mythic", 1000L,
            "signature", 0L);

    /** What each level after the first costs when the config gives no list. */
    private static final List<Long> DEFAULT_LEVEL_COSTS = List.of(
            100L, 250L, 450L, 700L, 1000L, 1400L, 1900L, 2500L,
            3200L, 4000L, 5000L, 6200L, 7600L, 9200L, 25000L);

    /** What a catch pays, and what each level costs. */
    private static Settings.LevelSettings levels(FileConfiguration config, List<String> problems) {
        Map<String, Long> perCatch = new LinkedHashMap<>(DEFAULT_CATCH_EXPERIENCE);
        ConfigurationSection section = config.getConfigurationSection("levels.experience-per-catch");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                long value = section.getLong(key);
                if (value < 0) {
                    problems.add("levels.experience-per-catch." + key + " was " + value
                            + ", which cannot be negative; using 0");
                    value = 0;
                }
                perCatch.put(key.toLowerCase(Locale.ROOT), value);
            }
        }

        long first = config.getLong("levels.first-catch-multiplier", 5);
        if (first < 1) {
            problems.add("levels.first-catch-multiplier was " + first + ", which is below 1; using 1");
            first = 1;
        }
        double bigBonus = Math.max(0, config.getDouble("levels.big-fish-bonus", 0.5));
        double bigShare = config.getDouble("levels.big-fish-share", 0.10);
        if (bigShare <= 0 || bigShare > 1) {
            problems.add("levels.big-fish-share was " + bigShare + ", which is not a share between 0 and 1; using 0.10");
            bigShare = 0.10;
        }
        long perStep = Math.max(0, config.getLong("levels.experience-per-milestone-step", 5));

        return new Settings.LevelSettings(
                new ExperienceRule(perCatch, first, bigBonus, bigShare, perStep),
                costs(config, problems));
    }

    /** What each level costs. A list that cannot be read is replaced whole, never half used. */
    private static List<Long> costs(FileConfiguration config, List<String> problems) {
        List<?> listed = config.getList("levels.costs");
        if (listed == null || listed.isEmpty()) {
            return DEFAULT_LEVEL_COSTS;
        }
        List<Long> costs = new ArrayList<>();
        for (int index = 0; index < listed.size(); index++) {
            long cost = number(listed.get(index), 0).longValue();
            if (cost < 1) {
                problems.add("levels.costs[" + index + "] was " + listed.get(index)
                        + ", which is not a cost of at least 1; using the shipped costs");
                return DEFAULT_LEVEL_COSTS;
            }
            costs.add(cost);
        }
        return costs;
    }

    /** A config number, whatever numeric type it was written as. */
    private static Number number(Object value, Number fallback) {
        return value instanceof Number given ? given : fallback;
    }

    /**
     * How lengths are spread.
     *
     * <p>Clamped rather than rejected, like everything else here, but the bounds
     * matter more than usual: a shape at or below zero and a smallest at or above
     * one both make the curve meaningless rather than merely odd.
     */
    private static Settings.SizeSettings sizes(FileConfiguration config, List<String> problems) {
        double shape = config.getDouble("sizes.shape", 1.5);
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

        long odds = config.getLong("sizes.record-odds", 10_000_000);
        if (odds < 1) {
            problems.add("sizes.record-odds was " + odds + ", which cannot be below 1; using 1");
            odds = 1;
        }
        double tailFrom = config.getDouble("sizes.tail-from", 0.80);
        if (tailFrom <= smallest || tailFrom > 1) {
            problems.add("sizes.tail-from was " + tailFrom + ", which has to be above sizes.smallest"
                    + " and at most 1; using 1, which turns the tail off");
            tailFrom = 1;
        }
        return new Settings.SizeSettings(shape, smallest, odds, tailFrom, brackets(config, problems));
    }

    /**
     * The marks along the length curve worth naming.
     *
     * <p>A list of label and chance pairs rather than fixed steps in the code,
     * because what counts as a good fish is a judgement about a server rather than
     * a fact about the curve.
     */
    private static List<Settings.SizeSettings.Bracket> brackets(FileConfiguration config,
                                                                List<String> problems) {
        List<Settings.SizeSettings.Bracket> brackets = new ArrayList<>();

        for (Map<?, ?> entry : config.getMapList("sizes.brackets")) {
            Object label = entry.get("label");
            Object chance = entry.get("chance");
            if (label == null || !(chance instanceof Number reached)) {
                problems.add("a sizes.brackets entry needs a label and a chance; skipping " + entry);
                continue;
            }
            double fraction = reached.doubleValue();
            if (fraction <= 0 || fraction > 1) {
                problems.add("sizes.brackets " + label + " was " + fraction
                        + ", which has to be between 0 and 1; skipping it");
                continue;
            }
            brackets.add(new Settings.SizeSettings.Bracket(String.valueOf(label), fraction));
        }

        if (brackets.isEmpty()) {
            problems.add("no sizes.brackets, so the built-in marks are used");
            return DEFAULT_BRACKETS;
        }

        // Longest first, so the list reads downwards however it was written.
        brackets.sort(Comparator.comparingDouble(Settings.SizeSettings.Bracket::chance));
        return List.copyOf(brackets);
    }

    private static Settings.EnchantmentSettings enchantments(FileConfiguration config,
                                                             List<String> problems) {
        return new Settings.EnchantmentSettings(
                percent(config, "enchantments.lure.bonus-per-level", 0, problems),
                percent(config, "enchantments.luck-of-the-sea.bonus-per-level", 10, problems),
                percent(config, "enchantments.luck-of-the-fish.bonus-per-level", 10, problems),
                tierName(config, "enchantments.luck-of-the-fish.lifts-from", "epic", problems));
    }

    /**
     * A tier named in config, checked against the tiers there are.
     *
     * <p>Checked here rather than where it is used, because a name that matches
     * nothing has to be reported once at load rather than quietly meaning nothing
     * every time somebody casts.
     */
    private static String tierName(FileConfiguration config, String path, String fallback,
                                   List<String> problems) {
        String named = config.getString(path, fallback).trim().toLowerCase(Locale.ROOT);
        for (Rarity rarity : Rarity.values()) {
            if (rarity.configName().equals(named)) {
                return named;
            }
        }
        problems.add(path + " was " + named + ", which is not a tier; using " + fallback);
        return fallback;
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
