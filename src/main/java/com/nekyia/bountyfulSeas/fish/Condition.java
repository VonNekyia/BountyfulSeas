package com.nekyia.bountyfulSeas.fish;

/** A world condition that must hold while fishing. */
public enum Condition implements ConfigValue {

    /** Precipitation is falling here. */
    RAIN,

    /** A thunderstorm, which in Minecraft always carries rain with it. */
    STORM,

    NIGHT,
    DAY
}
