package hopsync.core.manifest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackInfoTest {

    @ParameterizedTest
    @ValueSource(strings = {"play.example.net", "mc.example.net:25565", "192.168.1.10", "localhost:1", "a",
        "my-server.example.org:65535"})
    void hostsAndPortsAreServerAddresses(String address) {
        assertTrue(PackInfo.isServerAddress(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "play example.net", "https://play.example.net", "play.example.net:",
        "play.example.net:0", "play.example.net:65536", ".example.net", "example.net.", "-example.net", "a:b"})
    void anythingElseIsNot(String address) {
        assertFalse(PackInfo.isServerAddress(address));
    }
}
