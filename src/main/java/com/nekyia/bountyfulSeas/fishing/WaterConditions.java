package com.nekyia.bountyfulSeas.fishing;

import com.nekyia.bountyfulSeas.fish.Condition;
import com.nekyia.bountyfulSeas.fish.Depth;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fish.Terrain;
import com.nekyia.bountyfulSeas.fish.Vegetation;
import com.nekyia.bountyfulSeas.fish.WaterType;

import java.util.Set;

/**
 * What the water is like where somebody is fishing, in the vocabulary a fish
 * definition is written in.
 *
 * <p>This is the boundary to the map data. The selector never learns that water
 * regions are read from a binary file, that a sea is called a sea there rather
 * than an ocean, or that a cave is a modifier rather than a kind. Whoever has the
 * map answers these questions; translating the two vocabularies is their problem.
 */
public interface WaterConditions {

    WaterType waterType();

    Terrain terrain();

    Vegetation vegetation();

    Depth depth();

    /** Features present here, such as coral or ice. Possibly empty. */
    Set<Modifier> modifiers();

    /** What the world is doing right now, such as rain and time of day. */
    Set<Condition> conditions();
}
