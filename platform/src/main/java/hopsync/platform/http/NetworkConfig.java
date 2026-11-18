package hopsync.platform.http;

/**
 * Settings for every HTTP request the app makes.
 *
 * @param userAgent names the app and how to reach its authors, as the Modrinth API asks of its clients
 */
public record NetworkConfig(int connectTimeoutMillis, int readTimeoutMillis, String userAgent) {
    public static final NetworkConfig DEFAULT =
        new NetworkConfig(10_000, 30_000, "JJGarciaMartinez/hopsync (https://github.com/JJGarciaMartinez/hopsync)");
}
