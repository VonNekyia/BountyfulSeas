package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.nexo.NexoItem;
import com.nekyia.bountyfulSeas.nexo.NexoItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Who gets what when a fished-up boss dies.
 *
 * <p>The angler it bit rolls for each of its drops at that drop's chance -
 * whoever lands the last blow - and gets the full experience. Everyone else who
 * fought it shares in the loot: for every drop they roll on their own at
 * 1/{@value #SHARE} of the angler's chance, and win the same stack the angler
 * was offered; they get 1/{@value #SHARE} of the experience as well. One rule for
 * every boss. Each helper hears what they won, or that it was nothing.
 * MythicMobs drops nothing itself; the loot is handed out here because only this
 * plugin knows whose line it was.
 *
 * <p>Loot that cannot be handed over - the angler is offline or lying dead - waits
 * in {@code <boss>-loot.yml} and arrives at their next login or respawn.
 */
final class BossLoot implements Listener, Runnable {

    /**
     * One thing a boss can drop.
     *
     * @param stack  makes the stack, amount rolled; null when the item does not exist
     * @param chance the angler's chance at it, 0 to 1
     */
    record Drop(Supplier<ItemStack> stack, double chance) {

        static Drop of(Material material, int least, int most, double chance) {
            return new Drop(() -> new ItemStack(material,
                    ThreadLocalRandom.current().nextInt(least, most + 1)), chance);
        }

        static Drop nexo(String id, int least, int most, double chance) {
            return new Drop(() -> {
                ItemStack stack = NexoItemFactory.create(id);
                if (stack != null) {
                    stack.setAmount(ThreadLocalRandom.current().nextInt(least, most + 1));
                }
                return stack;
            }, chance);
        }
    }

    /**
     * One boss as this class sees it.
     *
     * @param key       file and log name, e.g. {@code kraken}
     * @param headTag   the scoreboard tag its MythicMobs skills put on the mob that dies
     * @param name      how messages name it, with article: "Der Kraken"
     * @param trophyId  the Nexo id of the trophy, dropped at the configured trophy-chance
     * @param trophyName, trophyLore  how the trophy item looks
     * @param drops     everything else it drops
     */
    record Boss(String key, String headTag, String name, String trophyId, String trophyName,
                List<String> trophyLore, List<Drop> drops) {

        /** The trophy, as an item Nexo can be given. */
        NexoItem trophyItem() {
            return new NexoItem() {
                @Override
                public String id() {
                    return trophyId;
                }

                @Override
                public String file() {
                    return "misc";
                }

                @Override
                public String displayName() {
                    return trophyName;
                }

                @Override
                public List<String> lore() {
                    return trophyLore;
                }

                @Override
                public java.util.OptionalDouble foodSaturation() {
                    return java.util.OptionalDouble.empty();
                }
            };
        }
    }

    static final Boss KRAKEN = new Boss("kraken", "kraken_kopf", "Der Kraken",
            "kraken_eye", "<#5ad1c8>Auge des Kraken",
            List.of("<!italic><dark_gray>Trophäe", "<italic><gray>Es blinzelt noch."),
            List.of(
                    Drop.of(Material.PRISMARINE_CRYSTALS, 8, 16, 1),
                    Drop.of(Material.PRISMARINE_SHARD, 8, 16, 1),
                    Drop.of(Material.NAUTILUS_SHELL, 1, 3, 1),
                    Drop.of(Material.HEART_OF_THE_SEA, 1, 1, 0.25)));

    // ponytail: placeholder spoils until the level/boss rewards are decided.
    static final Boss NESSY = new Boss("nessy", "nessy", "Nessy",
            "nessy_photo", "<#9fd27a>Verschwommenes Foto von Nessy",
            List.of("<!italic><dark_gray>Trophäe", "<italic><gray>Der Beweis! ... irgendwie."),
            List.of(
                    Drop.of(Material.TURTLE_SCUTE, 2, 4, 1),
                    Drop.of(Material.LILY_PAD, 4, 8, 1),
                    Drop.of(Material.NAME_TAG, 1, 1, 0.25),
                    Drop.nexo("earthworm", 4, 8, 1),
                    Drop.nexo("beech_worm", 4, 8, 1),
                    Drop.nexo("rice_beetle", 4, 8, 1),
                    Drop.nexo("field_beetle", 4, 8, 1)));

    /** The section of the loot file that remembers whose boss is up right now. */
    private static final String ACTIVE = "active";

    /** A helper's share of everything: their chance at each drop, and the experience, is the angler's / 25. */
    private static final int SHARE = 25;
    /** How close counts as fighting it, in blocks. */
    private static final double HELPER_RADIUS = 24;
    /** How often helpers are noted, in ticks. */
    static final long PERIOD = 40;
    private static final int EXPERIENCE = 600;

    private final Plugin plugin;
    private final Boss boss;
    private final DoubleSupplier trophyChance;
    private final File pendingFile;
    private final Map<UUID, UUID> catcherByHead = new HashMap<>();
    private final Map<UUID, Set<UUID>> helpersByHead = new HashMap<>();

    BossLoot(Plugin plugin, Boss boss, DoubleSupplier trophyChance) {
        this.plugin = plugin;
        this.boss = boss;
        this.trophyChance = trophyChance;
        this.pendingFile = new File(plugin.getDataFolder(), boss.key() + "-loot.yml");
    }

    Boss boss() {
        return boss;
    }

    /** Forgets every boss of this kind that is up, without telling anyone it got away. */
    void forgetAll() {
        catcherByHead.clear();
        helpersByHead.clear();
        YamlConfiguration pending = YamlConfiguration.loadConfiguration(pendingFile);
        pending.set(ACTIVE, null);
        save(pending);
    }

    /** Notes whose line the boss that is about to come up at {@code at} took. */
    void spawned(Location at, UUID catcher) {
        // A tick later: the mob carries its tag only once its spawn skills have run.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity entity : at.getWorld().getNearbyEntities(at, 8, 8, 8)) {
                if (entity.getScoreboardTags().contains(boss.headTag())) {
                    catcherByHead.put(entity.getUniqueId(), catcher);
                    helpersByHead.put(entity.getUniqueId(), new HashSet<>());
                    YamlConfiguration pending = YamlConfiguration.loadConfiguration(pendingFile);
                    pending.set(ACTIVE + "." + entity.getUniqueId(), catcher.toString());
                    save(pending);
                    return;
                }
            }
        }, 2);
    }

    /**
     * Bosses that were up when the server stopped are gone now - MythicMobs removes
     * them as their chunk loads - so their anglers are told they got away.
     */
    void escapedWhileAway() {
        YamlConfiguration pending = YamlConfiguration.loadConfiguration(pendingFile);
        if (pending.getConfigurationSection(ACTIVE) == null) {
            return;
        }
        for (String head : pending.getConfigurationSection(ACTIVE).getKeys(false)) {
            String catcher = pending.getString(ACTIVE + "." + head);
            if (catcher != null) {
                escaped(UUID.fromString(catcher));
            }
        }
        YamlConfiguration after = YamlConfiguration.loadConfiguration(pendingFile);
        after.set(ACTIVE, null);
        save(after);
    }

    /** Notes everyone fighting the boss; runs every {@link #PERIOD} ticks. */
    @Override
    public void run() {
        helpersByHead.entrySet().removeIf(entry -> {
            Entity head = Bukkit.getEntity(entry.getKey());
            if (head == null || !head.isValid()) {
                // Gone without dying: it dived for good, or its chunk was reloaded.
                UUID catcher = catcherByHead.remove(entry.getKey());
                forget(entry.getKey());
                if (catcher != null) {
                    escaped(catcher);
                }
                return true;
            }
            for (Player player : head.getLocation().getNearbyPlayers(HELPER_RADIUS)) {
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    entry.getValue().add(player.getUniqueId());
                }
            }
            return false;
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity head = event.getEntity();
        if (!head.getScoreboardTags().contains(boss.headTag())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        UUID catcher = catcherByHead.remove(head.getUniqueId());
        Set<UUID> helpers = helpersByHead.getOrDefault(head.getUniqueId(), Set.of());
        helpersByHead.remove(head.getUniqueId());
        forget(head.getUniqueId());
        if (catcher == null) {
            // Spawned by hand: whoever killed it stands in for the angler.
            Player killer = event.getEntity().getKiller();
            if (killer == null) {
                return;
            }
            catcher = killer.getUniqueId();
        }

        // Each drop's stack is made once, so everyone who wins it wins the same amount.
        List<Drop> drops = new ArrayList<>(boss.drops());
        drops.add(new Drop(() -> NexoItemFactory.create(boss.trophyId()), trophyChance.getAsDouble()));
        List<Map.Entry<ItemStack, Double>> offered = new ArrayList<>();
        for (Drop drop : drops) {
            ItemStack stack = drop.stack().get();
            if (stack != null) {
                offered.add(Map.entry(stack, drop.chance()));
            }
        }

        deliver(catcher, rolled(offered, 1), EXPERIENCE,
                boss.name() + " ist besiegt - die Beute gehört dir!");
        for (UUID helper : helpers) {
            if (helper.equals(catcher)) {
                continue;
            }
            // Every helper hears how their share went, a win or not.
            List<ItemStack> won = rolled(offered, SHARE);
            String experience = " (+" + EXPERIENCE / SHARE + " Erfahrung)";
            deliver(helper, won, EXPERIENCE / SHARE, won.isEmpty()
                    ? "Lootshare: Diesmal ging nichts an dich." + experience
                    : "Lootshare: " + describe(won) + experience);
        }
    }

    /** One roll per stack at its chance divided by {@code share}; the winners, copied. */
    static List<ItemStack> rolled(List<Map.Entry<ItemStack, Double>> offered, int share) {
        List<ItemStack> won = new ArrayList<>();
        for (Map.Entry<ItemStack, Double> offer : offered) {
            if (ThreadLocalRandom.current().nextDouble() < offer.getValue() / share) {
                won.add(offer.getKey().clone());
            }
        }
        return won;
    }

    private static String describe(List<ItemStack> items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) {
            parts.add(item.getAmount() + "x "
                    + PlainTextComponentSerializer.plainText().serialize(item.effectiveName()));
        }
        return String.join(", ", parts);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handOverWaiting(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> handOverWaiting(player));
    }

    /** Tells the angler their boss got away, now or when they are next on. */
    private void escaped(UUID catcher) {
        deliver(catcher, List.of(), 0, boss.name() + " ist in die Tiefe entkommen.");
    }

    private void forget(UUID head) {
        YamlConfiguration pending = YamlConfiguration.loadConfiguration(pendingFile);
        if (pending.contains(ACTIVE + "." + head)) {
            pending.set(ACTIVE + "." + head, null);
            save(pending);
        }
    }

    /** Into the inventory, the rest at their feet - or kept for later if they cannot take it now. */
    private void deliver(UUID player, List<ItemStack> items, int experience, String message) {
        Player online = Bukkit.getPlayer(player);
        if (online == null || online.isDead()) {
            keep(player, items, experience, message);
            return;
        }
        give(online, items, experience, List.of(message));
    }

    private void give(Player player, List<ItemStack> items, int experience, List<String> messages) {
        for (ItemStack item : items) {
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        if (experience > 0) {
            player.giveExp(experience);
        }
        messages.forEach(message -> player.sendMessage(Component.text(message, NamedTextColor.DARK_AQUA)));
    }

    private void keep(UUID player, List<ItemStack> items, int experience, String message) {
        YamlConfiguration pending = YamlConfiguration.loadConfiguration(pendingFile);
        String key = player.toString();
        List<ItemStack> waiting = new ArrayList<>();
        for (Object item : pending.getList(key + ".items", List.of())) {
            if (item instanceof ItemStack stack) {
                waiting.add(stack);
            }
        }
        waiting.addAll(items);
        List<String> messages = new ArrayList<>(pending.getStringList(key + ".messages"));
        messages.add(message);
        pending.set(key + ".items", waiting);
        pending.set(key + ".experience", pending.getInt(key + ".experience") + experience);
        pending.set(key + ".messages", messages);
        save(pending);
    }

    private void handOverWaiting(Player player) {
        if (!pendingFile.isFile()) {
            return;
        }
        YamlConfiguration pending = YamlConfiguration.loadConfiguration(pendingFile);
        String key = player.getUniqueId().toString();
        if (!pending.contains(key)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Object item : pending.getList(key + ".items", List.of())) {
            if (item instanceof ItemStack stack) {
                items.add(stack);
            }
        }
        int experience = pending.getInt(key + ".experience");
        List<String> messages = pending.getStringList(key + ".messages");
        pending.set(key, null);
        save(pending);
        give(player, items, experience, messages);
    }

    private void save(YamlConfiguration pending) {
        try {
            pending.save(pendingFile);
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not keep " + boss.key() + " loot for later: {0}",
                    failure.getMessage());
        }
    }
}
