package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Condition;
import com.nekyia.bountyfulSeas.fish.Depth;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fish.Terrain;
import com.nekyia.bountyfulSeas.fish.Vegetation;
import com.nekyia.bountyfulSeas.fish.WaterType;
import com.nekyia.bountyfulSeas.fishing.WaterConditions;
import com.nekyia.bountyfulSeas.water.WaterKind;
import com.nekyia.bountyfulSeas.water.WaterDepth;
import com.nekyia.bountyfulSeas.water.WaterModifier;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import com.nekyia.bountyfulSeas.water.WaterTemperature;
import org.bukkit.World;

import java.util.EnumSet;
import java.util.Set;

/**
 * Reads a scanned water region in the vocabulary a fish definition uses.
 *
 * <p>The two vocabularies do not line up, and this is the only place that knows
 * how they differ:
 * <ul>
 *   <li>The map calls open salt water a <em>sea</em>; fish call it an ocean.</li>
 *   <li>The map treats <em>cave</em> as a modifier on a lake; fish treat it as a
 *       water type of its own.</li>
 *   <li>The map treats <em>swamp</em> as a kind; fish treat it as terrain, so a
 *       swamp reads as still water standing in swampy ground.</li>
 *   <li>What a fish calls <em>vegetation</em> holds warm/temperate/cold, which is
 *       the map's <em>temperature</em>, not its vegetation.</li>
 *   <li>The map bands depth three ways; fish only know shallow and deep.</li>
 * </ul>
 */
record WaterSpot(
        WaterType waterType,
        Terrain terrain,
        Vegetation vegetation,
        Depth depth,
        Set<Modifier> modifiers,
        Set<Condition> conditions
) implements WaterConditions {

    /**
     * Reads a region into fish vocabulary. Takes the world conditions rather than
     * the world, so the translation can be exercised without a server running.
     */
    static WaterSpot of(WaterRegion region, Set<Condition> conditions) {
        return new WaterSpot(
                waterTypeOf(region),
                terrain(region),
                vegetation(region.temperature()),
                depth(region.depth()),
                modifiers(region),
                conditions);
    }

    static WaterSpot of(WaterRegion region, World world) {
        return of(region, conditionsOf(world));
    }

    /**
     * The map bands depth at 10 and 30 blocks; a fish only knows shallow and deep.
     * Only the map's deepest band counts as deep, so a normal lake stays shallow
     * water rather than being promoted past every shallow-water fish.
     */
    private static Depth depth(WaterDepth measured) {
        return measured == WaterDepth.DEEP ? Depth.DEEP : Depth.SHALLOW;
    }

    /** Shared with the map adapter, so both colour and catch agree on the type. */
    static WaterType waterTypeOf(WaterRegion region) {
        if (region.isCave()) {
            return WaterType.CAVE;
        }
        if (region.kind() == null) {
            return WaterType.LAKE;
        }
        return switch (region.kind()) {
            case SEA -> WaterType.OCEAN;
            case RIVER -> WaterType.RIVER;
            // Swamp water is still water; that it is a swamp is terrain, below.
            case LAKE, SWAMP -> WaterType.LAKE;
        };
    }

    private static Terrain terrain(WaterRegion region) {
        if (region.kind() == WaterKind.SWAMP || region.has(WaterModifier.MANGROVE)) {
            return Terrain.SWAMP;
        }
        if (region.has(WaterModifier.DESERT)) {
            return Terrain.DESERT;
        }
        return Terrain.PLAINS;
    }

    /** A fish definition spells temperature as vegetation, so this reads across. */
    private static Vegetation vegetation(WaterTemperature temperature) {
        if (temperature == null) {
            return Vegetation.TEMPERATE;
        }
        return switch (temperature) {
            case WARM -> Vegetation.WARM;
            case MEDIUM -> Vegetation.TEMPERATE;
            case COLD -> Vegetation.COLD;
        };
    }

    private static Set<Modifier> modifiers(WaterRegion region) {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (region.has(WaterModifier.CORALS)) {
            modifiers.add(Modifier.CORAL);
        }
        if (region.has(WaterModifier.ICE)) {
            modifiers.add(Modifier.ICE);
        }
        return modifiers;
    }

    /** What the world is doing, which the map has nothing to say about. */
    static Set<Condition> conditionsOf(World world) {
        Set<Condition> conditions = EnumSet.noneOf(Condition.class);

        long time = world.getTime();
        conditions.add(time >= 13000 && time < 23000 ? Condition.NIGHT : Condition.DAY);

        if (world.hasStorm()) {
            conditions.add(Condition.RAIN);
        }
        return conditions;
    }

}
