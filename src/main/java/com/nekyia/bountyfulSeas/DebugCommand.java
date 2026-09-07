package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fishing.Chance;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Reports what can be caught where the player's bobber is sitting.
 *
 * <p>Answers the question a fish table actually raises: not "what is here" but
 * "why is what I expected not here". So it prints the spot as the selector sees
 * it, then the odds, and says plainly when there are none.
 */
final class DebugCommand {

    private final Supplier<FishLibrary> fish;
    private final Supplier<WaterMap> waterMap;

    DebugCommand(Supplier<FishLibrary> fish, Supplier<WaterMap> waterMap) {
        this.fish = fish;
        this.waterMap = waterMap;
    }

    /** Reports the spot under the player's bobber. */
    void run(Player player) {
        WaterMap map = waterMap.get();
        if (map == null) {
            player.sendMessage(error("No water map is loaded, so nothing can be looked up."));
            return;
        }

        FishHook hook = player.getFishHook();
        if (hook == null) {
            player.sendMessage(error("Cast your line first, then run this while the bobber sits in the water."));
            return;
        }

        Location at = hook.getLocation();
        WaterRegion region = map.regionAt(at.getBlockX(), at.getBlockZ());
        if (region == null) {
            player.sendMessage(error("The bobber is not in classified water, so vanilla fishing applies here."));
            player.sendMessage(detail("position", at.getBlockX() + ", " + at.getBlockZ()));
            return;
        }

        WaterSpot spot = WaterSpot.of(region, at.getWorld());

        player.sendMessage(Component.text("Fishing spot", NamedTextColor.AQUA));
        player.sendMessage(detail("position", at.getBlockX() + ", " + at.getBlockZ()));
        player.sendMessage(detail("region", "#" + region.id() + "  " + lower(region.kind())
                + "  " + region.columns() + " columns"
                + "  depth " + region.meanDepth() + "/" + region.maxDepth() + " blocks"));
        player.sendMessage(detail("reads as", lower(spot.waterType())
                + "  " + lower(spot.terrain())
                + "  " + lower(spot.vegetation())
                + "  " + lower(spot.depth())));
        player.sendMessage(detail("modifiers", spot.modifiers().isEmpty() ? "none" : lower(spot.modifiers())));
        player.sendMessage(detail("conditions", lower(spot.conditions())));

        List<Chance> chances = FishSelector.chances(fish.get().all(), spot);
        if (chances.isEmpty()) {
            player.sendMessage(error("Nothing lives here. Vanilla keeps the catch."));
            return;
        }

        player.sendMessage(Component.text("Chances", NamedTextColor.AQUA));
        for (Chance chance : chances) {
            player.sendMessage(Component.text("  ")
                    .append(Component.text(String.format(Locale.ROOT, "%5.1f%%", chance.percent()),
                            NamedTextColor.WHITE))
                    .append(Component.text("  " + chance.fish().id(), NamedTextColor.GRAY))
                    .append(Component.text("  weight " + chance.weight()
                                    + ", " + chance.fish().rarity().configName(),
                            NamedTextColor.DARK_GRAY)));
        }
    }

    private static Component detail(String label, String value) {
        return Component.text("  " + label + ": ", NamedTextColor.DARK_GRAY)
                .append(Component.text(value, NamedTextColor.GRAY));
    }

    private static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private static String lower(Object value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
