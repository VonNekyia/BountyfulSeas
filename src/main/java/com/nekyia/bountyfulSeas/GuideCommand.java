package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.api.StatsApi;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.guide.CategoryMenu;
import com.nekyia.bountyfulSeas.guide.FishIcons;
import com.nekyia.bountyfulSeas.guide.GuideMenu;
import com.nekyia.bountyfulSeas.guide.GuideStandings;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.FishRecord;
import com.nekyia.bountyfulSeas.stats.FishStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
    private final AnglerLevels levels;

    GuideCommand(Plugin plugin, Supplier<FishLibrary> fish, Supplier<StatsApi> stats,
                 AnglerLevels levels) {
        this.plugin = plugin;
        this.fish = fish;
        this.stats = stats;
        this.levels = levels;
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
            GuideStandings standings = gather(api, player.getUniqueId());

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Progress standing = levels.progressOf(player.getUniqueId());
                if (category == null) {
                    new GuideMenu(player, library, standings, ICONS, standing).open();
                } else {
                    new CategoryMenu(player, category.toLowerCase(Locale.ROOT),
                            library, standings, ICONS, standing).open();
                }
            });
        });
    }

    /**
     * Everything the guide will need, in three cached queries and off the server
     * thread.
     *
     * <p>Gathered here rather than as each screen asks for it, because a screen is
     * built on the server thread: a lookup there would be a stall, and clicking
     * through four categories would be four of them.
     */
    private GuideStandings gather(StatsApi api, UUID player) {
        try {
            Map<String, FishStats> caught = api.player(player);
            Map<String, FishRecord> records = api.records();
            Map<String, Integer> places = api.places(player);
            return new GuideStandings(caught, records, places, namesOf(records));
        } catch (RuntimeException failure) {
            // An unreachable database costs the numbers, not the guide.
            plugin.getLogger().warning("Guide opened without statistics: " + failure.getMessage());
            return GuideStandings.none();
        }
    }

    /**
     * Names for the record holders.
     *
     * <p>Read from the server's own player cache, which is a disk lookup rather
     * than a network one - and this runs off the server thread anyway. Anyone the
     * cache has never heard of is simply left out; the guide falls back to a short
     * form of their id rather than holding the menu up to ask Mojang.
     */
    private Map<UUID, String> namesOf(Map<String, FishRecord> records) {
        Map<UUID, String> names = new HashMap<>();
        for (FishRecord record : records.values()) {
            names.computeIfAbsent(record.holder(), holder -> {
                String name = plugin.getServer().getOfflinePlayer(holder).getName();
                return name == null ? holder.toString().substring(0, 8) : name;
            });
        }
        return names;
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
