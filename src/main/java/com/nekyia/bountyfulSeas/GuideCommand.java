package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.api.StatsApi;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.guide.CategoryMenu;
import com.nekyia.bountyfulSeas.guide.FishIcons;
import com.nekyia.bountyfulSeas.guide.GuideMenu;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import com.nekyia.bountyfulSeas.stats.FishStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Opens the fishing guide.
 *
 * <p>The totals are fetched off the server thread and the menu is opened with them
 * already in hand. A menu populates itself on the main thread, so anything it had
 * to look up would be a database call the server waits on.
 */
final class GuideCommand {

    /** Fish icons come from Nexo, or fall back to a plain material when it cannot. */
    private static final FishIcons ICONS = fish -> {
        String id = FishNexoItems.nexoIdOf(fish.item());
        return id == null ? null : NexoItemFactory.create(id);
    };

    private final Plugin plugin;
    private final Supplier<FishLibrary> fish;
    private final Supplier<StatsApi> stats;

    GuideCommand(Plugin plugin, Supplier<FishLibrary> fish, Supplier<StatsApi> stats) {
        this.plugin = plugin;
        this.fish = fish;
        this.stats = stats;
    }

    /** The overview, or one category when named. */
    void run(Player player, String category) {
        Collection<Fish> library = fish.get().all();
        if (library.isEmpty()) {
            player.sendMessage(Component.text("No fish are defined yet.", NamedTextColor.RED));
            return;
        }

        StatsApi api = stats.get();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, FishStats> caught;
            try {
                caught = api.player(player.getUniqueId());
            } catch (RuntimeException failure) {
                // An unreachable database costs the numbers, not the guide.
                caught = Map.of();
                plugin.getLogger().warning("Guide opened without statistics: " + failure.getMessage());
            }

            Map<String, FishStats> totals = caught;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (category == null) {
                    new GuideMenu(player, library, totals, ICONS).open();
                } else {
                    new CategoryMenu(player, category.toLowerCase(java.util.Locale.ROOT),
                            library, totals, ICONS).open();
                }
            });
        });
    }

    /** Category names for tab completion. */
    List<String> categories() {
        return fish.get().all().stream()
                .map(Fish::category)
                .distinct()
                .sorted()
                .toList();
    }
}
