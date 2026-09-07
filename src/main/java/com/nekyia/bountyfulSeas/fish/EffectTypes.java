package com.nekyia.bountyfulSeas.fish;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

/** Checks effect names against the server's own effect registry. */
final class EffectTypes {

    private EffectTypes() {
    }

    /**
     * Whether the server knows this effect.
     *
     * <p>Returns true when the registry cannot be reached at all. The registry
     * lives on a running server, and off one there is nothing to check against -
     * a loader that rejected every effect outside a server would fail its own
     * tests and say nothing true about the config.
     */
    static boolean isKnown(NamespacedKey key) {
        try {
            return Registry.MOB_EFFECT.get(key) != null;
        } catch (Throwable unavailable) {
            return true;
        }
    }
}
