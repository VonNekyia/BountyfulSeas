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

// Interconnect deploys every jar in its selected plugin version folder into the
// test server. Override with -PtestServerPluginFolder=<path> for a different checkout.
val testServerPluginFolder: Provider<Directory> =
    providers.gradleProperty("testServerPluginFolder")
        .map { layout.projectDirectory.dir(it) }
        .orElse(layout.projectDirectory.dir("../Interconnect/plugins-26.2"))

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())

    // Pl3xMap is optional at runtime: the overlay checks it is there before touching
    // it, so a server without it simply has no water layer.
    compileOnly(libs.pl3xmap)
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
        description = "Copies the plugin jar into the Interconnect test server plugin version folder."
        from(shadowJar)
        into(testServerPluginFolder)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
