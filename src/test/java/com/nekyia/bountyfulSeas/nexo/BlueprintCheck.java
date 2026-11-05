package com.nekyia.bountyfulSeas.nexo;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Runs the blueprint writer against throwaway Nexo folders and fails loudly.
 *
 * <p>The case that matters is the migration: a server whose entries were all written
 * to one file, some of them edited by hand, has to end up with them in the right file
 * with those edits and their numbers intact. Getting that wrong does not fail anywhere
 * else, it just quietly turns a texture back into the default.
 */
public final class BlueprintCheck {

    /** A coin as an existing server holds it: every hand held field off its default. */
    private static final String HAND_EDITED_COIN = """
            silver_corroded:
              material: IRON_NUGGET
              Pack:
                parent_model: item/handheld
                custom_model_data: 9030
                texture: bountyfulseas/my_own_coin.png
              Mechanics:
                misc:
                  allow_in_vanilla_recipes: true
              itemname: stale
              lore:
              - stale
            """;

    private static final String ROACH = """
            common_roach:
              material: PAPER
              Pack:
                parent_model: item/generated
                custom_model_data: 9001
                texture: bountyfulseas/common_roach.png
              Mechanics:
                misc:
                  allow_in_vanilla_recipes: false
              itemname: Roach
              lore:
              - Cyprinids
            """;

    record Item(String id, String file) implements NexoItem {
        @Override
        public String displayName() {
            return "Name of " + id;
        }

        @Override
        public List<String> lore() {
            return List.of("Lore of " + id);
        }

        @Override
        public OptionalDouble foodSaturation() {
            return OptionalDouble.empty();
        }
    }

    private static final Item ROACH_ITEM = new Item("common_roach", "fish");
    private static final Item COIN_ITEM = new Item("silver_corroded", "misc");

    public static void main(String[] args) throws Exception {
        migrationCarriesTheEntryAndChangesNothingElse();
        secondRunWritesNothing();
        entriesMoveBackJustTheSame();
        crashLeftoverKeepsTheCopyInItsHome();
        duplicateNumbersAreRepairedAcrossFiles();
        foreignFilesAreNeitherOursNorOverwritten();
        unreadableFileLeavesEverythingAlone();
        System.out.println("blueprint writer: every check passed");
    }

    private static void migrationCarriesTheEntryAndChangesNothingElse() throws IOException {
        Path nexo = nexoFolder();
        own(nexo, "fish.yml", ROACH + HAND_EDITED_COIN);

        BlueprintResult result = NexoBlueprintWriter.write(nexo, List.of(ROACH_ITEM, COIN_ITEM));

        check(result.moved().equals(List.of("silver_corroded -> misc.yml")), "moved: " + result.moved());
        check(result.created().isEmpty(), "nothing is created by a move: " + result.created());
        check(result.repaired().isEmpty(), "a move repairs no number: " + result.repaired());

        YamlConfiguration fish = load(nexo, "fish.yml");
        YamlConfiguration misc = load(nexo, "misc.yml");
        check(!fish.contains("silver_corroded"), "coin left behind in fish.yml");
        check(fish.getInt("common_roach.Pack.custom_model_data") == 9001, "roach lost its number");

        check("IRON_NUGGET".equals(misc.getString("silver_corroded.material")), "material lost");
        check("item/handheld".equals(misc.getString("silver_corroded.Pack.parent_model")), "model lost");
        check("bountyfulseas/my_own_coin.png".equals(misc.getString("silver_corroded.Pack.texture")),
                "texture lost");
        check(misc.getInt("silver_corroded.Pack.custom_model_data") == 9030, "number lost");
        check(misc.getBoolean("silver_corroded.Mechanics.misc.allow_in_vanilla_recipes"), "mechanics lost");
        check("Name of silver_corroded".equals(misc.getString("silver_corroded.itemname")),
                "derived fields must still be maintained after the move");
    }

    private static void secondRunWritesNothing() throws IOException {
        Path nexo = nexoFolder();
        own(nexo, "fish.yml", ROACH + HAND_EDITED_COIN);
        List<Item> items = List.of(ROACH_ITEM, COIN_ITEM);
        NexoBlueprintWriter.write(nexo, items);

        Path misc = own(nexo).resolve("misc.yml");
        String before = Files.readString(misc);
        // A file left untouched keeps its time, which is what proves it was not rewritten.
        Files.setLastModifiedTime(misc, java.nio.file.attribute.FileTime.fromMillis(1_000_000L));

        BlueprintResult again = NexoBlueprintWriter.write(nexo, items);

        check(again.moved().isEmpty() && again.created().isEmpty() && again.repaired().isEmpty(),
                "second run did something: " + again);
        check(before.equals(Files.readString(misc)), "misc.yml changed on the second run");
        check(Files.getLastModifiedTime(misc).toMillis() == 1_000_000L, "misc.yml was rewritten");
    }

