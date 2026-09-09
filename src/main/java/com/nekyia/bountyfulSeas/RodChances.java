package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.enchantment.FishingEnchantments;
import com.nekyia.bountyfulSeas.fish.Rarity;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Works out a player's tier weights from the rod in their hands.
 *
 * <p>Two enchantments, two jobs, and neither touches the other's tiers:
 *
 * <ul>
 *   <li><b>Luck of the Fish</b> raises rare, epic and legendary.</li>
 *   <li><b>Luck of the Sea</b> raises treasure, where the enchanted books are.</li>
 * </ul>
 *
 * <p>Each adds a share of the tier's <em>base</em> weight per level, and everything
 * added is taken out of {@code uncommon}. That is what keeps the two independent:
 * a rod with both applies each in full, and the total still comes to what it was.
 * Normalising instead would have quietly diluted misc and treasure every time the
 * fish tiers went up, which is not what either enchantment is for.
 */
final class RodChances {

    private RodChances() {
    }

    /**
     * The weight of each tier for this player, rod included.
     *
     * @param player   whose rod to read, or null for the unenchanted weights
     * @param settings the configured base weights and bonuses
     */
    static ToDoubleFunction<Rarity> of(Player player, Settings settings) {
        Map<Rarity, Double> weights = weightsFor(player, settings);
        return rarity -> weights.getOrDefault(rarity, 0.0);
    }

    /** Exposed for reporting, so what is shown is what will be rolled. */
    static Map<Rarity, Double> weightsFor(Player player, Settings settings) {
        ItemStack rod = rodOf(player);
        Settings.EnchantmentSettings bonuses = settings.enchantments();

        // Lure is off by default and does the same job as Luck of the Fish when
        // switched on, so the two multiply into one figure for the fish tiers.
        double fish = bonuses.lureMultiplier(levelOf(rod, Enchantment.LURE))
                * bonuses.fishMultiplier(FishingEnchantments.LUCK_OF_THE_FISH.levelOn(rod));
        double sea = bonuses.luckMultiplier(levelOf(rod, Enchantment.LUCK_OF_THE_SEA));

        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        double added = 0;

        for (Rarity rarity : Rarity.values()) {
            double base = settings.chanceOf(rarity.configName());
            double multiplier = rarity.liftedByFishLuck() ? fish
                    : rarity.liftedBySeaLuck() ? sea
                    : 1;

            double raised = base * multiplier;
            added += raised - base;
            weights.put(rarity, raised);
        }

        // The buffer pays for all of it, and cannot go below nothing.
        Rarity buffer = bufferOf(weights);
        if (buffer != null) {
            weights.put(buffer, Math.max(0, weights.get(buffer) - added));
        }
        return weights;
    }

    private static Rarity bufferOf(Map<Rarity, Double> weights) {
        for (Rarity rarity : weights.keySet()) {
            if (rarity.isBuffer()) {
                return rarity;
            }
        }
        return null;
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
