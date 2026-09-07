package com.nekyia.bountyfulSeas.water;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the offline water map generator and waits for it to finish.
 *
 * <p>The scan is a separate Rust program, not part of this plugin, so all this does
 * is find the world's region files, build a command line and report what came back.
 *
 * <p>Blocks for as long as the scan takes - tens of seconds on a large world - so
 * it must never be called on the server thread.
 */
public final class WaterMapGenerator {

    /** Generous: a 30 GB world scans in well under a minute, but disks vary. */
    private static final long TIMEOUT_MINUTES = 15;

    private static final String OUTPUT_FILE = "water_regions.bin";

    private WaterMapGenerator() {
    }

    /**
     * @param output  where {@code water_regions.bin} is written
     * @param scanned the folder actually handed to the analyzer, for reporting
     */
    public record Result(boolean ok, String message, Path output, Path scanned) {

        static Result failed(String message) {
            return new Result(false, message, null, null);
        }
    }

    /**
     * Scans a world and writes {@code water_regions.bin} into {@code outputFolder}.
     *
     * @param analyzer     the {@code water-analyzer} executable
     * @param worldFolder  the Bukkit world folder
     * @param outputFolder where the result is written, normally the plugin folder
     * @param arguments    extra analyzer flags from the config
     */
    public static Result run(Path analyzer, Path worldFolder, Path outputFolder, List<String> arguments) {
        if (analyzer == null || !Files.isRegularFile(analyzer)) {
            return Result.failed("no analyzer executable at " + analyzer);
        }

        Path scanned = regionParent(worldFolder);
        if (scanned == null) {
            return Result.failed("found no region folder under " + worldFolder);
        }

        List<String> command = new ArrayList<>();
        command.add(analyzer.toString());
        command.add("--world");
        command.add(scanned.toString());
        command.add("--output");
        command.add(outputFolder.toString());
        command.addAll(arguments);

        try {
            Files.createDirectories(outputFolder);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String log = read(process.getInputStream());
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return Result.failed("the analyzer did not finish within " + TIMEOUT_MINUTES + " minutes");
            }

            if (process.exitValue() != 0) {
                return Result.failed("the analyzer exited with " + process.exitValue() + ": " + lastLine(log));
            }

            Path written = outputFolder.resolve(OUTPUT_FILE);
            if (!Files.isRegularFile(written)) {
                return Result.failed("the analyzer reported success but wrote no " + OUTPUT_FILE);
            }
            return new Result(true, lastLine(log), written, scanned);

        } catch (IOException exception) {
            return Result.failed("could not run the analyzer: " + exception.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.failed("interrupted while waiting for the analyzer");
        }
    }

    /**
     * The folder to hand the analyzer, which is whichever one holds {@code region/}.
     *
     * <p>Minecraft 26.2 moved the overworld's chunks to
     * {@code dimensions/minecraft/overworld/region}, while the analyzer expects
     * {@code region} directly inside the world folder. Pointing it at the dimension
     * folder finds the chunks; {@code level.dat} and {@code datapacks} are then out
     * of reach, which costs the data version and any datapack biome definitions but
     * not the scan itself.
     */
    private static Path regionParent(Path worldFolder) {
        if (Files.isDirectory(worldFolder.resolve("region"))) {
            return worldFolder;
        }
        Path dimension = worldFolder.resolve("dimensions").resolve("minecraft").resolve("overworld");
        if (Files.isDirectory(dimension.resolve("region"))) {
            return dimension;
        }
        return null;
    }

    private static String read(InputStream stream) throws IOException {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The analyzer's own summary is the last thing it prints worth repeating. */
    private static String lastLine(String log) {
        String[] lines = log.strip().split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i].strip();
            }
        }
        return "no output";
    }
}
