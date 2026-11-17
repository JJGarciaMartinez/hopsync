package hopsync.platform.secrets;

import hopsync.core.port.SecretStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemKeychainTest {

    @Test
    void memorySecretsKeepWhatIsWrittenForTheSessionOnly() throws IOException {
        SecretStore secrets = new MemorySecrets();
        assertFalse(secrets.persistent());
        secrets.write("github", "token");
        assertEquals(Optional.of("token"), secrets.read("github"));
        secrets.delete("github");
        secrets.delete("github");
        assertEquals(Optional.empty(), secrets.read("github"));
    }

    @Test
    void aSystemWithoutAUsableKeychainFallsBackToMemory() {
        // Another system's keychain library does not exist on this one, so loading it fails
        String other = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "Mac OS X" : "Windows 11";
        assertInstanceOf(MemorySecrets.class, SystemKeychain.choose(other));
    }

    /** Touches the real keychain of the machine running it, so it only runs when asked: HOPSYNC_KEYCHAIN_TEST=1. */
    @Test
    @EnabledIfEnvironmentVariable(named = "HOPSYNC_KEYCHAIN_TEST", matches = "1")
    void theSystemKeychainKeepsAndForgetsASecret() throws IOException {
        SecretStore keychain = SystemKeychain.forCurrentOs();
        assertTrue(keychain.persistent(), "this machine's keychain should be usable");
        keychain.write("hopsync-test", "first");
        keychain.write("hopsync-test", "second ñ");
        assertEquals(Optional.of("second ñ"), keychain.read("hopsync-test"));
        keychain.delete("hopsync-test");
        assertEquals(Optional.empty(), keychain.read("hopsync-test"));
    }
}
