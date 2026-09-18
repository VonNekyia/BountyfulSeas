package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.enchantment.FishingEnchantments;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fish.Rarity;
import com.nekyia.bountyfulSeas.fish.TierKinds;
import com.nekyia.bountyfulSeas.fishing.CatchOdds;
import com.nekyia.bountyfulSeas.fishing.Chance;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.fishing.WaterConditions;
import com.nekyia.bountyfulSeas.guide.FishNames;
import com.nekyia.bountyfulSeas.fishing.LengthCurve;
import com.nekyia.bountyfulSeas.level.Progress;
import com.nekyia.bountyfulSeas.stats.Milestone;
import com.nekyia.bountyfulSeas.swarm.Swarms;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * The tools for looking at a fish table, and for putting it in a state worth
 * looking at.
 *
 * <p>Every one of these answers a question that is otherwise answered by fishing
 * for an afternoon: what is under my bobber, where are the swarms, what does this
 * length actually amount to, what happens at a milestone I have not reached. None
 * of them invents its answer - they all go through the same curve, selector and
 * store a real catch goes through, so what they report is what the water gives.
 */
final class DebugCommand {

    static final List<String> SUBCOMMANDS =
            List.of("bobber", "regions", "swarm", "item", "enchant", "set", "lengthvalue");

    /** Below this, odds read better as "1 in n" than as a percentage. */
    private static final double RARE_PERCENT = 0.1;

    /** Above this, a percentage needs more decimals or it reads as everything. */
    private static final double NEARLY_ALL = 99.99;

    private final Supplier<FishLibrary> fish;
    private final Supplier<WaterMap> waterMap;
    private final Supplier<Swarms> swarms;
    private final Supplier<Settings> settings;
    private final Supplier<TierKinds> kinds;
    private final CatchCounts counts;
    private final AnglerLevels levels;
    private final ForcedCatches forced;

    /** Runs work that touches the database, off the server thread. */
    private final Consumer<Runnable> offThread;

    /**
     * Where a catch count is put when somebody sets one.
     *
     * <p>Owned here rather than taken as the stats API, which is a read surface on
     * purpose. Whoever implements this also has to drop whatever was cached, or the
     * level worked out a line later would still be the old one.
     */
    @FunctionalInterface
    interface CatchCounts {
        void set(UUID player, String fishId, long catches);
    }

    DebugCommand(Supplier<FishLibrary> fish, Supplier<WaterMap> waterMap,
                 Supplier<Swarms> swarms, Supplier<Settings> settings,
                 Supplier<TierKinds> kinds, CatchCounts counts, AnglerLevels levels,
                 ForcedCatches forced, Consumer<Runnable> offThread) {
        this.fish = fish;
        this.waterMap = waterMap;
        this.swarms = swarms;
        this.settings = settings;
        this.kinds = kinds;
        this.counts = counts;
        this.levels = levels;
        this.forced = forced;
        this.offThread = offThread;
    }

