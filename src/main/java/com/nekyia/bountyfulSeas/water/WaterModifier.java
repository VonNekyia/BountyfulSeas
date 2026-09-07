package com.nekyia.bountyfulSeas.water;

import java.util.EnumSet;
import java.util.Set;

/**
 * Special features of a water region, stored as a bitfield.
 *
 * <p>The format only ever appends new bits, so bits this reader does not know are
 * masked off and ignored rather than treated as an error.
 */
public enum WaterModifier {
    ICE(0),
    CORALS(1),
    DESERT(2),
    MANGROVE(3),
    CAVE(4);

    private final int bit;

    WaterModifier(int bit) {
        this.bit = bit;
    }

    static Set<WaterModifier> unpack(int bits) {
        Set<WaterModifier> modifiers = EnumSet.noneOf(WaterModifier.class);
        for (WaterModifier modifier : values()) {
            if ((bits & (1 << modifier.bit)) != 0) {
                modifiers.add(modifier);
            }
        }
        return modifiers;
    }
}
