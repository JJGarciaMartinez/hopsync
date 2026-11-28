package hopsync.core.manifest;

/**
 * A configuration file listed in the manifest.
 *
 * @param path      path relative to the folder {@code root} names (validated: no traversal)
 * @param overwrite {@code true} to always replace the player's copy, {@code false} to install it only if missing
 * @param root      the folder {@code path} is relative to
 * @param optional  {@code true} when the player chooses whether to receive it; never combined with {@code overwrite}
 */
public record ConfigEntry(String path, String url, String sha512, long size, boolean overwrite, ConfigRoot root,
                          boolean optional) {

    public ConfigEntry {
        if (overwrite && optional) {
            throw new IllegalArgumentException("an optional config cannot overwrite: " + path);
        }
    }

    /** What the sync state and the plan call this file: its path, prefixed for the game folder. */
    public String stateKey() {
        return root == ConfigRoot.GAME ? "game:" + path : path;
    }

    /** A regular config under {@code config/}. */
    public ConfigEntry(String path, String url, String sha512, long size, boolean overwrite) {
        this(path, url, sha512, size, overwrite, ConfigRoot.CONFIG, false);
    }
}
