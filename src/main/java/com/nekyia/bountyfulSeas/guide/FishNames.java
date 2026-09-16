package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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

    /** What each tier is worth, at a glance. */
    private static final Map<Rarity, String> TIER_COLOURS = new EnumMap<>(Map.of(
            Rarity.COMMON, "#C3CAD4",     // light slate grey
            Rarity.MISC, "#8B93A0",       // junk: the same grey, gone dull
            Rarity.UNCOMMON, "#8EE3A8",   // mint green
            Rarity.RARE, "#86C8F7",       // sky blue
            Rarity.EPIC, "#C9A2FF",       // lavender
            Rarity.LEGENDARY, "#FFC132",  // gold
            Rarity.TREASURE, "#FFE08A",   // pale gold
            Rarity.MYTHIC, "#FF6B6B",     // red
            Rarity.SIGNATURE, "#D48BFF"));// bright violet

    /**
     * The tiers whose names are recoloured wholesale.
     *
     * <p>Legendary and the rest keep whatever their definition writes - a legendary
     * is meant to look like itself. Their colours above are still used for the
     * stand-in shown before anyone has caught one.
     */
    private static final Set<Rarity> RECOLOURED =
            EnumSet.of(Rarity.COMMON, Rarity.MISC, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC);

    private FishNames() {
    }

    /** The name as MiniMessage, ready for anything that parses it - such as Nexo. */
    public static String miniMessage(Fish fish) {
        if (fish.rarity() == null || !RECOLOURED.contains(fish.rarity())) {
            return fish.name();
        }
        String colour = TIER_COLOURS.get(fish.rarity());
        // Stripped to plain text, then escaped, so a name that happens to contain a
        // bracket cannot be read back as a tag.
        String plain = MINI.escapeTags(MINI.stripTags(fish.name()));
        return "<color:" + colour + ">" + plain + "</color>";
    }

    /**
     * The stand-in for a fish nobody has caught yet, in its tier's colour.
     *
     * <p>The colour is the one thing worth giving away early: it says how hard the
     * catch will be without saying what it is.
     */
    public static Component unknown(Fish fish) {
        String colour = fish.rarity() == null ? null : TIER_COLOURS.get(fish.rarity());
        Component text = colour == null
                ? Component.text("???")
                : MINI.deserialize("<color:" + colour + ">???</color>");
        return text.decoration(TextDecoration.ITALIC, false);
    }

    /** The name as a component, not italic, for menus and chat. */
    public static Component of(Fish fish) {
        return MINI.deserialize(miniMessage(fish)).decoration(TextDecoration.ITALIC, false);
    }
}
