package com.nekyia.bountyfulSeas.guide;

import com.nekyia.bountyfulSeas.fish.Fish;
import org.bukkit.inventory.ItemStack;

/**
 * Supplies the item shown for a fish in the guide.
 *
 * <p>The boundary to whatever provides items. The menus never learn that fish are
 * Nexo items, so a fish whose item cannot be built still gets a slot and a name -
 * it just looks plainer.
 */
@FunctionalInterface
public interface FishIcons {

    /** The icon for this fish, or null to fall back to a plain material. */
    ItemStack iconFor(Fish fish);
}
