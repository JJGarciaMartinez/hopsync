package hopsync.desktop.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * The app version shown to players, read from the version.properties packaged in the JAR.
 */
public final class AppVersion {
    private static final System.Logger LOG = System.getLogger(AppVersion.class.getName());

    /** The version as written in version.properties, e.g. 26.2.0-b.1. */
    public static final String VERSION = loadVersion();
    public static final String VERSION_APP = displayVersion(VERSION);

    private AppVersion() {
    }

    static boolean isTestBuild(String version) {
        return ReleaseVersion.parse(version).map(ReleaseVersion::isTestBuild).orElse(false);
    }

    static String displayVersion(String version) {
        return isTestBuild(version) ? version + " (test build)" : version;
    }

    private static String loadVersion() {
        try (InputStream input = AppVersion.class.getClassLoader().getResourceAsStream("version.properties")) {
            if (input == null) {
                LOG.log(System.Logger.Level.WARNING, "version.properties not found, using default version");
                return "unknown";
            }
            Properties props = new Properties();
            props.load(input);
            return props.getProperty("app.version", "unknown");
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Could not load version.properties", e);
            return "unknown";
        }
    }
}
