package hopsync.core.usecase;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.port.ModCatalog;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Changes the mods and details of a pack the player created. Every change is saved to the pack's definition at once; the
 * files reach the game folder through the usual check, review and apply.
 */
public final class PackEditor {
    private final ModCatalog catalog;
    private final PackRegistry registry;
    private final PackDefinitionRepository definitions;

    public PackEditor(ModCatalog catalog, PackRegistry registry, PackDefinitionRepository definitions) {
        this.catalog = catalog;
        this.registry = registry;
        this.definitions = definitions;
    }

    /**
     * What adding a mod did.
     *
     * @param title     the mod's name
     * @param alsoAdded names of the mods it needs that were added with it
     */
    public record Added(String title, List<String> alsoAdded) {
        public Added {
            alsoAdded = List.copyOf(alsoAdded);
        }
    }

    /**
     * Adds a catalog mod at its newest version for the pack's Minecraft version and loader (a stable release when
     * there is one), together with every mod it requires that the pack does not have yet.
     *
     * @throws SyncException with {@link SyncFailure.ModUnavailable} when it, or a mod it requires, has no version
     *                       for this pack
     */
    public Added addFromCatalog(String packId, String projectId) throws IOException {
        PackDefinition definition = definition(packId);
        PackInfo info = definition.pack();
        Set<String> inPack = new LinkedHashSet<>();
        for (PackDefinition.Mod mod : definition.mods()) {
            if (mod instanceof PackDefinition.ModrinthMod modrinth) inPack.add(modrinth.projectId());
        }

        List<CatalogVersion> picked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(projectId));
        while (!pending.isEmpty()) {
            String next = pending.poll();
            if (inPack.contains(next) || !seen.add(next)) continue;
            CatalogVersion version = newest(catalog.versions(next, info.minecraft(), info.loader()))
                .orElseThrow(() -> new SyncException(new SyncFailure.ModUnavailable(titleOf(next))));
            picked.add(version);
            for (CatalogVersion.Dependency dependency : version.required()) {
                dependencyProject(dependency).ifPresent(pending::add);
            }
        }
        if (picked.isEmpty()) {
            return new Added(titleOf(projectId), List.of());
        }

