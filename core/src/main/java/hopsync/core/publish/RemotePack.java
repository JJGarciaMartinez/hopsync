package hopsync.core.publish;

import hopsync.core.pack.PackSource;

/**
 * A pack as its repository holds it.
 *
 * @param toml        the repository's {@code hopsync.toml}
 * @param publication where the pack is published; its content hash is still empty
 */
public record RemotePack(String toml, PackSource.Publication publication) {
}