    /** @param args everything after {@code /bs debug} */
    void run(Player player, String[] args) {
        String sub = args.length == 0 ? "bobber" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "bobber" -> bobber(player);
            case "regions" -> regions(player, rest);
            case "swarm" -> swarm(player);
            case "item" -> item(player, rest);
            case "enchant" -> enchant(player, rest);
            case "set" -> set(player, rest);
            case "lengthvalue" -> lengthValue(player, rest);
            default -> {
                player.sendMessage(error("No such debug: " + args[0]));
                usage(player);
            }
        }
    }

    /**
     * Tab completion for everything after {@code /bs debug}.
     *
     * @param args what has been typed after the word debug, the last possibly partial
     */
    List<String> suggest(String[] args) {
        if (args.length <= 1) {
            return starting(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "set" -> starting(withEnds(
                        fish.get().all().stream().map(Fish::id).sorted().toList()), args[1]);
                case "item", "lengthvalue" -> starting(
                        fish.get().all().stream().map(Fish::id).sorted().toList(), args[1]);
                case "enchant" -> starting(
                        allEnchantmentKeys(), args[1]);
                case "regions" -> starting(levelsToTry(), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    /**
     * Every level the roster actually locks something behind, for completion.
     *
     * <p>The levels in between are not offered, not because they are wrong to type
     * but because nothing changes at them - the menu would look the same.
     */
    private List<String> levelsToTry() {
        List<Integer> found = fish.get().all().stream().map(Fish::level).distinct().sorted().toList();
        List<String> levels = new ArrayList<>();
        for (int level : found) {
            levels.add(String.valueOf(level));
        }
        return levels;
    }

    /** The fish ids, with the two words that stand for all of them at once. */
    private static List<String> withEnds(List<String> ids) {
        List<String> names = new ArrayList<>(List.of("all", "reset"));
        names.addAll(ids);
        return names;
    }

    private static List<String> allEnchantmentKeys() {
        List<String> keys = new ArrayList<>();
        for (Enchantment enchantment : enchantments()) {
            keys.add(key(enchantment));
        }
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    private static List<String> starting(List<String> names, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.startsWith(prefix)).toList();
    }

    private void usage(Player player) {
        player.sendMessage(heading("Debug"));
        player.sendMessage(detail("bobber", "what can be caught where your bobber is"));
        player.sendMessage(detail("regions [level]", "every kind of water there is, and what bites in it"));
        player.sendMessage(detail("swarm", "where every swarm is right now"));
        player.sendMessage(detail("item <fish> [cm]", "make the next bite hand over this fish"));
        player.sendMessage(detail("enchant [enchantment] [level]", "enchant the rod you are holding"));
        player.sendMessage(detail("set <fish> <count>", "put your catch count at a number"));
        player.sendMessage(detail("set all | set reset",
                "put every count at the last milestone, or back to none"));
        player.sendMessage(detail("lengthvalue <fish> <cm>", "what a length of that fish amounts to"));
    }

    // ---------------------------------------------------------------- bobber

    /** Reports the spot under the player's bobber. */
    private void bobber(Player player) {
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

        WaterSpot spot = WaterSpot.of(region, at.getWorld(), swarms.get());

        player.sendMessage(heading("Fishing spot"));
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

        // The level is a filter like the rest, so it belongs with them: half the
        // "why is that fish not here" answers are simply that it is out of reach.
        Progress standing = levels.progressOf(player.getUniqueId());
        player.sendMessage(detail("your level", standing.capped()
                ? standing.level() + "  (the highest there is)"
                : standing.level() + "  " + standing.intoLevel() + "/" + standing.span() + " xp"));

        String swarming = swarms.get().fishAt(region.id());
        player.sendMessage(detail("swarm", swarming == null ? "none here" : swarming));

        ForcedCatches.Forced arranged = forced.peek(player.getUniqueId());
        if (arranged != null) {
            player.sendMessage(detail("next bite", "forced to " + arranged.fishId()
                    + (arranged.length() == null ? "" : " at " + cm(arranged.length()))));
        }

        // The player's own rod, so the odds reported are the odds they will get.
        CatchOdds odds = RodChances.oddsFor(player, settings.get(), kinds.get());
        List<Chance> chances = FishSelector.chances(fish.get().all(), spot,
                levels.levelOf(player.getUniqueId()), odds);
        if (chances.isEmpty()) {
            player.sendMessage(error("Nothing lives here. Vanilla keeps the catch."));
            return;
        }

        tiers(player, chances, odds);
        outOfReach(player, spot, levels.levelOf(player.getUniqueId()));
    }

    /**
     * What lives here and is still locked, as the guide would put it.
     *
     * <p>Named only by its tier colour, because the level is the answer and the
     * fish is still worth finding out. Half of "why did I not catch that here" is
     * this list rather than the one above it.
     */
    private void outOfReach(Player player, WaterConditions spot, int anglerLevel) {
        List<Fish> locked = FishSelector.outOfReach(fish.get().all(), spot, anglerLevel);
        if (locked.isEmpty()) {
            return;
        }

        player.sendMessage(heading("Not yours yet"));
        for (Fish waiting : locked) {
            player.sendMessage(Component.text("  ")
                    .append(FishNames.unknown(waiting))
                    .append(Component.text("  level " + waiting.level(), NamedTextColor.WHITE))
                    .append(Component.text("  " + waiting.rarity().configName()
                            + ", " + waiting.category(), NamedTextColor.DARK_GRAY)));
        }
    }

    // --------------------------------------------------------------- regions

    /**
     * Every kind of water the scan found, as a menu rather than a wall of chat.
     *
     * <p>A menu because the question is comparative - which combinations hold
     * nothing - and because the modifiers are worth turning on and off while
     * looking at the answer.
     */
    private void regions(Player player, String[] args) {
        WaterMap map = waterMap.get();
        if (map == null) {
            player.sendMessage(error("No water map is loaded, so there are no regions to show."));
            return;
        }
        if (map.regionCount() == 0) {
            player.sendMessage(error("The water map holds no regions."));
            return;
        }

        int standing = levels.levelOf(player.getUniqueId());
        int asked = standing;
        if (args.length > 0) {
            try {
                asked = Integer.parseInt(args[0]);
            } catch (NumberFormatException notANumber) {
                player.sendMessage(error("A level is a number, not \"" + args[0] + "\"."));
                return;
            }
            if (asked < 1) {
                player.sendMessage(error("There is no level below 1."));
                return;
            }
            if (asked != standing) {
                player.sendMessage(detail("looking as", "a level " + asked + " angler"
                        + "   (you are " + standing + ")"));
            }
        }

        new RegionsMenu(player, map, fish.get(),
                RodChances.oddsFor(player, settings.get(), kinds.get()), asked,
                EnumSet.noneOf(Modifier.class),
                WaterSpot.conditionsOf(player.getWorld())).open();
    }

    /**
     * The draw as it actually happens: the tier first, then the entry within it.
     *
     * <p>Reported the same way round, because a flat list of fish hides the thing
     * worth knowing - whether a fish is unlikely because its tier is, or because it
     * shares that tier with six others.
     *
     * <p>The tier percentages are the fish percentages added up, not a second sum
     * worked out from the config, so what this says and what the water gives cannot
     * come apart.
     */
    private void tiers(Player player, List<Chance> chances, CatchOdds odds) {
        Map<Rarity, List<Chance>> byTier = new EnumMap<>(Rarity.class);
        for (Chance chance : chances) {
            byTier.computeIfAbsent(chance.fish().rarity(), tier -> new ArrayList<>()).add(chance);
        }

        List<Map.Entry<Rarity, List<Chance>>> ordered = new ArrayList<>(byTier.entrySet());
        ordered.sort(Comparator.comparingDouble(
                (Map.Entry<Rarity, List<Chance>> entry) -> share(entry.getValue())).reversed());

        player.sendMessage(heading("Chances by tier"));
        for (Map.Entry<Rarity, List<Chance>> entry : ordered) {
            List<Chance> inTier = entry.getValue();
            player.sendMessage(Component.text("  ")
                    .append(Component.text(String.format(Locale.ROOT, "%5.1f%%", share(inTier)),
                            NamedTextColor.WHITE))
                    .append(Component.text("  " + entry.getKey().configName(), NamedTextColor.AQUA))
                    .append(Component.text("  " + inTier.size()
                            + (inTier.size() == 1 ? " entry here" : " entries here"),
                            NamedTextColor.DARK_GRAY)));

            for (Chance chance : inTier) {
                player.sendMessage(Component.text("      ")
                        .append(Component.text(String.format(Locale.ROOT, "%5.1f%%", chance.percent()),
                                NamedTextColor.GRAY))
                        .append(Component.text("  " + chance.fish().id(), NamedTextColor.GRAY))
                        .append(Component.text("  weight " + chance.weight()
                                        + ", level " + chance.fish().level(),
                                NamedTextColor.DARK_GRAY)));
            }
        }

        absent(player, byTier.keySet(), odds);
    }

    /**
     * What the tiers with nothing here are worth, and where that weight went.
     *
     * <p>Worth a line of its own: a spot where half the table is missing reads as
     * a spot full of junk, and this is the only place that says why.
     */
    private void absent(Player player, Set<Rarity> present, CatchOdds odds) {
        List<String> missing = new ArrayList<>();
        double forfeited = 0;
        for (Rarity rarity : Rarity.values()) {
            double chance = Math.max(0, odds.bare().applyAsDouble(rarity));
            if (chance > 0 && !present.contains(rarity)) {
                missing.add(rarity.configName());
                forfeited += chance;
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        String weight = String.format(Locale.ROOT, "%.1f", forfeited);
        player.sendMessage(detail("not here", String.join(", ", missing)));
        player.sendMessage(detail("their " + weight + " weight", present.contains(Rarity.MISC)
                ? "went to misc, which is why junk reads high here"
                : "is not drawn at all - there is no junk here to stand in for it"));
    }

    /** A tier's own odds: what everything in it comes to. */
    private static double share(List<Chance> inTier) {
        double total = 0;
        for (Chance chance : inTier) {
            total += chance.percent();
        }
        return total;
    }

    // ----------------------------------------------------------------- swarm

    /** Where every swarm currently sits, as coordinates worth walking to. */
    private void swarm(Player player) {
        WaterMap map = waterMap.get();
        if (map == null) {
            player.sendMessage(error("No water map is loaded, so there are no swarms."));
            return;
        }

        Swarms swarming = swarms.get();
        if (swarming.size() == 0) {
            player.sendMessage(error("No swarms right now. Check swarms.count in the config, "
                    + "and that some fish carry the swarm modifier."));
            return;
        }

        player.sendMessage(heading("Swarms  (" + swarming.size() + ")"));
        for (int id : swarming.regions()) {
            String fishId = swarming.fishAt(id);
            WaterRegion region = map.region(id);
            if (region == null) {
                player.sendMessage(detail(fishId, "region #" + id + " is no longer on the map"));
                continue;
            }

            // The middle of the region's box. Not its centre of mass, but close
            // enough to walk to, and it is a place rather than a region number.
            int x = (region.minX() + region.maxX()) / 2;
            int z = (region.minZ() + region.maxZ()) / 2;
            long away = Math.round(Math.hypot(x - player.getLocation().getX(),
                    z - player.getLocation().getZ()));

            player.sendMessage(detail(fishId, x + ", " + z
                    + "   region #" + id
                    + "   " + region.columns() + " columns"
                    + "   " + away + " blocks away"));
        }
    }

    // ------------------------------------------------------------------ item

    /** Arranges the next bite, optionally at a length. */
    private void item(Player player, String[] args) {
        if (args.length == 0) {
            if (forced.clear(player.getUniqueId())) {
                player.sendMessage(heading("Cleared the bite you had arranged"));
                return;
            }
            player.sendMessage(error("Which fish? /bs debug item <fish> [cm]"));
            return;
        }

        Fish picked = fish.get().get(args[0]);
        if (picked == null) {
            player.sendMessage(error("No fish is defined as " + args[0] + "."));
            return;
        }

        Double length = null;
        if (args.length > 1) {
            if (picked.isCatchMarker()) {
                player.sendMessage(error(picked.id() + " is not a fish and carries no length."));
                return;
            }
            Double asked = number(player, args[1]);
            if (asked == null) {
                return;
            }

            LengthCurve curve = curve();
            double shortest = curve.shortest(picked.maxLength());
            if (asked < shortest || asked > picked.maxLength()) {
                player.sendMessage(error("Outside what " + picked.id() + " can be: "
                        + cm(shortest) + " to " + cm(picked.maxLength()) + "."));
                return;
            }
            length = asked;
        }

        forced.set(player.getUniqueId(), picked.id(), length);
        player.sendMessage(heading("Next bite arranged"));
        player.sendMessage(detail("fish", picked.id() + "   " + picked.rarity().configName()
                + ", level " + picked.level()));
        player.sendMessage(detail("length", length == null ? "rolled as usual" : cm(length)));
        player.sendMessage(detail("note", "one bite, and the water still has to be fishable"));
    }

    // --------------------------------------------------------------- enchant

    /** Lists, explains, or applies a rod enchantment. */
    private void enchant(Player player, String[] args) {
        ItemStack rod = heldRod(player);
        if (rod == null) {
            player.sendMessage(error("Hold a fishing rod first."));
            return;
        }

        List<Enchantment> possible = rodEnchantments(rod);
        if (args.length == 0) {
            player.sendMessage(heading("Rod enchantments  (" + possible.size() + ")"));
            for (Enchantment enchantment : possible) {
                int had = rod.getEnchantmentLevel(enchantment);
                player.sendMessage(detail(key(enchantment),
                        "normally up to " + enchantment.getMaxLevel()
                                + (had > 0 ? ", on this rod at " + had : "")));
            }
            player.sendMessage(detail("note", "name one to have it explained, add a level to apply it"));
            return;
        }

        Enchantment enchantment = find(args[0], possible);
        if (enchantment == null) {
            player.sendMessage(error("No rod enchantment called " + args[0]
                    + ". Run /bs debug enchant for the list."));
            return;
        }

        if (args.length == 1) {
            explain(player, enchantment, rod);
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException notANumber) {
            player.sendMessage(error(args[1] + " is not a whole number."));
            return;
        }

        if (level <= 0) {
            rod.removeEnchantment(enchantment);
            player.sendMessage(heading("Removed " + key(enchantment)));
            return;
        }

        // Unsafe only in that it ignores the cap and what the item usually takes,
        // which is the whole point of being able to ask for level 30.
        rod.addUnsafeEnchantment(enchantment, level);
        player.sendMessage(heading("Enchanted"));
        player.sendMessage(detail(key(enchantment), "level " + level
                + (level > enchantment.getMaxLevel()
                        ? "   past the usual cap of " + enchantment.getMaxLevel() : "")));
        reportEffect(player, enchantment, level);
    }

    /** What an enchantment does here, which is not always what it does in vanilla. */
    private void explain(Player player, Enchantment enchantment, ItemStack rod) {
        Settings.EnchantmentSettings bonuses = settings.get().enchantments();

        player.sendMessage(heading(key(enchantment)));
        player.sendMessage(detail("normal cap", String.valueOf(enchantment.getMaxLevel())));
        player.sendMessage(detail("on this rod", String.valueOf(rod.getEnchantmentLevel(enchantment))));

        if (enchantment.equals(FishingEnchantments.LUCK_OF_THE_FISH.enchantment())) {
            player.sendMessage(detail("does", "raises rare, epic and legendary by "
                    + trim(bonuses.fishPerLevel()) + "% of their base per level"));
            player.sendMessage(detail("paid by", "uncommon, which loses exactly what they gain"));
        } else if (enchantment.equals(Enchantment.LUCK_OF_THE_SEA)) {
            player.sendMessage(detail("does", "raises treasure by "
                    + trim(bonuses.luckPerLevel()) + "% of its base per level"));
            player.sendMessage(detail("paid by", "uncommon, which loses exactly what it gains"));
        } else if (enchantment.equals(Enchantment.LURE)) {
            player.sendMessage(detail("does", bonuses.lurePerLevel() > 0
                    ? "raises the same tiers as Luck of the Fish, by "
                            + trim(bonuses.lurePerLevel()) + "% of base per level"
                    : "nothing to the odds; it keeps its vanilla job of making fish bite sooner"));
        } else {
            player.sendMessage(detail("does", "nothing to what is caught, only what vanilla does"));
        }
        player.sendMessage(detail("apply", "/bs debug enchant " + key(enchantment) + " <level>"));
    }

    /** After enchanting, the odds it actually bought. */
    private void reportEffect(Player player, Enchantment enchantment, int level) {
        Settings.EnchantmentSettings bonuses = settings.get().enchantments();

        double perLevel;
        if (enchantment.equals(FishingEnchantments.LUCK_OF_THE_FISH.enchantment())) {
            perLevel = bonuses.fishPerLevel();
        } else if (enchantment.equals(Enchantment.LUCK_OF_THE_SEA)) {
            perLevel = bonuses.luckPerLevel();
        } else if (enchantment.equals(Enchantment.LURE)) {
            perLevel = bonuses.lurePerLevel();
        } else {
            return;
        }
        if (perLevel <= 0) {
            return;
        }

        player.sendMessage(detail("worth", "x" + trim(1 + perLevel / 100 * level)
                + " on the tiers it lifts"));
        player.sendMessage(detail("see", "/bs debug bobber for what that does to the odds"));
    }

    private static ItemStack heldRod(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == Material.FISHING_ROD) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off.getType() == Material.FISHING_ROD ? off : null;
    }

    /**
     * Every enchantment a rod will take, asked of the registry rather than listed.
     *
     * <p>A list written here would go stale the moment anything adds one - our own
     * included, and that is precisely the one that has to show up.
     */
    private static List<Enchantment> rodEnchantments(ItemStack rod) {
        List<Enchantment> possible = new ArrayList<>();
        for (Enchantment enchantment : enchantments()) {
            if (enchantment.canEnchantItem(rod)) {
                possible.add(enchantment);
            }
        }
        possible.sort(Comparator.comparing(DebugCommand::key));
        return possible;
    }

    /** Every enchantment the server has, our own datapack-free one included. */
    private static Iterable<Enchantment> enchantments() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    private static Enchantment find(String name, List<Enchantment> possible) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Enchantment enchantment : possible) {
            String id = key(enchantment);
            if (id.equals(wanted) || id.endsWith(":" + wanted)) {
                return enchantment;
            }
        }
        return null;
    }

    private static String key(Enchantment enchantment) {
        return enchantment.key().asString();
    }

    // ------------------------------------------------------------------- set

    /** Puts a catch count where it is wanted, milestones and level and all. */
    private void set(Player player, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            everything(player, Milestone.values()[Milestone.values().length - 1].required(),
                    "every milestone");
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            everything(player, 0, "nothing caught");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(error("/bs debug set <fish> <count>, or set all, or set reset"));
            return;
        }

        Fish picked = fish.get().get(args[0]);
        if (picked == null) {
            player.sendMessage(error("No fish is defined as " + args[0] + "."));
            return;
        }

        long count;
        try {
            count = Long.parseLong(args[1]);
        } catch (NumberFormatException notANumber) {
            player.sendMessage(error(args[1] + " is not a whole number."));
            return;
        }
        if (count < 0) {
            player.sendMessage(error("A count cannot be negative."));
            return;
        }

        UUID id = player.getUniqueId();
        offThread.accept(() -> {
            Progress standing;
            try {
                counts.set(id, picked.id(), count);
                // The level is derived from the counts, so moving one moves it too.
                standing = levels.refresh(id);
            } catch (RuntimeException failure) {
                player.sendMessage(error("Could not set it: " + failure.getMessage()));
                return;
            }

            Milestone reached = Milestone.reached(count);
            player.sendMessage(heading("Count set"));
            player.sendMessage(detail(picked.id(), count + " caught"));
            player.sendMessage(detail("milestone", reached == null
                    ? "none yet" : reached.numeral() + "   next at "
                            + (Milestone.next(count) == null
                                    ? "nothing left" : Milestone.next(count).required())));
            player.sendMessage(detail("your level", standing.level()
                    + "   " + standing.experience() + " xp"));
        });
    }

    /**
     * Puts every fish at the same count at once, for testing the ends of the curve.
     *
     * <p>Both ends are worth having. Setting everything to the last milestone is the
     * only way to see the top of the level curve without fishing for a month, and
     * setting it back to nothing is the only way to see the first level again.
     *
     * <p>Objects are counted too: they carry milestones like anything else, and the
     * curve is built counting them, so leaving them out would not be the top.
     */
    private void everything(Player player, long count, String what) {
        List<Fish> all = List.copyOf(fish.get().all());
        if (all.isEmpty()) {
            player.sendMessage(error("There are no fish to set."));
            return;
        }

        UUID id = player.getUniqueId();
        player.sendMessage(detail("setting", all.size() + " entries to " + count
                + "   (" + what + ")"));

        offThread.accept(() -> {
            Progress standing;
            try {
                for (Fish each : all) {
                    counts.set(id, each.id(), count);
                }
                // Once, at the end: the level is derived from the counts, and working
                // it out after every one of them would be fifty reads of the same row
                // set to reach the same answer.
                standing = levels.refresh(id);
            } catch (RuntimeException failure) {
                player.sendMessage(error("Could not set them: " + failure.getMessage()));
                return;
            }

            player.sendMessage(heading("Counts set"));
            player.sendMessage(detail("entries", all.size() + " at " + count + " caught"));
            player.sendMessage(detail("your level", standing.level()
                    + (standing.capped() ? "   (the highest there is)" : "")
                    + "   " + standing.experience() + " xp"));
        });
    }

    // ----------------------------------------------------------- lengthvalue

    /** What a given length of a given fish actually amounts to. */
    private void lengthValue(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(error("/bs debug lengthvalue <fish> <cm>"));
            return;
        }

        Fish picked = fish.get().get(args[0]);
        if (picked == null) {
            player.sendMessage(error("No fish is defined as " + args[0] + "."));
            return;
        }
        if (picked.isCatchMarker()) {
            player.sendMessage(error(picked.id() + " is not a fish and carries no length."));
            return;
        }

        Double length = number(player, args[1]);
        if (length == null) {
            return;
        }

        LengthCurve curve = curve();
        double max = picked.maxLength();
        double chance = curve.chanceOfAtLeast(max, length);

        player.sendMessage(heading(picked.id() + " at " + cm(length)));
        player.sendMessage(detail("of the maximum", Math.round(100 * length / max) + "%"));
        player.sendMessage(detail("this good or better", odds(chance)));
        player.sendMessage(detail("beats", percent(1 - chance) + " of catches"));

        player.sendMessage(heading("What the curve gives"));
        for (Settings.SizeSettings.Bracket bracket : settings.get().sizes().brackets()) {
            double at = curve.lengthBeatenBy(max, bracket.chance());
            player.sendMessage(detail(bracket.label(), cm(at)
                    + "   " + percent(bracket.chance())
                    + (length >= at ? "   reached" : "")));
        }
        player.sendMessage(detail("shortest there is", cm(curve.shortest(max)) + "   100%"));
    }

    /**
     * A chance in both forms it gets read in.
     *
     * <p>A percentage is what a number in the config looks like; odds are what a
     * catch feels like. At these magnitudes neither says enough on its own -
     * "0.0001%" and "1 in a million" are the same fact and only one of them lands.
     */
    private static String odds(double chance) {
        if (chance <= 0) {
            return "never";
        }
        if (chance >= 1) {
            return "every catch";
        }
        String reads = percent(chance);
        return 100 * chance >= RARE_PERCENT
                ? reads
                : reads + "   1 in " + String.format(Locale.ROOT, "%,d", Math.round(1 / chance));
    }

    /**
     * A chance as a percentage, with as many decimals as it takes to say anything.
     *
     * <p>Two decimals would round a record to 0.00%, which is worse than useless.
     */
    private static String percent(double chance) {
        double reached = 100 * chance;

        // Two decimals at either end would lie in both directions: a record would
        // round to 0.00%, and what a record beats would round to a flat 100%.
        String text = reached >= NEARLY_ALL && chance < 1
                ? String.format(Locale.ROOT, "%.6f", reached)
                : reached >= 1 ? String.format(Locale.ROOT, "%.2f", reached)
                : reached >= 0.01 ? String.format(Locale.ROOT, "%.3f", reached)
                : String.format(Locale.ROOT, "%.6f", reached);

        // Trim the padding the format added: 0.000100 is 0.0001, and 1.00 is 1.
        if (text.indexOf('.') >= 0) {
            while (text.endsWith("0")) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text + "%";
    }

    // ---------------------------------------------------------------- shared

    private LengthCurve curve() {
        Settings.SizeSettings sizes = settings.get().sizes();
        return new LengthCurve(sizes.shape(), sizes.smallest(), sizes.oddsOfMax());
    }

    private static Double number(Player player, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException notANumber) {
            player.sendMessage(error(raw + " is not a number."));
            return null;
        }
    }

    private static String cm(double value) {
        return String.format(Locale.ROOT, "%.2f cm", value);
    }

    /** Drops a trailing .0, so a whole number does not read like a measurement. */
    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component heading(String text) {
        return Component.text(text, NamedTextColor.AQUA);
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
