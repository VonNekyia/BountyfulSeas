package com.nekyia.bountyfulSeas.water;

/**
 * Measured depth band, surface to floor, banded at 10 and 30 blocks.
 *
 * <p>{@code 0xFF} in the file means not measured, which this reader reports as
 * null rather than inventing a band.
 */
public enum WaterDepth {
    SHALLOW,
    NORMAL,
    DEEP;

    static WaterDepth of(int code) {
        WaterDepth[] values = values();
        return code >= 0 && code < values.length ? values[code] : null;
    }
}
