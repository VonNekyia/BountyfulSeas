package com.nekyia.bountyfulSeas.water;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads {@code water_regions.bin}, the output of the offline water map generator.
 *
 * <p>Format version 2, big endian throughout: an 80 byte header, a region table of
 * 48 byte entries, run length encoded geometry, and a spatial index that maps a
 * block position to the handful of regions worth testing.
 *
 * <p>Lookup goes position - index cell - candidate regions - run test. A cell is
 * 64x64 blocks and a region may cover only part of it, so a candidate still has to
 * be confirmed against the region's own runs.
 *
 * <p>The whole file is held in memory. It is a few tens of megabytes for a large
 * world, and every lookup is a handful of array reads with no allocation, which is
 * what makes it safe to call while a player is reeling in.
 */
public final class WaterMap {

    private static final long MAGIC = 0x4D43574154455200L;
    private static final int SUPPORTED_VERSION = 2;

    private static final int HEADER_SIZE = 80;
    private static final int REGION_ENTRY_SIZE = 48;
    private static final int RUN_SIZE = 12;

    private static final int CELL_EMPTY = 0xFFFFFFFF;
    private static final int CELL_INLINE = 0x80000000;

    private final ByteBuffer buffer;
    private final int regionCount;
    private final int cellShift;
    private final int cellsX;
    private final int cellsZ;
    private final int originCellX;
    private final int originCellZ;
    private final int regionTableOffset;
    private final int geometryOffset;
    private final int cellsOffset;
    private final int listsOffset;

    private final int seaLevel;
    private final int minecraftDataVersion;

    private WaterMap(ByteBuffer buffer) throws IOException {
        this.buffer = buffer;

        if (buffer.capacity() < HEADER_SIZE || buffer.getLong(0) != MAGIC) {
            throw new IOException("not a water_regions.bin file");
        }
        int version = buffer.getShort(8) & 0xFFFF;
        if (version != SUPPORTED_VERSION) {
            throw new IOException("unsupported water map version " + version
                    + ", this build reads version " + SUPPORTED_VERSION);
        }

        minecraftDataVersion = buffer.getInt(12);
        seaLevel = buffer.getShort(16);
        cellShift = buffer.get(18) & 0xFF;
        regionCount = buffer.getInt(20);
        regionTableOffset = (int) buffer.getLong(40);
        geometryOffset = (int) buffer.getLong(48);

        int index = (int) buffer.getLong(56);
        cellsX = buffer.getInt(index);
        cellsZ = buffer.getInt(index + 4);
        originCellX = buffer.getInt(index + 8);
        originCellZ = buffer.getInt(index + 12);
        cellsOffset = index + 16;
        // The + 4 steps over list_length, so lists[i] sits at listsOffset + 4 * i.
        listsOffset = cellsOffset + 4 * cellsX * cellsZ + 4;
    }

    /** Reads the file at the given path. */
    public static WaterMap read(Path path) throws IOException {
        return new WaterMap(ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.BIG_ENDIAN));
    }

    public int regionCount() {
        return regionCount;
    }

    /** Water surface height the scan settled on, which may not be vanilla's 62. */
    public int seaLevel() {
        return seaLevel;
    }

    /** Data version of the world that was scanned, or 0 when it could not be read. */
    public int minecraftDataVersion() {
        return minecraftDataVersion;
    }

    /**
     * The water region covering this block column, or null when none does.
     *
     * <p>Null is not an error. It means dry land, water too small to be a region,
     * or water the analyzer chose not to classify. What that means is the caller's
     * decision.
     */
    public WaterRegion regionAt(int x, int z) {
        int id = regionIdAt(x, z);
        return id < 0 ? null : region(id);
    }

    /** The region id at this position, or -1. Cheaper than {@link #regionAt} when only presence matters. */
    public int regionIdAt(int x, int z) {
        int cellX = (x >> cellShift) - originCellX;
        int cellZ = (z >> cellShift) - originCellZ;
        if (cellX < 0 || cellZ < 0 || cellX >= cellsX || cellZ >= cellsZ) {
            return -1;
        }

        int cell = buffer.getInt(cellsOffset + 4 * (cellZ * cellsX + cellX));
        if (cell == CELL_EMPTY) {
            return -1;
        }

        if ((cell & CELL_INLINE) != 0) {
            int id = cell & ~CELL_INLINE;
            return covers(id, x, z) ? id : -1;
        }

        int candidates = buffer.getInt(listsOffset + 4 * cell);
        for (int i = 1; i <= candidates; i++) {
            int id = buffer.getInt(listsOffset + 4 * (cell + i));
            if (covers(id, x, z)) {
                return id;
            }
        }
        return -1;
    }

    /** Reads one region out of the table. */
    public WaterRegion region(int id) {
        if (id < 0 || id >= regionCount) {
            throw new IndexOutOfBoundsException("no water region " + id);
        }
        int entry = entry(id);
        return new WaterRegion(
                id,
                WaterKind.of(buffer.get(entry + 4) & 0xFF),
                WaterTemperature.of(buffer.get(entry + 5) & 0xFF),
                WaterVegetation.of(buffer.get(entry + 6) & 0xFF),
                WaterDepth.of(buffer.get(entry + 7) & 0xFF),
                WaterModifier.unpack(buffer.getShort(entry + 8) & 0xFFFF),
                buffer.getShort(entry + 10),
                buffer.getInt(entry + 28),
                buffer.getShort(entry + 40) & 0xFFFF,
                buffer.getShort(entry + 42) & 0xFFFF,
                buffer.getInt(entry + 12),
                buffer.getInt(entry + 16),
                buffer.getInt(entry + 20),
                buffer.getInt(entry + 24));
    }

    /**
     * Walks the shape of a region, scanline by scanline.
     *
     * <p>Runs arrive sorted by {@code (z, x0)} and never overlap, which is what
     * lets a consumer merge them into larger shapes as it goes.
     */
    public void forEachRun(int id, WaterRuns visitor) {
        if (id < 0 || id >= regionCount) {
            throw new IndexOutOfBoundsException("no water region " + id);
        }
        int entry = entry(id);
        int first = buffer.getInt(entry + 32);
        int count = buffer.getInt(entry + 36);

        for (int i = 0; i < count; i++) {
            int run = geometryOffset + (first + i) * RUN_SIZE;
            visitor.accept(buffer.getInt(run), buffer.getInt(run + 4), buffer.getInt(run + 8));
        }
    }

    private int entry(int id) {
        return regionTableOffset + id * REGION_ENTRY_SIZE;
    }

    /**
     * Whether a region actually covers this column.
     *
     * <p>Runs are sorted by {@code (z, x0)} and never overlap, so a binary search
     * settles it without walking a region that may hold tens of thousands of them.
     */
    private boolean covers(int id, int x, int z) {
        int entry = entry(id);
        if (x < buffer.getInt(entry + 12) || x > buffer.getInt(entry + 20)) {
            return false;
        }
        if (z < buffer.getInt(entry + 16) || z > buffer.getInt(entry + 24)) {
            return false;
        }

        int low = buffer.getInt(entry + 32);
        int high = low + buffer.getInt(entry + 36) - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int run = geometryOffset + middle * RUN_SIZE;
            int runZ = buffer.getInt(run);
            int x0 = buffer.getInt(run + 4);
            int x1 = buffer.getInt(run + 8);

            if (runZ < z || (runZ == z && x1 < x)) {
                low = middle + 1;
            } else if (runZ > z || x0 > x) {
                high = middle - 1;
            } else {
                return true;
            }
        }
        return false;
    }
}
