package hopsync.desktop.effects;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.failure.SyncFailures;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSource;
import hopsync.core.port.ModCatalog;
import hopsync.core.usecase.PackEditor;
import hopsync.desktop.config.AppVersion;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.logging.Logging;
import hopsync.desktop.store.GameFolder;
import hopsync.desktop.store.LinkCheck;
import hopsync.desktop.store.Preferences;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.Store;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import hopsync.platform.fs.InstanceFinder;
import hopsync.platform.manifest.ManifestService;
import hopsync.platform.system.SystemInfoService;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Side effects of the sheets: checking a pack link, finding launcher instances, telling which pack uses a
 * folder, and saving a new pack, a pack's settings or the app preferences.
 */
final class SheetEffects {
    private static final System.Logger LOG = System.getLogger(SheetEffects.class.getName());
    static final String NEW_PACK_VERSION = "1.0.0";
    static final int SEARCH_RESULTS = 20;

    private final Store store;
    private final PackRegistry registry;
    private final ManifestService manifests;
    private final ModCatalog catalog;
    private final PackEditor editor;
    /** The sheet open before the action being handled; closing the add-mods sheet needs to know what it added. */
    private Sheet previous;
    private final InstanceFinder instances;
    private final java.util.concurrent.Executor background;
    private final Path settingsFile;
    private final Runnable reloadPacks;
    private final Consumer<String> checkPack;
    private final Consumer<String> dropUpdate;
    private final Predicate<String> writing;

    /**
     * @param reloadPacks reloads the registry into the store after a save
     * @param checkPack   starts checking a newly added pack
     * @param dropUpdate  drops a pack's check, plan or place in the queue, made for its old link or folder
     * @param writing     whether a pack is writing files right now, when its settings cannot change
     */
    SheetEffects(Store store, PackRegistry registry, ManifestService manifests, ModCatalog catalog, PackEditor editor,
                 InstanceFinder instances,
                 java.util.concurrent.Executor background, Path settingsFile, Runnable reloadPacks,
                 Consumer<String> checkPack, Consumer<String> dropUpdate, Predicate<String> writing) {
        this.store = store;
        this.registry = registry;
        this.manifests = manifests;
        this.catalog = catalog;
        this.editor = editor;
        this.instances = instances;
        this.background = background;
        this.settingsFile = settingsFile;
        this.reloadPacks = reloadPacks;
        this.checkPack = checkPack;
        this.dropUpdate = dropUpdate;
        this.writing = writing;
    }

    void run(UiAction action, UiState state) {
        try {
            handle(action, state);
        } finally {
            previous = state.sheet();
        }
    }

    private void handle(UiAction action, UiState state) {
        switch (action) {
            case UiAction.OpenAddMods a when state.sheet() instanceof Sheet.AddMods sheet -> {
                loadPackMods(sheet.packId());
                search(sheet);
            }
            case UiAction.SearchMods a when state.sheet() instanceof Sheet.AddMods sheet && sheet.results() == null ->
                search(sheet);
            case UiAction.OpenModDetail a when state.sheet() instanceof Sheet.AddMods sheet ->
                loadDetail(sheet, a.project());
            case UiAction.AddCatalogMod a when state.sheet() instanceof Sheet.AddMods sheet
                && sheet.adding().contains(a.projectId()) -> addCatalogMod(sheet.packId(), a.projectId());
            // Outside the sheet nothing else will check the pack, so the jar is checked at once
            case UiAction.AddOwnJar a -> addOwnJar(a.packId(), a.jar(), !(state.sheet() instanceof Sheet.AddMods));
            // What was added reaches the game folder through the usual check and review
            case UiAction.CloseSheet a when previous instanceof Sheet.AddMods closed && state.sheet() == null
                && closed.added() > 0 -> checkPack.accept(closed.packId());
            case UiAction.Escape a when previous instanceof Sheet.AddMods closed && state.sheet() == null
                && closed.added() > 0 -> checkPack.accept(closed.packId());
            case UiAction.CheckLink a -> fetchLink(state.sheet());
            case UiAction.StartFromScratch a when state.sheet() instanceof Sheet.AddPack wizard
                && wizard.creation() != null && wizard.creation().versions() == null -> loadGameVersions();
            case UiAction.WizardNext a when state.sheet() instanceof Sheet.AddPack wizard && wizard.step() == 2 ->
                findFolders();
            case UiAction.ChooseFolder a -> checkFolder(a.path(), state.sheet());
            case UiAction.FinishAddPack a when state.sheet() instanceof Sheet.AddPack wizard -> addPack(wizard);
            case UiAction.SavePackSettings a when state.sheet() instanceof Sheet.PackSettings settings ->
                savePackSettings(settings);
            case UiAction.SaveAppSettings a when state.sheet() instanceof Sheet.AppSettings settings ->
                saveAppSettings(settings.draft());
            case UiAction.CopyDiagnostics a -> copyDiagnostics();
            case UiAction.OpenLogsFolder a -> openLogsFolder();
            default -> { }
        }
    }

