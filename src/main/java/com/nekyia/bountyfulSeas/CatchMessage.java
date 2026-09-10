package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fishing.Catch;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.CatchOutcome;
import com.nekyia.bountyfulSeas.stats.Milestone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** The line a player sees when something comes out of the water. */
final class CatchMessage {

    private static final Component SEPARATOR =
            Component.text("  ·  ", NamedTextColor.DARK_GRAY);

    private CatchMessage() {
    }

    /**
     * Reads the name off the item rather than the fish definition.
     *
     * <p>The definition holds MiniMessage source; the item has it already parsed,
     * with whatever Nexo made of it. Taking the item's name means chat and the
     * tooltip cannot disagree about what the fish is called.
     */
    static Component of(Catch landed, ItemStack stack) {
        Component name = Component.empty().append(displayName(landed, stack));

        // Junk and treasure are objects, not fish. Printing 0.00 cm for a boot
        // would be stating a measurement nobody took.
        if (landed.fish().isCatchMarker()) {
            return name;
        }
        return name.append(SEPARATOR)
                .append(Component.text(format(landed.length()) + " cm", NamedTextColor.WHITE));
    }

    /**
     * The line shown when a catch is the longest of its kind.
     *
     * <p>A server record says so and nothing about the player's own, because beating
     * everyone means beating yourself too and saying both would be saying it twice.
     *
     * <p>Never reached by junk or treasure: they carry no length, so their previous
     * best is always zero and zero is never beaten.
     */
    static Component record(Fish fish, double length, CatchOutcome outcome) {
        boolean server = outcome.serverRecord();
        NamedTextColor accent = server ? NamedTextColor.GOLD : NamedTextColor.AQUA;
        String title = server ? "Server record" : "Personal best";
        double beaten = server ? outcome.beatenServerBest() : outcome.beatenOwnBest();

        return Component.text("★ ", accent)
                .append(Component.text(title, accent, TextDecoration.BOLD))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(MiniMessage.miniMessage().deserialize(fish.name()))
                .append(SEPARATOR)
                .append(Component.text(format(length) + " cm", NamedTextColor.WHITE))
                .append(Component.text("  beats " + format(beaten) + " cm", NamedTextColor.DARK_GRAY));
    }

    /**
     * The line shown when a catch lands exactly on a milestone.
     *
     * <p>Only on the crossing itself, not on every catch past it, so the numeral
     * stays worth seeing.
     */
    static Component milestone(Fish fish, Milestone earned, long catches) {
        return Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("Milestone ", NamedTextColor.YELLOW))
                .append(Component.text(earned.numeral(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(MiniMessage.miniMessage().deserialize(fish.name()))
                .append(Component.text("  " + catches + " caught", NamedTextColor.DARK_GRAY));
    }

    /**
     * The line shown when a milestone pushes a player up a level.
     *
     * <p>Says what the level opens rather than only what it is, because a number
     * on its own does not tell anybody why they should care.
     */
    static Component levelUp(Progress reached) {
        Component line = Component.text("⬆ ", NamedTextColor.GREEN)
                .append(Component.text("Angling level ", NamedTextColor.GREEN))
                .append(Component.text(reached.level(), NamedTextColor.GREEN, TextDecoration.BOLD));

        if (reached.capped()) {
            return line.append(Component.text("  the highest there is", NamedTextColor.DARK_GRAY));
        }
        return line.append(Component.text("  " + reached.remaining() + " xp to the next",
                NamedTextColor.DARK_GRAY));
    }

    private static Component displayName(Catch landed, ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            Component name = stack.getItemMeta().displayName();
            if (name != null) {
                return name;
            }
        }
        return MiniMessage.miniMessage().deserialize(landed.fish().name());
    }

    /** Two decimals, dot separator, so the number reads the same in every locale. */
    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
