package com.nekyia.bountyfulSeas;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Finds the water-analyzer executable.
 *
 * <p>Looked for in three places, in order, so a server does not need an absolute
 * path to somebody else's machine written into its config:
 *
 * <ol>
 *   <li>whatever {@code water-map.analyzer} names, when it is set;</li>
 *   <li>{@code plugins/BountyfulSeas/bin/}, where a copy already sits;</li>
 *   <li>the copy inside the plugin jar, unpacked into that same folder.</li>
 * </ol>
 *
 * <p>The bundled copy is native and therefore built for one platform. The jar is
 * built on Windows here, so a Linux server finds nothing to unpack and falls back
 * to the configured path - which is why that setting stays.
 */
final class AnalyzerBinary {

    private static final String FOLDER = "bin";

    private AnalyzerBinary() {
    }

    /**
     * The executable to run, or null when there is none to be had.
     *
     * @param configured the configured path, blank when unset
     */
    static Path resolve(Plugin plugin, String configured) {
        if (configured != null && !configured.isBlank()) {
            Path named = Path.of(configured);
            if (Files.isRegularFile(named)) {
                return named;
            }
            plugin.getLogger().warning("water-map.analyzer points at " + configured
                    + ", which is not there; looking for a bundled copy instead");
        }

        Path unpacked = plugin.getDataFolder().toPath().resolve(FOLDER).resolve(fileName());
        if (Files.isRegularFile(unpacked)) {
            return unpacked;
        }
        return unpack(plugin, unpacked);
    }

    /**
     * Writes the bundled executable out of the jar.
     *
     * <p>It has to live on disk to be run at all, so it is unpacked once and left
     * there. A jar built without one simply has nothing to unpack.
     */
    private static Path unpack(Plugin plugin, Path target) {
        String resource = FOLDER + "/" + fileName();

        try (InputStream bundled = plugin.getResource(resource)) {
            if (bundled == null) {
                return null;
            }

            Files.createDirectories(target.getParent());
            Files.copy(bundled, target, StandardCopyOption.REPLACE_EXISTING);

            // Copying out of a jar loses the executable bit, which Windows does not
            // have and everything else insists on.
            target.toFile().setExecutable(true);

            plugin.getLogger().info("Unpacked the bundled water analyzer to " + target);
            return target;

        } catch (IOException failure) {
            plugin.getLogger().warning("Could not unpack the bundled water analyzer: "
                    + failure.getMessage());
            return null;
        }
    }

    private static String fileName() {
        return isWindows() ? "water-analyzer.exe" : "water-analyzer";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
