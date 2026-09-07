package com.nekyia.bountyfulSeas.water;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One classified body of water.
 *
 * <p>Purely geographic: it says what the water is like and nothing about what
 * should happen there. Gameplay is somebody else's job.
 *
 * <p>The {@code id} is an index into the file it was read from and is regenerated
 * by every scan, so it must never be persisted or written into a config.
 *
 * @param depth      measured band, or null when the scan did not measure one
 * @param meanDepth  bathymetry in blocks
 * @param maxDepth   deepest column in blocks
 * @param surfaceY   mean water surface height
 * @param columns    number of water columns, a rough size
 * @param minX       bounding box of the region, inclusive
 */
public record WaterRegion(
        int id,
        WaterKind kind,
        WaterTemperature temperature,
        WaterVegetation vegetation,
        WaterDepth depth,
        Set<WaterModifier> modifiers,
        int surfaceY,
        int columns,
        int meanDepth,
        int maxDepth,
        int minX,
        int minZ,
        int maxX,
        int maxZ
) {

    public WaterRegion {
        modifiers = modifiers == null || modifiers.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(WaterModifier.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(modifiers));
    }

    public boolean has(WaterModifier modifier) {
        return modifiers.contains(modifier);
    }

    /** Cave water cannot see the sky, and the analyzer always calls it a lake. */
    public boolean isCave() {
        return has(WaterModifier.CAVE);
    }
}
