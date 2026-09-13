package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.EnumMap;
import java.util.Map;

/**
 * How a fish's name is shown, everywhere it is shown.
 *
 * <p>Below legendary a name is coloured by its tier and nothing else: whatever
 * formatting the definition wrote is stripped. A rarity has to read at a glance,
 * which only works when every fish of a tier looks the same - one hand-picked
 * colour per fish would make the colour mean nothing.
 *
 * <p>Legendaries keep the two-colour gradient their definition gives them. That
 * is the one tier where each fish is meant to look like itself.
 *
 * <p>The colours are softened on purpose rather than Minecraft's named ones,
 * which are fully saturated and shout. Each still clears 9.8:1 against the item
 * tooltip's background, so softer costs nothing in legibility.
 *
 * <p>Kept in one place so an item in the inventory, a line in chat and an icon in
 * the guide cannot disagree about what a fish is called.
 */
public final class FishNames {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** The tiers that are coloured by rank. Anything not listed keeps its own formatting. */
    private static final Map<Rarity, String> TIER_COLOURS = new EnumMap<>(Map.of(
            Rarity.MISC, "#C3CAD4",       // light slate grey
            Rarity.UNCOMMON, "#8EE3A8",   // mint green
            Rarity.RARE, "#86C8F7",       // sky blue
            Rarity.EPIC, "#C9A2FF"));     // lavender

    private FishNames() {
    }

    /** The name as MiniMessage, ready for anything that parses it - such as Nexo. */
    public static String miniMessage(Fish fish) {
        String colour = fish.rarity() == null ? null : TIER_COLOURS.get(fish.rarity());
        if (colour == null) {
            return fish.name();
        }
        // Stripped to plain text, then escaped, so a name that happens to contain a
        // bracket cannot be read back as a tag.
        String plain = MINI.escapeTags(MINI.stripTags(fish.name()));
        return "<color:" + colour + ">" + plain + "</color>";
    }

    /** The name as a component, not italic, for menus and chat. */
    public static Component of(Fish fish) {
        return MINI.deserialize(miniMessage(fish)).decoration(TextDecoration.ITALIC, false);
    }
}
