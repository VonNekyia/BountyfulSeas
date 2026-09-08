package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.WaterType;
import com.nekyia.bountyfulSeas.pl3xmap.MapArea;
import com.nekyia.bountyfulSeas.pl3xmap.MapPoint;
import com.nekyia.bountyfulSeas.swarm.Swarms;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

    /** Gold, so a swarm is the thing the eye lands on. */
    private static final int SWARM_RGB = 0xFFC132;

    static List<MapArea> from(WaterMap map, Swarms swarms) {
        List<MapArea> areas = new ArrayList<>(map.regionCount());
        for (int id = 0; id < map.regionCount(); id++) {
            areas.add(area(map, map.region(id), swarms.fishAt(id)));
        }
        return List.copyOf(areas);
    }

    private static MapArea area(WaterMap map, WaterRegion region, String swarmingFish) {
        WaterType type = WaterSpot.waterTypeOf(region);
        int rgb = swarmingFish != null ? SWARM_RGB : colorOf(type);
        String label = label(region, type, swarmingFish);

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

    /**
     * What this water is, in the words the fish rules use.
     *
     * <p>Both vocabularies are shown where they differ - the map calls a warm sea
     * "sea", a fish definition calls it an ocean - so somebody reading the map can
     * tell which fish would accept the spot without translating in their head.
     */
    private static String label(WaterRegion region, WaterType type, String swarmingFish) {
        StringBuilder text = new StringBuilder();

        // Pl3xMap draws tooltips on a dark background and inherits a dark text
        // colour, so unstyled text comes out invisible - a black box on hover.
        // The colour has to be stated here rather than left to the page.
        text.append("<div style=\"color:#D9D9D9;font-family:sans-serif;line-height:1.4\">");

        if (swarmingFish != null) {
            text.append("<span style=\"color:#FFC132\"><b>Swarm: ")
                    .append(swarmingFish).append("</b></span><br>");
        }

        text.append("<b>").append(capitalise(type.name())).append("</b>");
        if (region.kind() != null && !region.kind().name().equalsIgnoreCase(type.name())) {
            text.append(" <i>(map: ").append(capitalise(region.kind().name())).append(")</i>");
        }
        text.append("<br>");

        text.append(capitalise(String.valueOf(region.temperature())))
                .append(", ")
                .append(region.depth() == null ? "unmeasured" : capitalise(region.depth().name()))
                .append("<br>");

        if (!region.modifiers().isEmpty()) {
            text.append("Modifiers: ")
                    .append(region.modifiers().stream()
                            .map(modifier -> capitalise(modifier.name()))
                            .collect(Collectors.joining(", ")))
                    .append("<br>");
        }

        text.append("<span style=\"color:#9A9A9A\">")
                .append("region #").append(region.id()).append("<br>")
                .append(region.columns()).append(" columns<br>")
                .append("depth ").append(region.meanDepth()).append(" / ")
                .append(region.maxDepth()).append(" blocks<br>")
                .append("surface y ").append(region.surfaceY())
                .append("</span></div>");

        return text.toString();
    }

    private static String capitalise(String text) {
        return text.charAt(0) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
