package hopsync.core.pack;

import java.util.Objects;

/**
 * What the player decided about one optional config unit of a pack, at one revision of its content.
 *
 * @param revision the unit's revision the decision was taken at; a different one means the creator changed it
 * @param accepted {@code true} when the player ticked it
 * @param applied  {@code true} once the unit's files were installed; until then an accepted unit is forced in
 */
public record OptionalChoice(String revision, boolean accepted, boolean applied) {

    public OptionalChoice {
        Objects.requireNonNull(revision, "revision");
    }

    public OptionalChoice asApplied() {
        return new OptionalChoice(revision, accepted, true);
    }
}
