package hopsync.core.manifest;

/** The folder a {@link ConfigEntry} path is relative to. */
public enum ConfigRoot {
    /** The Minecraft {@code config/} folder. */
    CONFIG("config"),
    /** The game folder itself; only a short whitelist of files may live there. */
    GAME("game");

    private final String json;

    ConfigRoot(String json) {
        this.json = json;
    }

    /** The name used in {@code manifest.json}. */
    public String json() {
        return json;
    }

    public static ConfigRoot fromJson(String name) {
        for (ConfigRoot root : values()) {
            if (root.json.equals(name)) {
                return root;
            }
        }
        throw new IllegalArgumentException("unknown config root: " + name);
    }
}