    private void fetchLink(Sheet sheet) {
        LinkCheck link = switch (sheet) {
            case Sheet.AddPack wizard -> wizard.link();
            case Sheet.PackSettings settings -> settings.link();
            case null, default -> null;
        };
        if (!(link instanceof LinkCheck.Checking checking)) return;
        background.execute(() -> {
            try {
                store.dispatch(new UiAction.LinkChecked(checking.url(), manifests.fetchManifest(checking.url())));
            } catch (Exception e) {
                store.dispatch(new UiAction.LinkFailed(checking.url(), SyncFailures.from(e, "")));
            }
        });
    }

    private void findFolders() {
        background.execute(() -> {
            List<GameFolder> found = instances.find().stream()
                .map(instance -> new GameFolder(kindOf(instance.launcher()), instance.name(), instance.gameDir(),
                    instance.jarCount(), usedBy(instance.gameDir(), null)))
                .toList();
            store.dispatch(new UiAction.FoldersFound(found));
        });
    }

    private void checkFolder(Path path, Sheet sheet) {
        String ownPackId = sheet instanceof Sheet.PackSettings settings ? settings.packId() : null;
        background.execute(() -> store.dispatch(new UiAction.FolderChecked(new GameFolder(GameFolder.Kind.OTHER, null,
            path, InstanceFinder.jarCount(path), usedBy(path, ownPackId)))));
    }

    private String usedBy(Path folder, String exceptPackId) {
        return registry.packUsing(folder, exceptPackId)
            .map(Pack::label)
            .orElse(null);
    }

    private void loadPackMods(String packId) {
        background.execute(() -> {
            try {
                store.dispatch(new UiAction.PackModsLoaded(packId, editor.catalogProjects(packId)));
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not read the mods of pack " + packId, e);
            }
        });
    }

