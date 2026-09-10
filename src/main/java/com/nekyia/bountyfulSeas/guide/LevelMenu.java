package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.FishStats;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What each angling level opens up.
 *
 * <p>The one place the level is a goal rather than a number: every level anything
 * is locked behind, in order, with what it lets through and how far off it is.
 *
 * <p>A fish that has not been caught yet still reads as {@code ???}, exactly as it
 * does everywhere else. Knowing there are two more waiting at level 25 is a reason
 * to fish; being told what they are would spend the discovery early.
 */
public final class LevelMenu extends RoseGUI {

    private static final int[] LEVEL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final Collection<Fish> library;
    private final Map<String, FishStats> caught;
    private final FishIcons icons;
    private final Progress standing;

    public LevelMenu(Player player, Collection<Fish> library,
                     Map<String, FishStats> caught, FishIcons icons, Progress standing) {
        super(player, "bountyfulseas-levels",
                Component.text("Angling Levels", NamedTextColor.AQUA, TextDecoration.BOLD), 5);
        this.library = library;
        this.caught = caught;
        this.icons = icons;
        this.standing = standing;
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        fillGui(new RoseItem.Builder()
                .material(Material.BLACK_STAINED_GLASS_PANE)
                .displayName(Component.empty())
                .build());

        int index = 0;
        for (Map.Entry<Integer, List<Fish>> entry : byLevel(library).entrySet()) {
            if (index >= LEVEL_SLOTS.length) {
                break;
            }
            addItem(LEVEL_SLOTS[index++], levelIcon(entry.getKey(), entry.getValue()));
        }

        addItem(40, new RoseItem.Builder()
                .material(Material.ARROW)
                .displayName(Component.text("Back", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .build()
                .onClick(click -> new GuideMenu(player, library, caught, icons, standing).open()));
    }

    /** Every level something is locked behind, lowest first, with what it opens. */
    static Map<Integer, List<Fish>> byLevel(Collection<Fish> fishes) {
        Map<Integer, List<Fish>> levels = new TreeMap<>();
        for (Fish fish : fishes) {
            levels.computeIfAbsent(fish.level(), level -> new ArrayList<>()).add(fish);
        }
        return new LinkedHashMap<>(levels);
    }

    private RoseItem levelIcon(int level, List<Fish> opens) {
        boolean reached = standing.level() >= level;

        List<Component> lore = new ArrayList<>();
        lore.add(GuideText.line(reached ? "reached" : "not yet - you are level " + standing.level(),
                reached ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(GuideText.blank());
        lore.add(GuideText.line(opens.size() == 1 ? "opens 1 catch" : "opens " + opens.size() + " catches",
                NamedTextColor.GRAY));

        for (Fish fish : opens) {
            boolean found = caught.getOrDefault(fish.id(), FishStats.none(fish.id())).caught();
            Component name = found
                    ? MiniMessage.miniMessage().deserialize(fish.name())
                    : Component.text("???", NamedTextColor.DARK_GRAY);
            lore.add(Component.text("  ", NamedTextColor.DARK_GRAY)
                    .append(name)
                    .append(Component.text("  " + fish.rarity().configName(), NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }

        return new RoseItem.Builder()
                // Reached levels are lit; the next one along is the one to aim at.
                .material(reached ? Material.LIME_DYE
                        : standing.level() + 1 == level ? Material.YELLOW_DYE : Material.GRAY_DYE)
                .displayName(Component.text("Level " + level,
                                reached ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(lore.toArray(new Component[0]))
                .build();
    }
}
