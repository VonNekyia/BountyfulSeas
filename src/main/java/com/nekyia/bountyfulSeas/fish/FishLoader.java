package com.nekyia.bountyfulSeas.fish;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads every {@code *.yaml} in the fish folder into {@link Fish} definitions.
 *
 * <p>Nothing here throws on bad input. A file that will not parse, or a fish that
 * fails validation, is reported as a {@link FishProblem} and skipped, so one
 * broken family never costs the rest of the server its fish.
 */
public final class FishLoader {

    private static final String KEY_NAME = "fish_name";
    private static final String KEY_ITEM = "fish_item";
    private static final String KEY_MIN_LENGTH = "min_length";
    private static final String KEY_MAX_LENGTH = "max_length";
    private static final String KEY_WATER_TYPE = "water_type";
    private static final String KEY_TERRAIN = "terrain";
    private static final String KEY_VEGETATION = "vegetation";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_MODIFIER = "modifier";
    private static final String KEY_CONDITION = "condition";
    private static final String KEY_BAIT_LOCKED = "bait_locked";
    private static final String KEY_SPAWN_WEIGHT = "spawn_weight";
    private static final String KEY_RARITY = "rarity";
    private static final String KEY_ON_EAT = "on_eat";
    private static final String KEY_LORE = "lore";

    private static final String KEY_SATURATION = "saturation";
    private static final String KEY_EFFECTS = "effects";
    private static final String KEY_EFFECT_TYPE = "type";
    private static final String KEY_EFFECT_DURATION = "duration";
    private static final String KEY_EFFECT_AMPLIFIER = "amplifier";

    private static final Set<String> KNOWN_KEYS = Set.of(
            KEY_NAME, KEY_ITEM,
            KEY_MIN_LENGTH, KEY_MAX_LENGTH,
            KEY_WATER_TYPE, KEY_TERRAIN, KEY_VEGETATION, KEY_DEPTH,
            KEY_MODIFIER, KEY_CONDITION,
            KEY_BAIT_LOCKED, KEY_SPAWN_WEIGHT, KEY_RARITY, KEY_ON_EAT, KEY_LORE);

    /**
     * Settings that used to exist and are now ignored rather than rejected.
     *
     * <p>Weight was dropped in favour of length alone. A config written before that
     * is out of date, not broken, so it keeps its fish and gets told to tidy up.
     */
    private static final Set<String> DEPRECATED_KEYS = Set.of("min_weight", "max_weight");

    private static final Set<String> KNOWN_ON_EAT_KEYS = Set.of(KEY_SATURATION, KEY_EFFECTS);

    private static final Set<String> KNOWN_EFFECT_KEYS =
            Set.of(KEY_EFFECT_TYPE, KEY_EFFECT_DURATION, KEY_EFFECT_AMPLIFIER);

    /** Namespaced item reference, such as nexo:golden_carp. */
    private static final Pattern ITEM_REFERENCE = Pattern.compile("[a-z0-9_.-]+:[a-zA-Z0-9_./-]+");

    private static final int DEFAULT_SPAWN_WEIGHT = 100;

    private FishLoader() {
    }