    private void search(Sheet.AddMods sheet) {
        String query = sheet.query();
        background.execute(() -> {
            try {
                store.dispatch(new UiAction.ModsFound(query,
                    catalog.search(query, sheet.minecraft(), sheet.loader(), SEARCH_RESULTS)));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not search mods for \"" + query + "\"", e);
                store.dispatch(new UiAction.ModSearchFailed(query, SyncFailures.from(e, "")));
            }
        });
    }

    private void loadDetail(Sheet.AddMods sheet, CatalogProject project) {
        background.execute(() -> {
            try {
                ProjectDetails details = catalog.details(project.id()).orElse(null);
                if (details == null) {
                    store.dispatch(new UiAction.ModDetailFailed(project.id(),
                        new SyncFailure.ModUnavailable(project.title())));
                    return;
                }
                try {
                    PackEditor.Preview preview = editor.preview(sheet.packId(), project.id());
                    store.dispatch(new UiAction.ModDetailLoaded(project.id(), details, preview.version(),
                        preview.needs(), null));
                } catch (SyncException e) {
                    if (!(e.failure() instanceof SyncFailure.ModUnavailable unavailable)) throw e;
                    store.dispatch(new UiAction.ModDetailLoaded(project.id(), details, null, List.of(),
                        unavailable.mod()));
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not load the page of " + project.id(), e);
                store.dispatch(new UiAction.ModDetailFailed(project.id(), SyncFailures.from(e, "")));
            }
        });
    }

    private void addCatalogMod(String packId, String projectId) {
        background.execute(() -> {
            try {
                PackEditor.Added added = editor.addFromCatalog(packId, projectId);
                store.dispatch(new UiAction.ModAdded(projectId, new Sheet.Added(added.title(), added.alsoAdded()),
                    editor.catalogProjects(packId)));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not add " + projectId + " to pack " + packId, e);
                store.dispatch(new UiAction.ModAddFailed(projectId, SyncFailures.from(e, "")));
            }
        });
    }

    private void addOwnJar(String packId, Path jar, boolean checkAfter) {
        background.execute(() -> {
            try {
                String file = editor.addOwnJar(packId, jar);
                store.dispatch(new UiAction.ModAdded(null, new Sheet.Added(file, List.of()),
                    editor.catalogProjects(packId)));
                if (checkAfter) checkPack.accept(packId);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not add " + jar + " to pack " + packId, e);
                store.dispatch(new UiAction.ModAddFailed(null, SyncFailures.from(e, jar.toString())));
            }
        });
    }

    private void loadGameVersions() {
        background.execute(() -> {
            try {
                store.dispatch(new UiAction.GameVersionsLoaded(catalog.gameVersions()));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not load the Minecraft versions", e);
                store.dispatch(new UiAction.GameVersionsFailed(SyncFailures.from(e, "")));
            }
        });
    }

    /** A new pack starts empty at version 1.0.0; it has nothing to check until it gets mods. */
    private void createPack(Sheet.Creation creation, Path folder) {
        background.execute(() -> {
            try {
                checkWritable(folder);
                String server = creation.server().isBlank() ? null : creation.server().strip();
                PackInfo info = new PackInfo(creation.name().strip(), NEW_PACK_VERSION, "", server, creation.minecraft(),
                    creation.loader());
                Pack added = registry.addLocal(PackDefinition.empty(info), folder);
                registry.select(added.id());
                store.dispatch(new UiAction.SheetSaved());
                reloadPacks.run();
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not create the pack " + creation.name(), e);
                store.dispatch(new UiAction.SheetFailed(SyncFailures.from(e, folder.toString())));
            }
        });
    }

    private void addPack(Sheet.AddPack wizard) {
        if (wizard.chosen() == null || !wizard.chosen().isFree()) {
            return;
        }
        if (wizard.creation() != null) {
            if (wizard.creation().ready()) createPack(wizard.creation(), wizard.chosen().path());
            return;
        }
        if (!(wizard.link() instanceof LinkCheck.Valid valid)) {
            return;
        }
        Path folder = wizard.chosen().path();
        background.execute(() -> {
            try {
                checkWritable(folder);
                Pack added = registry.add(valid.url(), folder);
                registry.rememberName(added.id(), valid.info().name());
                registry.select(added.id());
                store.dispatch(new UiAction.SheetSaved());
                reloadPacks.run();
                checkPack.accept(added.id());
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not add the pack " + valid.url(), e);
                store.dispatch(new UiAction.SheetFailed(SyncFailures.from(e, folder.toString())));
            }
        });
    }

    private void savePackSettings(Sheet.PackSettings settings) {
        Pack pack = registry.find(settings.packId()).orElse(null);
        if (pack == null) return;
        String url = settings.url().trim();
        boolean newLink = !pack.link().map(url::equals).orElse(false);
        // A different link must have been checked: it may be another pack altogether
        if (newLink && !(settings.link() instanceof LinkCheck.Valid valid && valid.url().equals(url))) return;
        if (!settings.serverValid()) return;
        Path folder = settings.folder().path();
        boolean moved = newLink || !folder.equals(pack.gameDir());
        background.execute(() -> {
            try {
                if (writing.test(pack.id())) {
                    // The sheet disables saving; this covers an apply that started in between
                    store.dispatch(new UiAction.ShowToast(Messages.get("toast.waitForApply")));
                    return;
                }
                checkWritable(folder);
                if (moved) {
                    dropUpdate.accept(pack.id());
                }
                boolean newServer = pack.isLocal() && settings.server() != null
                    && !serverOf(pack.id()).equals(settings.server().strip());
                if (newServer) {
                    editor.changeServer(pack.id(), settings.server());
                }
                Pack changed = newLink ? pack.withSource(new PackSource.Link(url)).withName("") : pack;
                registry.update(changed.withGameDir(folder));
                store.dispatch(new UiAction.SheetSaved());
                reloadPacks.run();
                // A published pack then offers to publish the new address
                if (newServer) checkPack.accept(pack.id());
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not save the settings of pack " + pack.id(), e);
                store.dispatch(new UiAction.SheetFailed(SyncFailures.from(e, folder.toString())));
            }
        });
    }

    /** The server address saved in a created pack's definition, blank for none. */
    private String serverOf(String packId) {
        return registry.definition(packId).map(definition -> definition.pack().serverIp()).orElse("");
    }

    private void saveAppSettings(Preferences draft) {
        background.execute(() -> {
            try {
                registry.updatePreferences(draft.checkOnStart(), draft.chainQueue(), draft.parallelDownloads(),
                    draft.reduceMotion(), draft.language(), draft.checkAppUpdates());
                store.dispatch(new UiAction.SheetSaved());
                reloadPacks.run();
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not save the preferences", e);
                store.dispatch(new UiAction.ShowToast(Messages.get("settings.saveFailed",
                    Messages.failure(SyncFailures.from(e, settingsFile.toString())))));
            }
        });
    }

    /** The game folder must exist and accept new files; HopSync never creates it on its own. */
    private static void checkWritable(Path folder) throws SyncException {
        if (!Files.isDirectory(folder)) {
            throw new SyncException(new SyncFailure.FolderUnavailable(folder.toString()));
        }
        if (!Files.isWritable(folder)) {
            throw new SyncException(new SyncFailure.PermissionDenied(folder.toString()));
        }
    }

    private void copyDiagnostics() {
        String text = diagnostics();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        store.dispatch(new UiAction.ShowToast(Messages.get("toast.diagnosticsCopied")));
    }

    private void openLogsFolder() {
        Path logs = Logging.folderFor(settingsFile);
        background.execute(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    throw new IOException("opening folders is not supported here");
                }
                Files.createDirectories(logs);
                Desktop.getDesktop().open(logs.toFile());
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not open " + logs, e);
                store.dispatch(new UiAction.ShowToast(Messages.get("toast.folderFailed")));
            }
        });
    }

    /** What a bug report needs: versions, system, scale, paths and packs, without personal data beyond paths. */
    String diagnostics() {
        StringBuilder text = new StringBuilder();
        text.append("HopSync ").append(AppVersion.VERSION_APP).append('\n');
        text.append("OS: ").append(SystemInfoService.getOS()).append(' ').append(System.getProperty("os.version"))
            .append('\n');
        text.append("Java: ").append(System.getProperty("java.version")).append(' ')
            .append(System.getProperty("java.vendor")).append('\n');
        text.append("UI scale: ").append(System.getProperty("sun.java2d.uiScale", "auto")).append('\n');
        text.append("Settings: ").append(settingsFile).append('\n');
        text.append("Logs: ").append(Logging.folderFor(settingsFile)).append('\n');
        text.append("Packs: ").append(registry.packs().size()).append('\n');
        for (Pack pack : registry.packs()) {
            text.append("- ").append(pack.name().isEmpty() ? "?" : pack.name()).append(" | ").append(pack.link().orElse("created here"))
                .append(" | ").append(pack.gameDir()).append('\n');
        }
        return text.toString();
    }

    private static GameFolder.Kind kindOf(InstanceFinder.Launcher launcher) {
        return switch (launcher) {
            case OFFICIAL -> GameFolder.Kind.OFFICIAL;
            case PRISM -> GameFolder.Kind.PRISM;
            case CURSEFORGE -> GameFolder.Kind.CURSEFORGE;
            case MODRINTH -> GameFolder.Kind.MODRINTH;
        };
    }
}
