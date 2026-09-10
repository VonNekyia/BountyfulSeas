package com.nekyia.bountyfulSeas.fish;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which tiers are fish and which are objects.
 *
 * <p>An object is something the line brings up that was never swimming: junk, or a
 * chest's worth of enchanted books. It carries no length, so it has no maximum in
 * its definition, no measurement in chat and no record to beat.
 *
 * <p>The split is configured rather than fixed, because it is also what the two
 * luck enchantments are worked out from: one of them lifts the fish side, the
 * other lifts the object side, and each side's rarest tier is the one that never
 * pays for it. Naming the sides is therefore enough to say what the enchantments
 * do, however many tiers a server invents.
 */
public record TierKinds(Set<Rarity> objects) {

    public TierKinds {
        objects = objects.isEmpty()
                ? EnumSet.noneOf(Rarity.class)
                : EnumSet.copyOf(objects);
    }

    /**
     * Reads the split from configured names, ignoring any it does not know.
     *
     * <p>An unknown name is not an error here: whoever read the config has already
     * said so, and a tier that does not exist simply describes nothing.
     */
    public static TierKinds of(Collection<String> objectNames) {
        Set<Rarity> objects = EnumSet.noneOf(Rarity.class);
        for (String name : objectNames) {
            for (Rarity rarity : Rarity.values()) {
                if (rarity.configName().equals(name.trim().toLowerCase(Locale.ROOT))) {
                    objects.add(rarity);
                }
            }
        }
        return new TierKinds(objects);
    }

    /** Whether this tier holds actual fish. */
    public boolean isFish(Rarity rarity) {
        return rarity != null && !objects.contains(rarity);
    }

    /** Whether entries in this tier are objects, and so carry no length. */
    public boolean isObject(Rarity rarity) {
        return rarity != null && objects.contains(rarity);
    }
}
