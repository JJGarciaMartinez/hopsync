package hopsync.core.usecase;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ConfigRoot;
import hopsync.core.manifest.Manifest;
import hopsync.core.manifest.ModEntry;
import hopsync.core.manifest.ModSource;
import hopsync.core.pack.ConfigMode;
import hopsync.core.pack.ConfigRule;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.sync.ConfigUnits;
import hopsync.core.sync.FileHasher;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns a created pack's definition into the manifest the sync engine applies, as the pack repository's
 * build would publish it: Modrinth mods by their pinned version, the player's own jars and configs from the
 * pack's folder (as {@code file:} addresses, only ever produced here).
 */
public final class DefinitionResolver {
    static final String OWN_MODS = "local-mods";
    static final String CONFIGS = "configs";
    static final String GAME_FILES = "game-files";

    private final ModCatalog catalog;
    private final PackDefinitionRepository definitions;

    public DefinitionResolver(ModCatalog catalog, PackDefinitionRepository definitions) {
        this.catalog = catalog;
        this.definitions = definitions;
    }

    /**
     * @throws SyncException with {@link SyncFailure.DefinitionMissing} when the definition cannot be read, or
     *                       {@link SyncFailure.ModUnavailable} when a mod or own jar is gone
     */
    public Manifest resolve(Pack pack) throws IOException {
        PackDefinition definition = definitions.load(pack.id())
            .orElseThrow(() -> new SyncException(new SyncFailure.DefinitionMissing(pack.id())));
        return resolve(definition, definitions.folder(pack.id()));
    }

    Manifest resolve(PackDefinition definition, Path folder) throws IOException {
        List<ModEntry> mods = new ArrayList<>(modrinthMods(definition));
        for (PackDefinition.Mod mod : definition.mods()) {
            if (mod instanceof PackDefinition.OwnMod own) {
                mods.add(ownMod(folder.resolve(OWN_MODS), own.file()));
            }
        }
        mods.sort(Comparator.comparing(mod -> mod.file().toLowerCase(Locale.ROOT)));
        List<ConfigEntry> configs = new ArrayList<>(configs(folder.resolve(CONFIGS), definition.configRules()));
        configs.addAll(gameFiles(folder.resolve(GAME_FILES)));
        return new Manifest(Manifest.SUPPORTED_FORMAT_VERSION, definition.pack(), contentHash(mods, configs),
            mods, configs);
    }

    private List<ModEntry> modrinthMods(PackDefinition definition) throws IOException {
        List<PackDefinition.ModrinthMod> pinned = definition.mods().stream()
            .filter(PackDefinition.ModrinthMod.class::isInstance).map(PackDefinition.ModrinthMod.class::cast).toList();
        if (pinned.isEmpty()) {
            return List.of();
        }
        Map<String, CatalogVersion> versions = catalog.versionsById(
                pinned.stream().map(PackDefinition.ModrinthMod::versionId).toList()).stream()
            .collect(Collectors.toMap(CatalogVersion::id, Function.identity(), (a, b) -> a));
        Map<String, CatalogProject> projects = catalog.projects(
            versions.values().stream().map(CatalogVersion::projectId).distinct().toList());
        List<ModEntry> mods = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PackDefinition.ModrinthMod mod : pinned) {
            CatalogVersion version = versions.get(mod.versionId());
            CatalogVersion.File file = version == null ? null : version.primaryFile().orElse(null);
            if (file == null) {
                throw new SyncException(new SyncFailure.ModUnavailable(mod.slug()));
            }
            if (!seen.add(version.projectId())) {
                continue;
            }
            CatalogProject project = projects.get(version.projectId());
            mods.add(new ModEntry(project == null ? mod.slug() : project.slug(),
                project == null ? mod.slug() : project.title(), version.versionNumber(),
                project == null ? "" : project.author(), project == null ? "" : project.description(),
                file.filename(), file.url(), file.sha512(), file.size(), ModSource.MODRINTH));
        }
        return mods;
    }

    private static ModEntry ownMod(Path ownMods, String file) throws IOException {
        Path jar = SafePaths.resolveInside(ownMods, file);
        if (!Files.isRegularFile(jar)) {
            throw new SyncException(new SyncFailure.ModUnavailable(file));
        }
        String name = file.toLowerCase(Locale.ROOT).endsWith(".jar") ? file.substring(0, file.length() - 4) : file;
        return new ModEntry(name, name, "", "", "", file, jar.toUri().toString(), FileHasher.sha512(jar),
            Files.size(jar), ModSource.LOCAL);
    }

    private static List<ConfigEntry> configs(Path configs, List<ConfigRule> rules) throws IOException {
        Map<String, ConfigMode> modes = rules.stream().collect(Collectors.toMap(ConfigRule::unit, ConfigRule::mode));
        List<ConfigEntry> entries = new ArrayList<>();
        for (Path file : regularFiles(configs)) {
            String relative = configs.relativize(file).toString().replace('\\', '/');
            if (!SafePaths.isSafeRelativePath(relative)) {
                continue;
            }
            ConfigMode mode = modes.getOrDefault(ConfigUnits.configUnit(relative).id(), ConfigMode.IF_MISSING);
            entries.add(entry(relative, file, ConfigRoot.CONFIG, mode));
        }
        return entries;
    }

    /** Everything in {@code game-files/} is optional; only the whitelisted names are offered. */
    private static List<ConfigEntry> gameFiles(Path folder) throws IOException {
        List<ConfigEntry> entries = new ArrayList<>();
        for (Path file : regularFiles(folder)) {
            String name = folder.relativize(file).toString().replace('\\', '/');
            if (ConfigUnits.GAME_FILES.contains(name)) {
                entries.add(entry(name, file, ConfigRoot.GAME, ConfigMode.OPTIONAL));
            }
        }
        return entries;
    }

    private static List<Path> regularFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static ConfigEntry entry(String relative, Path file, ConfigRoot root, ConfigMode mode) throws IOException {
        return new ConfigEntry(relative, file.toUri().toString(), FileHasher.sha512(file), Files.size(file),
            mode == ConfigMode.ALWAYS, root, mode == ConfigMode.OPTIONAL);
    }

    /** Changes whenever a file to install changes, which is all the sync state compares. */
    private static String contentHash(List<ModEntry> mods, List<ConfigEntry> configs) {
        StringBuilder content = new StringBuilder();
        mods.forEach(mod -> content.append(mod.file()).append('\t').append(mod.sha512()).append('\n'));
        configs.forEach(config -> content.append(config.root().json()).append('\t').append(config.path())
            .append('\t').append(config.sha512()).append('\t').append(config.overwrite())
            .append('\t').append(config.optional()).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
