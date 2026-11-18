package hopsync.core.sync;

import java.util.Map;
import java.util.Objects;

/**
 * Files installed by HopSync in a game folder, keyed by mod file name and by config path.
 * Only files listed here are ever removed or treated as unmodified by the player.
 *
 * @param contentHash content hash of the last manifest fully applied, or {@code null}
 * @param packId      the pack that installed these files, or {@code null} before one applies
 */
public record LocalState(String contentHash, Map<String, FileRecord> mods, Map<String, FileRecord> configs,
                         String packId) {

    public LocalState {
        mods = Map.copyOf(mods);
        configs = Map.copyOf(configs);
    }

    /** A state no pack has claimed yet. */
    public LocalState(String contentHash, Map<String, FileRecord> mods, Map<String, FileRecord> configs) {
        this(contentHash, mods, configs, null);
    }

    public static LocalState empty() {
        return new LocalState(null, Map.of(), Map.of());
    }

    public boolean ownedBy(String id) {
        return Objects.equals(packId, id);
    }

    public LocalState withPackId(String id) {
        return new LocalState(contentHash, mods, configs, id);
    }
}
