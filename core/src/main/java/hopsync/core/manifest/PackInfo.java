package hopsync.core.manifest;

import java.util.regex.Pattern;

/**
 * Modpack metadata from the manifest's {@code pack} section.
 *
 * @param serverIp the server address players connect to, or {@code null} when the pack has none
 */
public record PackInfo(String name, String version, String description, String serverIp,
                       String minecraft, String loader) {

    /** A host name or IPv4 address, optionally followed by {@code :port}, as Minecraft's server list takes it. */
    private static final Pattern SERVER_ADDRESS =
        Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?(?::\\d{1,5})?");

    /** Whether players could type this into Minecraft to join: a host, optionally with a port from 1 to 65535. */
    public static boolean isServerAddress(String address) {
        if (address == null || !SERVER_ADDRESS.matcher(address).matches()) return false;
        int colon = address.indexOf(':');
        if (colon < 0) return true;
        int port = Integer.parseInt(address.substring(colon + 1));
        return port >= 1 && port <= 65535;
    }

    /** @param address the new server address, or {@code null} for none */
    public PackInfo withServerIp(String address) {
        return new PackInfo(name, version, description, address, minecraft, loader);
    }
}
