package hopsync.desktop;

import hopsync.core.event.EventBus;
import hopsync.core.event.SyncEvent;
import hopsync.core.pack.PackRegistry;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.SecretStore;
import hopsync.core.session.ApplyQueue;
import hopsync.core.session.PackSessions;
import hopsync.core.usecase.DefinitionResolver;
import hopsync.core.usecase.ModUpdateService;
import hopsync.core.usecase.PackEditor;
import hopsync.core.usecase.PackPublishing;
import hopsync.desktop.config.AppConfig;
import hopsync.platform.github.GitHubApi;
import hopsync.platform.github.GitHubAuth;
import hopsync.platform.github.GitHubPublisher;
import hopsync.platform.github.ReleaseFeed;
import hopsync.desktop.kit.RemoteImages;
import hopsync.platform.http.DownloadService;
import hopsync.platform.http.ImageCache;
import hopsync.platform.manifest.ManifestService;
import hopsync.platform.modrinth.ModrinthClient;
import hopsync.platform.secrets.SystemKeychain;
import hopsync.platform.settings.DefinitionStore;
import hopsync.platform.settings.SettingsStore;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root: builds every service once, wiring dependencies through constructors. Objects that
 * belong to one pack always come from {@link PackSessions}.
 */
public final class AppContainer {
    private static final System.Logger SYNC_LOG = System.getLogger("hopsync.sync");

    private final AppConfig config;
    private final EventBus eventBus = new EventBus();
    private final SettingsStore settingsStore;
    private final PackRegistry packRegistry;
    private final ManifestService manifestService;
    private final ModCatalog modCatalog;
    private final DefinitionResolver definitionResolver;
    private final PackEditor packEditor;
    private final SecretStore secrets = SystemKeychain.forCurrentOs();
    private final GitHubAuth gitHubAuth;
    private final PackPublishing publishing;
    private final ReleaseFeed releaseFeed;
    private final PackSessions packSessions;
    private final RemoteImages remoteImages;
    private final ExecutorService backgroundExecutor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("hopsync-update-", 0).factory());

    public AppContainer(AppConfig config, SettingsStore settingsStore) {
        this.config = config;
        this.settingsStore = settingsStore;
        DefinitionStore definitions = new DefinitionStore(settingsStore.getFile().resolveSibling("packs"));
        packRegistry = new PackRegistry(settingsStore, definitions);
        DownloadService downloadService = new DownloadService(config.network());
        ImageCache imageCache = new ImageCache(downloadService::getBytes, settingsStore.getFile().resolveSibling("images"));
        remoteImages = new RemoteImages(imageCache::load, backgroundExecutor);
        manifestService = new ManifestService(downloadService, settingsStore.getFile().resolveSibling("manifests"));
        modCatalog = new ModrinthClient(downloadService::getUrlString);
        releaseFeed = new ReleaseFeed(downloadService::getUrlString, config.updateRepo());
        definitionResolver = new DefinitionResolver(modCatalog, definitions);
        packEditor = new PackEditor(modCatalog, packRegistry, definitions);
        gitHubAuth = new GitHubAuth(config.githubClientId(), config.network());
        publishing = new PackPublishing(packRegistry, definitions,
            new GitHubPublisher(new GitHubApi(config.network()), secrets, GitHubAuth.TOKEN_KEY));
        ModUpdateService modUpdateService = new ModUpdateService(manifestService, definitionResolver, downloadService,
            packRegistry::parallelDownloads);
        ApplyQueue applyQueue = new ApplyQueue(backgroundExecutor, packRegistry::chainQueue);
        packSessions = new PackSessions(packRegistry, modUpdateService, applyQueue, eventBus::publish,
            backgroundExecutor);
        // Progress details from the sync engine go to the log file, where support can read them
        eventBus.subscribe(event -> {
            if (event instanceof SyncEvent.LogMessage log) {
                SYNC_LOG.log(System.Logger.Level.INFO, "[" + log.packId() + "] " + log.message());
            }
        });
    }

    public static AppContainer forCurrentUser() {
        return new AppContainer(AppConfig.load(), SettingsStore.forCurrentUser());
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public PackRegistry packRegistry() {
        return packRegistry;
    }

    public PackSessions packSessions() {
        return packSessions;
    }

    public ManifestService manifestService() {
        return manifestService;
    }

    public ModCatalog modCatalog() {
        return modCatalog;
    }

    public DefinitionResolver definitionResolver() {
        return definitionResolver;
    }

    public PackEditor packEditor() {
        return packEditor;
    }

    public SecretStore secrets() {
        return secrets;
    }

    public GitHubAuth gitHubAuth() {
        return gitHubAuth;
    }

    public PackPublishing publishing() {
        return publishing;
    }

    public ReleaseFeed releaseFeed() {
        return releaseFeed;
    }

    /** Virtual threads for background work: checks, applies, listing files, fetching manifests. */
    public Executor backgroundExecutor() {
        return backgroundExecutor;
    }

    public RemoteImages remoteImages() {
        return remoteImages;
    }

    /** Manifest suggested to new players, from app.properties (may be empty). */
    public String presetManifestUrl() {
        return config.presetManifestUrl();
    }

    /** Where the settings are saved, to name it when saving fails. */
    public Path settingsFile() {
        return settingsStore.getFile();
    }
}
