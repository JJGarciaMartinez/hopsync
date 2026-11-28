package hopsync.desktop.store;

import hopsync.core.event.SyncEvent;
import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ConfigRoot;
import hopsync.core.manifest.Manifest;
import hopsync.core.manifest.ModEntry;
import hopsync.core.manifest.ModSource;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackSource;
import hopsync.core.sync.SyncPlan;
import hopsync.core.sync.SyncPlan.ModAction;
import hopsync.core.sync.SyncPlan.ModChange;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Packs, plans and a reducer runner shared by the store and screen tests.
 */
public final class StoreFixtures {
    public static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    public static final PackInfo INFO = new PackInfo("Demo", "1.2.0", "", "play.example.org", "1.21.1", "neoforge");
    public static final Pack DEMO = new Pack("demo", "https://example.org/demo.json", Path.of("/games/demo").toAbsolutePath(), "Demo");
    public static final Pack OTHER = new Pack("other", "https://example.org/other.json", Path.of("/games/other").toAbsolutePath(), "Other");

    public static final Pack MINE = new Pack("mine", new PackSource.Local(null), Path.of("/games/mine").toAbsolutePath(),
        "Aventura con amigos");
    public static final PackInfo MINE_INFO = new PackInfo("Aventura con amigos", "1.0.0", "", null, "1.21.1", "fabric");

    private StoreFixtures() {
    }

    /** A pack the player created, with no mods yet, selected. */
    public static UiState created() {
        return Reducer.reduce(Reducer.reduce(UiState.INITIAL, new UiAction.PacksLoaded(List.of(DEMO, MINE), MINE.id())),
            new UiAction.ManifestLoaded(MINE.id(), new Manifest(2, MINE_INFO, "c".repeat(64), List.of(), List.of())));
    }

    public static ModEntry mod(String file) {
        return new ModEntry(file, file, "1.0", "", "", file, "https://x.org/" + file, "a".repeat(128), 100, ModSource.MODRINTH);
    }

    /** new.jar is new, upd.jar changes, kept.jar is up to date and gone.jar will be removed. */
    public static SyncPlan plan() {
        ModEntry fresh = mod("new.jar");
        ModEntry updated = mod("upd.jar");
        ModEntry kept = mod("kept.jar");
        Manifest manifest = new Manifest(2, INFO, "c".repeat(64), List.of(fresh, updated, kept), List.of());
        return new SyncPlan(manifest,
            List.of(new ModAction(fresh, ModChange.NEW), new ModAction(updated, ModChange.UPDATE),
                new ModAction(kept, ModChange.UP_TO_DATE)),
            List.of("gone.jar"), List.of(), Map.of(), Map.of());
    }

    /** Nothing to download, but the creator offers a folder of configs and the player's options.txt. */
    public static SyncPlan offeredPlan() {
        ModEntry kept = mod("kept.jar");
        ConfigEntry jei = new ConfigEntry("jei/a.json", "https://example.org/a", "d".repeat(128), 2048, false,
            ConfigRoot.CONFIG, true);
        ConfigEntry jeiB = new ConfigEntry("jei/b.json", "https://example.org/b", "e".repeat(128), 1024, false,
            ConfigRoot.CONFIG, true);
        ConfigEntry options = new ConfigEntry("options.txt", "https://example.org/o", "f".repeat(128), 4096, false,
            ConfigRoot.GAME, true);
        Manifest manifest = new Manifest(2, INFO, "c".repeat(64), List.of(kept), List.of(jei, jeiB, options));
        return new SyncPlan(manifest, List.of(new ModAction(kept, ModChange.UP_TO_DATE)), List.of(),
            List.of(new SyncPlan.ConfigAction(jei, SyncPlan.ConfigChange.OFFERED),
                new SyncPlan.ConfigAction(jeiB, SyncPlan.ConfigChange.OFFERED),
                new SyncPlan.ConfigAction(options, SyncPlan.ConfigChange.OFFERED)),
            Map.of(), Map.of());
    }

    public static SyncPlan upToDatePlan() {
        ModEntry kept = mod("kept.jar");
        Manifest manifest = new Manifest(2, INFO, "c".repeat(64), List.of(kept), List.of());
        return new SyncPlan(manifest, List.of(new ModAction(kept, ModChange.UP_TO_DATE)), List.of(), List.of(),
            Map.of(), Map.of());
    }

    /** The manifest of {@link #plan()}: new.jar, upd.jar and kept.jar. */
    public static Manifest manifest() {
        return plan().manifest();
    }

    /** Both packs loaded, DEMO selected. */
    public static UiState loaded() {
        return Reducer.reduce(UiState.INITIAL, new UiAction.PacksLoaded(List.of(DEMO, OTHER), DEMO.id()));
    }

    public static UiAction event(SyncEvent event) {
        return new UiAction.SyncEventReceived(event, NOW);
    }

    /** Applies actions and core events (wrapped as {@code SyncEventReceived} at {@link #NOW}) in order. */
    public static UiState reduce(UiState state, Object... actionsOrEvents) {
        for (Object item : actionsOrEvents) {
            UiAction action = switch (item) {
                case UiAction a -> a;
                case SyncEvent e -> event(e);
                default -> throw new IllegalArgumentException("Not an action or event: " + item);
            };
            state = Reducer.reduce(state, action);
        }
        return state;
    }
}