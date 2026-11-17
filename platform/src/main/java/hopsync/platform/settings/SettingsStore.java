package hopsync.platform.settings;

import hopsync.core.pack.Pack;
import hopsync.core.pack.PackSettings;
import hopsync.core.pack.PackSource;
import hopsync.core.port.PackSettingsRepository;
import hopsync.core.util.AtomicFiles;
import hopsync.core.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists the player's packs and preferences as JSON in the operating system's per-user configuration
 * folder. A file in any other format loads as no settings.
 */
public class SettingsStore implements PackSettingsRepository {
    private static final System.Logger LOG = System.getLogger(SettingsStore.class.getName());
    private static final long FORMAT = 3;

    private final Path file;

    public SettingsStore(Path file) {
        this.file = file;
    }

    /**
     * Where settings live for the current user.
     */
    public static SettingsStore forCurrentUser() {
        return new SettingsStore(defaultLocation(System.getProperty("os.name"), System.getenv(),
            System.getProperty("user.home")));
    }

    /**
     * Resolves the settings file following each platform's convention:
     * {@code %APPDATA%\HopSync} on Windows, {@code ~/Library/Application Support/HopSync} on macOS and
     * {@code $XDG_CONFIG_HOME/hopsync} (default {@code ~/.config/hopsync}) elsewhere.
     */
    static Path defaultLocation(String osName, Map<String, String> env, String userHome) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        Path home = Path.of(userHome);
        Path dir;
        if (os.contains("win")) {
            String appData = env.get("APPDATA");
            dir = (isUsable(appData) ? Path.of(appData) : home.resolve("AppData").resolve("Roaming")).resolve("HopSync");
        } else if (os.contains("mac")) {
            dir = home.resolve("Library").resolve("Application Support").resolve("HopSync");
        } else {
            String xdg = env.get("XDG_CONFIG_HOME");
            // The XDG spec says relative values must be ignored
            dir = (isUsable(xdg) && Path.of(xdg).isAbsolute() ? Path.of(xdg) : home.resolve(".config")).resolve("hopsync");
        }
        return dir.resolve("settings.json");
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank();
    }

    public Path getFile() {
        return file;
    }

    @Override
    public PackSettings load() {
        try {
            Map<?, ?> root = (Map<?, ?>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            Object format = root.get("format");
            if (Long.valueOf(FORMAT).equals(format)) {
                return readPacks(root);
            }
            LOG.log(System.Logger.Level.WARNING, "Ignoring settings in unknown format " + format + ": " + file);
            return PackSettings.EMPTY;
        } catch (NoSuchFileException e) {
            return PackSettings.EMPTY;
        } catch (IOException | ClassCastException | InvalidPathException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable settings " + file, e);
            return PackSettings.EMPTY;
        }
    }

    @Override
    public void save(PackSettings settings) throws IOException {
        List<Object> packs = new ArrayList<>();
        for (Pack pack : settings.packs()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", pack.id());
            entry.put("source", writeSource(pack.source()));
            entry.put("game_dir", pack.gameDir().toString());
            entry.put("name", pack.name());
            packs.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("active", settings.activeId());
        root.put("packs", packs);
        root.put("check_on_start", settings.checkOnStart());
        root.put("chain_queue", settings.chainQueue());
        root.put("parallel_downloads", settings.parallelDownloads());
        root.put("reduce_motion", settings.reduceMotion());
        root.put("library_width", settings.libraryWidth());

        AtomicFiles.writeString(file, Json.write(root));
    }

    private PackSettings readPacks(Map<?, ?> root) {
        List<Pack> packs = new ArrayList<>();
        if (root.get("packs") instanceof List<?> entries) {
            for (Object entry : entries) {
                PackSource source = entry instanceof Map<?, ?> map ? readSource(map) : null;
                if (source != null && entry instanceof Map<?, ?> map && map.get("id") instanceof String id
                        && !id.isBlank() && map.get("game_dir") instanceof String dir && !dir.isBlank()) {
                    packs.add(new Pack(id, source, Path.of(dir), map.get("name") instanceof String name ? name : ""));
                } else {
                    LOG.log(System.Logger.Level.WARNING, "Skipping incomplete pack in " + file + ": " + entry);
                }
            }
        }
        PackSettings defaults = PackSettings.EMPTY;
        return new PackSettings(packs,
            root.get("active") instanceof String active ? active : null,
            root.get("check_on_start") instanceof Boolean check ? check : defaults.checkOnStart(),
            root.get("chain_queue") instanceof Boolean chain ? chain : defaults.chainQueue(),
            root.get("parallel_downloads") instanceof Long parallel ? parallel.intValue() : defaults.parallelDownloads(),
            root.get("reduce_motion") instanceof Boolean reduce ? reduce : defaults.reduceMotion(),
            root.get("library_width") instanceof Long width ? width.intValue() : defaults.libraryWidth());
    }

    private static Map<String, Object> writeSource(PackSource source) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (source) {
            case PackSource.Link link -> {
                out.put("kind", "link");
                out.put("url", link.url());
            }
            case PackSource.Local local -> {
                out.put("kind", "local");
                if (local.publication() != null) {
                    out.put("repo", local.publication().repo());
                    out.put("url", local.publication().url());
                }
            }
        }
        return out;
    }

    /** The source of a pack entry, or {@code null} if unusable. */
    private static PackSource readSource(Map<?, ?> entry) {
        if (!(entry.get("source") instanceof Map<?, ?> source)) {
            return null;
        }
        return switch (source.get("kind") instanceof String kind ? kind : "") {
            case "link" -> source.get("url") instanceof String url && !url.isBlank() ? new PackSource.Link(url) : null;
            case "local" -> new PackSource.Local(
                source.get("repo") instanceof String repo && !repo.isBlank()
                    && source.get("url") instanceof String url && !url.isBlank()
                    ? new PackSource.Publication(repo, url) : null);
            default -> null;
        };
    }
}
