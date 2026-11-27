package hopsync.core.port;

import hopsync.core.publish.RemotePack;

import java.io.IOException;
import java.nio.file.Path;

/** Where the packs the player published are read back from (their GitHub repositories). */
public interface PackRepository {

    /**
     * Reads a pack repository: its {@code hopsync.toml} comes back as text and its {@code configs/} and
     * {@code local-mods/} files are downloaded into {@code into} under the same paths.
     *
     * @param repo GitHub repository as {@code owner/name}
     */
    RemotePack fetch(String repo, Path into) throws IOException;
}
