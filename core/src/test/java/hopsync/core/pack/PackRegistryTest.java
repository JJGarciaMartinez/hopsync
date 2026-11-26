package hopsync.core.pack;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.PackInfo;
import hopsync.core.port.PackSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackRegistryTest {
    private static final String URL = "https://example.org/manifest.json";

    @TempDir
    Path dir;
    final InMemorySettings repository = new InMemorySettings();
    final InMemoryDefinitions definitions = new InMemoryDefinitions();
    final PackRegistry registry = new PackRegistry(repository, definitions);

    @Test
    void firstPackBecomesActiveAndEverythingIsSaved() throws IOException {
        Pack first = registry.add(URL, dir.resolve("a"));
        Pack second = registry.add(URL, dir.resolve("b"));

        assertEquals(first, registry.active().orElseThrow());
        assertEquals(2, repository.saved.packs().size());

        registry.select(second.id());
        assertEquals(second.id(), repository.saved.activeId());
    }

    @Test
    void sameFolderIsRejected() throws IOException {
        registry.add(URL, dir.resolve("a"));
        assertFolderRejected(dir.resolve("a"));
        assertFolderRejected(dir.resolve("b/../a"));
    }

    @Test
    void copiesOfOnePackNeedTheirOwnFolders() throws IOException {
        Pack first = registry.add(URL, dir.resolve("a"));
        Pack copy = registry.add(URL, dir.resolve("b"));

        assertEquals(2, registry.packs().size());
        SyncException e = assertThrows(SyncException.class, () -> registry.update(copy.withGameDir(first.gameDir())));
        assertInstanceOf(SyncFailure.FolderUsedByPack.class, e.failure());
        registry.update(copy.withGameDir(dir.resolve("c")));
        assertEquals(dir.resolve("c"), registry.find(copy.id()).orElseThrow().gameDir());
    }

    @Test
    void nestedFoldersAreRejectedBothWays() throws IOException {
        registry.add(URL, dir.resolve("instances/one"));
        assertFolderRejected(dir.resolve("instances/one/sub"));
        assertFolderRejected(dir.resolve("instances"));
        assertDoesNotThrow(() -> registry.add(URL, dir.resolve("instances/one-other")));
    }

    @Test
    void symlinkedFolderIsRejected() throws IOException {
        Path real = Files.createDirectories(dir.resolve("real"));
        Path link = dir.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symlinks not available: " + e);
        }
        registry.add(URL, real);
        assertFolderRejected(link);
        assertFolderRejected(link.resolve("not-yet-created"));
    }

    @Test
    void tellsWhichPackUsesAFolder() throws IOException {
        Pack pack = registry.add(URL, dir.resolve("a"));

        assertEquals(pack, registry.packUsing(dir.resolve("a/sub"), null).orElseThrow());
        assertTrue(registry.packUsing(dir.resolve("a"), pack.id()).isEmpty(), "a pack may keep its own folder");
        assertTrue(registry.packUsing(dir.resolve("b"), null).isEmpty());
    }

    @Test
    void preferencesAreSavedAndClamped() throws IOException {
        registry.updatePreferences(false, false, 40, true, "en", false);

        assertEquals(new PackSettings(List.of(), null, false, false, 8, true, PackSettings.DEFAULT_LIBRARY_WIDTH, "en", false),
            repository.saved);
    }

    @Test
    void libraryWidthIsKeptWithinItsLimitsAndSurvivesPreferenceChanges() throws IOException {
        registry.rememberLibraryWidth(500);
        assertEquals(PackSettings.MAX_LIBRARY_WIDTH, repository.saved.libraryWidth());
        registry.updatePreferences(true, true, 4, false, "", true);
        assertEquals(PackSettings.MAX_LIBRARY_WIDTH, repository.saved.libraryWidth());
        registry.rememberLibraryWidth(10);
        assertEquals(PackSettings.MIN_LIBRARY_WIDTH, registry.settings().libraryWidth());
    }

    @Test
    void updatingAPackMayKeepItsOwnFolder() throws IOException {
        Pack pack = registry.add(URL, dir.resolve("a"));
        registry.update(pack.withSource(new PackSource.Link("https://example.org/other.json")));
        assertEquals(Optional.of("https://example.org/other.json"), registry.find(pack.id()).orElseThrow().link());
    }

    @Test
    void createdPackKeepsItsDefinitionUntilAskedToDeleteIt() throws IOException {
        PackDefinition definition = PackDefinition.empty(INFO);
        Pack pack = registry.addLocal(definition, dir.resolve("mine"));

        assertTrue(pack.isLocal());
        assertEquals(Optional.empty(), pack.link(), "not published yet");
        assertEquals("Mi pack", pack.name());
        assertEquals(Optional.of(definition), registry.definition(pack.id()));

        registry.remove(pack.id());
        assertTrue(definitions.saved.containsKey(pack.id()), "removing a pack keeps its definition by default");

        Pack again = registry.addLocal(definition, dir.resolve("mine"));
        registry.remove(again.id(), true);
        assertFalse(definitions.saved.containsKey(again.id()));
    }

    @Test
    void savingADefinitionRenamesThePackAndPublishingGivesItALink() throws IOException {
        Pack pack = registry.addLocal(PackDefinition.empty(INFO), dir.resolve("mine"));
        PackInfo renamed = new PackInfo("Otro nombre", "1.0.0", "", null, "1.21.1", "fabric");
        PackDefinition edited = new PackDefinition(renamed,
            List.of(new PackDefinition.ModrinthMod("P7dR8mSH", "fabric-api", "abc123", "0.100.0")), List.of());

        registry.saveDefinition(pack.id(), edited);
        registry.published(pack.id(), new PackSource.Publication("player/otro", URL, "abc"));

        Pack saved = registry.find(pack.id()).orElseThrow();
        assertEquals("Otro nombre", saved.name());
        assertEquals(Optional.of(URL), saved.link());
        assertEquals(Optional.of(edited), registry.definition(pack.id()));
    }

    @Test
    void linkPacksHaveNoDefinition() throws IOException {
        Pack pack = registry.add(URL, dir.resolve("a"));
        assertEquals(Optional.empty(), registry.definition(pack.id()));
        assertThrows(IllegalArgumentException.class, () -> registry.saveDefinition(pack.id(), PackDefinition.empty(INFO)));
    }

    @Test
    void aCreatedPackCannotTakeAFolderInUse() throws IOException {
        registry.add(URL, dir.resolve("a"));
        SyncException e = assertThrows(SyncException.class,
            () -> registry.addLocal(PackDefinition.empty(INFO), dir.resolve("a/inside")));
        assertInstanceOf(SyncFailure.FolderUsedByPack.class, e.failure());
        assertTrue(definitions.saved.isEmpty(), "nothing is kept for a pack that was not added");
    }

    @Test
    void removingAPackLeavesItsFilesAndSelectsAnother() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("a/mods"));
        Files.writeString(folder.resolve("x.jar"), "jar");
        Pack removed = registry.add(URL, dir.resolve("a"));
        Pack kept = registry.add(URL, dir.resolve("b"));

        registry.remove(removed.id());

        assertEquals(kept, registry.active().orElseThrow());
        assertTrue(Files.exists(folder.resolve("x.jar")));
    }

    @Test
    void rememberedNameIsSavedOnlyWhenItChanges() throws IOException {
        Pack pack = registry.add(URL, dir.resolve("a"));
        registry.rememberName(pack.id(), "Demo");
        assertEquals("Demo", repository.saved.find(pack.id()).orElseThrow().name());

        int saves = repository.saves;
        registry.rememberName(pack.id(), "Demo");
        assertEquals(saves, repository.saves);
    }

    private void assertFolderRejected(Path folder) {
        SyncException e = assertThrows(SyncException.class, () -> registry.add(URL, folder));
        assertInstanceOf(SyncFailure.FolderUsedByPack.class, e.failure());
    }

    static final PackInfo INFO = new PackInfo("Mi pack", "1.0.0", "", null, "1.21.1", "fabric");

    static final class InMemorySettings implements PackSettingsRepository {
        PackSettings saved = PackSettings.EMPTY;
        int saves;

        @Override
        public PackSettings load() {
            return saved;
        }

        @Override
        public void save(PackSettings settings) {
            saved = settings;
            saves++;
        }
    }
}
