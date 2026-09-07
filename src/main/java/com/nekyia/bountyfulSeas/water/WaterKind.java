package com.nekyia.bountyfulSeas.water;

/** What sort of water body a region is. Values match the file format. */
public enum WaterKind {
    SEA,
    RIVER,
    LAKE,
    SWAMP;

    static WaterKind of(int code) {
        WaterKind[] values = values();
        return code >= 0 && code < values.length ? values[code] : null;
    }
}
