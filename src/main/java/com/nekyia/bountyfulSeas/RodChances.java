package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.config.Settings;
import com.nekyia.bountyfulSeas.fish.Rarity;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.ToDoubleFunction;

/**
 * Works out a player's tier chances from the rod in their hands.
 *
 * <p>Enchantments scale a tier's <em>base</em> chance rather than adding
 * percentage points, so the balance survives whatever the base numbers are:
 *
 * <ul>
 *   <li><b>Luck of the Fish</b> lifts every fish tier above common.</li>
 *   <li><b>Luck of the Sea</b> lifts treasure, and only treasure.</li>
 *   <li><b>Lure</b> can do what Luck of the Fish does, and is off by default so
 *       it keeps its vanilla job of making the fish bite sooner.</li>
 * </ul>
 *
 * <p>Chances are relative and normalised when they are rolled, so raising the
 * rarer tiers lowers common on its own - there is nothing to subtract by hand.
 */
final class RodChances {

    private RodChances() {
    }

    /**
     * The chance of each tier for this player, rod included.
     *
     * @param player   whose rod to read, or null for the unenchanted odds
     * @param settings the configured base chances and bonuses
     */
    static ToDoubleFunction<Rarity> of(Player player, Settings settings) {
        ItemStack rod = rodOf(player);
        Settings.EnchantmentSettings bonuses = settings.enchantments();

        // Lure and Luck of the Fish both lift the fish tiers, so they multiply
        // together rather than one winning.
        double fish = bonuses.lureMultiplier(levelOf(rod, Enchantment.LURE))
                * bonuses.fishMultiplier(levelOf(rod, custom(bonuses.fishKey())));
        double treasure = bonuses.luckMultiplier(levelOf(rod, Enchantment.LUCK_OF_THE_SEA));

        return rarity -> {
            double base = settings.chanceOf(rarity.configName());
            if (base <= 0) {
                // A tier switched off stays off; a multiplier cannot revive it.
                return 0;
            }
            if (rarity == Rarity.TREASURE) {
                return base * treasure;
            }
            if (rarity.isFish() && rarity != Rarity.COMMON) {
                return base * fish;
            }
            return base;
        };
    }

    /**
     * An enchantment the server defines rather than Minecraft.
     *
     * <p>Looked up by key every time rather than cached, because a datapack reload
     * replaces the registry entry and a held reference would go stale. Returns null
     * when nothing has defined it, which simply means nobody can have it yet.
     */
    private static Enchantment custom(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        if (parsed == null) {
            return null;
        }
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(parsed);
        } catch (RuntimeException unavailable) {
            return null;
        }
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
