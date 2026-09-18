package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.enchantment.FishingEnchantments;
import com.nekyia.bountyfulSeas.fish.Rarity;
import com.nekyia.bountyfulSeas.fishing.CatchOdds;
import com.nekyia.bountyfulSeas.fish.TierKinds;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Works out a player's tier weights from the rod in their hands.
 *
 * <p>Two enchantments, one for each side of the table:
 *
 * <ul>
 *   <li><b>Luck of the Fish</b> lifts the fish tiers.</li>
 *   <li><b>Luck of the Sea</b> lifts the object tiers, where the treasure is.</li>
 * </ul>
 *
 * <p>Which tiers those are is not written down anywhere. Each side's <em>commonest</em>
 * tier is the one that pays rather than gains - uncommon on the fish side, junk on
 * the object side - and each side's <em>rarest</em> is the one that gains and never
 * pays. Everything between does both. So naming the two sides in the config, and
 * setting their chances, is enough to say what the enchantments do.
 *
 * <p>Each level adds a share of a tier's <em>base</em> weight, and everything added
 * is taken back out of the paying tiers, commonest first, each emptied before the
 * next is touched. That keeps the two enchantments independent: a rod with both
 * applies each in full and the total still comes to what it was. Normalising
 * instead would have quietly diluted the object side every time the fish side went
 * up, which is not what either enchantment is for.
 *
 * <p>Because the two rarest never pay, a rod enchanted far past the vanilla cap
 * does not stop improving. It eats the tiers below one after another until the only
 * question left is the rarest fish against the rarest object. There is no level at
 * which another level stops meaning anything.
 */
final class RodChances {

    private RodChances() {
    }

    /**
     * The weight of each tier for this player, rod included.
     *
     * @param player   whose rod to read, or null for the unenchanted weights
     * @param settings the configured base weights and bonuses
     * @param kinds    which tiers are fish and which are objects
     */
    static ToDoubleFunction<Rarity> of(Player player, Settings settings, TierKinds kinds) {
        Map<Rarity, Double> weights = weightsFor(player, settings, kinds);
        return rarity -> weights.getOrDefault(rarity, 0.0);
    }

    /**
     * Everything the draw needs to know about a rod, in one piece.
     *
     * <p>The unenchanted table travels with the enchanted one because a tier that is
     * not at a spot hands its share to junk, and what it hands over has to be what
     * it was worth before the rod: see {@link CatchOdds}.
     */
    static CatchOdds oddsFor(Player player, Settings settings, TierKinds kinds) {
        ItemStack rod = rodOf(player);
        int fishLevel = FishingEnchantments.LUCK_OF_THE_FISH.levelOn(rod);
        int seaLevel = levelOf(rod, Enchantment.LUCK_OF_THE_SEA);
        int lureLevel = levelOf(rod, Enchantment.LURE);

        ToDoubleFunction<Rarity> bare = of(null, settings, kinds);
        return new CatchOdds(
                present -> weightsFor(fishLevel, seaLevel, lureLevel, settings, kinds, present),
                bare, seaLuck(player, settings));
    }

    /**
     * What Luck of the Sea does to a fish that carries its own chance.
     *
     * <p>Those fish sit outside the tier weights - they are drawn before the tiers
     * are - so the enchantment cannot reach them the way it reaches a tier, and has
     * to be handed to the draw separately. Luck of the Sea rather than Luck of the
     * Fish: what has a chance of its own is what the sea gives up, not what swims
     * into the net.
     */
    static double seaLuck(Player player, Settings settings) {
        return settings.enchantments().luckMultiplier(
                levelOf(rodOf(player), Enchantment.LUCK_OF_THE_SEA));
    }

    /** Exposed for reporting, so what is shown is what will be rolled. */
    static Map<Rarity, Double> weightsFor(Player player, Settings settings, TierKinds kinds) {
        ItemStack rod = rodOf(player);
        return weightsFor(FishingEnchantments.LUCK_OF_THE_FISH.levelOn(rod),
                levelOf(rod, Enchantment.LUCK_OF_THE_SEA),
                levelOf(rod, Enchantment.LURE), settings, kinds);
    }

    /**
     * The same, from the enchantment levels alone.
     *
     * <p>Split out from the rod so the table can be worked out - and checked -
     * without a player holding anything.
     */
    static Map<Rarity, Double> weightsFor(int fishLevel, int seaLevel, int lureLevel,
                                          Settings settings, TierKinds kinds) {
        return weightsFor(fishLevel, seaLevel, lureLevel, settings, kinds, null);
    }

    /**
     * The same, for one spot.
     *
     * <p>Only the tiers on offer take part. A rod that lifts the rarest fish is
     * worth nothing where none live, and must cost nothing there either - the
     * everyday tiers pay for what the rod adds, and they should not be paying for a
     * legendary that was never on the table. Left null, every tier takes part,
     * which is the table in the abstract rather than at any particular spot.
     *
     * @param present the tiers with something to offer, or null for all of them
     */
    static Map<Rarity, Double> weightsFor(int fishLevel, int seaLevel, int lureLevel,
                                          Settings settings, TierKinds kinds,
                                          java.util.Set<Rarity> present) {
        Map<Rarity, Double> base = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            base.put(rarity, present == null || present.contains(rarity)
                    ? settings.chanceOf(rarity.configName())
                    : 0);
        }

