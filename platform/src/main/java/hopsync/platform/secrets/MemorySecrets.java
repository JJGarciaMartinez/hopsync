package hopsync.platform.secrets;

import hopsync.core.port.SecretStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Secrets for this session only, when the system has no keychain HopSync can use. */
public final class MemorySecrets implements SecretStore {
    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    @Override
    public boolean persistent() {
        return false;
    }

    @Override
    public Optional<String> read(String key) {
        return Optional.ofNullable(secrets.get(key));
    }

    @Override
    public void write(String key, String secret) {
        secrets.put(key, secret);
    }

    @Override
    public void delete(String key) {
        secrets.remove(key);
    }
}
