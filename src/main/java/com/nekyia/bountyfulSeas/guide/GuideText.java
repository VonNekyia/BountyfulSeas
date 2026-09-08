package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.stats.FishStats;
import com.nekyia.bountyfulSeas.stats.Milestone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import com.nekyia.bountyfulSeas.fish.ConfigValue;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.Modifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The wording and formatting the guide menus share. */
final class GuideText {

    private static final int BAR_WIDTH = 20;

    private GuideText() {
    }

    /** Lore is italic purple by default, so every line turns that off. */
    static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    static Component blank() {
        return Component.empty().decoration(TextDecoration.ITALIC, false);
    }

    /** A filled bar, used for how much of a category has been found. */
    static Component bar(long done, long total) {
        int filled = total <= 0 ? 0 : (int) Math.round((double) done / total * BAR_WIDTH);
        return Component.text("\u25AC".repeat(filled), NamedTextColor.AQUA)
                .append(Component.text("\u25AC".repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * The milestone line: the numeral reached, and how far to the next one.
     *
     * <p>Shown as Roman numerals I to X rather than the raw count, so standing
     * reads at a glance.
     */
    static Component milestone(FishStats stats) {
        Milestone reached = Milestone.reached(stats.catches());
        Milestone next = Milestone.next(stats.catches());

        Component numeral = Component.text(
                reached == null ? "-" : reached.numeral(),
                reached == null ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD);

        Component progress = next == null
                ? Component.text("  maxed", NamedTextColor.GREEN)
                : Component.text("  " + stats.catches() + " / " + next.required()
                        + " to " + next.numeral(), NamedTextColor.DARK_GRAY);

        return Component.text("Milestone ", NamedTextColor.GRAY)
                .append(numeral)
                .append(progress)
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Everything a spot must satisfy for this fish to bite, on one line.
     *
     * <p>Empty trait lists place no restriction, so they are simply left out - the
     * line lists what is required, not every axis that exists.
     */
    static String requirements(Fish fish) {
        List<String> parts = new ArrayList<>();
        add(parts, fish.waterTypes());
        add(parts, fish.terrains());
        add(parts, fish.vegetations());
        add(parts, fish.depths());
        add(parts, fish.modifiers().stream()
                .filter(Modifier::describesWater)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class))));
        add(parts, fish.conditions());
        if (fish.baitLocked()) {
            parts.add("bait only");
        }
        return parts.isEmpty() ? "anywhere, anytime" : String.join(", ", parts);
    }

    private static <E extends ConfigValue> void add(List<String> parts, Set<E> values) {
        if (values.isEmpty()) {
            return;
        }
        // Alternatives within one axis, so they read as a choice rather than a list.
        parts.add(values.stream().map(ConfigValue::configName).collect(Collectors.joining("/")));
    }


    static String cm(double value) {
        return String.format(Locale.ROOT, "%.1f cm", value);
    }

    /** deep_sea becomes Deep Sea. */
    static String readable(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String word : text.split("[_-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? text : out.toString();
    }
}
