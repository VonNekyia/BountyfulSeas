package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.Rarity;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.Leaderboard;
import com.nekyia.bountyfulSeas.stats.Leaderboard.Standing;
import com.nekyia.bountyfulSeas.stats.PlayerTotal;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The leaderboards: who leads, and where the viewer stands.
 *
 * <p>Every board is ranked from rows the guide fetched before it opened, so
 * nothing here touches the database. The longest of each single fish lives in the
 * records screen of its category, reached from the category page here.
 */
public final class LeaderboardMenu extends RoseGUI {

    private static final int SHOWN = 10;
    private static final int WINNERS = 3;

    private static final int[] CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final Collection<Fish> library;
    private final GuideStandings standings;
    private final FishIcons icons;
    private final Progress standing;
    private final boolean categories;

    public LeaderboardMenu(Player player, Collection<Fish> library, GuideStandings standings,
                           FishIcons icons, Progress standing, boolean categories) {
        super(player, "bountyfulseas-leaderboards",
                Component.text(categories ? "Combined length by category" : "Leaderboards",
                        NamedTextColor.GOLD, TextDecoration.BOLD), 5);
        this.library = library;
        this.standings = standings;
        this.icons = icons;
        this.standing = standing;
        this.categories = categories;
    }

    /** Ids of the entries that carry a length: fish, not objects. */
    public static Set<String> fishIds(Collection<Fish> library) {
        return library.stream().filter(fish -> !fish.isCatchMarker()).map(Fish::id).collect(Collectors.toSet());
    }

    /** Ids of the treasures. */
    public static Set<String> treasureIds(Collection<Fish> library) {
        return library.stream().filter(fish -> fish.rarity() == Rarity.TREASURE).map(Fish::id)
                .collect(Collectors.toSet());
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        fillGui(new RoseItem.Builder()
                .material(Material.BLACK_STAINED_GLASS_PANE)
                .displayName(Component.empty())
                .build());
        if (categories) {
            categoryPage();
        } else {
            mainPage();
        }
    }

    private void mainPage() {
        Set<String> fish = fishIds(library);
        Set<String> treasures = treasureIds(library);
        List<PlayerTotal> all = standings.everything();
        Predicate<String> anything = id -> true;

        addItem(11, board(Material.NAUTILUS_SHELL, "Longest combined",
                "your best of every fish, added up",
                Leaderboard.combinedLongest(all, fish::contains), cm(), null));

        addItem(13, new RoseItem.Builder()
                .material(Material.BOOKSHELF)
                .displayName(title("Combined length by category"))
                .addLore(GuideText.line("the same, one category at a time", NamedTextColor.GRAY),
                        GuideText.line("and the longest of every single fish", NamedTextColor.GRAY),
                        GuideText.blank(),
                        GuideText.line("Click to open", NamedTextColor.YELLOW))
                .build()
                .onClick(click -> new LeaderboardMenu(player, library, standings, icons, standing, true).open()));

        addItem(15, board(Material.AMETHYST_SHARD, "Rarest catch",
                "the least likely single catch",
                Leaderboard.rarest(all), rarest(), null));

        addItem(20, board(Material.FISHING_ROD, "Most catches", "everything that came up",
                Leaderboard.mostCaught(all, anything), count(), null));
        addItem(22, board(Material.COD, "Most fish", "fish only, no junk or treasure",
                Leaderboard.mostCaught(all, fish::contains), count(), null));
        addItem(24, board(Material.CHEST, "Most treasures", "treasure only",
                Leaderboard.mostCaught(all, treasures::contains), count(), null));

        addItem(29, board(Material.CLOCK, "Most catches this month", "resets on the 1st",
                Leaderboard.mostCaught(standings.thisMonth(), anything), count(),
                Leaderboard.mostCaught(standings.lastMonth(), anything)));
        addItem(31, board(Material.SALMON, "Most fish this month", "resets on the 1st",
                Leaderboard.mostCaught(standings.thisMonth(), fish::contains), count(),
                Leaderboard.mostCaught(standings.lastMonth(), fish::contains)));
        addItem(33, board(Material.ENDER_CHEST, "Most treasures this month", "resets on the 1st",
                Leaderboard.mostCaught(standings.thisMonth(), treasures::contains), count(),
                Leaderboard.mostCaught(standings.lastMonth(), treasures::contains)));

        addItem(40, back().onClick(click -> new GuideMenu(player, library, standings, icons, standing).open()));
    }

