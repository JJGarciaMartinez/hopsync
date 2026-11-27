package hopsync.core.usecase;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSettings;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackSettingsRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackEditorTest {
    private static final PackInfo INFO = new PackInfo("Mi pack", "1.0.0", "", null, "1.21.1", "fabric");

    @TempDir
    Path dir;

    private final Map<String, List<CatalogVersion>> versions = new HashMap<>();
    private PackRegistry registry;
    private PackEditor editor;
    private Pack pack;

    private static CatalogVersion version(String id, String project, String number, boolean release,
                                          CatalogVersion.Dependency... dependencies) {
        return new CatalogVersion(id, project, number, List.of("1.21.1"), List.of("fabric"), release,
            List.of(new CatalogVersion.File("https://cdn.modrinth.com/" + id + ".jar", id + ".jar", "a".repeat(128), 1, true)),
            List.of(dependencies));
    }

    private static CatalogVersion.Dependency needs(String project) {
        return new CatalogVersion.Dependency(project, null, true);
    }

    @BeforeEach
    void createPack() throws IOException {
        Map<String, PackDefinition> saved = new HashMap<>();
        PackDefinitionRepository definitions = new PackDefinitionRepository() {
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
        ModCatalog catalog = new ModCatalog() {
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
                return versions.getOrDefault(project, List.of());
            }

            @Override
            public List<CatalogVersion> versionsById(Collection<String> ids) {
                return versions.values().stream().flatMap(List::stream).filter(v -> ids.contains(v.id())).toList();
            }

            @Override
            public Optional<ProjectDetails> details(String id) {
                return Optional.empty();
            }

            @Override
            public Map<String, CatalogProject> projects(Collection<String> ids) {
                return ids.stream().collect(Collectors.toMap(id -> id,
                    id -> new CatalogProject(id, id + "-slug", "Title " + id, "", "", null, 0)));
            }
        };
        registry = new PackRegistry(settings, definitions);
        editor = new PackEditor(catalog, registry, definitions);
        pack = registry.addLocal(PackDefinition.empty(INFO), dir.resolve("game"));
    }

    private List<PackDefinition.Mod> mods() {
        return registry.definition(pack.id()).orElseThrow().mods();
    }

    @Test
    void addsTheNewestReleaseWithEveryModItRequires() throws IOException {
        versions.put("extra", List.of(version("e2", "extra", "2.0-beta", false), version("e1", "extra", "1.0", true,
            needs("sodium"), new CatalogVersion.Dependency("optional", null, false))));
        versions.put("sodium", List.of(version("s1", "sodium", "0.6", true, needs("lib"))));
        versions.put("lib", List.of(version("l1", "lib", "3.0", true)));

        PackEditor.Added added = editor.addFromCatalog(pack.id(), "extra");

        assertEquals(new PackEditor.Added("Title extra", List.of("Title sodium", "Title lib")), added);
        assertEquals(List.of(new PackDefinition.ModrinthMod("extra", "extra-slug", "e1", "1.0"),
            new PackDefinition.ModrinthMod("sodium", "sodium-slug", "s1", "0.6"),
            new PackDefinition.ModrinthMod("lib", "lib-slug", "l1", "3.0")), mods());
        assertEquals(Set.of("extra", "sodium", "lib"), editor.catalogProjects(pack.id()));
    }

    @Test
    void aModThePackHasIsNotAddedTwiceAndItsDependenciesAreReused() throws IOException {
        versions.put("sodium", List.of(version("s1", "sodium", "0.6", true)));
        versions.put("extra", List.of(version("e1", "extra", "1.0", true, needs("sodium"))));
        editor.addFromCatalog(pack.id(), "sodium");

        assertEquals(List.of(), editor.addFromCatalog(pack.id(), "extra").alsoAdded());
        assertEquals(List.of(), editor.addFromCatalog(pack.id(), "sodium").alsoAdded());
        assertEquals(2, mods().size());
    }

    @Test
    void aModWithoutAVersionForThePackIsNotAdded() throws IOException {
        versions.put("extra", List.of(version("e1", "extra", "1.0", true, needs("gone"))));

        SyncException e = assertThrows(SyncException.class, () -> editor.addFromCatalog(pack.id(), "extra"));

        assertEquals(new SyncFailure.ModUnavailable("Title gone"), e.failure());
        assertEquals(List.of(), mods(), "nothing is added when a required mod cannot be");
    }

    @Test
    void previewNamesNewestVersionAndMissingDependencies() throws IOException {
        versions.put("a", List.of(version("a1", "a", "1.0", true)));
        versions.put("b", List.of(version("b2", "b", "2.0", true, needs("a")), version("b1", "b", "1.0", true)));

        PackEditor.Preview preview = editor.preview(pack.id(), "b");

        assertEquals("2.0", preview.version().versionNumber());
        assertEquals(List.of("Title a"), preview.needs());
    }

    @Test
    void previewSkipsDependenciesAlreadyInPack() throws IOException {
        versions.put("a", List.of(version("a1", "a", "1.0", true)));
        versions.put("b", List.of(version("b1", "b", "1.0", true, needs("a"))));
        editor.addFromCatalog(pack.id(), "a");

        assertEquals(List.of(), editor.preview(pack.id(), "b").needs());
    }

    @Test
    void previewThrowsWhenNoVersionFitsThePack() {
        versions.put("b", List.of(version("b1", "b", "1.0", true, needs("gone"))));

        SyncException e = assertThrows(SyncException.class, () -> editor.preview(pack.id(), "b"));

        assertEquals(new SyncFailure.ModUnavailable("Title gone"), e.failure());
    }

    @Test
    void previewDoesNotSave() throws IOException {
        versions.put("a", List.of(version("a1", "a", "1.0", true)));
        versions.put("b", List.of(version("b1", "b", "1.0", true, needs("a"))));

        editor.preview(pack.id(), "b");

        assertEquals(List.of(), mods());
    }

    @Test
    void ownJarsAreCopiedIntoThePackAndListedOnce() throws IOException {
        Path jar = Files.writeString(dir.resolve("mine.jar"), "v1");

        assertEquals("mine.jar", editor.addOwnJar(pack.id(), jar));
        Files.writeString(jar, "v2");
        editor.addOwnJar(pack.id(), jar);

        assertEquals(List.of(new PackDefinition.OwnMod("mine.jar")), mods());
        assertEquals("v2", Files.readString(dir.resolve("packs").resolve(pack.id()).resolve("local-mods/mine.jar")));
        SyncException notAJar = assertThrows(SyncException.class,
            () -> editor.addOwnJar(pack.id(), Files.writeString(dir.resolve("notes.txt"), "x")));
        assertInstanceOf(SyncFailure.UnsafePath.class, notAJar.failure());
    }

    @Test
    void aModCanMoveToAnotherVersionOrLeaveThePack() throws IOException {
        versions.put("sodium", List.of(version("s2", "sodium", "0.7", true), version("s1", "sodium", "0.6", true)));
        versions.put("iris", List.of(version("i1", "iris", "1.8", true)));
        editor.addFromCatalog(pack.id(), "sodium");
        editor.addFromCatalog(pack.id(), "iris");
        editor.addOwnJar(pack.id(), Files.writeString(dir.resolve("mine.jar"), "jar"));

        assertEquals(List.of("s2", "s1"), editor.versions(pack.id(), "sodium").stream().map(CatalogVersion::id).toList());
        editor.changeVersion(pack.id(), "sodium-slug", "s1");
        editor.remove(pack.id(), "iris-slug", false);
        editor.remove(pack.id(), "mine.jar", true);

        assertEquals(List.of(new PackDefinition.ModrinthMod("sodium", "sodium-slug", "s1", "0.6")), mods());
        assertFalse(Files.exists(dir.resolve("packs").resolve(pack.id()).resolve("local-mods/mine.jar")));
        SyncException gone = assertThrows(SyncException.class, () -> editor.changeVersion(pack.id(), "sodium-slug", "nope"));
        assertEquals(new SyncFailure.ModUnavailable("sodium-slug"), gone.failure());
    }

    @Test
    void theServerAddressCanBeSetChangedAndCleared() throws IOException {
        editor.changeServer(pack.id(), " play.example.net:25565 ");
        assertEquals("play.example.net:25565", registry.definition(pack.id()).orElseThrow().pack().serverIp());

        editor.changeServer(pack.id(), "  ");
        assertEquals(INFO, registry.definition(pack.id()).orElseThrow().pack());
        assertThrows(IllegalArgumentException.class, () -> editor.changeServer(pack.id(), "play example"));
    }

    @Test
    void aModNamedByItsSlugIsNotItsOwnDependency() throws IOException {
        // The catalog answers for a slug with versions that carry the project's id
        versions.put("sodium-slug", List.of(version("s1", "sodium", "0.6", true)));

        assertEquals(new PackEditor.Added("Title sodium", List.of()), editor.addFromCatalog(pack.id(), "sodium-slug"));
        assertEquals(List.of(new PackDefinition.ModrinthMod("sodium", "sodium-slug", "s1", "0.6")), mods());
        assertEquals(List.of(), editor.addFromCatalog(pack.id(), "sodium-slug").alsoAdded());
        assertEquals(1, mods().size(), "the slug finds the mod already in the pack");
    }
}
