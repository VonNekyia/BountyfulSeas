package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.WaterType;
import com.nekyia.bountyfulSeas.pl3xmap.MapArea;
import com.nekyia.bountyfulSeas.pl3xmap.MapPoint;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns scanned water regions into shapes the map can draw.
 *
 * <p>The other half of the water bridge: {@link WaterSpot} says what a region means
 * to a fish, this says what it looks like on a map. Neither the map module nor the
 * water module knows the other exists.
 */
final class WaterMapAreas {

    /** Fill is translucent so the terrain stays readable underneath. */
    private static final int FILL_ALPHA = 0x55000000;
    private static final int STROKE_ALPHA = 0xCC000000;

    private WaterMapAreas() {
    }

    static List<MapArea> from(WaterMap map) {
        List<MapArea> areas = new ArrayList<>(map.regionCount());
        for (int id = 0; id < map.regionCount(); id++) {
            areas.add(area(map, map.region(id)));
        }
        return List.copyOf(areas);
    }

    private static MapArea area(WaterMap map, WaterRegion region) {
        WaterType type = WaterSpot.waterTypeOf(region);
        int rgb = colorOf(type);
        String label = label(region, type);

        return new MapArea() {
            @Override
            public String key() {
                return "bs-water-" + region.id();
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public int fillColor() {
                return FILL_ALPHA | rgb;
            }

            @Override
            public int strokeColor() {
                return STROKE_ALPHA | rgb;
            }

            @Override
            public List<List<MapPoint>> rings() {
                return WaterOutline.trace(map, region.id());
            }
        };
    }

    /** One colour per water type, so the map reads the same way the fish rules do. */
    private static int colorOf(WaterType type) {
        return switch (type) {
            case OCEAN -> 0x1E63C8;
            case RIVER -> 0x36B8D8;
            case LAKE -> 0x35C08A;
            case CAVE -> 0x8A6BC8;
        };
    }

    private static String label(WaterRegion region, WaterType type) {
        return "<b>" + capitalise(type.name()) + "</b><br>"
                + "region #" + region.id() + "<br>"
                + region.columns() + " columns<br>"
                + "depth " + region.meanDepth() + " / " + region.maxDepth() + " blocks<br>"
                + "surface y " + region.surfaceY();
    }

    private static String capitalise(String text) {
        return text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
