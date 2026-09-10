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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** One category: every fish in it, with its milestone and its records. */
public final class CategoryMenu extends RoseGUI {

    private static final int[] FISH_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final List<Fish> fishes;
    private final Map<String, FishStats> caught;
    private final FishIcons icons;
    private final Progress standing;

    /** The whole library, kept so Back can rebuild the full overview. */
    private final Collection<Fish> library;

    public CategoryMenu(Player player, String category, Collection<Fish> library,
                        Map<String, FishStats> caught, FishIcons icons, Progress standing) {
        super(player, "bountyfulseas-category",
                Component.text(GuideText.readable(category), NamedTextColor.AQUA, TextDecoration.BOLD), 5);
        this.library = library;
        this.fishes = GuideMenu.byCategory(library).getOrDefault(category, List.of());
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
        for (Fish fish : fishes) {
            if (index >= FISH_SLOTS.length) {
                break;
            }
            addItem(FISH_SLOTS[index++], fishIcon(fish));
        }

        addItem(40, new RoseItem.Builder()
                .material(Material.ARROW)
                .displayName(Component.text("Back", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .build()
                .onClick(click -> new GuideMenu(player, library, caught, icons, standing).open()));
    }

    private RoseItem fishIcon(Fish fish) {
        FishStats mine = caught.getOrDefault(fish.id(), FishStats.none(fish.id()));
        boolean found = mine.caught();
        boolean locked = fish.level() > standing.level();
        List<Component> lore = new ArrayList<>();

        // Said first, because it is the reason nothing else on the icon has filled
        // in yet - and it is the one line that says what to go and do about it.
        if (locked) {
            lore.add(GuideText.line("locked  needs angling level " + fish.level()
                    + "   you are " + standing.level(), NamedTextColor.RED));
            lore.add(GuideText.blank());
        }

        if (found) {
            lore.add(GuideText.milestone(mine));
        }

        // Trash and treasure have no length, so the line would be a row of zeroes.
        if (!fish.isCatchMarker()) {
            if (found) {
                lore.add(GuideText.blank());
                // Best against the most this fish reaches, so the gap left is visible.
                lore.add(GuideText.line("length  " + GuideText.cm(mine.longest())
                        + " / " + GuideText.cm(fish.maxLength()), NamedTextColor.GRAY));
            } else {
                lore.add(GuideText.line("length  ??? / " + GuideText.cm(fish.maxLength()),
                        NamedTextColor.DARK_GRAY));
            }
        }

        lore.add(GuideText.blank());
        lore.add(GuideText.line("conditions: " + GuideText.requirements(fish), NamedTextColor.DARK_AQUA));

        // Flavour text is part of the reward for finding the fish, so an unfound
        // one gives nothing away.
        if (found && !fish.lore().isEmpty()) {
            lore.add(GuideText.blank());
            for (String flavour : fish.lore()) {
                lore.add(Component.text(flavour, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true));
            }
        }

        Component name = found
                ? MiniMessage.miniMessage().deserialize(fish.name())
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text(locked ? "Level " + fish.level() : "???", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false);

        RoseItem.Builder builder = new RoseItem.Builder();
        ItemStack icon = found ? icons.iconFor(fish) : null;
        if (icon != null) {
            builder.copyStack(icon);
        } else {
            builder.material(found ? Material.COD : locked ? Material.BARRIER : Material.GRAY_DYE);
        }

        return builder.displayName(name).addLore(lore.toArray(new Component[0])).build();
    }
}
