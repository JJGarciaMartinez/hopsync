package hopsync.core.pack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * @param language          language tag of the app's texts the player picked, or blank to follow the system
 * @param checkAppUpdates   ask HopSync's repository for a newer release when the app starts
 * @param choices           per pack id, the player's decision on each optional config unit, by
 *                          {@link hopsync.core.sync.ConfigUnits.Unit#key()}
 */
public record PackSettings(List<Pack> packs, String activeId, boolean checkOnStart, boolean chainQueue,
                           int parallelDownloads, boolean reduceMotion, int libraryWidth, String language,
                           boolean checkAppUpdates, Map<String, Map<String, OptionalChoice>> choices) {

    public static final int MIN_PARALLEL_DOWNLOADS = 1;
    public static final int MAX_PARALLEL_DOWNLOADS = 8;
    public static final int MIN_LIBRARY_WIDTH = 200;
    public static final int MAX_LIBRARY_WIDTH = 360;
    public static final int DEFAULT_LIBRARY_WIDTH = 232;
    public static final PackSettings EMPTY = new PackSettings(List.of(), null, true, true, 4, false);

    /**
     * Settings with the library at its default width, the texts in the system's language and the check for newer
     * releases on.
     */
    public PackSettings(List<Pack> packs, String activeId, boolean checkOnStart, boolean chainQueue,
                        int parallelDownloads, boolean reduceMotion) {
        this(packs, activeId, checkOnStart, chainQueue, parallelDownloads, reduceMotion, DEFAULT_LIBRARY_WIDTH, "", true);
    }

    public PackSettings(List<Pack> packs, String activeId, boolean checkOnStart, boolean chainQueue,
                        int parallelDownloads, boolean reduceMotion, int libraryWidth, String language,
                        boolean checkAppUpdates) {
        this(packs, activeId, checkOnStart, chainQueue, parallelDownloads, reduceMotion, libraryWidth, language,
            checkAppUpdates, Map.of());
    }

    public PackSettings {
        packs = List.copyOf(packs);
        String requested = activeId;
        if (requested == null || packs.stream().noneMatch(pack -> pack.id().equals(requested))) {
            activeId = packs.isEmpty() ? null : packs.getFirst().id();
        }
        parallelDownloads = Math.clamp(parallelDownloads, MIN_PARALLEL_DOWNLOADS, MAX_PARALLEL_DOWNLOADS);
        libraryWidth = Math.clamp(libraryWidth, MIN_LIBRARY_WIDTH, MAX_LIBRARY_WIDTH);
        language = language == null ? "" : language.strip();
        Map<String, Map<String, OptionalChoice>> kept = new HashMap<>();
        if (choices != null) {
            choices.forEach((packId, byUnit) -> kept.put(packId, Map.copyOf(byUnit)));
        }
        choices = Map.copyOf(kept);
    }

    /** The player's decisions on the optional config units of one pack. */
    public Map<String, OptionalChoice> choicesOf(String packId) {
        return choices.getOrDefault(packId, Map.of());
    }

    public PackSettings withChoices(String packId, Map<String, OptionalChoice> packChoices) {
        Map<String, Map<String, OptionalChoice>> next = new HashMap<>(choices);
        if (packChoices.isEmpty()) {
            next.remove(packId);
        } else {
            next.put(packId, packChoices);
        }
        return new PackSettings(packs, activeId, checkOnStart, chainQueue, parallelDownloads, reduceMotion,
            libraryWidth, language, checkAppUpdates, next);
    }

    public Optional<Pack> active() {
        return find(activeId);
    }

    public Optional<Pack> find(String id) {
        return packs.stream().filter(pack -> pack.id().equals(id)).findFirst();
    }

    public PackSettings withPacks(List<Pack> newPacks, String newActiveId) {
        Map<String, Map<String, OptionalChoice>> kept = new HashMap<>(choices);
        kept.keySet().removeIf(id -> newPacks.stream().noneMatch(pack -> pack.id().equals(id)));
        return new PackSettings(newPacks, newActiveId, checkOnStart, chainQueue, parallelDownloads, reduceMotion,
            libraryWidth, language, checkAppUpdates, kept);
    }
}
