package hopsync.desktop.effects;

import hopsync.core.event.EventBus;
import hopsync.core.event.SyncEvent;
import hopsync.core.failure.SyncFailures;
import hopsync.core.manifest.Manifest;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSource;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.SecretStore;
import hopsync.core.session.PackSessions;
import hopsync.core.session.UpdateState;
import hopsync.core.usecase.DefinitionResolver;
import hopsync.core.usecase.PackEditor;
import hopsync.core.usecase.PackPublishing;
import hopsync.desktop.config.AppVersion;
import hopsync.desktop.config.ReleaseVersion;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.store.ModVersions;
import hopsync.desktop.store.Preferences;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.Store;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import hopsync.platform.fs.InstanceFinder;
import hopsync.platform.github.GitHubAuth;
import hopsync.platform.github.ReleaseFeed;
import hopsync.platform.manifest.ManifestService;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The only bridge between the UI and the core: turns core events into actions, and actions with side
 * effects (check, confirm, cancel, remove, load from disk or network, open folders, copy to the clipboard)
 * into calls to the core or the desktop. Screens never call the core themselves.
 */
public final class Effects {
    private static final System.Logger LOG = System.getLogger(Effects.class.getName());
    private static final Duration QUIT_WAIT = Duration.ofSeconds(5);

    private final Store store;
    private final PackRegistry registry;
    private final PackSessions sessions;
    private final ManifestService manifests;
    private final ModLoadService modLoadService;
    private final Executor background;
    private final Clock clock;
    private final Runnable exit;
    private final SheetEffects sheets;
    private final DefinitionResolver createdPacks;
    private final PackEditor editor;
    private final AccountEffects accounts;
    private final PublishEffects publishes;
    private final ReleaseFeed releases;

    /**
     * @param catalog      the mod catalog created packs pick from
     * @param createdPacks resolves a created pack's definition into the manifest the list shows
     * @param editor       changes the mods of a created pack
     * @param secrets      the system keychain, holding the GitHub token
     * @param gitHub       signs the player in to GitHub
     * @param publishing   publishes created packs
     * @param releases     HopSync's own releases, to offer a newer version
     */
    public Effects(Store store, PackRegistry registry, PackSessions sessions, ManifestService manifests,
                   ModCatalog catalog, DefinitionResolver createdPacks, PackEditor editor, SecretStore secrets,
                   GitHubAuth gitHub, PackPublishing publishing, ReleaseFeed releases, ModLoadService modLoadService,
                   InstanceFinder instances, Executor background, Path settingsFile, Clock clock, Runnable exit) {
        this.store = store;
        this.createdPacks = createdPacks;
        this.editor = editor;
        this.releases = releases;
        this.accounts = new AccountEffects(store, secrets, gitHub, background);
        this.publishes = new PublishEffects(store, registry, publishing, background, this::reloadPacks);
        this.registry = registry;
        this.sessions = sessions;
        this.manifests = manifests;
        this.modLoadService = modLoadService;
        this.background = background;
        this.clock = clock;
        this.exit = exit;
        this.sheets = new SheetEffects(store, registry, manifests, catalog, editor, instances, background, settingsFile,
            this::reloadPacks, packId -> sessions.get(packId).startUpdate(),
            packId -> sessions.get(packId).cancelUpdate(),
            packId -> sessions.get(packId).getPhase() == UpdateState.Phase.APPLYING);
    }

    /** Starts listening to the core and the store, then loads the packs. */
    public void start(EventBus events) {
        events.subscribe(event -> store.dispatch(new UiAction.SyncEventReceived(event, clock.instant())));
        store.addEffect(this::run);
        reloadPacks();
        accounts.load();
        checkForAppUpdate();
        if (registry.settings().checkOnStart()) {
            store.dispatch(new UiAction.CheckAll());
        }
    }

