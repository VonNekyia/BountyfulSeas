package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.stats.FishStats;
import com.nekyia.bountyfulSeas.level.Progress;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The guide's front page: one icon per category, showing how much of it is found.
 *
 * <p>Handed the totals it needs rather than looking them up. Menus are populated
 * on the server thread, and reading a player's totals can reach the database, so
 * the fetching is done before the menu is ever opened.
 */
public final class GuideMenu extends RoseGUI {

    private static final int[] CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final Collection<Fish> fishes;
    private final GuideStandings standings;
    private final FishIcons icons;

    /**
     * Where the viewer stands.
     *
     * <p>Shown on the summary and used to grey out what they cannot reach yet. It
     * is the only place a player sees their level at all - debug is for operators.
     */
    private final Progress standing;

    public GuideMenu(Player player, Collection<Fish> fishes,
                     GuideStandings standings, FishIcons icons, Progress standing) {
        super(player, "bountyfulseas-guide",
                Component.text("Fishing Guide", NamedTextColor.AQUA, TextDecoration.BOLD), 5);
        this.fishes = fishes;
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

        Map<String, List<Fish>> categories = byCategory(fishes);
        int index = 0;
        long allFish = 0;
        long allFound = 0;

        for (Map.Entry<String, List<Fish>> entry : categories.entrySet()) {
            if (index >= CATEGORY_SLOTS.length) {
                break;
            }
            List<Fish> inCategory = entry.getValue();
            long found = inCategory.stream()
                    .filter(fish -> stats(fish).caught())
                    .count();
            long catches = inCategory.stream()
                    .mapToLong(fish -> stats(fish).catches())
                    .sum();

            allFish += inCategory.size();
            allFound += found;

            addItem(CATEGORY_SLOTS[index++], categoryIcon(entry.getKey(), inCategory, found, catches));
        }

        addItem(38, levelIcon());
        addItem(40, summaryIcon(allFound, allFish));
    }

    /** The way through to what each level opens up. */
    private RoseItem levelIcon() {
        Map<Integer, List<Fish>> levels = LevelMenu.byLevel(fishes);
        long reached = levels.keySet().stream().filter(level -> level <= standing.level()).count();

        return new RoseItem.Builder()
                .material(Material.EXPERIENCE_BOTTLE)
                .displayName(Component.text("Angling Levels", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(GuideText.line("you are level " + standing.level(), NamedTextColor.GREEN),
                        GuideText.line(reached + " of " + levels.size() + " unlock levels reached",
                                NamedTextColor.GRAY),
                        GuideText.blank(),
                        GuideText.line("Click to see what opens where", NamedTextColor.YELLOW))
                .build()
                .onClick(click -> new LevelMenu(player, fishes, standings, icons, standing).open());
    }

    private RoseItem categoryIcon(String category, List<Fish> inCategory, long found, long catches) {
        boolean complete = found == inCategory.size();

        List<Component> lore = new ArrayList<>();
        lore.add(GuideText.bar(found, inCategory.size()));
        lore.add(GuideText.line(found + " of " + inCategory.size() + " discovered",
                complete ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(GuideText.line(catches + " caught in total", NamedTextColor.DARK_GRAY));
        lore.add(GuideText.blank());
        lore.add(GuideText.line("Click to open", NamedTextColor.YELLOW));
        lore.add(GuideText.line("Right-click for the records", NamedTextColor.GOLD));

        RoseItem item = new RoseItem.Builder()
                // A finished category is enchanted-looking; an empty one stays drab.
                .material(complete ? Material.HEART_OF_THE_SEA
                        : found > 0 ? Material.NAUTILUS_SHELL : Material.GRAY_DYE)
                .displayName(Component.text(GuideText.readable(category),
                        complete ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(lore.toArray(new Component[0]))
                .build();

        return item.onClick(click -> {
            if (click.isRightClick()) {
                new RecordsMenu(player, category, fishes, standings, icons, standing).open();
                return;
            }
            new CategoryMenu(player, category, fishes, standings, icons, standing).open();
        });
    }

    private RoseItem summaryIcon(long found, long total) {
        List<Component> lore = new ArrayList<>();
        lore.add(GuideText.bar(found, total));
        lore.add(GuideText.line(found + " of " + total + " fish discovered", NamedTextColor.GRAY));
        lore.add(GuideText.blank());

        lore.add(GuideText.line("Angling level " + standing.level(), NamedTextColor.GREEN));
        if (standing.capped()) {
            lore.add(GuideText.line("the highest there is", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(GuideText.bar(standing.intoLevel(), standing.span()));
            lore.add(GuideText.line(standing.intoLevel() + " / " + standing.span() + " xp"
                    + "   " + standing.remaining() + " to go", NamedTextColor.DARK_GRAY));
        }
        lore.add(GuideText.line(standing.experience() + " xp earned in all", NamedTextColor.DARK_GRAY));
        lore.add(GuideText.blank());
        lore.add(GuideText.line("Milestones are what pay for levels", NamedTextColor.DARK_GRAY));

        return new RoseItem.Builder()
                .material(Material.FISHING_ROD)
                .displayName(Component.text("Your Collection", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(lore.toArray(new Component[0]))
                .build();
    }

    private FishStats stats(Fish fish) {
        return standings.statsOf(fish.id());
    }

    static Map<String, List<Fish>> byCategory(Collection<Fish> fishes) {
        Map<String, List<Fish>> categories = new LinkedHashMap<>();
        for (Fish fish : fishes) {
            categories.computeIfAbsent(fish.category(), key -> new ArrayList<>()).add(fish);
        }
        return categories;
    }
}
