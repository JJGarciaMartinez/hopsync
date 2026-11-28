package hopsync.core.pack;

/** How a config unit of a pack is installed on the player's side. */
public enum ConfigMode {
    /** Installed only if the player does not have it yet. The default, so it is never stored as a rule. */
    IF_MISSING,
    /** Replaces the player's copy on every update. */
    ALWAYS,
    /** Only installed when the player ticks it; offered again whenever its content changes. */
    OPTIONAL
}
