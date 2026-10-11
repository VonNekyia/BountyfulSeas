package com.nekyia.bountyfulSeas;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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
 * <p>The bundled copies are native, one per platform, kept apart in the jar as
 * {@code bin/<os>-<arch>/}. Each server unpacks only its own. A platform nobody
 * built for finds nothing and falls back to the configured path, which is why that
 * setting stays.
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

        // Unpacked into a folder named for the platform, so a data folder carried from
        // one machine to another never runs the other machine's build.
        Path unpacked = plugin.getDataFolder().toPath()
                .resolve(FOLDER).resolve(platform()).resolve(fileName());
        if (Files.isRegularFile(unpacked) && matchesBundled(plugin, unpacked)) {
            return unpacked;
        }
        return unpack(plugin, unpacked);
    }

    /**
     * Whether what is on disk is the build this jar carries.
     *
     * <p>Unpacking once and keeping it forever meant a plugin update shipped a
     * fixed analyzer that no existing server ever ran: the stale copy was already
     * there, so it was kept. Compared by content rather than by a version number,
     * because the binary has none, and because a half-written copy from a crashed
     * unpack should also be replaced.
     *
     * <p>A jar with nothing bundled leaves whatever is on disk alone - that is a
     * platform we ship no build for, and the file came from somewhere else.
     */
    private static boolean matchesBundled(Plugin plugin, Path unpacked) {
        String resource = FOLDER + "/" + platform() + "/" + fileName();
        try (InputStream bundled = plugin.getResource(resource)) {
            if (bundled == null) {
                return true;
            }
            return Arrays.equals(digestOf(bundled), digestOf(Files.newInputStream(unpacked)));
        } catch (IOException | NoSuchAlgorithmException unreadable) {
            // Cannot tell, so keep what runs rather than risk replacing it with nothing.
            plugin.getLogger().fine("Could not compare the unpacked water analyzer: "
                    + unreadable.getMessage());
            return true;
        }
    }

    private static byte[] digestOf(InputStream stream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream reading = new DigestInputStream(stream, digest)) {
            byte[] block = new byte[8192];
            while (reading.read(block) >= 0) {
                // Read for the digest alone.
            }
        }
        return digest.digest();
    }

    /**
     * Writes the bundled executable out of the jar.
     *
     * <p>It has to live on disk to be run at all, so it is unpacked and left there,
     * and replaced whenever the jar carries a different build. A jar built without
     * one simply has nothing to unpack.
     */
    private static Path unpack(Plugin plugin, Path target) {
        String resource = FOLDER + "/" + platform() + "/" + fileName();

        try (InputStream bundled = plugin.getResource(resource)) {
            if (bundled == null) {
                plugin.getLogger().info("No water analyzer is bundled for " + platform()
                        + "; set water-map.analyzer to use one built for this machine.");
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

    /**
     * This machine as the jar names it, such as {@code linux-x86_64}.
     *
     * <p>The JVM reports architectures under several names for the same thing -
     * {@code amd64} on Linux where Windows says {@code x86_64}, {@code arm64} on a
     * Mac for what Linux calls {@code aarch64} - so they are folded into the names
     * cargo uses.
     */
    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String system = os.contains("win") ? "windows"
                : os.contains("mac") || os.contains("darwin") ? "macos"
                : "linux";

        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String machine = switch (arch) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };
        return system + "-" + machine;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
