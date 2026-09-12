package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.nexo.NexoItem;

import com.nekyia.bountyfulSeas.fish.Rarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Describes fish in Nexo's terms.
 *
 * <p>This is the only place that knows both sides. The {@code fish} package reads
 * config and knows nothing about items; the {@code nexo} package writes items and
 * knows nothing about fish. Everything that translates between them - the
 * {@code nexo:} prefix, what a category looks like as lore, what "edible" means as
 * a saturation value - lives here, so neither side has to grow an opinion about
 * the other.
 */
final class FishNexoItems {

    /** Lore is italic purple by default in vanilla, so the tag turns that off. */
    private static final String CATEGORY_LORE = "<!italic><dark_gray>%s";

    /** The tier, directly under the name, where a rarity is looked for. */
    private static final String RARITY_LORE = "<!italic>%s%s";

    /**
     * What each tier is coloured, as MiniMessage.
     *
     * <p>A rarity is read at a glance or not at all, so the colour carries it and
     * the word only confirms. Held here rather than on the tier itself: the fish
     * package has no opinion about how anything looks.
     */
    private static final Map<Rarity, String> RARITY_COLOURS = new EnumMap<>(Map.of(
            Rarity.UNCOMMON, "<gray>",
            Rarity.RARE, "<aqua>",
            Rarity.MISC, "<dark_gray>",
            Rarity.EPIC, "<light_purple>",
            Rarity.LEGENDARY, "<gold>",
            Rarity.TREASURE, "<yellow>",
            Rarity.MYTHIC, "<red>",
            Rarity.SIGNATURE, "<dark_purple>"));

    /** Flavour text, kept italic and a shade lighter so it reads as prose, not data. */
    private static final String FLAVOUR_LORE = "<italic><gray>%s";

    private static final String NEXO_NAMESPACE = "nexo";

    private FishNexoItems() {
    }

    /** Every fish that names a Nexo item, as items Nexo can be given. */
    static List<NexoItem> from(Collection<Fish> fishes) {
        return fishes.stream()
                .filter(fish -> nexoIdOf(fish.item()) != null)
                .<NexoItem>map(FishNexoItems::describe)
                .toList();
    }

    private static NexoItem describe(Fish fish) {
        String id = nexoIdOf(fish.item());
        String displayName = fish.name();
        List<String> lore = new ArrayList<>();
        if (fish.rarity() != null) {
            lore.add(RARITY_LORE.formatted(
                    RARITY_COLOURS.getOrDefault(fish.rarity(), "<gray>"),
                    readable(fish.rarity().configName())));
        }
        lore.add(CATEGORY_LORE.formatted(readable(fish.category())));
        for (String line : fish.lore()) {
            lore.add(FLAVOUR_LORE.formatted(line));
        }
        List<String> loreLines = List.copyOf(lore);
        OptionalDouble saturation = fish.edible()
                ? OptionalDouble.of(fish.onEat().saturation())
                : OptionalDouble.empty();

        return new NexoItem() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public List<String> lore() {
                return loreLines;
            }

            @Override
            public OptionalDouble foodSaturation() {
                return saturation;
            }
        };
    }

    /** The item id for a {@code nexo:<id>} reference, or null for any other namespace. */
    static String nexoIdOf(String item) {
        int colon = item.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String namespace = item.substring(0, colon).toLowerCase(Locale.ROOT);
        return namespace.equals(NEXO_NAMESPACE) ? item.substring(colon + 1) : null;
    }

    /** Turns a category file name into something worth showing: deep_sea to Deep Sea. */
    private static String readable(String category) {
        StringBuilder text = new StringBuilder(category.length());
        for (String word : category.split("[_-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return text.isEmpty() ? category : text.toString();
    }
}
