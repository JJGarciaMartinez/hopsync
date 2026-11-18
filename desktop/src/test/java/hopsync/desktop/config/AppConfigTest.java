package hopsync.desktop.config;

import hopsync.platform.http.NetworkConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void readsNetworkAndPresetFromProperties() {
        Properties props = new Properties();
        props.setProperty("network.connect.timeout", "5000");
        props.setProperty("network.read.timeout", "7000");
        props.setProperty("network.user.agent", "HopSync-Test");
        props.setProperty("manifest.url", "https://example.org/manifest.json");
        props.setProperty("github.client.id", " Iv1.test ");
        props.setProperty("update.repo", "owner/hopsync");

        AppConfig config = AppConfig.fromProperties(props, "b26.2.0");
        assertEquals("Iv1.test", config.githubClientId());
        assertEquals("owner/hopsync", config.updateRepo());

        assertEquals(new NetworkConfig(5000, 7000, "HopSync-Test"), config.network());
        assertEquals("https://example.org/manifest.json", config.presetManifestUrl());
    }

    @Test
    void invalidOrMissingValuesFallBackToDefaults() {
        Properties props = new Properties();
        props.setProperty("network.connect.timeout", "soon");

        AppConfig config = AppConfig.fromProperties(props, "b26.2.0");

        assertEquals(NetworkConfig.DEFAULT, config.network());
        assertEquals(AppConfig.DEFAULT_MANIFEST_URL, config.presetManifestUrl());
        assertEquals(AppConfig.DEFAULT_GITHUB_CLIENT_ID, config.githubClientId());
        assertEquals(AppConfig.DEFAULT_UPDATE_REPO, config.updateRepo());
    }

    @Test
    void userAgentCarriesTheAppVersion() {
        Properties props = new Properties();
        props.setProperty("network.user.agent", "owner/hopsync/{version} (https://example.org)");

        AppConfig config = AppConfig.fromProperties(props, "26.2.0");

        assertEquals("owner/hopsync/26.2.0 (https://example.org)", config.network().userAgent());
    }

    @Test
    void packagedPropertiesLoad() {
        AppConfig config = AppConfig.load();
        assertTrue(config.presetManifestUrl().startsWith("https://"));
        assertTrue(config.network().connectTimeoutMillis() > 0);
        assertEquals("JJGarciaMartinez/hopsync/" + AppVersion.VERSION + " (https://github.com/JJGarciaMartinez/hopsync)",
            config.network().userAgent());
    }
}
