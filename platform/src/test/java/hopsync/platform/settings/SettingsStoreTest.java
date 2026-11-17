package hopsync.platform.settings;

import hopsync.core.pack.Pack;
import hopsync.core.pack.PackSettings;
import hopsync.core.pack.PackSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStoreTest {

    @TempDir
    Path dir;

    @Test
    void roundTrips() throws IOException {
        SettingsStore store = new SettingsStore(dir.resolve("nested/settings.json"));
        Pack first = new Pack("a1", "https://example.org/manifest.json", dir.resolve("My Instance [1.21]"), "Demo");
        Pack second = new Pack("b2", "https://example.org/other.json", dir.resolve("other"), "");
        Pack created = new Pack("c3", new PackSource.Local(null), dir.resolve("mine"), "Mi pack");
        Pack published = new Pack("d4", new PackSource.Local(new PackSource.Publication("player/pack",
            "https://github.com/player/pack/releases/download/hopsync/manifest.json")), dir.resolve("pub"), "Pub");
        PackSettings settings = new PackSettings(List.of(first, second, created, published), "b2", false, false, 6, true);

        store.save(settings);

        assertEquals(settings, store.load());
        assertFalse(Files.exists(dir.resolve("nested/settings.json.tmp")));
    }

    @Test
    void missingOrCorruptFileMeansNoSettings() throws IOException {
        Path file = dir.resolve("settings.json");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());

        Files.writeString(file, "not json");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());

        Files.writeString(file, "{\"format\": 1, \"manifest_url\": 3, \"game_dir\": \"/x\"}");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());

        Files.writeString(file, "{\"format\": 1, \"manifest_url\": \"\", \"game_dir\": \"/x\"}");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());
    }

    @Test
    void unknownFormatMeansNoSettings() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"format\": 4, \"packs\": []}");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());
    }

    @Test
    void incompletePacksAreSkippedAndPreferencesClamped() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"format\": 2, \"active\": \"gone\", \"parallel_downloads\": 40, \"packs\": ["
            + "{\"id\": \"a\", \"manifest_url\": \"https://x/m.json\", \"game_dir\": \"" + jsonPath(dir.resolve("a")) + "\"},"
            + "{\"id\": \"b\", \"manifest_url\": \"https://x/m.json\"}]}");

        PackSettings settings = new SettingsStore(file).load();

        assertEquals(1, settings.packs().size());
        assertEquals("a", settings.activeId());
        assertEquals("", settings.packs().getFirst().name());
        assertEquals(PackSettings.MAX_PARALLEL_DOWNLOADS, settings.parallelDownloads());
        assertTrue(settings.checkOnStart());
        assertFalse(settings.reduceMotion(), "files saved before the preference existed keep the animation");
    }

    @Test
    void singlePackFormatIsMigratedWithABackup() throws IOException {
        Path file = dir.resolve("settings.json");
        Path instance = dir.resolve("instance");
        String v1 = "{\"format\": 1, \"manifest_url\": \"https://example.org/m.json\", \"game_dir\": \"" + jsonPath(instance) + "\"}";
        Files.writeString(file, v1);
        SettingsStore store = new SettingsStore(file);

        PackSettings migrated = store.load();

        assertEquals(1, migrated.packs().size());
        Pack pack = migrated.packs().getFirst();
        assertEquals(Optional.of("https://example.org/m.json"), pack.link());
        assertEquals(instance.toAbsolutePath(), pack.gameDir());
        assertEquals(pack.id(), migrated.activeId());
        assertEquals(v1, Files.readString(store.backupFile(1)));
        // Saved in the current format right away, so the id stays the same on the next start
        assertEquals(migrated, new SettingsStore(file).load());
    }

    @Test
    void backupIsNeverOverwritten() throws IOException {
        Path file = dir.resolve("settings.json");
        SettingsStore store = new SettingsStore(file);
        Files.writeString(store.backupFile(1), "original");
        Files.writeString(file, "{\"format\": 1, \"manifest_url\": \"https://example.org/m.json\", \"game_dir\": \"/x\"}");

        store.load();

        assertEquals("original", Files.readString(store.backupFile(1)));
    }

    @Test
    void linkPacksFormatIsMigratedWithABackup() throws IOException {
        Path file = dir.resolve("settings.json");
        String v2 = "{\"format\": 2, \"active\": \"a\", \"reduce_motion\": true, \"packs\": [{\"id\": \"a\", "
            + "\"manifest_url\": \"https://x/m.json\", \"game_dir\": \"" + jsonPath(dir.resolve("a")) + "\", \"name\": \"Demo\"}]}";
        Files.writeString(file, v2);
        SettingsStore store = new SettingsStore(file);

        PackSettings migrated = store.load();

        Pack pack = migrated.packs().getFirst();
        assertEquals(new PackSource.Link("https://x/m.json"), pack.source());
        assertEquals("Demo", pack.name());
        assertTrue(migrated.reduceMotion());
        assertEquals(v2, Files.readString(store.backupFile(2)));
        assertTrue(Files.readString(file).contains("\"format\":3"));
        assertEquals(migrated, new SettingsStore(file).load());
    }

    private static String jsonPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\");
    }

    @Test
    void windowsUsesAppData() {
        assertEquals(Path.of("C:/Users/p/AppData/Roaming/HopSync/settings.json"),
            SettingsStore.defaultLocation("Windows 11", Map.of("APPDATA", "C:/Users/p/AppData/Roaming"), "C:/Users/p"));
        assertEquals(Path.of("/home/p/AppData/Roaming/HopSync/settings.json"),
            SettingsStore.defaultLocation("Windows 10", Map.of(), "/home/p"));
    }

    @Test
    void macUsesApplicationSupport() {
        assertEquals(Path.of("/Users/p/Library/Application Support/HopSync/settings.json"),
            SettingsStore.defaultLocation("Mac OS X", Map.of(), "/Users/p"));
    }

    @Test
    void linuxFollowsXdg() {
        assertEquals(Path.of("/home/p/.config/hopsync/settings.json"),
            SettingsStore.defaultLocation("Linux", Map.of(), "/home/p"));
        // XDG_CONFIG_HOME must be absolute on the machine running the test: on Windows "/xdg" is not
        Path xdg = Path.of("/xdg").toAbsolutePath();
        assertEquals(xdg.resolve("hopsync").resolve("settings.json"),
            SettingsStore.defaultLocation("Linux", Map.of("XDG_CONFIG_HOME", xdg.toString()), "/home/p"));
        assertEquals(Path.of("/home/p/.config/hopsync/settings.json"),
            SettingsStore.defaultLocation("Linux", Map.of("XDG_CONFIG_HOME", "relative/dir"), "/home/p"));
    }
}