    private void run(UiAction action, UiState state) {
        publishes.run(action, state);
        switch (action) {
            case UiAction.CheckPack a -> sessions.get(a.packId()).startUpdate();
            case UiAction.CheckAll a -> state.packs().values().stream()
                .filter(pack -> !pack.phase().isBusy())
                .forEach(pack -> sessions.get(pack.packId()).startUpdate());
            case UiAction.ConfirmPlan a -> sessions.get(a.packId()).confirmPlan();
            case UiAction.DismissPlan a -> sessions.get(a.packId()).cancelUpdate();
            case UiAction.CancelUpdate a -> sessions.get(a.packId()).cancelUpdate();
            case UiAction.ContinueUpdate a -> sessions.get(a.packId()).continueUpdate(a.files());
            case UiAction.Quit a -> quit();
            case UiAction.ReloadInstalled a -> loadInstalled(a.packId());
            case UiAction.SelectPack a -> {
                remember(a.packId());
                loadInstalled(a.packId());
                loadManifest(a.packId());
            }
            case UiAction.OpenFolder a -> openFolder(a.packId());
            case UiAction.OpenAppUpdate a -> Optional.ofNullable(state.appUpdate())
                .ifPresent(update -> openLink(update.url()));
            case UiAction.CopyServerIp a -> Optional.ofNullable(state.packs().get(a.packId()))
                .map(PackUiState::packInfo)
                .filter(info -> info.serverIp() != null && !info.serverIp().isBlank())
                .ifPresent(info -> copy(info.serverIp(), Messages.get("toast.ipCopied")));
            case UiAction.CopyPackLink a -> registry.find(a.packId()).flatMap(Pack::link)
                .ifPresent(link -> copy(link, Messages.get("toast.linkCopied")));
            case UiAction.RemovePack a -> removePack(a.packId(), state);
            case UiAction.OpenModPage a -> openLink(a.url());
            case UiAction.SyncEventReceived a when finishesUpdate(a.event()) -> {
                // Back to what is on disk once the worker has stopped
                loadInstalled(a.event().packId());
                if (a.event() instanceof SyncEvent.UpdateCompleted done && done.failCount() == 0 && done.successCount() > 0) {
                    store.dispatch(new UiAction.ShowToast(Messages.get("toast.applied", done.successCount())));
                }
            }
            case UiAction.LoadModVersions a -> loadModVersions(a);
            case UiAction.ChangeModVersion a -> editPack(a.packId(), () -> editor.changeVersion(a.packId(), a.slug(),
                a.versionId()), null);
            case UiAction.RemoveModFromPack a -> editPack(a.packId(), () -> editor.remove(a.packId(), a.slugOrFile(),
                a.ownJar()), Messages.get("toast.modRemoved", a.name()));
            case UiAction.ResizeLibrary a -> background.execute(() -> registry.rememberLibraryWidth(a.width()));
            case UiAction.ConnectGitHub a -> accounts.run(action, state);
            case UiAction.CancelGitHubConnect a -> accounts.run(action, state);
            case UiAction.OpenGitHubDevice a -> accounts.run(action, state);
            case UiAction.DisconnectGitHub a -> accounts.run(action, state);
            default -> sheets.run(action, state);
        }
    }

    private static boolean finishesUpdate(SyncEvent event) {
        return event instanceof SyncEvent.UpdateCompleted || event instanceof SyncEvent.UpdateCancelled
            || event instanceof SyncEvent.UpdateFailed;
    }

    private void reloadPacks() {
        Optional<Pack> active = registry.active();
        var settings = registry.settings();
        store.dispatch(new UiAction.PacksLoaded(registry.packs(), active.map(Pack::id).orElse(null),
            new Preferences(settings.checkOnStart(), settings.chainQueue(), settings.parallelDownloads(),
                settings.reduceMotion(), settings.libraryWidth(), settings.language(), settings.checkAppUpdates())));
        active.ifPresent(pack -> {
            loadInstalled(pack.id());
            loadManifest(pack.id());
        });
    }

    /**
     * Cancels whatever runs, gives the workers a moment to save the state of what they finished, then exits.
     * Exiting earlier would also be safe: the next check adopts finished files and deletes partial ones.
     */
    private void quit() {
        if (!sessions.anyRunning()) {
            exit.run();
            return;
        }
        sessions.cancelAll();
        background.execute(() -> {
            long deadline = System.nanoTime() + QUIT_WAIT.toNanos();
            while (sessions.anyRunning() && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            exit.run();
        });
    }

    /** The selected pack is the one shown first on the next start. */
    private void remember(String packId) {
        try {
            registry.select(packId);
        } catch (IOException | IllegalArgumentException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the selected pack " + packId, e);
        }
    }

    private void removePack(String packId, UiState state) {
        if (sessions.get(packId).getPhase() == UpdateState.Phase.APPLYING) {
            // The sheet disables the button; this covers an apply that started in between
            store.dispatch(new UiAction.ShowToast(Messages.get("toast.waitForApply")));
            return;
        }
        String name = Optional.ofNullable(state.packs().get(packId)).map(PackUiState::displayName).orElse(packId);
        sessions.drop(packId);
        Optional<String> link = registry.find(packId).flatMap(Pack::link);
        try {
            registry.remove(packId);
            // The cached manifest is HopSync's own file, unless another pack still uses the same link
            link.filter(url -> registry.packs().stream().noneMatch(other -> other.link().filter(url::equals).isPresent()))
                .ifPresent(manifests::forget);
            store.dispatch(new UiAction.ShowToast(Messages.get("toast.removed", name)));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not remove pack " + packId, e);
            store.dispatch(new UiAction.ShowToast(Messages.failure(SyncFailures.from(e, ""))));
        }
        reloadPacks();
    }

    /**
     * Once per start, unless the player turned it off; a failure is only logged, the player loses nothing but the
     * notice.
     */
    private void checkForAppUpdate() {
        if (!releases.configured() || !registry.settings().checkAppUpdates()) return;
        background.execute(() -> {
            try {
                ReleaseVersion.newerThan(AppVersion.VERSION, releases.releases())
                    .ifPresent(release -> store.dispatch(new UiAction.AppUpdateFound(release.tag(), release.url())));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.INFO, "Could not check for a newer HopSync release", e);
            }
        });
    }