    private void categoryPage() {
        int index = 0;
        for (Map.Entry<String, List<Fish>> entry : GuideMenu.byCategory(library).entrySet()) {
            Set<String> ids = entry.getValue().stream().filter(fish -> !fish.isCatchMarker())
                    .map(Fish::id).collect(Collectors.toSet());
            if (ids.isEmpty() || index >= CATEGORY_SLOTS.length) {
                continue;   // junk and treasure have no length to add up
            }
            String category = entry.getKey();
            RoseItem item = board(Material.NAUTILUS_SHELL, GuideText.readable(category),
                    "best of each fish here, added up - click for each fish",
                    Leaderboard.combinedLongest(standings.everything(), ids::contains), cm(), null)
                    .onClick(click -> new RecordsMenu(player, category, library, standings, icons, standing).open());
            addItem(CATEGORY_SLOTS[index++], item);
        }
        addItem(40, back().onClick(click -> new LeaderboardMenu(player, library, standings, icons, standing, false).open()));
    }

    /**
     * One board as an icon: the top ten, the viewer's place, and for a monthly board
     * last month's winners.
     */
    private RoseItem board(Material material, String name, String about, List<Standing> board,
                           Function<Standing, Component> value, List<Standing> lastMonth) {
        List<Component> lore = new ArrayList<>();
        lore.add(GuideText.line(about, NamedTextColor.DARK_GRAY));
        lore.add(GuideText.blank());

        if (board.isEmpty()) {
            lore.add(GuideText.line("nobody is on it yet", NamedTextColor.GRAY));
        }
        for (int index = 0; index < Math.min(SHOWN, board.size()); index++) {
            lore.add(place(index + 1, board.get(index), value));
        }

        lore.add(GuideText.blank());
        int mine = Leaderboard.placeOf(board, player.getUniqueId());
        if (mine == 0) {
            lore.add(GuideText.line("you are not on it yet", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(GuideText.line("you  " + RecordsMenu.ordinal(mine) + " of " + board.size(),
                    mine == 1 ? NamedTextColor.GOLD : NamedTextColor.WHITE)
                    .append(Component.text("  ")).append(value.apply(board.get(mine - 1))));
        }

        if (lastMonth != null && !lastMonth.isEmpty()) {
            lore.add(GuideText.blank());
            lore.add(GuideText.line("Last month", NamedTextColor.YELLOW));
            for (int index = 0; index < Math.min(WINNERS, lastMonth.size()); index++) {
                lore.add(place(index + 1, lastMonth.get(index), value));
            }
        }

        return new RoseItem.Builder()
                .material(material)
                .displayName(title(name))
                .addLore(lore.toArray(new Component[0]))
                .build();
    }

    private Component place(int place, Standing entry, Function<Standing, Component> value) {
        NamedTextColor color = switch (place) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.WHITE;
            case 3 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GRAY;
        };
        return GuideText.line(place + ". " + standings.nameOf(entry.player()) + "  ", color)
                .append(value.apply(entry));
    }

    private static Function<Standing, Component> cm() {
        return entry -> GuideText.line(GuideText.cm(entry.value()), NamedTextColor.AQUA);
    }

    private static Function<Standing, Component> count() {
        return entry -> GuideText.line(String.valueOf(Math.round(entry.value())), NamedTextColor.AQUA);
    }

    private Function<Standing, Component> rarest() {
        Map<String, Fish> byId = library.stream().collect(Collectors.toMap(Fish::id, fish -> fish, (a, b) -> a));
        return entry -> {
            Fish fish = byId.get(entry.detail());
            Component name = fish == null ? GuideText.line(entry.detail(), NamedTextColor.GRAY) : FishNames.of(fish);
            return name.append(GuideText.line("  " + Leaderboard.odds(entry.value()), NamedTextColor.LIGHT_PURPLE));
        };
    }

    private static Component title(String text) {
        return Component.text(text, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
    }

    private static RoseItem back() {
        return new RoseItem.Builder()
                .material(Material.ARROW)
                .displayName(Component.text("Back", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                .build();
    }
}
