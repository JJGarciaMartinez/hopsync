package hopsync.desktop.effects;

import hopsync.core.event.SyncEvent;
import hopsync.core.failure.SyncFailures;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSource;
import hopsync.core.usecase.PackPublishing;
import hopsync.core.util.CancellationToken;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.Store;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

/**
 * Publishing created packs: what a publish would share, the publish itself with its steps, and whether a published
 * pack has changes since.
 */
final class PublishEffects {
    private static final System.Logger LOG = System.getLogger(PublishEffects.class.getName());

    private final Store store;
    private final PackRegistry registry;
    private final PackPublishing publishing;
    private final Executor background;
    private final Runnable reloadPacks;

    PublishEffects(Store store, PackRegistry registry, PackPublishing publishing, Executor background,
                   Runnable reloadPacks) {
        this.store = store;
        this.registry = registry;
        this.publishing = publishing;
        this.background = background;
        this.reloadPacks = reloadPacks;
    }

    void run(UiAction action, UiState state) {
        switch (action) {
            case UiAction.OpenPublish a when state.sheet() instanceof Sheet.Publish sheet -> listOwnFiles(sheet.packId());
            case UiAction.StartPublish a when state.sheet() instanceof Sheet.Publish sheet
                && sheet.stage() == Sheet.Publish.Stage.PUBLISHING -> publish(sheet);
            // A created pack's definition may have changed: every edit ends in a check
            case UiAction.SyncEventReceived a when a.event() instanceof SyncEvent.PlanReady
                || a.event() instanceof SyncEvent.UpToDate -> checkUnpublished(a.event().packId());
            case UiAction.PacksLoaded a -> a.packs().stream().filter(Pack::isLocal)
                .forEach(pack -> checkUnpublished(pack.id()));
            default -> { }
        }
    }

    private void listOwnFiles(String packId) {
        background.execute(() -> registry.find(packId).ifPresent(pack -> {
            try {
                List<String> files = publishing.request(pack, null).files().keySet().stream().sorted().toList();
                store.dispatch(new UiAction.PublishFilesLoaded(packId, files));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not list what pack " + packId + " would publish", e);
                store.dispatch(new UiAction.PublishFailed(packId, SyncFailures.from(e, "")));
            }
        }));
    }

    private void publish(Sheet.Publish sheet) {
        String packId = sheet.packId();
        background.execute(() -> {
            try {
                Pack pack = registry.find(packId).orElseThrow();
                PackSource.Publication publication = publishing.publish(pack, sheet.repoName(),
                    step -> store.dispatch(new UiAction.PublishProgressed(packId, step)), CancellationToken.none());
                store.dispatch(new UiAction.Published(packId, publication.url()));
                store.dispatch(new UiAction.ShowToast(Messages.get(sheet.update() ? "toast.publishedChanges"
                    : "toast.published")));
                reloadPacks.run();
            } catch (CancellationException e) {
                // Nothing to say: the player closed HopSync
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not publish pack " + packId, e);
                store.dispatch(new UiAction.PublishFailed(packId, SyncFailures.from(e, "")));
            }
        });
    }

    private void checkUnpublished(String packId) {
        background.execute(() -> registry.find(packId).filter(Pack::isLocal).ifPresent(pack -> {
            try {
                store.dispatch(new UiAction.PublishStateChecked(packId, publishing.changedSincePublished(pack)));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not tell whether pack " + packId + " has changes to publish", e);
            }
        }));
    }
}
