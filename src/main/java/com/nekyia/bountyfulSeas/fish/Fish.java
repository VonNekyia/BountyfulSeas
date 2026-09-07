package com.nekyia.bountyfulSeas.fish;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One fish, as defined by a single entry in a {@code fishes/*.yaml} file.
 *
 * <p>Every trait set works as a filter: an empty set places no restriction on
 * that axis, so a fish with no {@code water_type} may be caught in any water.
 * A populated set means the catch location has to match one of its values.
 *
 * @param id          the YAML key the fish was declared under, unique across all files
 * @param category    the file the fish came from, without its extension
 * @param name        display name, MiniMessage as everywhere else in the stack
 * @param item        the item this fish is handed out as, for example {@code nexo:my_fish}
 * @param lore        flavour text lines, shown under the category; possibly empty
 * @param minWeight   lower bound of the rolled weight, never above {@code maxWeight}
 * @param maxWeight   upper bound of the rolled weight
 * @param minLength   lower bound of the rolled length, never above {@code maxLength}
 * @param maxLength   upper bound of the rolled length
 * @param baitLocked  whether the fish can only be caught with a bait that names it
 * @param spawnWeight relative weight in the catch roll; 0 disables the fish
 * @param rarity      the rarity tier the fish is announced as
 * @param onEat       what eating it does, or null when the fish is not edible
 */
public record Fish(
        String id,
        String category,
        String name,
        String item,
        List<String> lore,
        double minWeight,
        double maxWeight,
        double minLength,
        double maxLength,
        Set<WaterType> waterTypes,
        Set<Terrain> terrains,
        Set<Vegetation> vegetations,
        Set<Depth> depths,
        Set<Modifier> modifiers,
        Set<Condition> conditions,
        boolean baitLocked,
        int spawnWeight,
        Rarity rarity,
        OnEat onEat
) {

    public Fish {
        lore = List.copyOf(lore);
        waterTypes = immutableCopy(waterTypes, WaterType.class);
        terrains = immutableCopy(terrains, Terrain.class);
        vegetations = immutableCopy(vegetations, Vegetation.class);
        depths = immutableCopy(depths, Depth.class);
        modifiers = immutableCopy(modifiers, Modifier.class);
        conditions = immutableCopy(conditions, Condition.class);
    }

    private static <E extends Enum<E>> Set<E> immutableCopy(Set<E> values, Class<E> type) {
        return values == null || values.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(type))
                : Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    /** Whether this fish can be eaten at all, which is what an on_eat block declares. */
    public boolean edible() {
        return onEat != null;
    }

    /** Whether an empty filter or a matching value lets this fish through. */
    private static <E extends Enum<E>> boolean allows(Set<E> filter, E value) {
        return filter.isEmpty() || filter.contains(value);
    }

    public boolean allowsWaterType(WaterType value) {
        return allows(waterTypes, value);
    }

    public boolean allowsTerrain(Terrain value) {
        return allows(terrains, value);
    }

    public boolean allowsVegetation(Vegetation value) {
        return allows(vegetations, value);
    }

    public boolean allowsDepth(Depth value) {
        return allows(depths, value);
    }

    public boolean allowsModifier(Modifier value) {
        return allows(modifiers, value);
    }

    public boolean allowsCondition(Condition value) {
        return allows(conditions, value);
    }
}
