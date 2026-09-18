package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Condition;
import com.nekyia.bountyfulSeas.fish.Depth;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fish.Rarity;
import com.nekyia.bountyfulSeas.fish.Terrain;
import com.nekyia.bountyfulSeas.fish.Vegetation;
import com.nekyia.bountyfulSeas.fish.WaterType;
import com.nekyia.bountyfulSeas.fishing.Chance;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.water.WaterMap;
import com.nekyia.bountyfulSeas.water.WaterRegion;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Every kind of water the scan found, and what bites in it.
 *
 * <p>The map holds hundreds of regions but only a few dozen <em>kinds</em> of
 * water: a warm shallow ocean is the same fishing whether it is one region or
 * forty. So the regions are tallied by what a fish definition can actually see -
 * water type, terrain, temperature and depth - and each of those gets one icon.
 *
 * <p>Coral, ice and the weather are toggles rather than part of the tally. They are
 * the things a spot has some of the time, and the question worth asking is what
 * they would change: turn coral on and every combination that has no coral fish in
 * it goes to a barrier at once.
 *
 * <p>A barrier is not an error. Most of them are correct - there is no coral in a
 * river, so a river with coral holds nothing - and that is the point: the ones that
 * are <em>not</em> barriers when they should be are visible in the same glance.
 * Nothing here forbids a combination; the fish files decide, and this only reports
 * what they decided.
 */
final class RegionsMenu extends RoseGUI {

    private static final int[] PROFILE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    /** The water as a fish definition sees it, with everything changeable left out. */
    record Profile(WaterType waterType, Terrain terrain, Vegetation vegetation, Depth depth) {

        String describe() {
            return lower(waterType) + "  " + lower(terrain) + "  "
                    + lower(vegetation) + "  " + lower(depth);
        }
    }

    /** What the scan actually found of one kind of water. */
    record Tally(int regions, long columns, int coral, int ice) {

        Tally plus(WaterRegion region, Set<Modifier> present) {
            return new Tally(regions + 1, columns + region.columns(),
                    coral + (present.contains(Modifier.CORAL) ? 1 : 0),
                    ice + (present.contains(Modifier.ICE) ? 1 : 0));
        }
    }

    private final WaterMap map;
    private final FishLibrary library;
    private final ToDoubleFunction<Rarity> chanceOf;
    private final int anglerLevel;
    private final Set<Modifier> modifiers;
    private final Set<Condition> conditions;

