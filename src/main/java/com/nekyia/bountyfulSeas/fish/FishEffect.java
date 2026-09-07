package com.nekyia.bountyfulSeas.fish;

import org.bukkit.NamespacedKey;

/**
 * One potion effect applied when a fish is eaten.
 *
 * @param type      the effect, for example {@code minecraft:regeneration}
 * @param duration  how long it lasts, in ticks
 * @param amplifier the effect level, 0 being the first tier
 */
public record FishEffect(NamespacedKey type, int duration, int amplifier) {
}
