package hopsync.core.usecase;

import hopsync.core.catalog.CatalogVersion;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSource;
import hopsync.core.pack.PackToml;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackRepository;
import hopsync.core.publish.RemotePack;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Brings a pack the player published back into the app as a created pack: its definition comes from the repository's
 * {@code hopsync.toml}, its configs and own jars from the repository's files. The result is as if the player had
 * created and published it here, so it is not offered for publishing until they change it.
 */
public final class PackImporter {
    private final PackRepository repository;
    private final ModCatalog catalog;
    private final PackRegistry registry;
    private final PackDefinitionRepository definitions;

    public PackImporter(PackRepository repository, ModCatalog catalog, PackRegistry registry,
                        PackDefinitionRepository definitions) {
        this.repository = repository;
        this.catalog = catalog;
        this.registry = registry;
        this.definitions = definitions;
    }

    /**
     * @param repo    GitHub repository as {@code owner/name}
     * @param gameDir the game folder the pack will update
     * @throws SyncException with {@link SyncFailure.ManifestInvalid} when the toml is malformed,
     *                       {@link SyncFailure.ModUnavailable} when a mod's pinned version is gone from the catalog,
     *                       or {@link SyncFailure.FolderUsedByPack} when another pack uses the folder
     */
    public Pack importPack(String repo, Path gameDir) throws IOException {
        Path staging = Files.createTempDirectory("hopsync-import");
        try {
            RemotePack remote = repository.fetch(repo, staging);
            PackDefinition definition = complete(parse(remote.toml()), ownJars(staging.resolve(DefinitionResolver.OWN_MODS)));
            Pack pack = registry.addLocal(definition, gameDir);
            try {
                Path folder = definitions.folder(pack.id());
                copyTree(staging.resolve(DefinitionResolver.CONFIGS), folder.resolve(DefinitionResolver.CONFIGS));
                copyTree(staging.resolve(DefinitionResolver.OWN_MODS), folder.resolve(DefinitionResolver.OWN_MODS));
                String hash = PackPublishing.fingerprint(PackToml.write(definition),
                    PackPublishing.files(definition, folder));
                PackSource.Publication found = remote.publication();
                registry.published(pack.id(), new PackSource.Publication(found.repo(), found.url(), hash));
            } catch (IOException | RuntimeException e) {
                try {
                    registry.remove(pack.id(), true);
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
            return registry.find(pack.id()).orElseThrow();
        } finally {
            deleteTree(staging);
        }
    }

    private static PackDefinition parse(String toml) throws SyncException {
        try {
            return PackToml.parse(toml);
        } catch (IllegalArgumentException e) {
            throw new SyncException(new SyncFailure.ManifestInvalid(e.getMessage()));
        }
    }

    /** Fills in the catalog mods' project ids and appends the own jars, which the toml does not list. */
    private PackDefinition complete(PackDefinition parsed, List<String> jars) throws IOException {
        List<String> versionIds = parsed.mods().stream()
            .map(mod -> ((PackDefinition.ModrinthMod) mod).versionId()).toList();
        Map<String, CatalogVersion> byId = new HashMap<>();
        if (!versionIds.isEmpty()) {
            for (CatalogVersion version : catalog.versionsById(versionIds)) {
                byId.put(version.id(), version);
            }
        }
        List<PackDefinition.Mod> mods = new ArrayList<>();
        for (PackDefinition.Mod mod : parsed.mods()) {
            PackDefinition.ModrinthMod modrinth = (PackDefinition.ModrinthMod) mod;
            CatalogVersion version = byId.get(modrinth.versionId());
            if (version == null) {
                throw new SyncException(new SyncFailure.ModUnavailable(modrinth.slug()));
            }
            mods.add(new PackDefinition.ModrinthMod(version.projectId(), modrinth.slug(), modrinth.versionId(),
                modrinth.versionNumber().isBlank() ? version.versionNumber() : modrinth.versionNumber()));
        }
        jars.forEach(jar -> mods.add(new PackDefinition.OwnMod(jar)));
        return parsed.withMods(mods);
    }

    /** The jars directly in the folder, by name; anything else the repository holds there is not a mod. */
    private static List<String> ownJars(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> found = Files.list(folder)) {
            return found.filter(Files::isRegularFile).map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(".jar")).sorted().toList();
        }
    }

    /** Copies the folder's files, refusing names that would land outside the target. */
    private static void copyTree(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> found = Files.walk(from)) {
            files = found.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            String relative = from.relativize(file).toString().replace('\\', '/');
            Path target = SafePaths.resolveInside(to, relative);
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path folder) {
        try (Stream<Path> found = Files.walk(folder)) {
            for (Path path : found.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            System.getLogger(PackImporter.class.getName()).log(System.Logger.Level.WARNING,
                "Could not remove the import's temporary folder " + folder, e);
        }
    }
}
