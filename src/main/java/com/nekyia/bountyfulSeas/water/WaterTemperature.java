package com.nekyia.bountyfulSeas.water;

/** How warm the water is, read from the biome. Values match the file format. */
public enum WaterTemperature {
    WARM,
    MEDIUM,
    COLD;

    static WaterTemperature of(int code) {
        WaterTemperature[] values = values();
        return code >= 0 && code < values.length ? values[code] : null;
    }
}
