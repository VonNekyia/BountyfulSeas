package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.pl3xmap.MapPoint;
import com.nekyia.bountyfulSeas.water.WaterMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Traces the outline of a water region.
 *
 * <p>The map stores water as run length encoded scanlines, one block tall. Drawing
 * those directly gives a field of little squares. What is wanted is the shape's
 * edge, so this walks the boundary instead: an edge between two water columns is
 * interior and cancels, and only the edges with water on exactly one side survive.
 * Linking the survivors end to end gives closed rings - the outline, plus one more
 * ring for every island the water surrounds.
 *
 * <p>It works on the runs rather than on individual columns, so the cost follows
 * the number of scanlines and not the area. A large ocean is millions of columns
 * but only tens of thousands of runs.
 */
final class WaterOutline {

    private WaterOutline() {
    }

    /**
     * The rings making up a region, largest first, so the outline comes before the
     * holes it contains.
     */
    static List<List<MapPoint>> trace(WaterMap map, int regionId) {
        // Runs arrive sorted by (z, x0), so each row's spans land in order.
        Map<Integer, List<int[]>> rows = new TreeMap<>();
        map.forEachRun(regionId, (z, x0, x1) ->
                rows.computeIfAbsent(z, key -> new ArrayList<>()).add(new int[]{x0, x1 + 1}));

        Map<Long, Deque<Long>> edges = new HashMap<>();
        for (Map.Entry<Integer, List<int[]>> row : rows.entrySet()) {
            int z = row.getKey();
            List<int[]> above = rows.getOrDefault(z - 1, List.of());
            List<int[]> below = rows.getOrDefault(z + 1, List.of());

            for (int[] span : row.getValue()) {
                int lo = span[0];
                int hi = span[1];

                // Sides are always exposed: a neighbour there would have been part
                // of this same run.
                edge(edges, lo, z + 1, lo, z);
                edge(edges, hi, z, hi, z + 1);

                // Top and bottom only where the neighbouring row leaves a gap.
                for (int[] gap : gaps(lo, hi, above)) {
                    edge(edges, gap[0], z, gap[1], z);
                }
                for (int[] gap : gaps(lo, hi, below)) {
                    edge(edges, gap[1], z + 1, gap[0], z + 1);
                }
            }
        }

        List<List<MapPoint>> rings = new ArrayList<>();
        for (Long start : List.copyOf(edges.keySet())) {
            while (edges.containsKey(start)) {
                List<MapPoint> ring = walk(edges, start);
                if (ring.isEmpty()) {
                    // The chain from here does not close, so stop rather than retry
                    // the same broken start forever.
                    edges.remove(start);
                    break;
                }
                if (ring.size() >= 4) {
                    rings.add(simplify(ring));
                }
            }
        }

        rings.sort(Comparator.comparingDouble(WaterOutline::area).reversed());
        return List.copyOf(rings);
    }

    /** Follows edges from a point until the path returns to it. */
    private static List<MapPoint> walk(Map<Long, Deque<Long>> edges, long start) {
        List<MapPoint> ring = new ArrayList<>();
        long at = start;

        while (true) {
            Deque<Long> outgoing = edges.get(at);
            if (outgoing == null) {
                // A broken chain cannot close, so the partial ring is dropped.
                return List.of();
            }
            long next = outgoing.poll();
            if (outgoing.isEmpty()) {
                edges.remove(at);
            }

            ring.add(new MapPoint(x(at), z(at)));
            at = next;
            if (at == start) {
                return ring;
            }
        }
    }

    /**
     * Drops the points that sit in the middle of a straight stretch.
     *
     * <p>A boundary is traced one block at a time, so a coastline running straight
     * for four hundred blocks arrives as four hundred points describing a line with
     * two ends. Only the corners carry information.
     */
    private static List<MapPoint> simplify(List<MapPoint> ring) {
        int size = ring.size();
        List<MapPoint> corners = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            MapPoint previous = ring.get((i - 1 + size) % size);
            MapPoint current = ring.get(i);
            MapPoint next = ring.get((i + 1) % size);

            boolean straight = (previous.x() == current.x() && current.x() == next.x())
                    || (previous.z() == current.z() && current.z() == next.z());
            if (!straight) {
                corners.add(current);
            }
        }
        return corners.size() >= 4 ? List.copyOf(corners) : List.copyOf(ring);
    }

    /** Shoelace area, used only to tell the outline from the holes inside it. */
    private static double area(List<MapPoint> ring) {
        double total = 0;
        for (int i = 0; i < ring.size(); i++) {
            MapPoint a = ring.get(i);
            MapPoint b = ring.get((i + 1) % ring.size());
            total += (double) a.x() * b.z() - (double) b.x() * a.z();
        }
        return Math.abs(total) / 2.0;
    }

    /**
     * The parts of {@code [lo, hi)} that the given spans do not cover.
     *
     * <p>Spans arrive sorted and never overlap, which is what lets this be a single
     * sweep rather than a set difference.
     */
    private static List<int[]> gaps(int lo, int hi, List<int[]> spans) {
        List<int[]> gaps = new ArrayList<>();
        int cursor = lo;

        for (int[] span : spans) {
            if (span[1] <= cursor) {
                continue;
            }
            if (span[0] >= hi) {
                break;
            }
            if (span[0] > cursor) {
                gaps.add(new int[]{cursor, Math.min(span[0], hi)});
            }
            cursor = Math.max(cursor, span[1]);
            if (cursor >= hi) {
                break;
            }
        }

        if (cursor < hi) {
            gaps.add(new int[]{cursor, hi});
        }
        return gaps;
    }

    private static void edge(Map<Long, Deque<Long>> edges, int fromX, int fromZ, int toX, int toZ) {
        edges.computeIfAbsent(point(fromX, fromZ), key -> new ArrayDeque<>()).add(point(toX, toZ));
    }

    private static long point(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int x(long point) {
        return (int) (point >> 32);
    }

    private static int z(long point) {
        return (int) point;
    }
}
