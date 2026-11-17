package hopsync.core.pack;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A modpack the player keeps in sync, bound to its own game folder.
 *
 * @param id      random UUID, stable for the pack's life; events and sessions are keyed by it
 * @param source  a published manifest link, or a pack the player created
 * @param gameDir absolute game folder (or launcher instance folder) holding {@code mods/} and {@code config/}
 * @param name    name from the last manifest fetched, only for display before the network answers;
 *                empty until then
 */
public record Pack(String id, PackSource source, Path gameDir, String name) {

    public Pack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        gameDir = gameDir.toAbsolutePath();
        name = name == null ? "" : name;
    }

    /** A pack followed through its https manifest link. */
    public Pack(String id, String manifestUrl, Path gameDir, String name) {
        this(id, new PackSource.Link(manifestUrl), gameDir, name);
    }

    /** The link friends use to follow the pack: the manifest link, or where a created pack is published. */
    public Optional<String> link() {
        return source.link();
    }

    public boolean isLocal() {
        return source instanceof PackSource.Local;
    }

    /** The name to show: the manifest's, else the link, else nothing yet. */
    public String label() {
        return name.isEmpty() ? link().orElse("") : name;
    }

    public PackPaths paths() {
        return new PackPaths(gameDir);
    }

    public Pack withSource(PackSource newSource) {
        return new Pack(id, newSource, gameDir, name);
    }

    public Pack withGameDir(Path dir) {
        return new Pack(id, source, dir, name);
    }

    public Pack withName(String newName) {
        return new Pack(id, source, gameDir, newName);
    }
}
