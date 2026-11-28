package hopsync.platform.settings;

import hopsync.core.pack.OptionalChoice;
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
            "https://github.com/player/pack/releases/download/hopsync/manifest.json", "f".repeat(64))), dir.resolve("pub"), "Pub");
        PackSettings settings = new PackSettings(List.of(first, second, created, published), "b2", false, false, 6, true, 300,
            "en", false, Map.of("c3", Map.of("game:options.txt", new OptionalChoice("r1", true, false),
                "config:jei/", new OptionalChoice("r2", false, false))));

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

        Files.writeString(file, "[]");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());
    }

    @Test
    void otherFormatsMeanNoSettings() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"format\": 4, \"packs\": []}");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());

        Files.writeString(file, "{\"format\": 2, \"packs\": [{\"id\": \"a\", \"manifest_url\": \"https://x/m.json\", "
            + "\"game_dir\": \"" + jsonPath(dir.resolve("a")) + "\"}]}");
        assertEquals(PackSettings.EMPTY, new SettingsStore(file).load());
    }

    @Test
    void incompletePacksAreSkippedAndPreferencesClamped() throws IOException {
        Path file = dir.resolve("settings.json");
        String link = "\"source\": {\"kind\": \"link\", \"url\": \"https://x/m.json\"}";
        Files.writeString(file, "{\"format\": 3, \"active\": \"gone\", \"parallel_downloads\": 40, \"packs\": ["
            + "{\"id\": \"a\", " + link + ", \"game_dir\": \"" + jsonPath(dir.resolve("a")) + "\"},"
            + "{\"id\": \"b\", " + link + "},"
            + "{\"id\": \"c\", \"source\": {\"kind\": \"other\"}, \"game_dir\": \"" + jsonPath(dir.resolve("c")) + "\"}]}");

        PackSettings settings = new SettingsStore(file).load();

        assertEquals(1, settings.packs().size());
        assertEquals("a", settings.activeId());
        assertEquals("", settings.packs().getFirst().name());
        assertEquals(PackSettings.MAX_PARALLEL_DOWNLOADS, settings.parallelDownloads());
        assertEquals(PackSettings.DEFAULT_LIBRARY_WIDTH, settings.libraryWidth());
        assertTrue(settings.checkOnStart());
        assertFalse(settings.reduceMotion(), "files saved before the preference existed keep the animation");
        assertTrue(settings.checkAppUpdates(), "files saved before the preference existed keep checking");
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
