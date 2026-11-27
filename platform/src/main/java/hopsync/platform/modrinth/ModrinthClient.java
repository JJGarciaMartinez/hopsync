package hopsync.platform.modrinth;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.port.ModCatalog;
import hopsync.core.util.Json;
import hopsync.core.util.JsonParseException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Modrinth API (v2) as the mod catalog of created packs. Read-only and without an account; requests go
 * through the app's HTTP client, so they carry its user agent, retries and size limit.
 */
public class ModrinthClient implements ModCatalog {
    static final String API = "https://api.modrinth.com/v2";
    /** Ids per request, so the address stays well under any server's limit. */
    static final int BATCH = 100;

    /** Fetches a text document; the app's {@code DownloadService::getUrlString}. */
    @FunctionalInterface
    public interface Fetcher {
        String get(String url) throws IOException;
    }

    private final Fetcher fetcher;

    public ModrinthClient(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public List<String> gameVersions() throws IOException {
        List<String> versions = new ArrayList<>();
        for (Object item : (List<?>) parse(API + "/tag/game_version")) {
            Map<?, ?> version = (Map<?, ?>) item;
            if ("release".equals(version.get("version_type"))) {
                versions.add(text(version, "version"));
            }
        }
        return versions;
    }

    @Override
    public List<CatalogProject> search(String query, String minecraft, String loader, int limit) throws IOException {
        List<List<String>> facets = List.of(List.of("project_type:mod"), List.of("categories:" + loader),
            List.of("versions:" + minecraft));
        String url = API + "/search?query=" + encode(query == null ? "" : query.strip())
            + "&facets=" + encode(Json.write(facets)) + "&limit=" + Math.clamp(limit, 1, 100)
            + (query == null || query.isBlank() ? "&index=downloads" : "");
        List<CatalogProject> projects = new ArrayList<>();
        for (Object hit : (List<?>) ((Map<?, ?>) parse(url)).get("hits")) {
            Map<?, ?> project = (Map<?, ?>) hit;
            projects.add(new CatalogProject(text(project, "project_id"), text(project, "slug"),
                optional(project, "title"), optional(project, "description"), optional(project, "author"),
                optional(project, "icon_url"), number(project, "downloads")));
        }
        return projects;
    }

    @Override
    public List<CatalogVersion> versions(String projectIdOrSlug, String minecraft, String loader) throws IOException {
        String url = API + "/project/" + encode(projectIdOrSlug) + "/version?loaders="
            + encode(Json.write(List.of(loader))) + "&game_versions=" + encode(Json.write(List.of(minecraft)));
        Object versions;
        try {
            versions = parse(url);
        } catch (SyncException e) {
            if (e.failure() instanceof SyncFailure.HttpStatus status && status.status() == 404) {
                return List.of();
            }
            throw e;
        }
        return readVersions(versions);
    }

    @Override
    public Optional<ProjectDetails> details(String projectIdOrSlug) throws IOException {
        Map<?, ?> project;
        try {
            project = (Map<?, ?>) parse(API + "/project/" + encode(projectIdOrSlug));
        } catch (SyncException e) {
            if (e.failure() instanceof SyncFailure.HttpStatus status && status.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
        List<ProjectDetails.Image> gallery = new ArrayList<>();
        if (project.get("gallery") instanceof List<?> items) {
            for (Object item : items) {
                Map<?, ?> image = (Map<?, ?>) item;
                if (image.get("url") instanceof String url && !url.isEmpty()) {
                    gallery.add(new ProjectDetails.Image(url, optional(image, "title"),
                        Boolean.TRUE.equals(image.get("featured"))));
                }
            }
        }
        return Optional.of(new ProjectDetails(text(project, "id"), optional(project, "slug"),
            optional(project, "title"), optional(project, "description"), optional(project, "body"), gallery));
    }

    @Override
    public List<CatalogVersion> versionsById(Collection<String> versionIds) throws IOException {
        List<CatalogVersion> versions = new ArrayList<>();
        for (List<String> batch : batches(versionIds)) {
            versions.addAll(readVersions(parse(API + "/versions?ids=" + encode(Json.write(batch)))));
        }
        return versions;
    }

    @Override
    public Map<String, CatalogProject> projects(Collection<String> projectIds) throws IOException {
        List<Map<?, ?>> found = new ArrayList<>();
        for (List<String> batch : batches(projectIds)) {
            for (Object project : (List<?>) parse(API + "/projects?ids=" + encode(Json.write(batch)))) {
                found.add((Map<?, ?>) project);
            }
        }
        Map<String, String> owners = teamOwners(found.stream().map(project -> text(project, "team")).distinct().toList());
        Map<String, CatalogProject> projects = new LinkedHashMap<>();
        for (Map<?, ?> project : found) {
            String id = text(project, "id");
            projects.put(id, new CatalogProject(id, text(project, "slug"), optional(project, "title"),
                optional(project, "description"), owners.get(text(project, "team")), optional(project, "icon_url"),
                number(project, "downloads")));
        }
        return projects;
    }

    /** Each team's owner, else its first member, as the pack repository's build names authors. */
    private Map<String, String> teamOwners(List<String> teamIds) throws IOException {
        Map<String, String> owners = new LinkedHashMap<>();
        for (List<String> batch : batches(teamIds)) {
            for (Object team : (List<?>) parse(API + "/teams?ids=" + encode(Json.write(batch)))) {
                List<Map<?, ?>> members = ((List<?>) team).stream().<Map<?, ?>>map(member -> (Map<?, ?>) member).toList();
                if (members.isEmpty()) continue;
                Map<?, ?> owner = members.stream()
                    .filter(m -> Boolean.TRUE.equals(m.get("is_owner")) || "Owner".equals(m.get("role")))
                    .findFirst().orElse(members.getFirst());
                owners.put(text(owner, "team_id"), text((Map<?, ?>) owner.get("user"), "username"));
            }
        }
        return owners;
    }

    private static List<CatalogVersion> readVersions(Object json) {
        List<CatalogVersion> versions = new ArrayList<>();
        for (Object item : (List<?>) json) {
            Map<?, ?> version = (Map<?, ?>) item;
            List<CatalogVersion.File> files = new ArrayList<>();
            for (Object entry : (List<?>) version.get("files")) {
                Map<?, ?> file = (Map<?, ?>) entry;
                files.add(new CatalogVersion.File(text(file, "url"), text(file, "filename"),
                    text((Map<?, ?>) file.get("hashes"), "sha512"), number(file, "size"),
                    Boolean.TRUE.equals(file.get("primary"))));
            }
            List<CatalogVersion.Dependency> dependencies = new ArrayList<>();
            if (version.get("dependencies") instanceof List<?> list) {
                for (Object entry : list) {
                    Map<?, ?> dependency = (Map<?, ?>) entry;
                    dependencies.add(new CatalogVersion.Dependency(optional(dependency, "project_id"),
                        optional(dependency, "version_id"), "required".equals(dependency.get("dependency_type"))));
                }
            }
            versions.add(new CatalogVersion(text(version, "id"), text(version, "project_id"),
                optional(version, "version_number"), strings(version.get("game_versions")),
                strings(version.get("loaders")), "release".equals(version.get("version_type")), files, dependencies));
        }
        return versions;
    }

    private Object parse(String url) throws IOException {
        String body = fetcher.get(url);
        try {
            return Json.parse(body);
        } catch (JsonParseException e) {
            throw new SyncException(new SyncFailure.Unexpected("Modrinth answered with invalid JSON: " + url), e);
        }
    }

    private static List<List<String>> batches(Collection<String> ids) {
        List<String> all = List.copyOf(ids);
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < all.size(); i += BATCH) {
            batches.add(all.subList(i, Math.min(all.size(), i + BATCH)));
        }
        return batches;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(Map<?, ?> map, String key) {
        if (map.get(key) instanceof String value && !value.isEmpty()) {
            return value;
        }
        throw new IllegalArgumentException("Modrinth answer without " + key);
    }

    private static String optional(Map<?, ?> map, String key) {
        return map.get(key) instanceof String value ? value : null;
    }

    private static long number(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number value ? value.longValue() : 0;
    }

    private static List<String> strings(Object list) {
        return list instanceof List<?> items ? items.stream().map(String::valueOf).toList() : List.of();
    }
}
