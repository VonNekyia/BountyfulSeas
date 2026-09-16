package com.nekyia.bountyfulSeas;

import com.nekyia.bountyfulSeas.fish.Condition;
import com.nekyia.bountyfulSeas.fish.Fish;
import com.nekyia.bountyfulSeas.fish.FishLibrary;
import com.nekyia.bountyfulSeas.fish.Modifier;
import com.nekyia.bountyfulSeas.fish.Rarity;
import com.nekyia.bountyfulSeas.fishing.Chance;
import com.nekyia.bountyfulSeas.fishing.FishSelector;
import com.nekyia.bountyfulSeas.guide.FishNames;
import com.nekyia.bountyfulSeas.water.WaterMap;
import de.mcterranova.terranovaLib.roseGUI.RoseGUI;
import de.mcterranova.terranovaLib.roseGUI.RoseItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Everything one kind of water holds, in the order it is likely to bite.
 *
 * <p>Both halves of the answer, because both are asked: what bites, and what lives
 * here but is still locked. A locked fish reads as {@code ???} with the level that
 * opens it, the same way it does in the guide - what it is stays worth finding out.
 */
final class RegionFishMenu extends RoseGUI {

    private static final int[] FISH_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final WaterMap map;
    private final FishLibrary library;
    private final ToDoubleFunction<Rarity> chanceOf;
    private final int anglerLevel;
    private final Set<Modifier> modifiers;
    private final Set<Condition> conditions;
    private final RegionsMenu.Profile profile;
    private final RegionsMenu.Tally tally;

    RegionFishMenu(Player player, WaterMap map, FishLibrary library,
                   ToDoubleFunction<Rarity> chanceOf, int anglerLevel,
                   Set<Modifier> modifiers, Set<Condition> conditions,
                   RegionsMenu.Profile profile, RegionsMenu.Tally tally) {
        super(player, "bountyfulseas-region-fish",
                Component.text(profile.describe(), NamedTextColor.AQUA, TextDecoration.BOLD), 6);
        this.map = map;
        this.library = library;
        this.chanceOf = chanceOf;
        this.anglerLevel = anglerLevel;
        this.modifiers = modifiers;
        this.conditions = conditions;
        this.profile = profile;
        this.tally = tally;
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        fillGui(new RoseItem.Builder()
                .material(Material.BLACK_STAINED_GLASS_PANE)
                .displayName(Component.empty())
                .build());

        WaterSpot spot = RegionsMenu.spotFor(profile, modifiers, conditions);

        List<Chance> chances = FishSelector.chances(library.all(), spot, anglerLevel, chanceOf);
        List<Fish> locked = FishSelector.outOfReach(library.all(), spot, anglerLevel);

        int index = 0;
        for (Chance chance : chances) {
            if (index >= FISH_SLOTS.length) {
                break;
            }
            addItem(FISH_SLOTS[index++], caught(chance));
        }
        for (Fish fish : locked) {
            if (index >= FISH_SLOTS.length) {
                break;
            }
            addItem(FISH_SLOTS[index++], unreachable(fish));
        }

        if (chances.isEmpty() && locked.isEmpty()) {
            addItem(22, new RoseItem.Builder()
                    .material(Material.BARRIER)
                    .displayName(RegionsMenu.line("nothing bites here", NamedTextColor.RED))
                    .addLore(
                            RegionsMenu.line("no entry accepts this water", NamedTextColor.GRAY),
                            RegionsMenu.line("with the toggles as they stand", NamedTextColor.GRAY),
                            RegionsMenu.blank(),
                            RegionsMenu.line("vanilla keeps the catch", NamedTextColor.DARK_GRAY))
                    .build());
        }

        addItem(45, new RoseItem.Builder()
                .material(Material.ARROW)
                .displayName(RegionsMenu.line("Back", NamedTextColor.YELLOW))
                .build()
                .onClick(click -> new RegionsMenu(player, map, library, chanceOf, anglerLevel,
                        modifiers, conditions).open()));

        addItem(49, new RoseItem.Builder()
                .material(Material.BOOK)
                .displayName(RegionsMenu.line("This water", NamedTextColor.YELLOW))
                .addLore(
                        RegionsMenu.line(tally.regions() + " scanned "
                                + (tally.regions() == 1 ? "region" : "regions"), NamedTextColor.GRAY),
                        RegionsMenu.line("assuming  " + assumed(), NamedTextColor.GRAY),
                        RegionsMenu.blank(),
                        RegionsMenu.line(chances.size() + " biting, " + locked.size()
                                + " out of reach", NamedTextColor.DARK_GRAY),
                        RegionsMenu.line("you are level " + anglerLevel, NamedTextColor.DARK_GRAY))
                .build());
    }

    private String assumed() {
        List<String> said = new ArrayList<>();
        for (Modifier modifier : modifiers) {
            said.add(RegionsMenu.lower(modifier));
        }
        for (Condition condition : conditions) {
            said.add(RegionsMenu.lower(condition));
        }
        return said.isEmpty() ? "nothing" : String.join(", ", said);
    }

    private RoseItem caught(Chance chance) {
        Fish fish = chance.fish();
        List<Component> lore = new ArrayList<>();
        lore.add(RegionsMenu.line(String.format(Locale.ROOT, "%.2f%%", chance.percent())
                + "   of every bite here", NamedTextColor.WHITE));
        lore.add(RegionsMenu.blank());
        lore.add(RegionsMenu.line(fish.rarity().configName() + "   weight " + chance.weight(),
                NamedTextColor.GRAY));
        lore.add(RegionsMenu.line("level " + fish.level() + "   " + fish.category(),
                NamedTextColor.DARK_GRAY));

        // Which of these are here only because a toggle is on is the whole question
        // the menu above was asked, so it is worth saying on the fish itself.
        Set<Modifier> wanted = new java.util.LinkedHashSet<>(fish.modifiers());
        wanted.retainAll(modifiers);
        if (!wanted.isEmpty()) {
            lore.add(RegionsMenu.blank());
            lore.add(RegionsMenu.line("here because of " + RegionsMenu.lower(wanted),
                    NamedTextColor.AQUA));
        }

        return new RoseItem.Builder()
                .material(fish.isCatchMarker() ? Material.PAPER : Material.COD)
                .displayName(FishNames.of(fish))
                .addLore(lore.toArray(new Component[0]))
                .build();
    }

    private RoseItem unreachable(Fish fish) {
        return new RoseItem.Builder()
                .material(Material.GRAY_DYE)
                .displayName(FishNames.unknown(fish))
                .addLore(
                        RegionsMenu.line("level " + fish.level()
                                + "   you are " + anglerLevel, NamedTextColor.RED),
                        RegionsMenu.blank(),
                        RegionsMenu.line("it lives here, it is just not yours yet",
                                NamedTextColor.DARK_GRAY))
                .build();
    }
}
