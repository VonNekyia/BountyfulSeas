package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.enchantment.FishingEnchantments;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

/**
 * Runs before the server has worlds, which is the only moment registries are open.
 *
 * <p>Nothing else belongs here. The plugin proper still starts in
 * {@link BountyfulSeas}; this exists because an enchantment cannot be added once
 * the server is up, and so has to be declared a stage earlier than everything else.
 */
public final class BountyfulSeasBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        FishingEnchantments.register(context);
    }
}
