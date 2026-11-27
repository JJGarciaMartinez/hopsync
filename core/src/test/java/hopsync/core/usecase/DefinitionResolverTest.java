package hopsync.core.usecase;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.Manifest;
import hopsync.core.manifest.ModEntry;
import hopsync.core.manifest.ModSource;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.InMemoryDefinitions;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackSource;
import hopsync.core.port.ModCatalog;
import hopsync.core.sync.FileHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionResolverTest {
    private static final PackInfo INFO = new PackInfo("Mi pack", "1.0.0", "", null, "1.21.1", "fabric");
    private static final String SHA = "a".repeat(128);
    private static final CatalogVersion SODIUM = new CatalogVersion("v1", "AANobbMI", "0.6.0", List.of("1.21.1"),
        List.of("fabric"), true, List.of(new CatalogVersion.File("https://cdn.modrinth.com/sodium.jar", "sodium.jar",
            SHA, 10, true)), List.of());

    @TempDir
    Path dir;

    /** A catalog that knows only Sodium. */
    private static final ModCatalog CATALOG = new ModCatalog() {
        @Override
        public List<String> gameVersions() {
            return List.of("1.21.1");
        }

        @Override
        public List<CatalogProject> search(String query, String minecraft, String loader, int limit) {
            return List.of();
        }

        @Override
        public List<CatalogVersion> versions(String project, String minecraft, String loader) {
            return List.of();
        }

        @Override
        public List<CatalogVersion> versionsById(Collection<String> ids) {
            return ids.contains("v1") ? List.of(SODIUM) : List.of();
        }

        @Override
        public Optional<ProjectDetails> details(String id) {
            return Optional.empty();
        }

        @Override
        public Map<String, CatalogProject> projects(Collection<String> ids) {
            return Map.of("AANobbMI", new CatalogProject("AANobbMI", "sodium", "Sodium", "Fast", "jelly", null, 1));
        }
    };

    private final InMemoryDefinitions definitions = new InMemoryDefinitions();
    private final DefinitionResolver resolver = new DefinitionResolver(CATALOG, definitions);

    private static PackDefinition.ModrinthMod sodium(String versionId) {
        return new PackDefinition.ModrinthMod("AANobbMI", "sodium", versionId, "0.6.0");
    }

    @Test
    void modrinthModsOwnJarsAndConfigsBecomeOneManifest() throws IOException {
        Path own = Files.writeString(Files.createDirectories(dir.resolve("local-mods")).resolve("Mine.jar"), "jar");
        Path config = Files.writeString(Files.createDirectories(dir.resolve("configs/server")).resolve("rules.json"), "{}");
        Files.writeString(dir.resolve("configs/client.txt"), "x");
        PackDefinition definition = new PackDefinition(INFO,
            List.of(new PackDefinition.OwnMod("Mine.jar"), sodium("v1")), List.of("server/rules.json"));

        Manifest manifest = resolver.resolve(definition, dir);

        assertEquals(INFO, manifest.pack());
        assertEquals(List.of(
            new ModEntry("Mine", "Mine", "", "", "", "Mine.jar", own.toUri().toString(), FileHasher.sha512(own), 3,
                ModSource.LOCAL),
            new ModEntry("sodium", "Sodium", "0.6.0", "jelly", "Fast", "sodium.jar",
                "https://cdn.modrinth.com/sodium.jar", SHA, 10, ModSource.MODRINTH)), manifest.mods());
        assertEquals(List.of("client.txt", "server/rules.json"), manifest.configs().stream().map(ConfigEntry::path).toList());
        assertEquals(List.of(false, true), manifest.configs().stream().map(ConfigEntry::overwrite).toList());
        assertEquals(config.toUri().toString(), manifest.configs().get(1).url());
    }

    @Test
    void theContentHashFollowsTheFiles() throws IOException {
        PackDefinition definition = new PackDefinition(INFO, List.of(sodium("v1")), List.of());
        String before = resolver.resolve(definition, dir).contentHash();
        assertEquals(before, resolver.resolve(definition, dir).contentHash());

        Files.writeString(Files.createDirectories(dir.resolve("configs")).resolve("a.txt"), "x");
        assertNotEquals(before, resolver.resolve(definition, dir).contentHash());
    }

    @Test
    void aGoneVersionOrJarIsReportedByName() {
        SyncException version = assertThrows(SyncException.class,
            () -> resolver.resolve(new PackDefinition(INFO, List.of(sodium("gone")), List.of()), dir));
        assertEquals(new SyncFailure.ModUnavailable("sodium"), version.failure());

        SyncException jar = assertThrows(SyncException.class,
            () -> resolver.resolve(new PackDefinition(INFO, List.of(new PackDefinition.OwnMod("lost.jar")), List.of()), dir));
        assertEquals(new SyncFailure.ModUnavailable("lost.jar"), jar.failure());
    }

    @Test
    void aPackWithoutItsDefinitionCannotBeResolved() {
        Pack pack = new Pack("p1", new PackSource.Local(null), dir, "");
        SyncException e = assertThrows(SyncException.class, () -> resolver.resolve(pack));
        assertInstanceOf(SyncFailure.DefinitionMissing.class, e.failure());
    }

    @Test
    void theSameProjectTwiceIsInstalledOnce() throws IOException {
        Manifest manifest = resolver.resolve(new PackDefinition(INFO, List.of(sodium("v1"), sodium("v1")), List.of()), dir);
        assertEquals(1, manifest.mods().size());
    }
}
