package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.api.StatsApi;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.guide.LeaderboardMenu;
import com.nekyia.bountyfulSeas.stats.Leaderboard;
import com.nekyia.bountyfulSeas.stats.PlayerTotal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Tells each player, once, who won last month's boards.
 *
 * <p>Which month a player has already been told about is kept on the player
 * itself rather than in a table: it is only ever read when that player joins.
 */
final class MonthlyWinners implements Listener {

    private static final int WINNERS = 3;

    private final Plugin plugin;
    private final Supplier<FishLibrary> fish;
    private final Supplier<StatsApi> stats;
    private final NamespacedKey seen;

    MonthlyWinners(Plugin plugin, Supplier<FishLibrary> fish, Supplier<StatsApi> stats) {
        this.plugin = plugin;
        this.fish = fish;
        this.stats = stats;
        this.seen = new NamespacedKey(plugin, "winners-seen");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String month = YearMonth.now().minusMonths(1).toString();
        if (month.equals(player.getPersistentDataContainer().get(seen, PersistentDataType.STRING))) {
            return;
        }

        Collection<Fish> library = fish.get().all();
        StatsApi api = stats.get();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Component> lines;
            try {
                lines = announcement(api.month(month), library);
            } catch (RuntimeException failure) {
                return;   // try again next join
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                lines.forEach(player::sendMessage);
                player.getPersistentDataContainer().set(seen, PersistentDataType.STRING, month);
            });
        });
    }

    private List<Component> announcement(List<PlayerTotal> rows, Collection<Fish> library) {
        List<Component> lines = new ArrayList<>();
        if (rows.isEmpty()) {
            return lines;
        }
        Set<String> fishIds = LeaderboardMenu.fishIds(library);
        Set<String> treasures = LeaderboardMenu.treasureIds(library);

        lines.add(Component.text("Last month's best anglers", NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(winners("Most catches", rows, id -> true));
        lines.add(winners("Most fish", rows, fishIds::contains));
        lines.add(winners("Most treasures", rows, treasures::contains));
        return lines;
    }

    private Component winners(String board, List<PlayerTotal> rows, Predicate<String> counted) {
        List<Leaderboard.Standing> ranked = Leaderboard.mostCaught(rows, counted);
        Component line = Component.text("  " + board + ": ", NamedTextColor.YELLOW);
        if (ranked.isEmpty()) {
            return line.append(Component.text("nobody", NamedTextColor.DARK_GRAY));
        }
        for (int index = 0; index < Math.min(WINNERS, ranked.size()); index++) {
            Leaderboard.Standing entry = ranked.get(index);
            String name = plugin.getServer().getOfflinePlayer(entry.player()).getName();
            line = line.append(Component.text((index > 0 ? ", " : "") + (index + 1) + ". "
                    + (name == null ? "?" : name) + " (" + Math.round(entry.value()) + ")",
                    index == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        }
        return line;
    }
}
