package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.PackSource;
import hopsync.core.publish.PublishRequest;
import hopsync.core.publish.PublishStep;
import hopsync.core.util.CancellationToken;
import hopsync.core.util.Json;
import hopsync.platform.secrets.MemorySecrets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubPublisherTest {
    private static final String REPO = "player/mi-pack";

    @TempDir
    Path dir;

    private final MemorySecrets secrets = new MemorySecrets();
    private final List<String> calls = new ArrayList<>();
    private final List<Map<?, ?>> trees = new ArrayList<>();
    private int generateStatus = 201;
    private String newTree = "tree-2";
    private String conclusion = "success";
    private final List<String> releaseAssets = new ArrayList<>(List.of("manifest.json", "4833657844-mine.jar",
        "27c74670ad-keep.json", "0123456789-create_connected.jar"));

    /** A GitHub that knows one template copy with the demo's files. */
    private final GitHubApi.Transport github = (method, url, json, token) -> {
        String path = url.substring(GitHubApi.API.length());
        calls.add(method + " " + path);
        assertEquals("gho_token", token);
        return switch (method + " " + path.replaceAll("\\?.*", "")) {
            case "GET /user" -> new GitHubApi.Response(200, "{\"login\":\"player\"}");
            case "POST /repos/JJGarciaMartinez/hopsync-demo/generate" ->
                new GitHubApi.Response(generateStatus, "{\"full_name\":\"" + REPO + "\"}");
            case "GET /repos/player/mi-pack/git/ref/heads/main" ->
                new GitHubApi.Response(200, "{\"object\":{\"sha\":\"commit-1\"}}");
            case "GET /repos/player/mi-pack/git/commits/commit-1" ->
                new GitHubApi.Response(200, "{\"tree\":{\"sha\":\"tree-1\"}}");
            case "GET /repos/player/mi-pack/git/trees/tree-1" -> new GitHubApi.Response(200, "{\"tree\":["
                + "{\"path\":\"hopsync.toml\",\"type\":\"blob\"},{\"path\":\"manifest.json\",\"type\":\"blob\"},"
                + "{\"path\":\".github/workflows/check-updates.yml\",\"type\":\"blob\"},"
                + "{\"path\":\".github/workflows/build.yml\",\"type\":\"blob\"},"
                + "{\"path\":\"configs/demo.json\",\"type\":\"blob\"},{\"path\":\"configs/keep.json\",\"type\":\"blob\"},"
                + "{\"path\":\"local-mods/demo.jar\",\"type\":\"blob\"},{\"path\":\"local-mods\",\"type\":\"tree\"}]}");
            case "POST /repos/player/mi-pack/git/blobs" -> new GitHubApi.Response(201, "{\"sha\":\"blob-" + calls.size() + "\"}");
            case "POST /repos/player/mi-pack/git/trees" -> {
                trees.add((Map<?, ?>) Json.parse(json));
                yield new GitHubApi.Response(201, "{\"sha\":\"" + newTree + "\"}");
            }
            case "POST /repos/player/mi-pack/git/commits" -> new GitHubApi.Response(201, "{\"sha\":\"commit-2\"}");
            case "PATCH /repos/player/mi-pack/git/refs/heads/main" -> new GitHubApi.Response(200, "{\"object\":{}}");
            case "GET /repos/player/mi-pack/actions/runs" -> new GitHubApi.Response(200, "{\"workflow_runs\":[{"
                + "\"path\":\".github/workflows/build.yml\",\"status\":\"completed\",\"conclusion\":\"" + conclusion
                + "\",\"html_url\":\"https://github.com/player/mi-pack/actions/runs/7\"}]}");
            case "GET /repos/player/mi-pack/releases/tags/hopsync" -> new GitHubApi.Response(200, "{\"assets\":["
                + releaseAssets.stream().map(name -> "{\"id\":" + (name.hashCode() & 0xffff) + ",\"name\":\"" + name + "\"}")
                    .collect(Collectors.joining(",")) + "]}");
            default -> {
                if (method.equals("DELETE") && path.startsWith("/repos/player/mi-pack/releases/assets/")) {
                    yield new GitHubApi.Response(204, "");
                }
                throw new AssertionError("Unexpected call " + method + " " + path);
            }
        };
    };

    private final GitHubPublisher publisher = new GitHubPublisher(new GitHubApi(github), secrets, "github.token",
        (duration, cancel) -> { });

    private PublishRequest request(PackSource.Publication existing) throws IOException {
        Path keep = Files.writeString(dir.resolve("keep.json"), "{}");
        Path jar = Files.writeString(dir.resolve("mine.jar"), "jar");
        return new PublishRequest("Mi pack", "mi-pack", existing, "[pack]\nname = \"Mi pack\"\n",
            Map.of("configs/keep.json", keep, "local-mods/mine.jar", jar), "hash-1");
    }

    @BeforeEach
    void signIn() {
        secrets.write("github.token", "gho_token");
    }

    @Test
    void aFirstPublishCreatesTheRepositoryAndReplacesTheTemplatesContent() throws IOException {
        List<PublishStep> steps = new ArrayList<>();

        PackSource.Publication publication = publisher.publish(request(null), steps::add, CancellationToken.none());

        assertEquals(new PackSource.Publication(REPO, GitHubPublisher.manifestUrl(REPO), "hash-1"), publication);
        assertEquals(List.of(PublishStep.CREATING_REPOSITORY, PublishStep.UPLOADING, PublishStep.BUILDING), steps);
        Map<?, ?> tree = trees.getFirst();
        assertEquals("tree-1", tree.get("base_tree"));
        Map<String, Object> entries = ((List<?>) tree.get("tree")).stream().map(item -> (Map<?, ?>) item)
            .collect(Collectors.toMap(entry -> entry.get("path").toString(),
                entry -> entry.containsKey("content") ? "text" : entry.get("sha") == null ? "deleted" : "blob"));
        assertEquals(Map.of("hopsync.toml", "text", "README.md", "text", "configs/keep.json", "blob",
            "local-mods/mine.jar", "blob", "configs/demo.json", "deleted", "local-mods/demo.jar", "deleted",
            "manifest.json", "deleted", ".github/workflows/check-updates.yml", "deleted"), entries);
        assertTrue(calls.contains("PATCH /repos/player/mi-pack/git/refs/heads/main"));
    }

    @Test
    void releaseAssetsNoFileOfThePackMapsToAreDeleted() throws IOException {
        publisher.publish(request(null), step -> { }, CancellationToken.none());

        List<String> deletions = calls.stream().filter(call -> call.startsWith("DELETE ")).toList();
        assertEquals(List.of("DELETE /repos/player/mi-pack/releases/assets/"
            + ("0123456789-create_connected.jar".hashCode() & 0xffff)), deletions);
    }

    @Test
    void nothingIsDeletedWhileAFileOfThePackHasNoAssetYet() throws IOException {
        releaseAssets.remove("4833657844-mine.jar");

        publisher.publish(request(null), step -> { }, CancellationToken.none());

        assertFalse(calls.stream().anyMatch(call -> call.startsWith("DELETE ")));
    }

    @Test
    void anUnchangedPackNeedsNoCommitAndNoBuild() throws IOException {
        newTree = "tree-1";
        List<PublishStep> steps = new ArrayList<>();
        PackSource.Publication existing = new PackSource.Publication(REPO, GitHubPublisher.manifestUrl(REPO), "old");

        publisher.publish(request(existing), steps::add, CancellationToken.none());

        assertEquals(List.of(PublishStep.UPLOADING), steps);
        assertFalse(calls.stream().anyMatch(call -> call.contains("/git/commits") && call.startsWith("POST")));
        assertFalse(calls.stream().anyMatch(call -> call.contains("generate")));
    }

    @Test
    void aTakenNameAFailedBuildOrNoTokenAreSaidPlainly() throws IOException {
        generateStatus = 422;
        assertEquals(new SyncFailure.RepoNameTaken("mi-pack"), assertThrows(SyncException.class,
            () -> publisher.publish(request(null), step -> { }, CancellationToken.none())).failure());

        generateStatus = 201;
        conclusion = "failure";
        assertEquals(new SyncFailure.PublishBuildFailed("https://github.com/player/mi-pack/actions/runs/7"),
            assertThrows(SyncException.class, () -> publisher.publish(request(null), step -> { },
                CancellationToken.none())).failure());

        secrets.delete("github.token");
        assertEquals(new SyncFailure.GitHubSignedOut(), assertThrows(SyncException.class,
            () -> publisher.publish(request(null), step -> { }, CancellationToken.none())).failure());
    }

    @Test
    void theReadmeLinksThePackAndCarriesTheNotice() throws IOException {
        String readme = GitHubPublisher.readme("Mi pack", REPO);
        assertTrue(readme.startsWith("# Mi pack\n"));
        assertTrue(readme.contains(GitHubPublisher.manifestUrl(REPO)));
        assertTrue(readme.contains("no se hace responsable"));
        assertFalse(readme.contains("{name}") || readme.contains("{link}"));
        assertEquals(Set.of("manifest.json", ".github/workflows/check-updates.yml"), GitHubPublisher.TEMPLATE_ONLY);
    }
}