        // The mod asked for may be named by its slug; its version carries the project's id
        String requested = picked.getFirst().projectId();
        if (inPack.contains(requested)) {
            return new Added(titleOf(requested), List.of());
        }
        Set<String> ids = new LinkedHashSet<>();
        picked.forEach(version -> ids.add(version.projectId()));
        Map<String, CatalogProject> projects = catalog.projects(ids);
        List<PackDefinition.Mod> mods = new ArrayList<>(definition.mods());
        List<String> alsoAdded = new ArrayList<>();
        for (CatalogVersion version : picked) {
            if (inPack.contains(version.projectId())) continue;
            inPack.add(version.projectId());
            CatalogProject project = projects.get(version.projectId());
            mods.add(new PackDefinition.ModrinthMod(version.projectId(),
                project == null ? version.projectId() : project.slug(), version.id(), version.versionNumber()));
            if (!version.projectId().equals(requested)) {
                alsoAdded.add(project == null ? version.projectId() : project.title());
            }
        }
        registry.saveDefinition(packId, definition.withMods(mods));
        CatalogProject added = projects.get(requested);
        return new Added(added == null ? requested : added.title(), alsoAdded);
    }

    /**
     * Copies a jar the player picked into the pack's own mods and lists it; a jar with the same name is replaced.
     *
     * @return the jar's file name
     */
    public String addOwnJar(String packId, Path jar) throws IOException {
        PackDefinition definition = definition(packId);
        String file = jar.getFileName().toString();
        if (!file.toLowerCase(Locale.ROOT).endsWith(".jar") || !SafePaths.isSafeFileName(file)) {
            throw new SyncException(new SyncFailure.UnsafePath(file));
        }
        Path ownMods = definitions.folder(packId).resolve(DefinitionResolver.OWN_MODS);
        Files.createDirectories(ownMods);
        Files.copy(jar, SafePaths.resolveInside(ownMods, file), StandardCopyOption.REPLACE_EXISTING);
        boolean listed = definition.mods().stream()
            .anyMatch(mod -> mod instanceof PackDefinition.OwnMod own && own.file().equals(file));
        if (!listed) {
            List<PackDefinition.Mod> mods = new ArrayList<>(definition.mods());
            mods.add(new PackDefinition.OwnMod(file));
            registry.saveDefinition(packId, definition.withMods(mods));
        }
        return file;
    }

    /** A mod's versions for the pack's Minecraft version and loader, newest first. */
    public List<CatalogVersion> versions(String packId, String slug) throws IOException {
        PackInfo info = definition(packId).pack();
        return catalog.versions(slug, info.minecraft(), info.loader());
    }

    /**
     * Pins a catalog mod of the pack, named by its slug, to another of its versions.
     *
     * @throws SyncException with {@link SyncFailure.ModUnavailable} when that version is gone
     */
    public void changeVersion(String packId, String slug, String versionId) throws IOException {
        PackDefinition definition = definition(packId);
        CatalogVersion version = catalog.versionsById(List.of(versionId)).stream().findFirst()
            .orElseThrow(() -> new SyncException(new SyncFailure.ModUnavailable(slug)));
        List<PackDefinition.Mod> mods = new ArrayList<>();
        for (PackDefinition.Mod mod : definition.mods()) {
            mods.add(mod instanceof PackDefinition.ModrinthMod modrinth && modrinth.slug().equals(slug)
                ? new PackDefinition.ModrinthMod(modrinth.projectId(), slug, version.id(), version.versionNumber())
                : mod);
        }
        registry.saveDefinition(packId, definition.withMods(mods));
    }

    /**
     * Takes a mod out of the pack: a catalog mod by its slug, or one of the player's jars by its file name, whose
     * copy in the pack is deleted too. The next apply removes it from the game folder.
     */
    public void remove(String packId, String slugOrFile, boolean ownJar) throws IOException {
        PackDefinition definition = definition(packId);
        List<PackDefinition.Mod> mods = new ArrayList<>(definition.mods());
        mods.removeIf(mod -> ownJar ? mod instanceof PackDefinition.OwnMod own && own.file().equals(slugOrFile)
            : mod instanceof PackDefinition.ModrinthMod modrinth && modrinth.slug().equals(slugOrFile));
        registry.saveDefinition(packId, definition.withMods(mods));
        if (ownJar && SafePaths.isSafeFileName(slugOrFile)) {
            Files.deleteIfExists(definitions.folder(packId).resolve(DefinitionResolver.OWN_MODS).resolve(slugOrFile));
        }
    }

    /**
     * Sets the address of the server the pack is played on, shown to the players who follow it.
     *
     * @param address a {@link PackInfo#isServerAddress server address}, or blank for none
     */
    public void changeServer(String packId, String address) throws IOException {
        String server = address == null || address.isBlank() ? null : address.strip();
        if (server != null && !PackInfo.isServerAddress(server)) {
            throw new IllegalArgumentException("Not a server address: " + server);
        }
        PackDefinition definition = definition(packId);
        registry.saveDefinition(packId, definition.withPack(definition.pack().withServerIp(server)));
    }

    /** The catalog projects a created pack already lists, so the search can mark them as added. */
    public Set<String> catalogProjects(String packId) throws IOException {
        Set<String> projects = new LinkedHashSet<>();
        for (PackDefinition.Mod mod : definition(packId).mods()) {
            if (mod instanceof PackDefinition.ModrinthMod modrinth) projects.add(modrinth.projectId());
        }
        return projects;
    }

    private PackDefinition definition(String packId) throws SyncException {
        return registry.definition(packId)
            .orElseThrow(() -> new SyncException(new SyncFailure.DefinitionMissing(packId)));
    }

    /** The newest stable release, else the newest version of any kind; the catalog lists them newest first. */
    static Optional<CatalogVersion> newest(List<CatalogVersion> versions) {
        return versions.stream().filter(CatalogVersion::release).findFirst().or(() -> versions.stream().findFirst());
    }

    private Optional<String> dependencyProject(CatalogVersion.Dependency dependency) throws IOException {
        if (dependency.projectId() != null) return Optional.of(dependency.projectId());
        if (dependency.versionId() == null) return Optional.empty();
        return catalog.versionsById(List.of(dependency.versionId())).stream().findFirst().map(CatalogVersion::projectId);
    }

    private String titleOf(String projectId) {
        try {
            CatalogProject project = catalog.projects(List.of(projectId)).get(projectId);
            return project == null ? projectId : project.title();
        } catch (IOException e) {
            return projectId;
        }
    }
}
