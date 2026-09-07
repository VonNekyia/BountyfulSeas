package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fishing.Catch;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        return Component.empty()
                .append(displayName(landed, stack))
                .append(SEPARATOR)
                .append(Component.text(format(landed.weight()) + " kg", NamedTextColor.WHITE))
                .append(SEPARATOR)
                .append(Component.text(format(landed.length()) + " cm", NamedTextColor.WHITE));
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
