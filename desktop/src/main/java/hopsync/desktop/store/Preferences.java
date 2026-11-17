package hopsync.desktop.store;

/**
 * App-wide preferences, as the settings sheet edits them.
 *
 * @param parallelDownloads files downloaded at the same time, 1–8
 * @param reduceMotion      keep the animated mark still
 * @param libraryWidth      width of the pack library in unscaled pixels, as the player last dragged it
 */
public record Preferences(boolean checkOnStart, boolean chainQueue, int parallelDownloads, boolean reduceMotion,
                          int libraryWidth) {
    public static final int MIN_PARALLEL = 1;
    public static final int MAX_PARALLEL = 8;
    public static final int MIN_LIBRARY_WIDTH = 200;
    public static final int MAX_LIBRARY_WIDTH = 360;
    public static final int DEFAULT_LIBRARY_WIDTH = 232;
    public static final Preferences DEFAULT = new Preferences(true, true, 4, false);

    public Preferences {
        parallelDownloads = Math.clamp(parallelDownloads, MIN_PARALLEL, MAX_PARALLEL);
        libraryWidth = Math.clamp(libraryWidth, MIN_LIBRARY_WIDTH, MAX_LIBRARY_WIDTH);
    }

    /** Preferences with the library at its default width, as the settings sheet edits them. */
    public Preferences(boolean checkOnStart, boolean chainQueue, int parallelDownloads, boolean reduceMotion) {
        this(checkOnStart, chainQueue, parallelDownloads, reduceMotion, DEFAULT_LIBRARY_WIDTH);
    }

    Preferences withLibraryWidth(int width) {
        return new Preferences(checkOnStart, chainQueue, parallelDownloads, reduceMotion, width);
    }
}
