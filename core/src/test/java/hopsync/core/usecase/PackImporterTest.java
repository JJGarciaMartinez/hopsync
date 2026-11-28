package hopsync.core.usecase;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.pack.ConfigMode;
import hopsync.core.pack.ConfigRule;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSettings;
import hopsync.core.pack.PackSource;
import hopsync.core.pack.PackToml;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackPublisher;
import hopsync.core.port.PackRepository;
import hopsync.core.port.PackSettingsRepository;
import hopsync.core.publish.FetchProgress;
import hopsync.core.publish.RemotePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackImporterTest {
    private static final PackInfo INFO = new PackInfo("Mi pack", "1.0.0", "", "play.example.net", "1.21.1", "fabric");
    private static final String URL = "https://github.com/player/mi-pack/releases/download/hopsync/manifest.json";

    @TempDir
    Path dir;

    private final Map<String, CatalogVersion> catalogVersions = new HashMap<>();
    private final Map<String, String> repoFiles = new HashMap<>();
    private final List<FetchProgress> reported = new java.util.ArrayList<>();
    private String repoToml;
    private PackDefinitionRepository definitions;
    private PackRegistry registry;
    private PackImporter importer;

    @BeforeEach
    void setUp() {
        Map<String, PackDefinition> saved = new HashMap<>();
        definitions = new PackDefinitionRepository() {
            @Override
            public Optional<PackDefinition> load(String packId) {
                return Optional.ofNullable(saved.get(packId));
            }

            @Override
            public void save(String packId, PackDefinition definition) {
                saved.put(packId, definition);
            }

            @Override
            public void delete(String packId) {
                saved.remove(packId);
            }

            @Override
            public Path folder(String packId) {
                return dir.resolve("packs").resolve(packId);
            }
        };
        PackSettingsRepository settings = new PackSettingsRepository() {
            private PackSettings current = PackSettings.EMPTY;

            @Override
            public PackSettings load() {
                return current;
            }

            @Override
            public void save(PackSettings next) {
                current = next;
            }
        };
        PackRepository repository = (repo, into, progress) -> {
            progress.accept(new FetchProgress(repoFiles.size(), repoFiles.size()));
            for (Map.Entry<String, String> file : repoFiles.entrySet()) {
                Path target = into.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue());
            }
            return new RemotePack(repoToml, new PackSource.Publication(repo, URL, ""));
        };
        ModCatalog catalog = new ModCatalog() {
            @Override
            public List<String> gameVersions() {
                return List.of();
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
                return catalogVersions.values().stream().filter(v -> ids.contains(v.id())).toList();
            }

            @Override
            public Optional<ProjectDetails> details(String id) {
                return Optional.empty();
            }

            @Override
            public Map<String, CatalogProject> projects(Collection<String> ids) {
                return Map.of();
            }
        };
        registry = new PackRegistry(settings, definitions);
        importer = new PackImporter(repository, catalog, registry, definitions);
        catalogVersions.put("Lwa1Q6e4", new CatalogVersion("Lwa1Q6e4", "AANobbMI", "0.6.13", List.of("1.21.1"),
            List.of("fabric"), true, List.of(), List.of()));
        repoToml = PackToml.write(new PackDefinition(INFO,
            List.of(new PackDefinition.ModrinthMod("AANobbMI", "sodium", "Lwa1Q6e4", "0.6.13")),
            List.of(new ConfigRule("server/", ConfigMode.ALWAYS))));
        repoFiles.put("configs/server/rules.json", "{}");
        repoFiles.put("local-mods/mine.jar", "jar");
    }

    @Test
    void importsThePackAsOneTheSameAsPublishedFromHere() throws IOException {
        Pack pack = importer.importPack("player/mi-pack", dir.resolve("game"), reported::add);

        PackDefinition definition = registry.definition(pack.id()).orElseThrow();
        assertEquals(INFO, definition.pack());
        assertEquals(List.of(new PackDefinition.ModrinthMod("AANobbMI", "sodium", "Lwa1Q6e4", "0.6.13"),
            new PackDefinition.OwnMod("mine.jar")), definition.mods());
        assertEquals(List.of(new ConfigRule("server/", ConfigMode.ALWAYS)), definition.configRules());
        Path folder = definitions.folder(pack.id());
        assertEquals("jar", Files.readString(folder.resolve("local-mods/mine.jar")));
        assertEquals("{}", Files.readString(folder.resolve("configs/server/rules.json")));
        assertEquals(List.of(new FetchProgress(2, 2)), reported, "the repository's progress reaches the caller");
        assertEquals(Optional.of(URL), pack.link());
        assertEquals("player/mi-pack", ((PackSource.Local) pack.source()).publication().repo());
        PackPublisher neverUsed = (request, progress, cancel) -> {
            throw new AssertionError("nothing to publish");
        };
        assertFalse(new PackPublishing(registry, definitions, neverUsed).changedSincePublished(pack),
            "an imported pack has no changes to publish");
    }

    @Test
    void aModGoneFromTheCatalogFailsAndLeavesNothingBehind() {
        catalogVersions.clear();

        SyncException e = assertThrows(SyncException.class,
            () -> importer.importPack("player/mi-pack", dir.resolve("game"), progress -> { }));

        assertEquals(new SyncFailure.ModUnavailable("sodium"), e.failure());
        assertTrue(registry.packs().isEmpty());
    }

    @Test
    void aMalformedTomlFailsAndLeavesNothingBehind() {
        repoToml = "[pack]\nname = \"open\n";

        SyncException e = assertThrows(SyncException.class,
            () -> importer.importPack("player/mi-pack", dir.resolve("game"), progress -> { }));

        assertInstanceOf(SyncFailure.ManifestInvalid.class, e.failure());
        assertTrue(registry.packs().isEmpty());
    }

    @Test
    void aFolderAnotherPackUsesFailsAndLeavesNothingBehind() throws IOException {
        Path game = dir.resolve("game");
        registry.addLocal(PackDefinition.empty(INFO), game);

        SyncException e = assertThrows(SyncException.class, () -> importer.importPack("player/mi-pack", game, progress -> { }));

        assertInstanceOf(SyncFailure.FolderUsedByPack.class, e.failure());
        assertEquals(1, registry.packs().size());
    }
}
