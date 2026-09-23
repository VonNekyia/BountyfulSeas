plugins {
    id("java-library")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
}

// The test server's plugin folder. Override with -PtestServerPluginFolder=<path>
// to deploy into a different checkout.
val testServerPluginFolder: Provider<Directory> =
    providers.gradleProperty("testServerPluginFolder")
        .map { layout.projectDirectory.dir(it) }
        .orElse(layout.projectDirectory.dir("../server-terranova/servers/main/plugins"))

// TerranovaLib publishes no artifact this build can resolve, so its API comes from
// the jar deployed alongside us in the test server.
val terranovaLibJar = testServerPluginFolder.map { folder ->
    folder.asFileTree.matching { include("TerranovaLib*.jar") }
}

// The water-analyzer, one native build per platform, taken from wherever cargo put
// each one. Override the cargo target folder with -PwaterAnalyzerDir=<path>.
//
// A platform that has not been built is simply left out, so none of this is ever
// a build requirement; the plugin then falls back to a path from config.yml on
// that platform. The binaries are copied in, never committed.
//
// Each is matched by its exact path rather than by name alone: target/release also
// holds a copy of the Windows build without the .exe, and a name-only match would
// happily ship that to Linux.
val analyzerTarget = providers.gradleProperty("waterAnalyzerDir")
    .map { layout.projectDirectory.dir(it) }
    .orElse(layout.projectDirectory.dir("../minecraft-water-map-generator/target"))

/** Where each platform's build lands in the jar, and where cargo leaves it. */
val analyzerBuilds = mapOf(
    "windows-x86_64" to ("release" to "water-analyzer.exe"),
    "linux-x86_64" to ("x86_64-unknown-linux-musl/release" to "water-analyzer"),
    "linux-aarch64" to ("aarch64-unknown-linux-musl/release" to "water-analyzer"),
)

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())

    // Loaded at runtime by Paper from the libraries block in plugin.yml, so none
    // of this is shaded into the jar. Versions must match that list.
    compileOnly(libs.hikari)
    compileOnly(libs.mariadb)
    compileOnly(libs.caffeine)
    compileOnly(files(terranovaLibJar))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    // The shadow jar is the one that ships, so it takes the plain name and the
    // thin jar steps aside. Without this both write build/libs/<name>-<version>.jar
    // and deployToTestServer cannot tell which task produced the file it copies.
    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
    }

    register<Copy>("deployToTestServer") {
        group = "distribution"
        description = "Copies the plugin jar into the test server's plugin folder."
        from(shadowJar)
        into(testServerPluginFolder)

        // A live server holds files open in that folder, and Gradle refuses to
        // fingerprint a destination it cannot fully read. Nothing here needs
        // up-to-date checks anyway: it is one file copied over another.
        doNotTrackState("the destination is a running server's plugin folder")
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }

        // Only expanded in paper-plugin.yml above; a native binary must not be run
        // through the token filter or it comes out corrupted.
        analyzerBuilds.forEach { (platform, build) ->
            val (folder, file) = build
            from(analyzerTarget.map { it.dir(folder).asFileTree.matching { include(file) } }) {
                into("bin/$platform")
            }
        }
    }
}