    /**
     * Reads the folder recursively. A missing folder simply yields no fish.
     *
     * @param folder the {@code fishes} folder inside the plugin data folder
     */
    public static FishLoadResult load(Path folder) {
        List<FishProblem> problems = new ArrayList<>();
        Map<String, Fish> fishes = new LinkedHashMap<>();
        Map<String, String> declaredIn = new HashMap<>();

        if (!Files.isDirectory(folder)) {
            return new FishLoadResult(FishLibrary.empty(), problems);
        }

        List<Path> files;
        try (Stream<Path> paths = Files.walk(folder)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(FishLoader::isYaml)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            problems.add(FishProblem.file(folder.toString(),
                    "could not be read: " + exception.getMessage()));
            return new FishLoadResult(FishLibrary.empty(), problems);
        }

        for (Path file : files) {
            readFile(folder, file, fishes, declaredIn, problems);
        }

        return new FishLoadResult(new FishLibrary(fishes), problems);
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private static void readFile(Path folder, Path file, Map<String, Fish> fishes,
                                 Map<String, String> declaredIn, List<FishProblem> problems) {
        String name = folder.relativize(file).toString().replace('\\', '/');
        String category = category(file);

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (InvalidConfigurationException exception) {
            problems.add(FishProblem.file(name, "is not valid YAML: " + flatten(exception.getMessage())));
            return;
        } catch (IOException exception) {
            problems.add(FishProblem.file(name, "could not be read: " + exception.getMessage()));
            return;
        }

        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) {
                problems.add(FishProblem.fish(name, id, "must be a block of fish settings, not a single value"));
                continue;
            }

            String previous = declaredIn.get(id);
            if (previous != null) {
                problems.add(FishProblem.fish(name, id, "is already defined in " + previous));
                continue;
            }

            Fish fish = parse(name, category, id, section, problems);
            if (fish != null) {
                fishes.put(id, fish);
                declaredIn.put(id, name);
            }
        }
    }

    /** The category is the file a fish sits in, so a sub folder does not change it. */
    private static String category(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static Fish parse(String file, String category, String id,
                              ConfigurationSection section, List<FishProblem> problems) {
        long fatalBefore = problems.stream().filter(FishProblem::fatal).count();

        for (String key : section.getKeys(false)) {
            if (DEPRECATED_KEYS.contains(key)) {
                problems.add(FishProblem.notice(file, id,
                        "still sets " + key + ", which no longer exists and is ignored"));
            } else if (!KNOWN_KEYS.contains(key)) {
                problems.add(FishProblem.fish(file, id, "has the unknown setting " + key));
            }
        }

        String name = requiredText(file, id, section, KEY_NAME, problems);
        String item = requiredText(file, id, section, KEY_ITEM, problems);
        if (item != null && !ITEM_REFERENCE.matcher(item).matches()) {
            problems.add(FishProblem.fish(file, id, KEY_ITEM
                    + " must be a namespaced item such as nexo:golden_carp, but is " + item));
        }

        List<String> lore = textList(file, id, section, problems);

        double minLength = number(file, id, section, KEY_MIN_LENGTH, problems);
        double maxLength = number(file, id, section, KEY_MAX_LENGTH, problems);

        checkRange(file, id, KEY_MIN_LENGTH, minLength, KEY_MAX_LENGTH, maxLength, problems);

        Set<WaterType> waterTypes = enums(WaterType.class, file, id, section, KEY_WATER_TYPE, problems);
        Set<Terrain> terrains = enums(Terrain.class, file, id, section, KEY_TERRAIN, problems);
        Set<Vegetation> vegetations = enums(Vegetation.class, file, id, section, KEY_VEGETATION, problems);
        Set<Depth> depths = enums(Depth.class, file, id, section, KEY_DEPTH, problems);
        Set<Modifier> modifiers = enums(Modifier.class, file, id, section, KEY_MODIFIER, problems);
        Set<Condition> conditions = enums(Condition.class, file, id, section, KEY_CONDITION, problems);

        boolean baitLocked = flag(file, id, section, problems);
        int spawnWeight = spawnWeight(file, id, section, problems);
        Rarity rarity = rarity(file, id, section, problems);
        if (rarity != null && rarity.suppressesSize() && (minLength > 0 || maxLength > 0)) {
            problems.add(FishProblem.fish(file, id, "is " + rarity.configName()
                    + ", which is not a fish and carries no length, so "
                    + KEY_MIN_LENGTH + " and " + KEY_MAX_LENGTH + " must be left out"));
        }
        OnEat onEat = onEat(file, id, section, problems);

        // Only a fatal problem costs the fish its place; a notice just gets said.
        if (problems.stream().filter(FishProblem::fatal).count() != fatalBefore) {
            return null;
        }

        return new Fish(id, category, name, item, lore,
                minLength, maxLength,
                waterTypes, terrains, vegetations, depths, modifiers, conditions,
                baitLocked, spawnWeight, rarity, onEat);
    }

    /**
     * Reads the optional {@code on_eat} block. Its absence is the whole way a fish
     * says it cannot be eaten, so a missing block is not a problem and returns null.
     */
    private static OnEat onEat(String file, String id, ConfigurationSection section, List<FishProblem> problems) {
        Object raw = section.get(KEY_ON_EAT);
        if (raw == null) {
            return null;
        }

        ConfigurationSection block = section.getConfigurationSection(KEY_ON_EAT);
        if (block == null) {
            problems.add(FishProblem.fish(file, id, KEY_ON_EAT + " must be a block with "
                    + KEY_SATURATION + " and " + KEY_EFFECTS));
            return null;
        }

        for (String key : block.getKeys(false)) {
            if (!KNOWN_ON_EAT_KEYS.contains(key)) {
                problems.add(FishProblem.fish(file, id, KEY_ON_EAT + " has the unknown setting " + key));
            }
        }

        double saturation = number(file, id, block, KEY_SATURATION, problems);
        List<FishEffect> effects = effects(file, id, block, problems);

        return new OnEat(saturation, effects);
    }

    /**
     * Effects are a list of blocks, which Bukkit hands back as plain maps rather
     * than configuration sections, so they are read as maps here.
     */
    private static List<FishEffect> effects(String file, String id, ConfigurationSection block,
                                            List<FishProblem> problems) {
        List<FishEffect> effects = new ArrayList<>();

        Object raw = block.get(KEY_EFFECTS);
        if (raw == null) {
            return effects;
        }
        if (!(raw instanceof List<?> entries)) {
            problems.add(FishProblem.fish(file, id, KEY_EFFECTS + " must be a list of effect blocks"));
            return effects;
        }

        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                problems.add(FishProblem.fish(file, id, KEY_EFFECTS + " may only contain blocks with "
                        + KEY_EFFECT_TYPE + ", " + KEY_EFFECT_DURATION + " and " + KEY_EFFECT_AMPLIFIER));
                continue;
            }

            for (Object key : map.keySet()) {
                if (!KNOWN_EFFECT_KEYS.contains(String.valueOf(key))) {
                    problems.add(FishProblem.fish(file, id, KEY_EFFECTS
                            + " has the unknown setting " + key));
                }
            }

            NamespacedKey type = effectType(file, id, map.get(KEY_EFFECT_TYPE), problems);
            int duration = effectInt(file, id, map.get(KEY_EFFECT_DURATION), KEY_EFFECT_DURATION, 0, problems);
            int amplifier = effectInt(file, id, map.get(KEY_EFFECT_AMPLIFIER), KEY_EFFECT_AMPLIFIER, 0, problems);

            if (map.get(KEY_EFFECT_DURATION) == null) {
                problems.add(FishProblem.fish(file, id, KEY_EFFECTS + " is missing " + KEY_EFFECT_DURATION));
            } else if (duration <= 0) {
                problems.add(FishProblem.fish(file, id, KEY_EFFECT_DURATION + " must be more than 0 ticks"));
            }

            if (type != null) {
                effects.add(new FishEffect(type, duration, amplifier));
            }
        }
        return effects;
    }

    private static NamespacedKey effectType(String file, String id, Object raw, List<FishProblem> problems) {
        if (raw == null) {
            problems.add(FishProblem.fish(file, id, KEY_EFFECTS + " is missing " + KEY_EFFECT_TYPE));
            return null;
        }
        if (!(raw instanceof String text) || text.isBlank()) {
            problems.add(FishProblem.fish(file, id, KEY_EFFECT_TYPE + " must be text"));
            return null;
        }

        NamespacedKey key = NamespacedKey.fromString(text.trim().toLowerCase(Locale.ROOT));
        if (key == null) {
            problems.add(FishProblem.fish(file, id, KEY_EFFECT_TYPE + " " + text
                    + " is not a valid effect name"));
            return null;
        }
        if (!EffectTypes.isKnown(key)) {
            problems.add(FishProblem.fish(file, id, KEY_EFFECT_TYPE + " " + text
                    + " is not an effect this server knows"));
            return null;
        }
        return key;
    }

    private static int effectInt(String file, String id, Object raw, String key,
                                 int fallback, List<FishProblem> problems) {
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Integer value)) {
            problems.add(FishProblem.fish(file, id, key + " must be a whole number"));
            return fallback;
        }
        if (value < 0) {
            problems.add(FishProblem.fish(file, id, key + " must not be negative"));
        }
        return value;
    }

    /** Flavour lines. Free text, so only the shape is checked, never the wording. */
    private static List<String> textList(String file, String id, ConfigurationSection section,
                                         List<FishProblem> problems) {
        Object raw = section.get(KEY_LORE);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> entries)) {
            problems.add(FishProblem.fish(file, id, KEY_LORE + " must be a list of text lines"));
            return List.of();
        }

        List<String> lines = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (entry instanceof String text) {
                lines.add(text);
            } else {
                problems.add(FishProblem.fish(file, id, KEY_LORE + " may only contain text lines"));
            }
        }
        return List.copyOf(lines);
    }

    private static String requiredText(String file, String id, ConfigurationSection section,
                                       String key, List<FishProblem> problems) {
        Object raw = section.get(key);
        if (raw == null) {
            problems.add(FishProblem.fish(file, id, "is missing " + key));
            return null;
        }
        if (!(raw instanceof String text)) {
            problems.add(FishProblem.fish(file, id, key + " must be text"));
            return null;
        }
        if (text.isBlank()) {
            problems.add(FishProblem.fish(file, id, key + " must not be empty"));
            return null;
        }
        return text;
    }

    private static double number(String file, String id, ConfigurationSection section,
                                 String key, List<FishProblem> problems) {
        Object raw = section.get(key);
        if (raw == null) {
            return 0;
        }
        if (!(raw instanceof Number value)) {
            problems.add(FishProblem.fish(file, id, key + " must be a number"));
            return 0;
        }
        double result = value.doubleValue();
        if (result < 0) {
            problems.add(FishProblem.fish(file, id, key + " must not be negative"));
        }
        return result;
    }

    private static void checkRange(String file, String id, String minKey, double min,
                                   String maxKey, double max, List<FishProblem> problems) {
        if (min > max) {
            problems.add(FishProblem.fish(file, id,
                    minKey + " (" + min + ") must not be greater than " + maxKey + " (" + max + ")"));
        }
    }

    private static boolean flag(String file, String id, ConfigurationSection section, List<FishProblem> problems) {
        Object raw = section.get(KEY_BAIT_LOCKED);
        if (raw == null) {
            return false;
        }
        if (!(raw instanceof Boolean value)) {
            problems.add(FishProblem.fish(file, id, KEY_BAIT_LOCKED + " must be true or false"));
            return false;
        }
        return value;
    }

    private static int spawnWeight(String file, String id, ConfigurationSection section, List<FishProblem> problems) {
        Object raw = section.get(KEY_SPAWN_WEIGHT);
        if (raw == null) {
            return DEFAULT_SPAWN_WEIGHT;
        }
        if (!(raw instanceof Integer value)) {
            problems.add(FishProblem.fish(file, id, KEY_SPAWN_WEIGHT + " must be a whole number"));
            return DEFAULT_SPAWN_WEIGHT;
        }
        if (value < 0) {
            problems.add(FishProblem.fish(file, id, KEY_SPAWN_WEIGHT + " must not be negative"));
        }
        return value;
    }

    private static Rarity rarity(String file, String id, ConfigurationSection section, List<FishProblem> problems) {
        Object raw = section.get(KEY_RARITY);
        if (raw == null) {
            problems.add(FishProblem.fish(file, id, "is missing " + KEY_RARITY
                    + ", expected one of: " + allowed(Rarity.class)));
            return null;
        }
        if (!(raw instanceof String text)) {
            problems.add(FishProblem.fish(file, id, KEY_RARITY + " must be text"));
            return null;
        }
        Rarity rarity = match(Rarity.class, text);
        if (rarity == null) {
            problems.add(FishProblem.fish(file, id, KEY_RARITY + " " + text
                    + " is unknown, expected one of: " + allowed(Rarity.class)));
        }
        return rarity;
    }

    private static <E extends Enum<E> & ConfigValue> Set<E> enums(Class<E> type, String file, String id,
                                                                  ConfigurationSection section, String key,
                                                                  List<FishProblem> problems) {
        Set<E> values = EnumSet.noneOf(type);

        Object raw = section.get(key);
        if (raw == null) {
            return values;
        }
        if (!(raw instanceof List<?> entries)) {
            problems.add(FishProblem.fish(file, id, key + " must be a list of: " + allowed(type)));
            return values;
        }

        for (Object entry : entries) {
            if (!(entry instanceof String text)) {
                problems.add(FishProblem.fish(file, id, key + " may only contain text values"));
                continue;
            }
            E value = match(type, text);
            if (value == null) {
                problems.add(FishProblem.fish(file, id, key + " value " + text
                        + " is unknown, expected one of: " + allowed(type)));
                continue;
            }
            if (!values.add(value)) {
                problems.add(FishProblem.fish(file, id, key + " lists " + text + " twice"));
            }
        }
        return values;
    }

    private static <E extends Enum<E> & ConfigValue> E match(Class<E> type, String text) {
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (E candidate : type.getEnumConstants()) {
            if (candidate.configName().equals(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    private static <E extends Enum<E> & ConfigValue> String allowed(Class<E> type) {
        return Stream.of(type.getEnumConstants())
                .map(ConfigValue::configName)
                .collect(Collectors.joining(", "));
    }

    /**
     * SnakeYAML reports the cause on the first line and the line and column on the
     * following ones, so the whole message is folded onto one console line rather
     * than cut down to the half that does not say where to look.
     */
    private static String flatten(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.replaceAll("\\s*\\R\\s*", "; ").trim();
    }
}
