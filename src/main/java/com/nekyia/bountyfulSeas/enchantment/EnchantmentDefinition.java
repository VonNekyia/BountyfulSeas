package com.nekyia.bountyfulSeas.enchantment;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

/**
 * One enchantment this plugin owns, as data and as its own registration.
 *
 * <p>The fields are the ones a datapack file would carry, because that is exactly
 * what this replaces: the same definition, handed to the server through Paper's
 * registry rather than left in a folder for somebody to install by hand.
 *
 * @param name           what the enchantment is called in the plugin's own namespace
 * @param fallbackName   shown to a client with no translation for it
 * @param maxLevel       the highest level obtainable
 * @param weight         how often the enchanting table offers it against everything else
 * @param anvilCost      levels an anvil charges to apply it
 * @param minimumCost    the enchanting table cost at level one, and per level after
 * @param maximumCost    the top of that same range
 */
public record EnchantmentDefinition(
        String name,
        String fallbackName,
        int maxLevel,
        int weight,
        int anvilCost,
        Cost minimumCost,
        Cost maximumCost
) {

    /** An enchanting table cost: a base, plus this much for every level above the first. */
    public record Cost(int base, int perLevel) {
    }

    /** The plugin's namespace, so every enchantment it defines is plainly its own. */
    private static final String NAMESPACE = "bountyfulseas";

    /** The key the enchantment lives under, such as {@code bountyfulseas:luck_of_the_fish}. */
    public Key key() {
        return Key.key(NAMESPACE, name);
    }

    /**
     * The level of this enchantment on an item, or 0 when it is not on it.
     *
     * <p>Looked up through the registry every time rather than held onto, because a
     * registry reload replaces the entry and a kept reference would go stale. An
     * unavailable registry means nobody can be carrying it, so 0 is the answer.
     */
    public int levelOn(ItemStack item) {
        if (item == null) {
            return 0;
        }
        Enchantment enchantment = enchantment();
        return enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    /** The registered enchantment, or null when the registry has no such entry. */
    public Enchantment enchantment() {
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(typedKey());
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    TypedKey<Enchantment> typedKey() {
        return EnchantmentKeys.create(key());
    }

    /**
     * Writes this definition into the registry being composed.
     *
     * <p>Only ever called from the compose event, which is the one moment the
     * enchantment registry accepts entries at all.
     */
    void addTo(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event) {
        event.registry().register(typedKey(), builder -> builder
                .description(Component.translatable("enchantment." + NAMESPACE + "." + name)
                        .fallback(fallbackName))
                .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_FISHING))
                .weight(weight)
                .maxLevel(maxLevel)
                .anvilCost(anvilCost)
                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(
                        minimumCost.base(), minimumCost.perLevel()))
                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(
                        maximumCost.base(), maximumCost.perLevel()))
                .activeSlots(EquipmentSlotGroup.MAINHAND));
    }
}
