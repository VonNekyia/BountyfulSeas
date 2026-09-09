package com.nekyia.bountyfulSeas.enchantment;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.event.RegistryEvents;

/**
 * The enchantments this plugin defines, and the one place they are registered.
 *
 * <p>An enchantment is part of the server's registry, and a registry is only open
 * for entries once, before any world exists. That is why this hangs off the
 * bootstrap rather than onEnable: by the time a plugin is enabled the registry has
 * been frozen and nothing can be added to it.
 *
 * <p>A datapack would be the other way to do this, and it was how this started, but
 * it asked the server owner to copy a folder into every world before any of it
 * worked - and silently did nothing until they did.
 */
public final class FishingEnchantments {

    /**
     * Raises the odds of a rare, epic or legendary catch.
     *
     * <p>Deliberately a fishing enchantment in its own right rather than a rework of
     * Luck of the Sea, which keeps its vanilla job of pulling up better treasure.
     */
    public static final EnchantmentDefinition LUCK_OF_THE_FISH = new EnchantmentDefinition(
            "luck_of_the_fish",
            "Luck of the Fish",
            5,
            2,
            4,
            new EnchantmentDefinition.Cost(15, 9),
            new EnchantmentDefinition.Cost(65, 9));

    private FishingEnchantments() {
    }

    /**
     * Hands every definition to the server while the registry is still being built.
     *
     * @param context the plugin's bootstrap, which owns the lifecycle events
     */
    public static void register(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.ENCHANTMENT.compose().newHandler(LUCK_OF_THE_FISH::addTo));
    }
}