    private void openFolder(String packId) {
        registry.find(packId).ifPresent(pack -> background.execute(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    throw new IOException("opening folders is not supported here");
                }
                Desktop.getDesktop().open(pack.gameDir().toFile());
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not open " + pack.gameDir(), e);
                store.dispatch(new UiAction.ShowToast(Messages.get("toast.folderFailed")));
            }
        }));
    }

    private void copy(String text, String notice) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        store.dispatch(new UiAction.ShowToast(notice));
    }

    private void loadInstalled(String packId) {
        registry.find(packId).ifPresent(pack -> background.execute(() -> {
            try {
                store.dispatch(new UiAction.InstalledModsLoaded(packId, modLoadService.load(pack)));
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not list installed mods of pack " + packId, e);
            }
        }));
    }

    /** The cached manifest first, so the list shows right away, then the current one from the network. */
    private void loadManifest(String packId) {
        Optional<Pack> found = registry.find(packId);
        if (found.isEmpty()) return;
        if (!(found.get().source() instanceof PackSource.Link(String url))) {
            // A created pack's manifest comes from its definition, even once published
            background.execute(() -> {
                try {
                    store.dispatch(new UiAction.ManifestLoaded(packId, createdPacks.resolve(found.get())));
                } catch (Exception e) {
                    store.dispatch(new UiAction.ManifestFailed(packId, SyncFailures.from(e, "")));
                }
            });
            return;
        }
        background.execute(() -> {
            manifests.cached(url)
                .ifPresent(cached -> store.dispatch(new UiAction.ManifestLoaded(packId, cached)));
            try {
                Manifest manifest = manifests.fetchManifest(url);
                registry.rememberName(packId, manifest.pack().name());
                store.dispatch(new UiAction.ManifestLoaded(packId, manifest));
            } catch (Exception e) {
                store.dispatch(new UiAction.ManifestFailed(packId, SyncFailures.from(e, "")));
            }
        });
    }

    @FunctionalInterface
    private interface Edit {
        void run() throws IOException;
    }

    /** Changes a created pack, then checks it so the change shows up for review like any update. */
    private void editPack(String packId, Edit edit, String notice) {
        background.execute(() -> {
            try {
                edit.run();
                if (notice != null) store.dispatch(new UiAction.ShowToast(notice));
                sessions.get(packId).startUpdate();
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not change pack " + packId, e);
                store.dispatch(new UiAction.ShowToast(Messages.failure(SyncFailures.from(e, ""))));
            }
        });
    }

    private void loadModVersions(UiAction.LoadModVersions request) {
        background.execute(() -> {
            try {
                List<ModVersions.Choice> choices = editor.versions(request.packId(), request.slug()).stream()
                    .map(version -> new ModVersions.Choice(version.id(), version.versionNumber(),
                        version.versionNumber().equals(request.currentVersion()), version.release()))
                    .toList();
                store.dispatch(new UiAction.ModVersionsLoaded(request.packId(), request.slug(), choices));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not load the versions of " + request.slug(), e);
                store.dispatch(new UiAction.ModVersionsFailed(request.packId(), request.slug(),
                    SyncFailures.from(e, "")));
            }
        });
    }

    private void openLink(String url) {
        if (url == null || !url.startsWith("https://")) return;
        background.execute(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("opening links is not supported here");
                }
                Desktop.getDesktop().browse(URI.create(url));
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not open " + url, e);
                store.dispatch(new UiAction.ShowToast(Messages.get("toast.linkFailed")));
            }
        });
    }
}
