package hopsync.desktop.effects;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSettings;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackSettingsRepository;
import hopsync.core.usecase.PackEditor;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.Store;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import hopsync.platform.fs.InstanceFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheetEffectsDetailTest {
    private static final PackInfo INFO = new PackInfo("Mi pack", "1.0.0", "", null, "1.21.1", "fabric");
    private static final CatalogProject PROJECT = new CatalogProject("sodium", "sodium", "Sodium", "", "", null, 0);
    private static final ProjectDetails DETAILS = new ProjectDetails("sodium", "sodium", "Sodium", "s", "body", List.of());

    @TempDir
    Path dir;

    private final Map<String, List<CatalogVersion>> versions = new HashMap<>();
    private final List<UiAction> dispatched = new ArrayList<>();
    private final List<String> checked = new ArrayList<>();
    private Optional<ProjectDetails> details = Optional.of(DETAILS);
    private IOException detailsError;
    private SheetEffects effects;
    private Sheet.AddMods sheet;

    private static CatalogVersion version(String id, String project) {
        return new CatalogVersion(id, project, "1.0", List.of("1.21.1"), List.of("fabric"), true,
            List.of(new CatalogVersion.File("https://cdn.modrinth.com/" + id + ".jar", id + ".jar", "a".repeat(128), 1, true)),
            List.of());
    }

    @BeforeEach
    void setUp() throws IOException {
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
                return List.of();
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
                return List.of();
            }

            @Override
            public Optional<ProjectDetails> details(String id) throws IOException {
                if (detailsError != null) throw detailsError;
                return details;
            }

            @Override
            public Map<String, CatalogProject> projects(Collection<String> ids) {
                return Map.of();
            }
        };
        PackRegistry registry = new PackRegistry(settings, definitions);
        PackEditor editor = new PackEditor(catalog, registry, definitions);
        Pack pack = registry.addLocal(PackDefinition.empty(INFO), dir.resolve("game"));
        sheet = Sheet.AddMods.start(pack.id(), "1.21.1", "fabric");

        Store store = new Store(UiState.INITIAL);
        store.addEffect((action, state) -> dispatched.add(action));
        effects = new SheetEffects(store, registry, null, catalog, editor, new InstanceFinder("Linux", Map.of(), dir),
            Runnable::run, dir.resolve("settings.json"), () -> { }, checked::add, id -> { }, id -> false);
    }

    private static UiState withSheet(Sheet open) {
        UiState i = UiState.INITIAL;
        return new UiState(i.packs(), i.selectedPackId(), i.tab(), i.filter(), i.queries(), i.packQuery(),
            i.expandedRow(), open, i.toast(), i.preferences(), i.modVersions(), i.account(), i.appUpdate());
    }

    private List<UiAction> open() throws Exception {
        UiState state = withSheet(sheet);
        SwingUtilities.invokeAndWait(() -> effects.run(new UiAction.OpenModDetail(PROJECT), state));
        SwingUtilities.invokeAndWait(() -> { });
        return dispatched.stream().filter(a -> !(a instanceof UiAction.OpenModDetail)).toList();
    }

    @Test
    void loadsDetailsAndPreview() throws Exception {
        versions.put("sodium", List.of(version("s1", "sodium")));
        UiAction.ModDetailLoaded loaded = assertInstanceOf(UiAction.ModDetailLoaded.class, open().getFirst());
        assertEquals("sodium", loaded.projectId());
        assertEquals(DETAILS, loaded.details());
        assertEquals("s1", loaded.version().id());
        assertTrue(loaded.needs().isEmpty());
        assertFalse(loaded.noVersion());
    }

    @Test
    void noVersionStillShowsTheDetails() throws Exception {
        UiAction.ModDetailLoaded loaded = assertInstanceOf(UiAction.ModDetailLoaded.class, open().getFirst());
        assertEquals(DETAILS, loaded.details());
        assertNull(loaded.version());
        assertTrue(loaded.needs().isEmpty());
        assertTrue(loaded.noVersion());
    }

    @Test
    void unknownProjectFails() throws Exception {
        details = Optional.empty();
        UiAction.ModDetailFailed failed = assertInstanceOf(UiAction.ModDetailFailed.class, open().getFirst());
        assertEquals("sodium", failed.projectId());
        assertEquals(new SyncFailure.ModUnavailable("Sodium"), failed.failure());
    }

    @Test
    void ioErrorFailsWithoutThrowing() throws Exception {
        detailsError = new IOException("offline");
        UiAction.ModDetailFailed failed = assertInstanceOf(UiAction.ModDetailFailed.class, open().getFirst());
        assertEquals("sodium", failed.projectId());
    }

    @Test
    void closingWithEscapeChecksThePackOnlyWhenModsWereAdded() throws Exception {
        Sheet.AddMods withMods = new Sheet.AddMods(sheet.packId(), "1.21.1", "fabric", "", null, null, null,
            Set.of(), 1, null, null, null);
        SwingUtilities.invokeAndWait(() -> {
            effects.run(new UiAction.OpenModDetail(PROJECT), withSheet(withMods));
            effects.run(new UiAction.Escape(), withSheet(null));
        });
        assertEquals(List.of(sheet.packId()), checked);

        SwingUtilities.invokeAndWait(() -> {
            effects.run(new UiAction.OpenModDetail(PROJECT), withSheet(sheet));
            effects.run(new UiAction.Escape(), withSheet(null));
        });
        assertEquals(List.of(sheet.packId()), checked);
    }
}
