package com.nekyia.bountyfulSeas.pl3xmap;

import java.util.List;

/**
 * One shaded area to draw on the web map.
 *
 * <p>The boundary to the map. The overlay never learns what water is, what a fish
 * is, or where any of it was read from - it is handed rings, a colour and a label,
 * and it draws them.
 */
public interface MapArea {

    /** Unique among the areas in one layer. */
    String key();

    /** Shown when someone hovers the area. Plain text or simple HTML. */
    String label();

    /** Fill colour as {@code 0xAARRGGBB}. The alpha is what makes it an overlay. */
    int fillColor();

    /** Outline colour as {@code 0xAARRGGBB}. */
    int strokeColor();

    /**
     * The area's outline: the outer ring first, then any holes.
     *
     * <p>Each ring is a closed loop of block corners, without repeating the first
     * point at the end. An area with no rings is not drawn.
     */
    List<List<MapPoint>> rings();
}
