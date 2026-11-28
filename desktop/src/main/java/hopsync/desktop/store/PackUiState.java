package hopsync.desktop.store;

import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.Manifest;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.sync.SyncPlan;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the UI shows about one pack. Immutable: the reducer returns a new instance on each change.
 *
 * @param manifest      the last manifest known (from the network or the cache), or {@code null}
 * @param manifestError why the manifest could not be fetched for display, or {@code null}
 * @param plan          the last plan computed, or {@code null} before the first check
 * @param installed     what is in the pack's {@code mods/} folder
 * @param progress      while applying
 * @param rowStatus     per file, only for files the running or last apply touched
 * @param result        counts of the last finished apply
 * @param error         why the last update stopped, in {@link PackPhase#ERROR}
 * @param lastChecked   when the last check finished, or {@code null}
 * @param created       a pack the player created, rather than one followed by its link
 * @param unpublished   a created pack that is published and changed since, so it has changes to publish
 * @param optionalPicks the optional config units ({@code ConfigUnits.Unit#key()}) the player ticked in the review
 */
public record PackUiState(String packId, String name, String manifestUrl, boolean created, boolean unpublished,
                          Path gameDir, PackPhase phase,
                          Manifest manifest, SyncFailure manifestError, SyncPlan plan, InstalledMods installed,
                          Progress progress, Map<String, RowStatus> rowStatus, Result result, SyncFailure error,
                          Instant lastChecked, Set<String> optionalPicks) {

    /** @param currentFile the file being downloaded, or {@code null} */
    public record Progress(int doneFiles, int totalFiles, long bytesDone, long bytesTotal, long bytesPerSecond,
                           String currentFile) {
        public static final Progress NONE = new Progress(0, 0, 0, 0, 0, null);

        /** 0–100, by bytes when there is something to download, else by files. */
        public int percent() {
            if (bytesTotal > 0) return Math.clamp(bytesDone * 100 / bytesTotal, 0, 100);
            return totalFiles > 0 ? doneFiles * 100 / totalFiles : 0;
        }

        Progress withCurrentFile(String file) {
            return new Progress(doneFiles, totalFiles, bytesDone, bytesTotal, bytesPerSecond, file);
        }
    }

    public record Result(int succeeded, int failed) {
        public static final Result NONE = new Result(0, 0);
    }

    public PackUiState {
        rowStatus = Map.copyOf(rowStatus);
        optionalPicks = Set.copyOf(optionalPicks);
    }

    /** A pack the player created and has published, so its repository can bring it back after it is removed. */
    public boolean published() {
        return created && !manifestUrl.isBlank();
    }

    /** Pack details from the manifest, or {@code null} before it is known. */
    public PackInfo packInfo() {
        return manifest == null ? null : manifest.pack();
    }

    /** Mods the pack lists, 0 before the manifest is known. */
    public int modCount() {
        return manifest == null ? 0 : manifest.mods().size();
    }

    /** Name to show: from the manifest when known, else the cached one, else the link. */
    public String displayName() {
        if (manifest != null) return manifest.pack().name();
        return name.isEmpty() ? manifestUrl : name;
    }

    public static PackUiState of(Pack pack) {
        return new PackUiState(pack.id(), pack.name(), pack.link().orElse(""), pack.isLocal(), false, pack.gameDir(), PackPhase.UNCHECKED,
            null, null, null, InstalledMods.NONE, Progress.NONE, Map.of(), Result.NONE, null, null, Set.of());
    }

    /** Same pack with its link, folder and name taken from the registry. */
    PackUiState withPack(Pack pack) {
        return new PackUiState(packId, pack.name(), pack.link().orElse(""), pack.isLocal(), unpublished, pack.gameDir(), phase, manifest,
            manifestError, plan, installed, progress, rowStatus, result, error, lastChecked, optionalPicks);
    }

    PackUiState withUnpublished(boolean changed) {
        return new PackUiState(packId, name, manifestUrl, created, changed, gameDir, phase, manifest, manifestError,
            plan, installed, progress, rowStatus, result, error, lastChecked, optionalPicks);
    }

    PackUiState withPhase(PackPhase newPhase) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, newPhase, manifest, manifestError,
            plan, installed, progress, rowStatus, result, error, lastChecked, optionalPicks);
    }

    PackUiState withManifest(Manifest newManifest, SyncFailure newError) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, newManifest, newError, plan, installed,
            progress, rowStatus, result, error, lastChecked, optionalPicks);
    }

    PackUiState withPlan(SyncPlan newPlan, Instant checkedAt) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, newPlan.manifest(), null, newPlan,
            installed, progress, rowStatus, result, error, checkedAt, Set.of());
    }

    PackUiState withInstalled(InstalledMods files) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, manifest, manifestError, plan,
            files, progress, rowStatus, result, error, lastChecked, optionalPicks);
    }

    PackUiState withProgress(Progress newProgress) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, manifest, manifestError, plan,
            installed, newProgress, rowStatus, result, error, lastChecked, optionalPicks);
    }

    PackUiState withRowStatus(Map<String, RowStatus> rows) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, manifest, manifestError, plan,
            installed, progress, rows, result, error, lastChecked, optionalPicks);
    }

    PackUiState withResult(Result newResult) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, manifest, manifestError, plan,
            installed, progress, rowStatus, newResult, error, lastChecked, optionalPicks);
    }

    PackUiState withError(SyncFailure failure) {
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, manifest, manifestError, plan,
            installed, progress, rowStatus, result, failure, lastChecked, optionalPicks);
    }

    /** Ticks or unticks an optional config unit of the review. */
    PackUiState withPickToggled(String unitKey) {
        Set<String> next = new HashSet<>(optionalPicks);
        if (!next.remove(unitKey)) next.add(unitKey);
        return new PackUiState(packId, name, manifestUrl, created, unpublished, gameDir, phase, manifest, manifestError,
            plan, installed, progress, rowStatus, result, error, lastChecked, next);
    }
}
