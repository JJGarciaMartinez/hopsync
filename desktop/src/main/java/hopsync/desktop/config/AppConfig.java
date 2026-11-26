package hopsync.desktop.config;

import hopsync.platform.http.NetworkConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Settings read once from app.properties at startup and passed to the services that need them.
 *
 * @param presetManifestUrl manifest offered to players who have not chosen a pack yet
 * @param updateRepo        {@code owner/name} of HopSync's repository, whose releases tell of a newer version
 */
public record AppConfig(NetworkConfig network, String presetManifestUrl, String githubClientId, String updateRepo) {
    private static final System.Logger LOG = System.getLogger(AppConfig.class.getName());

    /** HopSync's GitHub OAuth app; a client id is not a secret (device flow, no client secret). */
    static final String DEFAULT_GITHUB_CLIENT_ID = "Ov23lihI0NGFxSrR2Y5K";
    static final String DEFAULT_MANIFEST_URL =
        "https://github.com/JJGarciaMartinez/hopsync-demo/releases/download/hopsync/manifest.json";
    static final String DEFAULT_UPDATE_REPO = "JJGarciaMartinez/hopsync";

    /** Replaced in network.user.agent by the app version. */
    static final String VERSION_PLACEHOLDER = "{version}";

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (input != null) props.load(input);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not load app.properties, using defaults", e);
        }
        return fromProperties(props, AppVersion.VERSION);
    }

    static AppConfig fromProperties(Properties props, String version) {
        NetworkConfig defaults = NetworkConfig.DEFAULT;
        NetworkConfig network = new NetworkConfig(
            intOr(props, "network.connect.timeout", defaults.connectTimeoutMillis()),
            intOr(props, "network.read.timeout", defaults.readTimeoutMillis()),
            props.getProperty("network.user.agent", defaults.userAgent()).strip().replace(VERSION_PLACEHOLDER, version));
        return new AppConfig(network, props.getProperty("manifest.url", DEFAULT_MANIFEST_URL),
            props.getProperty("github.client.id", DEFAULT_GITHUB_CLIENT_ID).strip(),
            props.getProperty("update.repo", DEFAULT_UPDATE_REPO).strip());
    }

    private static int intOr(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
