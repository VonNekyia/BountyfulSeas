package com.nekyia.bountyfulSeas;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.List;

/**
 * Fetches the plugin's runtime libraries instead of shading them into the jar.
 *
 * <p>This is what the {@code libraries} block in a plugin.yml used to do. A Paper
 * plugin has no such block, so the same list lives here; the effect is unchanged -
 * the jar stays small and servers share one copy of each library.
 *
 * <p>Versions must match the ones in build.gradle.kts, where they are compiled
 * against.
 */
public final class BountyfulSeasLibraries implements PluginLoader {

    private static final List<String> LIBRARIES = List.of(
            "com.zaxxer:HikariCP:7.0.2",
            "org.mariadb.jdbc:mariadb-java-client:3.5.10",
            "com.github.ben-manes.caffeine:caffeine:3.2.3");

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        // Paper refuses repo1.maven.org outright: Maven Central's terms do not allow
        // being used as a CDN, so it hands out a mirror to use instead.
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        for (String library : LIBRARIES) {
            resolver.addDependency(new Dependency(new DefaultArtifact(library), null));
        }
        classpath.addLibrary(resolver);
    }
}