    private static void entriesMoveBackJustTheSame() throws IOException {
        Path nexo = nexoFolder();
        own(nexo, "misc.yml", HAND_EDITED_COIN);

        BlueprintResult result = NexoBlueprintWriter.write(nexo, List.of(new Item("silver_corroded", "fish")));

        check(result.moved().equals(List.of("silver_corroded -> fish.yml")), "moved: " + result.moved());
        check(!load(nexo, "misc.yml").contains("silver_corroded"), "coin left behind in misc.yml");
        check(load(nexo, "fish.yml").getInt("silver_corroded.Pack.custom_model_data") == 9030, "number lost");
    }

    private static void crashLeftoverKeepsTheCopyInItsHome() throws IOException {
        Path nexo = nexoFolder();
        // A crash between the two saves: already in misc.yml, not yet gone from fish.yml.
        own(nexo, "misc.yml", HAND_EDITED_COIN);
        own(nexo, "fish.yml", HAND_EDITED_COIN.replace("my_own_coin", "stale_copy"));

        BlueprintResult result = NexoBlueprintWriter.write(nexo, List.of(COIN_ITEM));

        check(result.moved().isEmpty(), "nothing had to move: " + result.moved());
        check(result.repaired().isEmpty(), "the leftover must not count as a clash: " + result.repaired());
        check(!load(nexo, "fish.yml").contains("silver_corroded"), "leftover not removed");
        check("bountyfulseas/my_own_coin.png".equals(load(nexo, "misc.yml").getString("silver_corroded.Pack.texture")),
                "the copy in its home must win");
    }

    private static void duplicateNumbersAreRepairedAcrossFiles() throws IOException {
        Path nexo = nexoFolder();
        own(nexo, "fish.yml", ROACH);
        own(nexo, "misc.yml", HAND_EDITED_COIN.replace("9030", "9001"));

        BlueprintResult result = NexoBlueprintWriter.write(nexo, List.of(ROACH_ITEM, COIN_ITEM));

        check(result.repaired().size() == 1, "repaired: " + result.repaired());
        // Name order: fish.yml claims first, so the roach keeps what it was drawn against.
        check(load(nexo, "fish.yml").getInt("common_roach.Pack.custom_model_data") == 9001, "roach moved");
        check(load(nexo, "misc.yml").getInt("silver_corroded.Pack.custom_model_data") != 9001, "clash remained");
    }

    private static void foreignFilesAreNeitherOursNorOverwritten() throws IOException {
        Path nexo = nexoFolder();
        Path foreign = nexo.resolve("items/someone_else/items.yml");
        Files.createDirectories(foreign.getParent());
        Files.writeString(foreign, HAND_EDITED_COIN.replace("9030", "9000"));
        String before = Files.readString(foreign);

        BlueprintResult result = NexoBlueprintWriter.write(nexo, List.of(COIN_ITEM, ROACH_ITEM));

        check(before.equals(Files.readString(foreign)), "a foreign file was changed");
        check(result.created().equals(List.of("common_roach")), "created: " + result.created());
        check(!Files.exists(own(nexo).resolve("misc.yml")), "a foreign id must not be written again");
        check(load(nexo, "fish.yml").getInt("common_roach.Pack.custom_model_data") == 9001,
                "9000 is taken by the foreign file, so the first free one is 9001");
    }

    private static void unreadableFileLeavesEverythingAlone() throws IOException {
        Path nexo = nexoFolder();
        own(nexo, "fish.yml", ROACH + HAND_EDITED_COIN);
        own(nexo, "misc.yml", "this: [is not yaml");
        String before = Files.readString(own(nexo).resolve("fish.yml"));

        BlueprintResult result = NexoBlueprintWriter.write(nexo, List.of(ROACH_ITEM, COIN_ITEM));

        check(result.skipped(), "an unreadable file must stop the run");
        check(before.equals(Files.readString(own(nexo).resolve("fish.yml"))), "fish.yml was touched");
    }

    private static Path nexoFolder() throws IOException {
        Path nexo = Files.createTempDirectory("blueprint-check");
        Files.createDirectories(own(nexo));
        return nexo;
    }

    private static Path own(Path nexo) {
        return nexo.resolve("items").resolve(NexoBlueprintWriter.FOLDER);
    }

    private static void own(Path nexo, String file, String content) throws IOException {
        Files.writeString(own(nexo).resolve(file), content);
    }

    private static YamlConfiguration load(Path nexo, String file) {
        return YamlConfiguration.loadConfiguration(own(nexo).resolve(file).toFile());
    }

    private static void check(boolean holds, String complaint) {
        if (!holds) {
            throw new AssertionError(complaint);
        }
    }

    private BlueprintCheck() {
    }
}
