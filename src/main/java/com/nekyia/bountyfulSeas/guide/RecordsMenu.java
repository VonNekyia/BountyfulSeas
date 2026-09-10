package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.FishRecord;
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
import java.util.Locale;

/**
 * The longest of every fish in one category, and where the viewer stands on it.
 *
 * <p>Everything shown here was fetched before the menu opened - one query for the
 * whole record board and one for the viewer's places, both cached - so populating
 * it touches nothing. A menu builds on the server thread, and a database call
 * there is a stall every viewer pays for.
 */
public final class RecordsMenu extends RoseGUI {

    private static final int[] FISH_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final String category;
    private final Collection<Fish> library;
    private final GuideStandings standings;
    private final FishIcons icons;
    private final Progress standing;

    public RecordsMenu(Player player, String category, Collection<Fish> library,
                       GuideStandings standings, FishIcons icons, Progress standing) {
        super(player, "bountyfulseas-records",
                Component.text(GuideText.readable(category) + " records",
                        NamedTextColor.GOLD, TextDecoration.BOLD), 5);
        this.category = category;
        this.library = library;
        this.standings = standings;
        this.icons = icons;
        this.standing = standing;
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        fillGui(new RoseItem.Builder()
                .material(Material.BLACK_STAINED_GLASS_PANE)
                .displayName(Component.empty())
                .build());

        List<Fish> fishes = GuideMenu.byCategory(library).getOrDefault(category, List.of());
        int index = 0;
        for (Fish fish : fishes) {
            if (index >= FISH_SLOTS.length) {
                break;
            }
            // An object has no length, so it has no record to hold either.
            if (!fish.isCatchMarker()) {
                addItem(FISH_SLOTS[index++], recordIcon(fish));
            }
        }

        addItem(40, new RoseItem.Builder()
                .material(Material.ARROW)
                .displayName(Component.text("Back", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .build()
                .onClick(click -> new GuideMenu(player, library, standings, icons, standing).open()));
    }

    private RoseItem recordIcon(Fish fish) {
        FishStats mine = standings.statsOf(fish.id());
        FishRecord record = standings.records().get(fish.id());
        boolean found = mine.caught();

        List<Component> lore = new ArrayList<>();

        if (record == null) {
            lore.add(GuideText.line("nobody has landed one yet", NamedTextColor.DARK_GRAY));
            lore.add(GuideText.blank());
            lore.add(GuideText.line("the first is the record", NamedTextColor.YELLOW));
        } else {
            lore.add(GuideText.line("record  " + GuideText.cm(record.length()), NamedTextColor.GOLD));
            lore.add(GuideText.line("held by " + standings.nameOf(record.holder()), NamedTextColor.GRAY));
            lore.add(GuideText.blank());

            if (!found) {
                lore.add(GuideText.line("you have not caught one", NamedTextColor.DARK_GRAY));
            } else {
                Integer place = standings.places().get(fish.id());
                lore.add(GuideText.line("yours  " + GuideText.cm(mine.longest()),
                        NamedTextColor.WHITE));
                lore.add(GuideText.line(place == null ? "unplaced" : ordinal(place) + " of "
                                + standings.anglersOn(fish.id()),
                        place != null && place == 1 ? NamedTextColor.GOLD : NamedTextColor.GRAY));

                double behind = record.length() - mine.longest();
                if (behind > 0) {
                    lore.add(GuideText.line(GuideText.cm(behind) + " off the record",
                            NamedTextColor.DARK_GRAY));
                }
            }
        }

        lore.add(GuideText.blank());
        lore.add(GuideText.line("the most it reaches  " + GuideText.cm(fish.maxLength()),
                NamedTextColor.DARK_GRAY));

        Component name = found
                ? MiniMessage.miniMessage().deserialize(fish.name()).decoration(TextDecoration.ITALIC, false)
                : Component.text("???", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

        RoseItem.Builder builder = new RoseItem.Builder();
        ItemStack icon = found ? icons.iconFor(fish) : null;
        if (icon != null) {
            builder.copyStack(icon);
        } else {
            builder.material(found ? Material.COD : Material.GRAY_DYE);
        }

        return builder.displayName(name).addLore(lore.toArray(new Component[0])).build();
    }

    /** 1st, 2nd, 3rd, 4th - the English rules, teens included. */
    static String ordinal(int place) {
        int tens = place % 100;
        String suffix = tens >= 11 && tens <= 13 ? "th" : switch (place % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return place + suffix.toLowerCase(Locale.ROOT);
    }
}
