package hopsync.core.port;

import hopsync.core.publish.FetchProgress;
import hopsync.core.publish.RemotePack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Where the packs the player published are read back from (their GitHub repositories). */
public interface PackRepository {

    /**
     * Reads a pack repository: its {@code hopsync.toml} comes back as text and its {@code configs/} and
     * {@code local-mods/} files are downloaded into {@code into} under the same paths.
     *
     * @param repo     GitHub repository as {@code owner/name}
     * @param progress told after each file, possibly from several threads; a pack can hold hundreds of jars
     */
    RemotePack fetch(String repo, Path into, Consumer<FetchProgress> progress) throws IOException;
}
