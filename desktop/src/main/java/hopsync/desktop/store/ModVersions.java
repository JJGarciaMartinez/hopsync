package hopsync.desktop.store;

import hopsync.core.failure.SyncFailure;

import java.util.List;

/**
 * The versions offered for one mod of a created pack, for the "Cambiar versión" menu of its row.
 *
 * @param slug    the mod, as the pack's catalog names it
 * @param choices newest first; {@code null} while they load
 * @param error   why they could not be loaded, or {@code null}
 */
public record ModVersions(String packId, String slug, List<Choice> choices, SyncFailure error) {

    public ModVersions {
        choices = choices == null ? null : List.copyOf(choices);
    }

    /**
     * @param current the version the pack has now
     * @param release a stable release, not a beta or alpha
     */
    public record Choice(String id, String number, boolean current, boolean release) {
    }
}