    RegionsMenu(Player player, WaterMap map, FishLibrary library,
                ToDoubleFunction<Rarity> chanceOf, int anglerLevel,
                Set<Modifier> modifiers, Set<Condition> conditions) {
        super(player, "bountyfulseas-regions",
                Component.text("Water kinds", NamedTextColor.AQUA, TextDecoration.BOLD), 6);
        this.map = map;
        this.library = library;
        this.chanceOf = chanceOf;
        this.anglerLevel = anglerLevel;
        this.modifiers = EnumSet.copyOf(modifiers.isEmpty()
                ? EnumSet.noneOf(Modifier.class) : modifiers);
        this.conditions = EnumSet.copyOf(conditions.isEmpty()
                ? EnumSet.of(Condition.DAY) : conditions);
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        fillGui(new RoseItem.Builder()
                .material(Material.BLACK_STAINED_GLASS_PANE)
                .displayName(Component.empty())
                .build());

        toggles();

        Map<Profile, Tally> found = tally();
        List<Map.Entry<Profile, Tally>> ordered = new ArrayList<>(found.entrySet());
        ordered.sort(Comparator.comparingInt(
                (Map.Entry<Profile, Tally> entry) -> entry.getValue().regions()).reversed());

        int index = 0;
        for (Map.Entry<Profile, Tally> entry : ordered) {
            if (index >= PROFILE_SLOTS.length) {
                break;
            }
            addItem(PROFILE_SLOTS[index++], profileIcon(entry.getKey(), entry.getValue()));
        }

        addItem(49, new RoseItem.Builder()
                .material(Material.BOOK)
                .displayName(Component.text("What this is", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(
                        line(map.regionCount() + " scanned regions, tallied into "
                                + found.size() + " kinds of water", NamedTextColor.GRAY),
                        line("read as a level " + anglerLevel + " angler", NamedTextColor.GRAY),
                        blank(),
                        line("a barrier means nothing bites there", NamedTextColor.DARK_GRAY),
                        line("with the toggles as they stand", NamedTextColor.DARK_GRAY),
                        blank(),
                        line("swarming fish are left out: a swarm", NamedTextColor.DARK_GRAY),
                        line("is one region at one time, not a kind", NamedTextColor.DARK_GRAY))
                .build());
    }

    /** The row that changes the question rather than the water. */
    private void toggles() {
        addItem(2, toggle("coral", Material.BRAIN_CORAL, modifiers.contains(Modifier.CORAL),
                () -> flip(modifiers, Modifier.CORAL)));
        addItem(3, toggle("ice", Material.ICE, modifiers.contains(Modifier.ICE),
                () -> flip(modifiers, Modifier.ICE)));
        addItem(5, toggle("night", Material.CLOCK, conditions.contains(Condition.NIGHT),
                this::flipNight));
        addItem(6, toggle("rain", Material.WATER_BUCKET, conditions.contains(Condition.RAIN),
                () -> flip(conditions, Condition.RAIN)));
        addItem(7, toggle("storm", Material.TRIDENT, conditions.contains(Condition.STORM),
                () -> flip(conditions, Condition.STORM)));
    }

    private RoseItem toggle(String label, Material material, boolean on, Runnable flip) {
        return new RoseItem.Builder()
                .material(on ? material : Material.GRAY_DYE)
                .displayName(Component.text(label, on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(line(on ? "on - click to drop it" : "off - click to assume it",
                        NamedTextColor.GRAY))
                .build()
                .onClick(click -> {
                    flip.run();
                    reopen();
                });
    }

    private <E extends Enum<E>> void flip(Set<E> set, E value) {
        if (!set.remove(value)) {
            set.add(value);
        }
    }

    /** Day and night are the same switch: the water is always one or the other. */
    private void flipNight() {
        if (conditions.remove(Condition.NIGHT)) {
            conditions.add(Condition.DAY);
        } else {
            conditions.remove(Condition.DAY);
            conditions.add(Condition.NIGHT);
        }
    }

    private void reopen() {
        new RegionsMenu(player, map, library, chanceOf, anglerLevel, modifiers, conditions).open();
    }

    /**
     * Every region read into fish vocabulary and counted by kind.
     *
     * <p>Walked in full, which is a few hundred reads out of a mapped buffer and
     * cheap enough to do on opening rather than keep in step with a reload.
     */
    private Map<Profile, Tally> tally() {
        Map<Profile, Tally> found = new LinkedHashMap<>();
        for (int id = 0; id < map.regionCount(); id++) {
            WaterRegion region = map.region(id);
            // No swarms and no weather: the profile is what the water is, not what
            // is happening in it.
            WaterSpot spot = WaterSpot.of(region, Set.of(), null);
            Profile profile = new Profile(spot.waterType(), spot.terrain(),
                    spot.vegetation(), spot.depth());
            found.put(profile, found.getOrDefault(profile, new Tally(0, 0, 0, 0))
                    .plus(region, spot.modifiers()));
        }
        return found;
    }

    private RoseItem profileIcon(Profile profile, Tally tally) {
        WaterSpot spot = spotFor(profile, modifiers, conditions);
        List<Chance> chances = FishSelector.chances(library.all(), spot, anglerLevel, chanceOf);
        List<Fish> locked = FishSelector.outOfReach(library.all(), spot, anglerLevel);

        // Junk has no requirements, so it bites everywhere. Counting it would make
        // every square green and the barrier would never mean anything.
        long fishBiting = chances.stream().filter(chance -> !chance.fish().isCatchMarker()).count();
        long byToggle = chances.stream().filter(chance -> asked(chance.fish())).count();

        // A modifier can only add fish, never take them away - a fish with no
        // requirement matches any water. So with a toggle on, the question worth a
        // barrier is not whether anything bites but whether the toggle is worth
        // anything here: coral in a river is a barrier because there is no coral
        // life in rivers, which is exactly the correlation this menu is for.
        boolean dead = fishBiting == 0 && locked.isEmpty();
        boolean pointless = !modifiers.isEmpty() && byToggle == 0;
        boolean barrier = dead || pointless;

        List<Component> lore = new ArrayList<>();
        lore.add(line(tally.regions() + (tally.regions() == 1 ? " region" : " regions")
                + "   " + String.format(Locale.ROOT, "%,d", tally.columns()) + " columns",
                NamedTextColor.GRAY));
        lore.add(line("coral in " + tally.coral() + ", ice in " + tally.ice(),
                NamedTextColor.DARK_GRAY));
        lore.add(blank());

        if (dead) {
            lore.add(line("nothing bites here", NamedTextColor.RED));
            lore.add(line("vanilla keeps the catch", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(line(fishBiting + (fishBiting == 1 ? " fish bites" : " fish bite")
                    + "   " + chances.size() + " entries in all", NamedTextColor.GREEN));
            int shown = 0;
            for (Chance chance : chances) {
                if (shown++ >= 3) {
                    break;
                }
                lore.add(line(String.format(Locale.ROOT, "  %5.1f%%  ", chance.percent())
                        + chance.fish().id(), NamedTextColor.DARK_GRAY));
            }
            if (chances.size() > 3) {
                lore.add(line("  and " + (chances.size() - 3) + " more", NamedTextColor.DARK_GRAY));
            }
            if (!locked.isEmpty()) {
                lore.add(line(locked.size() + " out of reach at level " + anglerLevel,
                        NamedTextColor.YELLOW));
            }
        }

        if (!modifiers.isEmpty()) {
            lore.add(blank());
            lore.add(line(pointless
                    ? "nothing here wants " + asked()
                    : byToggle + (byToggle == 1 ? " entry is here" : " entries are here")
                            + " for " + asked(),
                    pointless ? NamedTextColor.RED : NamedTextColor.AQUA));
        }

        lore.add(blank());
        lore.add(line(barrier ? "click anyway" : "click for everything here", NamedTextColor.YELLOW));

        return new RoseItem.Builder()
                .material(barrier ? Material.BARRIER : materialFor(profile.waterType()))
                .displayName(Component.text(profile.describe(),
                                barrier ? NamedTextColor.RED : NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .addLore(lore.toArray(new Component[0]))
                .build()
                .onClick(click -> new RegionFishMenu(player, map, library, chanceOf, anglerLevel,
                        modifiers, conditions, profile, tally).open());
    }

    /** Whether a fish is here because of what is toggled on, rather than anyway. */
    boolean asked(Fish fish) {
        for (Modifier modifier : modifiers) {
            if (fish.modifiers().contains(modifier)) {
                return true;
            }
        }
        return false;
    }

    private String asked() {
        List<String> said = new ArrayList<>();
        for (Modifier modifier : modifiers) {
            said.add(lower(modifier));
        }
        return String.join(" or ", said);
    }

    /**
     * The water this kind is, with the toggles standing in for what changes.
     *
     * <p>No region id and no swarms: a kind of water is not a place, so nothing here
     * can be swarming. A swarming fish is left out of every answer this menu gives.
     */
    static WaterSpot spotFor(Profile profile, Set<Modifier> modifiers, Set<Condition> conditions) {
        return new WaterSpot(profile.waterType(), profile.terrain(), profile.vegetation(),
                profile.depth(), EnumSet.copyOf(modifiers), EnumSet.copyOf(conditions), -1, null);
    }

    private static Material materialFor(WaterType type) {
        return switch (type) {
            case OCEAN -> Material.PRISMARINE;
            case RIVER -> Material.SUGAR_CANE;
            case LAKE -> Material.LILY_PAD;
            case CAVE -> Material.DEEPSLATE;
        };
    }

    static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    static Component blank() {
        return Component.empty().decoration(TextDecoration.ITALIC, false);
    }

    static String lower(Object value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
