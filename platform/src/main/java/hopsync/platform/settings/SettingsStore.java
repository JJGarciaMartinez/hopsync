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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persists the player's packs and preferences as JSON in the operating system's per-user configuration
 * folder. Older formats (1: a single pack; 2: packs by manifest link only) are migrated to format 3 on load
 * and saved right away, keeping a copy of the old file.
 */
public class SettingsStore implements PackSettingsRepository {
    private static final System.Logger LOG = System.getLogger(SettingsStore.class.getName());
    private static final long FORMAT = 3;
    private static final long FORMAT_LINKS_ONLY = 2;
    private static final long FORMAT_SINGLE_PACK = 1;

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

    /** Copy of a file in an older format, written before it is first saved in the current one. */
    Path backupFile(long format) {
        return file.resolveSibling(file.getFileName() + ".v" + format + ".bak");
    }

    @Override
    public PackSettings load() {
        try {
            Map<?, ?> root = (Map<?, ?>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            Object format = root.get("format");
            if (Long.valueOf(FORMAT).equals(format)) {
                return readPacks(root);
            }
            if (Long.valueOf(FORMAT_LINKS_ONLY).equals(format)) {
                return migrate(readPacks(root), FORMAT_LINKS_ONLY);
            }
            if (Long.valueOf(FORMAT_SINGLE_PACK).equals(format)) {
                return migrateSinglePack(root);
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
        backUpOlderFormat();
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
            root.get("reduce_motion") instanceof Boolean reduce ? reduce : defaults.reduceMotion());
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

    /** The source of a pack entry; format 2 entries only have {@code manifest_url}. {@code null} if unusable. */
    private static PackSource readSource(Map<?, ?> entry) {
        if (entry.get("manifest_url") instanceof String url && !url.isBlank()) {
            return new PackSource.Link(url);
        }
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

    /** Saves settings read from an older format right away, so the next start reads the current one. */
    private PackSettings migrate(PackSettings settings, long from) {
        try {
            save(settings);
            LOG.log(System.Logger.Level.INFO, "Migrated settings from format " + from + " to " + FORMAT
                + "; the old file is in " + backupFile(from));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the migrated settings " + file, e);
        }
        return settings;
    }

    /**
     * Turns the single pack of format 1 into the first pack of the current format and saves it right away, so the
     * pack keeps the same id from now on.
     */
    private PackSettings migrateSinglePack(Map<?, ?> root) {
        String url = (String) root.get("manifest_url");
        String gameDir = (String) root.get("game_dir");
        if (url == null || url.isBlank() || gameDir == null || gameDir.isBlank()) {
            return PackSettings.EMPTY;
        }
        Pack pack = new Pack(UUID.randomUUID().toString(), url, Path.of(gameDir), "");
        return migrate(PackSettings.EMPTY.withPacks(List.of(pack), pack.id()), FORMAT_SINGLE_PACK);
    }

    /** Keeps a copy of a file in an older format before it is overwritten; an existing copy is never replaced. */
    private void backUpOlderFormat() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        long format;
        try {
            Map<?, ?> root = (Map<?, ?>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!(root.get("format") instanceof Long found) || found >= FORMAT) {
                return;
            }
            format = found;
        } catch (IOException | ClassCastException e) {
            return;
        }
        if (!Files.exists(backupFile(format))) {
            Files.copy(file, backupFile(format), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }
}
