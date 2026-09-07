package com.nekyia.bountyfulSeas.pl3xmap;

import net.pl3x.map.core.markers.layer.WorldLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The map layer holding the fishable water.
 *
 * <p>The markers do not move once drawn, because the water map is a file produced
 * offline: it only changes when somebody rescans the world. So the layer is built
 * once and left alone, with no update interval to keep re-serialising thousands of
 * rectangles that have not changed.
 */
public final class WaterLayer extends WorldLayer {

    public static final String KEY = "bountyfulseas-water";

    private final Map<String, Marker<?>> markers = new LinkedHashMap<>();

    WaterLayer(@NotNull World world, @NotNull String label) {
        super(KEY, world, () -> label);

        setShowControls(true);
        setDefaultHidden(false);
        setPriority(120);
        setZIndex(500);
    }

    void add(String key, Marker<?> marker, Options options) {
        markers.put(key, marker.setOptions(options));
    }

    int size() {
        return markers.size();
    }

    @Override
    public @NotNull Collection<Marker<?>> getMarkers() {
        return markers.values();
    }
}
