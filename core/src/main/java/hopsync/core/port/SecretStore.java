package hopsync.core.port;

import java.io.IOException;
import java.util.Optional;

/**
 * Where the player's secrets are kept between runs: the operating system's keychain. Secrets never go to the
 * settings file or the logs.
 */
public interface SecretStore {

    /** Whether secrets outlive the app; {@code false} when the system has no keychain HopSync can use. */
    boolean persistent();

    Optional<String> read(String key) throws IOException;

    void write(String key, String secret) throws IOException;

    /** Removes the secret; a missing one is fine. */
    void delete(String key) throws IOException;
}
