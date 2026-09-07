package com.nekyia.bountyfulSeas.water;

/**
 * Receives the run length encoded scanlines that make up a region's shape.
 *
 * <p>A callback rather than a returned list because a single ocean can run to tens
 * of thousands of runs, and a consumer that only wants to draw them should not pay
 * for a collection of them first.
 */
@FunctionalInterface
public interface WaterRuns {

    /**
     * One scanline of water: every column from {@code x0} to {@code x1} inclusive
     * on row {@code z}.
     */
    void accept(int z, int x0, int x1);
}
