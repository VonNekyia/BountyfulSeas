plugins {
    id("java-library")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
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

// The water-analyzer executable, bundled into the jar when it has been built.
// Override the folder with -PwaterAnalyzerDir=<path>. When it is not there the jar
// ships without it and the plugin falls back to a path from config.yml, so this is
// never a build requirement - and the binary is copied in, never committed.
val analyzerBinary = providers.gradleProperty("waterAnalyzerDir")
    .map { layout.projectDirectory.dir(it) }
    .orElse(layout.projectDirectory.dir("../minecraft-water-map-generator/target/release"))
    .map { folder ->
        // Unanchored patterns match the folder itself only, not all of target/.
        folder.asFileTree.matching { include("water-analyzer", "water-analyzer.exe") }
    }

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())

    // Pl3xMap is optional at runtime: the overlay checks it is there before touching
    // it, so a server without it simply has no water layer.
    compileOnly(libs.pl3xmap)

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
        from(analyzerBinary) {
            into("bin")
        }
    }
}
