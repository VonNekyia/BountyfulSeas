package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.enchantment.FishingEnchantments;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fishing.Chance;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
            List.of("bobber", "swarm", "item", "enchant", "set", "lengthvalue");

    /** Below this, odds read better as "1 in n" than as a percentage. */
    private static final double RARE_PERCENT = 0.1;

    private final Supplier<FishLibrary> fish;
    private final Supplier<WaterMap> waterMap;
    private final Supplier<Swarms> swarms;
    private final Supplier<Settings> settings;
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
                 CatchCounts counts, AnglerLevels levels, ForcedCatches forced,
                 Consumer<Runnable> offThread) {
        this.fish = fish;
        this.waterMap = waterMap;
        this.swarms = swarms;
        this.settings = settings;
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
                case "item", "set", "lengthvalue" -> starting(
                        fish.get().all().stream().map(Fish::id).sorted().toList(), args[1]);
                case "enchant" -> starting(
                        allEnchantmentKeys(), args[1]);
                default -> List.of();
            };
        }
        return List.of();
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
        player.sendMessage(detail("swarm", "where every swarm is right now"));
        player.sendMessage(detail("item <fish> [cm]", "make the next bite hand over this fish"));
        player.sendMessage(detail("enchant [enchantment] [level]", "enchant the rod you are holding"));
        player.sendMessage(detail("set <fish> <count>", "put your catch count at a number"));
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
        List<Chance> chances = FishSelector.chances(fish.get().all(), spot,
                levels.levelOf(player.getUniqueId()),
                RodChances.of(player, settings.get()));
        if (chances.isEmpty()) {
            player.sendMessage(error("Nothing lives here. Vanilla keeps the catch."));
            return;
        }

        player.sendMessage(heading("Chances"));
        for (Chance chance : chances) {
            player.sendMessage(Component.text("  ")
                    .append(Component.text(String.format(Locale.ROOT, "%5.1f%%", chance.percent()),
                            NamedTextColor.WHITE))
                    .append(Component.text("  " + chance.fish().id(), NamedTextColor.GRAY))
                    .append(Component.text("  weight " + chance.weight()
                                    + ", " + chance.fish().rarity().configName()
                                    + ", level " + chance.fish().level(),
                            NamedTextColor.DARK_GRAY)));
        }
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
        if (args.length < 2) {
            player.sendMessage(error("/bs debug set <fish> <count>"));
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
        player.sendMessage(detail("beats", trim(100 * (1 - chance)) + "% of catches"));

        player.sendMessage(heading("What the curve gives"));
        player.sendMessage(detail("shortest", cm(curve.shortest(max))));
        player.sendMessage(detail("half are under", cm(curve.lengthBeatenBy(max, 0.5))));
        player.sendMessage(detail("top 10%", cm(curve.lengthBeatenBy(max, 0.10))));
        player.sendMessage(detail("top 1%", cm(curve.lengthBeatenBy(max, 0.01))));
        player.sendMessage(detail("1 in 1000", cm(curve.lengthBeatenBy(max, 0.001))));
        player.sendMessage(detail("the record", cm(max) + "   "
                + odds(curve.chanceOfAtLeast(max, max))));
    }

    /** A chance as odds, because "0.0001%" says less than "1 in a million". */
    private static String odds(double chance) {
        if (chance <= 0) {
            return "never";
        }
        if (chance >= 1) {
            return "every catch";
        }
        if (100 * chance >= RARE_PERCENT) {
            return trim(100 * chance) + "% of catches";
        }
        return "1 in " + String.format(Locale.ROOT, "%,d", Math.round(1 / chance));
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
