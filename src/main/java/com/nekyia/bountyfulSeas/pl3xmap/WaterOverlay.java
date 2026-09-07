package com.nekyia.bountyfulSeas.pl3xmap;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Publishes areas onto Pl3xMap as a layer.
 *
 * <p>Pl3xMap is optional. {@link #available()} is the guard, and nothing else here
 * runs until it has answered yes - so on a server without Pl3xMap the classes are
 * never loaded and the plugin simply has no overlay.
 */
public final class WaterOverlay {

    private WaterOverlay() {
    }

    /** Whether Pl3xMap is installed and its API is up. */
    public static boolean available() {
        try {
            return Pl3xMap.api() != null;
        } catch (Throwable absent) {
            return false;
        }
    }

    /**
     * Draws these areas as the water layer of one world, replacing whatever was
     * there before.
     *
     * @param worldName the Bukkit world name
     * @param label     the layer's name in the map's layer control
     * @return how many markers were drawn, or -1 when Pl3xMap does not map this world
     */
    public static int publish(String worldName, String label, Collection<? extends MapArea> areas) {
        World world = Pl3xMap.api().getWorldRegistry().get(worldName);
        if (world == null) {
            return -1;
        }

        // Re-registering under the same key would throw, so the old layer goes first.
        world.getLayerRegistry().unregister(WaterLayer.KEY);

        WaterLayer layer = new WaterLayer(world, label);

        for (MapArea area : areas) {
            List<List<MapPoint>> rings = area.rings();
            if (rings.isEmpty()) {
                continue;
            }

            Options options = Options.builder()
                    .fillColor(area.fillColor())
                    .strokeColor(area.strokeColor())
                    .tooltipContent(area.label())
                    .build();

            // One polyline per ring: the first is the outline, the rest are holes
            // punched out of it, which is how Leaflet reads a multi ring polygon.
            List<Polyline> lines = new ArrayList<>(rings.size());
            for (int i = 0; i < rings.size(); i++) {
                List<Point> points = new ArrayList<>(rings.get(i).size());
                for (MapPoint point : rings.get(i)) {
                    points.add(Point.of(point.x(), point.z()));
                }
                lines.add(new Polyline(area.key() + "-ring-" + i, points));
            }

            layer.add(area.key(), Marker.polygon(area.key(), lines), options);
        }

        world.getLayerRegistry().register(WaterLayer.KEY, layer);
        return layer.size();
    }
}
