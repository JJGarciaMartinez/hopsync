package hopsync.platform.secrets;

import hopsync.core.port.SecretStore;

import java.util.Locale;

/**
 * The operating system's keychain as HopSync's {@link SecretStore}: Keychain on macOS, Credential Manager on
 * Windows, the Secret Service (GNOME Keyring, KWallet) on Linux, reached through their native APIs. When the
 * keychain cannot be used, secrets are kept in memory for the session only.
 */
public final class SystemKeychain {
    /** The service every secret is filed under, so the player can find them in their keychain. */
    static final String SERVICE = "HopSync";
    private static final System.Logger LOG = System.getLogger(SystemKeychain.class.getName());

    private SystemKeychain() {
    }

    public static SecretStore forCurrentOs() {
        return choose(System.getProperty("os.name"));
    }

    static SecretStore choose(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        try {
            SecretStore store = os.contains("mac") ? new MacKeychain()
                : os.contains("win") ? new WindowsCredentials()
                : new LinuxSecretService();
            // A lookup of a key that does not exist shows whether the keychain answers at all
            store.read("probe");
            return store;
        } catch (Exception | LinkageError e) {
            LOG.log(System.Logger.Level.WARNING, "No usable system keychain; secrets last for this session only", e);
            return new MemorySecrets();
        }
    }
}
