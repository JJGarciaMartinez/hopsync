package hopsync.core.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One version of a mod project in the catalog.
 *
 * @param id            version id, what a created pack pins
 * @param projectId     the project it belongs to
 * @param versionNumber the version as its author writes it
 * @param gameVersions  Minecraft versions it supports
 * @param loaders       mod loaders it supports, lowercase
 * @param release       a stable release, not a beta or alpha
 * @param files         its files; the primary one is the mod jar
 * @param dependencies  other projects or versions it relates to
 */
public record CatalogVersion(String id, String projectId, String versionNumber, List<String> gameVersions,
                             List<String> loaders, boolean release, List<File> files, List<Dependency> dependencies) {

    public CatalogVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        versionNumber = versionNumber == null ? "" : versionNumber;
        gameVersions = List.copyOf(gameVersions);
        loaders = List.copyOf(loaders);
        files = List.copyOf(files);
        dependencies = List.copyOf(dependencies);
    }

    /** The jar to install: the file marked primary, else the first one. */
    public Optional<File> primaryFile() {
        return files.stream().filter(File::primary).findFirst().or(() -> files.stream().findFirst());
    }

    /** The projects this version cannot work without. */
    public List<Dependency> required() {
        return dependencies.stream().filter(Dependency::required).toList();
    }

    /**
     * @param url      https download address
     * @param filename name of the file once installed
     * @param sha512   lowercase hex SHA-512
     * @param size     bytes
     * @param primary  the file the author marked as the main one
     */
    public record File(String url, String filename, String sha512, long size, boolean primary) {
    }

    /**
     * @param projectId the project depended on, or {@code null} when only a version is named
     * @param versionId a specific version, or {@code null} for any compatible one
     * @param required  the mod does not work without it (optional, embedded and incompatible ones are not)
     */
    public record Dependency(String projectId, String versionId, boolean required) {
    }
}
