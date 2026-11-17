package hopsync.platform.settings;

import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.PackDefinition;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.util.AtomicFiles;
import hopsync.core.util.Json;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the packs the player created next to the settings, one folder per pack id:
 * {@code packs/<id>/pack.json} with the definition, {@code local-mods/} with the player's own jars and
 * {@code configs/} with the configs the pack installs.
 */
public class DefinitionStore implements PackDefinitionRepository {
    private static final System.Logger LOG = System.getLogger(DefinitionStore.class.getName());
    private static final long FORMAT = 1;
    private static final String FILE = "pack.json";

    private final Path root;

    /** @param root the folder holding one folder per created pack, {@code <settings dir>/packs} */
    public DefinitionStore(Path root) {
        this.root = root;
    }

    @Override
    public Path folder(String packId) {
        if (!SafePaths.isSafeFileName(packId)) {
            throw new IllegalArgumentException("Unsafe pack id " + packId);
        }
        return root.resolve(packId);
    }

    @Override
    public Optional<PackDefinition> load(String packId) {
        Path file = folder(packId).resolve(FILE);
        try {
            Map<?, ?> json = (Map<?, ?>) Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!Long.valueOf(FORMAT).equals(json.get("format"))) {
                LOG.log(System.Logger.Level.WARNING, "Ignoring pack definition in unknown format: " + file);
                return Optional.empty();
            }
            return Optional.of(read(json));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | ClassCastException | IllegalArgumentException | NullPointerException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable pack definition " + file, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(String packId, PackDefinition definition) throws IOException {
        Path folder = folder(packId);
        Files.createDirectories(folder);
        AtomicFiles.writeString(folder.resolve(FILE), Json.write(write(definition)));
    }

    @Override
    public void delete(String packId) throws IOException {
        Path folder = folder(packId);
        if (!Files.exists(folder)) {
            return;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) throw e;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static Map<String, Object> write(PackDefinition definition) {
        PackInfo info = definition.pack();
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("name", info.name());
        pack.put("version", info.version());
        pack.put("description", info.description());
        pack.put("server_ip", info.serverIp());
        pack.put("minecraft", info.minecraft());
        pack.put("loader", info.loader());
        List<Object> mods = new ArrayList<>();
        for (PackDefinition.Mod mod : definition.mods()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            switch (mod) {
                case PackDefinition.ModrinthMod m -> {
                    entry.put("source", "modrinth");
                    entry.put("project_id", m.projectId());
                    entry.put("slug", m.slug());
                    entry.put("version_id", m.versionId());
                    entry.put("version", m.versionNumber());
                }
                case PackDefinition.OwnMod m -> {
                    entry.put("source", "local");
                    entry.put("file", m.file());
                }
            }
            mods.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("pack", pack);
        root.put("mods", mods);
        root.put("configs", Map.of("overwrite", List.copyOf(definition.overwriteConfigs())));
        return root;
    }

    static PackDefinition read(Map<?, ?> root) {
        Map<?, ?> pack = (Map<?, ?>) root.get("pack");
        PackInfo info = new PackInfo(text(pack, "name"), text(pack, "version"), optional(pack, "description", ""),
            optional(pack, "server_ip", null), text(pack, "minecraft"), text(pack, "loader"));
        List<PackDefinition.Mod> mods = new ArrayList<>();
        for (Object item : (List<?>) root.get("mods")) {
            Map<?, ?> mod = (Map<?, ?>) item;
            mods.add(switch (text(mod, "source")) {
                case "modrinth" -> new PackDefinition.ModrinthMod(text(mod, "project_id"), text(mod, "slug"),
                    text(mod, "version_id"), optional(mod, "version", ""));
                case "local" -> new PackDefinition.OwnMod(safeFile(text(mod, "file")));
                default -> throw new IllegalArgumentException("Unknown mod source " + mod.get("source"));
            });
        }
        List<String> overwrite = new ArrayList<>();
        if (root.get("configs") instanceof Map<?, ?> configs && configs.get("overwrite") instanceof List<?> paths) {
            for (Object path : paths) {
                if (!(path instanceof String relative) || !SafePaths.isSafeRelativePath(relative)) {
                    throw new IllegalArgumentException("Unsafe config path " + path);
                }
                overwrite.add(relative);
            }
        }
        return new PackDefinition(info, mods, overwrite);
    }

    private static String safeFile(String name) {
        if (!SafePaths.isSafeFileName(name)) {
            throw new IllegalArgumentException("Unsafe file name " + name);
        }
        return name;
    }

    private static String text(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new IllegalArgumentException("Missing " + key);
    }

    private static String optional(Map<?, ?> map, String key, String fallback) {
        return map.get(key) instanceof String value ? value : fallback;
    }
}
