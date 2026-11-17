package hopsync.core.pack;

import java.util.List;
import java.util.Optional;

/**
 * Everything the player configured: the packs, the one shown first and the app-wide preferences.
 *
 * @param packs             in the order they were added
 * @param activeId          id of the selected pack, or {@code null} when there are no packs
 * @param checkOnStart      check every pack in the background when the app starts
 * @param chainQueue        keep applying queued packs after the running one is cancelled
 * @param parallelDownloads files downloaded at the same time, 1–8
 * @param reduceMotion      keep the animated mark still, for players sensitive to motion
 * @param libraryWidth      width of the pack library the player dragged to, in unscaled pixels, 200–360
 */
public record PackSettings(List<Pack> packs, String activeId, boolean checkOnStart, boolean chainQueue,
                           int parallelDownloads, boolean reduceMotion, int libraryWidth) {

    public static final int MIN_PARALLEL_DOWNLOADS = 1;
    public static final int MAX_PARALLEL_DOWNLOADS = 8;
    public static final int MIN_LIBRARY_WIDTH = 200;
    public static final int MAX_LIBRARY_WIDTH = 360;
    public static final int DEFAULT_LIBRARY_WIDTH = 232;
    public static final PackSettings EMPTY = new PackSettings(List.of(), null, true, true, 4, false);

    /** Settings with the library at its default width. */
    public PackSettings(List<Pack> packs, String activeId, boolean checkOnStart, boolean chainQueue,
                        int parallelDownloads, boolean reduceMotion) {
        this(packs, activeId, checkOnStart, chainQueue, parallelDownloads, reduceMotion, DEFAULT_LIBRARY_WIDTH);
    }

    public PackSettings {
        packs = List.copyOf(packs);
        String requested = activeId;
        if (requested == null || packs.stream().noneMatch(pack -> pack.id().equals(requested))) {
            activeId = packs.isEmpty() ? null : packs.getFirst().id();
        }
        parallelDownloads = Math.clamp(parallelDownloads, MIN_PARALLEL_DOWNLOADS, MAX_PARALLEL_DOWNLOADS);
        libraryWidth = Math.clamp(libraryWidth, MIN_LIBRARY_WIDTH, MAX_LIBRARY_WIDTH);
    }

    public Optional<Pack> active() {
        return find(activeId);
    }

    public Optional<Pack> find(String id) {
        return packs.stream().filter(pack -> pack.id().equals(id)).findFirst();
    }

    public PackSettings withPacks(List<Pack> newPacks, String newActiveId) {
        return new PackSettings(newPacks, newActiveId, checkOnStart, chainQueue, parallelDownloads, reduceMotion,
            libraryWidth);
    }
}
