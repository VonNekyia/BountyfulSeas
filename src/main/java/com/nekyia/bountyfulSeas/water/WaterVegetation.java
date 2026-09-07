package com.nekyia.bountyfulSeas.water;

/** How planted the water is. Values match the file format. */
public enum WaterVegetation {
    NONE,
    SPARSE,
    NORMAL,
    JUNGLE;

    static WaterVegetation of(int code) {
        WaterVegetation[] values = values();
        return code >= 0 && code < values.length ? values[code] : null;
    }
}