        // A tier at zero takes no part at all: it is neither lifted nor spent, which
        // is how a tier is switched off without having to say so twice.
        // Which fish tiers the enchantment reaches at all. Named by the commonest
        // one it touches, so "from epic" means epic and everything rarer - said as a
        // tier rather than as a list, so adding a tier does not mean editing one.
        double liftedFrom = liftFloor(base, settings.enchantments().fishLiftsFrom());

        Rarity fishPays = commonest(base, kinds::isFish);
        Rarity fishKeeps = rarest(base, kinds::isFish);
        Rarity objectPays = commonest(base, kinds::isObject);
        Rarity objectKeeps = rarest(base, kinds::isObject);

        Settings.EnchantmentSettings bonuses = settings.enchantments();

        // Lure is off by default and does the same job as Luck of the Fish when
        // switched on, so the two multiply into one figure for the fish side.
        double fishLuck = bonuses.lureMultiplier(lureLevel) * bonuses.fishMultiplier(fishLevel);
        double seaLuck = bonuses.luckMultiplier(seaLevel);

        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        double owed = 0;

        for (Rarity rarity : Rarity.values()) {
            double chance = base.get(rarity);
            double multiplier = 1;
            if (chance > 0 && kinds.isFish(rarity) && rarity != fishPays && chance <= liftedFrom) {
                multiplier = fishLuck;
            } else if (chance > 0 && kinds.isObject(rarity) && rarity != objectPays) {
                multiplier = seaLuck;
            }

            double raised = chance * multiplier;
            owed += raised - chance;
            weights.put(rarity, raised);
        }

        drain(weights, payers(base, fishKeeps, objectKeeps), owed);
        if (present != null) {
            weights.keySet().retainAll(present);
        }
        return weights;
    }

    /**
     * The weight at and below which Luck of the Fish applies.
     *
     * <p>A rarer tier is a smaller weight, so "epic and rarer" is "no more than what
     * epic is worth". A tier the config does not know leaves the enchantment
     * reaching nothing, which is safer than reaching everything.
     */
    private static double liftFloor(Map<Rarity, Double> base, String named) {
        for (Rarity rarity : Rarity.values()) {
            if (rarity.configName().equals(named)) {
                return base.getOrDefault(rarity, 0.0);
            }
        }
        return 0;
    }

    /**
     * The tiers that pay, commonest first.
     *
     * <p>Everything in play except the rarest of each side. Ordered by what they
     * were worth to begin with, so the ordinary is spent before the good is
     * touched - and the order follows the numbers rather than a list somebody has
     * to remember to update.
     */
    private static List<Rarity> payers(Map<Rarity, Double> base, Rarity fishKeeps, Rarity objectKeeps) {
        List<Rarity> payers = new ArrayList<>();
        for (Map.Entry<Rarity, Double> entry : base.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey() != fishKeeps && entry.getKey() != objectKeeps) {
                payers.add(entry.getKey());
            }
        }
        payers.sort(Comparator.comparingDouble((Rarity rarity) -> base.get(rarity)).reversed());
        return payers;
    }

    /**
     * Takes what the enchantments added back out of the tiers that pay.
     *
     * <p>Nothing goes below zero, and once the payers are empty the rest simply
     * goes unpaid. By then only the two rarest are left standing, and what matters
     * is their weight against each other rather than against a hundred.
     */
    private static void drain(Map<Rarity, Double> weights, List<Rarity> payers, double owed) {
        for (Rarity payer : payers) {
            if (owed <= 0) {
                return;
            }
            double available = weights.getOrDefault(payer, 0.0);
            double taken = Math.min(available, owed);
            weights.put(payer, available - taken);
            owed -= taken;
        }
    }

    /** The likeliest tier on one side, or null when that side has none in play. */
    private static Rarity commonest(Map<Rarity, Double> base, Predicate<Rarity> side) {
        return pick(base, side, Comparator.comparingDouble(entry -> (double) entry.getValue()));
    }

    /** The least likely tier on one side, which is the one worth protecting. */
    private static Rarity rarest(Map<Rarity, Double> base, Predicate<Rarity> side) {
        return pick(base, side,
                Comparator.<Map.Entry<Rarity, Double>>comparingDouble(entry -> entry.getValue()).reversed());
    }

    private static Rarity pick(Map<Rarity, Double> base, Predicate<Rarity> side,
                               Comparator<Map.Entry<Rarity, Double>> worstFirst) {
        return base.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && side.test(entry.getKey()))
                .max(worstFirst)
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** The rod being fished with, checking both hands, or null when there is none. */
    private static ItemStack rodOf(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == Material.FISHING_ROD) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off.getType() == Material.FISHING_ROD ? off : null;
    }

    private static int levelOf(ItemStack rod, Enchantment enchantment) {
        return rod == null || enchantment == null ? 0 : rod.getEnchantmentLevel(enchantment);
    }
}
