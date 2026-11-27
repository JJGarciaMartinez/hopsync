package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.publish.RemotePack;
import hopsync.platform.secrets.MemorySecrets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubPacksTest {
    private static final String REPO = "Player/mi-pack";

    @TempDir
    Path dir;

    private final MemorySecrets secrets = new MemorySecrets();
    private String tree;
    private String reposAnswer;

    /** A GitHub where the signed-in player is "player" and owns the repositories listed in {@code reposAnswer}. */
    private final GitHubApi.Transport github = (method, url, json, token) -> {
        String path = url.substring(GitHubApi.API.length());
        assertEquals("GET", method);
        assertEquals("gho_token", token);
        if (path.equals("/user")) {
            return new GitHubApi.Response(200, "{\"login\":\"player\"}");
        }
        if (path.startsWith("/user/repos")) {
            return new GitHubApi.Response(200, reposAnswer);
        }
        if (path.equals("/repos/player/mi-pack/contents/hopsync.toml")
                || path.equals("/repos/player/recent/contents/hopsync.toml")) {
            return new GitHubApi.Response(200, "{\"name\":\"hopsync.toml\"}");
        }
        if (path.startsWith("/repos/player/") && path.endsWith("/contents/hopsync.toml")) {
            return new GitHubApi.Response(404, "{\"message\":\"Not Found\"}");
        }
        if (path.equals("/repos/" + REPO + "/git/trees/main?recursive=1")) {
            return tree == null ? new GitHubApi.Response(404, "{}") : new GitHubApi.Response(200, tree);
        }
        if (path.startsWith("/repos/" + REPO + "/git/blobs/")) {
            String text = switch (path.substring(path.lastIndexOf('/') + 1)) {
                case "sha-toml" -> "[pack]\nname = \"Mi pack\"\n";
                case "sha-rules" -> "{}";
                case "sha-jar" -> "jar bytes";
                default -> throw new AssertionError("Unexpected blob " + path);
            };
            String base64 = Base64.getMimeEncoder(4, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
            return new GitHubApi.Response(200,
                "{\"encoding\":\"base64\",\"content\":\"" + base64.replace("\n", "\\n") + "\"}");
        }
        throw new AssertionError("Unexpected call " + method + " " + path);
    };

    private final GitHubPacks packs = new GitHubPacks(new GitHubApi(github), secrets, "github.token");

    @BeforeEach
    void signInAndPublish() {
        secrets.write("github.token", "gho_token");
        reposAnswer = "[{\"full_name\":\"player/mi-pack\"},{\"full_name\":\"player/notes\"},"
            + "{\"full_name\":\"player/forked\",\"fork\":true},{\"full_name\":\"player/old\",\"archived\":true},"
            + "{\"full_name\":\"player/recent\"}]";
        tree = "{\"truncated\":false,\"tree\":["
            + "{\"path\":\"hopsync.toml\",\"type\":\"blob\",\"sha\":\"sha-toml\",\"size\":24},"
            + "{\"path\":\"README.md\",\"type\":\"blob\",\"sha\":\"sha-readme\",\"size\":9},"
            + "{\"path\":\"configs\",\"type\":\"tree\"},"
            + "{\"path\":\"configs/server/rules.json\",\"type\":\"blob\",\"sha\":\"sha-rules\",\"size\":2},"
            + "{\"path\":\"local-mods/mine.jar\",\"type\":\"blob\",\"sha\":\"sha-jar\",\"size\":9},"
            + "{\"path\":\"local-mods/deep/other.jar\",\"type\":\"blob\",\"sha\":\"sha-other\",\"size\":9}]}";
    }

    @Test
    void listsTheOwnReposThatHoldAPackAndSkipsForksAndArchived() throws IOException {
        assertEquals(List.of("player/mi-pack", "player/recent"), packs.ownedRepos());
    }

    @Test
    void listingNeedsASignedInPlayer() {
        secrets.delete("github.token");

        SyncException e = assertThrows(SyncException.class, packs::ownedRepos);

        assertEquals(new SyncFailure.GitHubSignedOut(), e.failure());
    }

    @Test
    void aLinkIsOwnedOnlyWhenItsRepoBelongsToTheSignedInPlayer() throws IOException {
        String mine = GitHubPublisher.manifestUrl("Player/mi-pack");

        assertEquals(Optional.of("Player/mi-pack"), packs.ownedRepoOf(mine));
        assertEquals(Optional.empty(), packs.ownedRepoOf(GitHubPublisher.manifestUrl("friend/their-pack")));
        assertEquals(Optional.empty(), packs.ownedRepoOf("https://example.com/player/mi-pack/manifest.json"));
        assertEquals(Optional.empty(), packs.ownedRepoOf(
            "https://github.com/player/mi-pack/releases/download/v1/manifest.json"));
        assertEquals(Optional.empty(), packs.ownedRepoOf("https://github.com/player/mi-pack/releases/download/hopsync/manifest.json.evil"));
    }

    @Test
    void withoutSessionNoLinkIsOwned() throws IOException {
        secrets.delete("github.token");

        assertEquals(Optional.empty(), packs.ownedRepoOf(GitHubPublisher.manifestUrl("player/mi-pack")));
    }

    @Test
    void fetchDownloadsTheTomlAndTheConfigsAndOwnJarsOnly() throws IOException {
        RemotePack remote = packs.fetch(REPO, dir);

        assertEquals("[pack]\nname = \"Mi pack\"\n", remote.toml());
        assertEquals(REPO, remote.publication().repo());
        assertEquals(GitHubPublisher.manifestUrl(REPO), remote.publication().url());
        assertEquals("", remote.publication().contentHash());
        assertEquals("{}", Files.readString(dir.resolve("configs/server/rules.json")));
        assertEquals("jar bytes", Files.readString(dir.resolve("local-mods/mine.jar")));
        assertFalse(Files.exists(dir.resolve("README.md")));
        assertFalse(Files.exists(dir.resolve("local-mods/deep")), "only jars directly in local-mods/ are the pack's");
    }

    @Test
    void fetchRefusesARepoOfAnotherAccount() {
        SyncException e = assertThrows(SyncException.class, () -> packs.fetch("friend/their-pack", dir));

        assertEquals(new SyncFailure.NotRepoOwner("friend/their-pack"), e.failure());
    }

    @Test
    void fetchRefusesARepoWithoutTomlOrBranch() {
        tree = "{\"truncated\":false,\"tree\":[{\"path\":\"README.md\",\"type\":\"blob\",\"sha\":\"x\"}]}";
        assertEquals(new SyncFailure.NotAHopSyncRepo(REPO),
            assertThrows(SyncException.class, () -> packs.fetch(REPO, dir)).failure());

        tree = null;
        assertEquals(new SyncFailure.NotAHopSyncRepo(REPO),
            assertThrows(SyncException.class, () -> packs.fetch(REPO, dir)).failure());
    }

    @Test
    void fetchRefusesAFileTooBigToHoldAndAPathOutsideTheFolder() {
        tree = "{\"tree\":[{\"path\":\"hopsync.toml\",\"type\":\"blob\",\"sha\":\"sha-toml\",\"size\":24},"
            + "{\"path\":\"local-mods/huge.jar\",\"type\":\"blob\",\"sha\":\"sha-jar\",\"size\":"
            + (GitHubPacks.MAX_FILE_BYTES + 1) + "}]}";
        SyncFailure tooLarge = assertThrows(SyncException.class, () -> packs.fetch(REPO, dir)).failure();
        assertTrue(tooLarge instanceof SyncFailure.ResponseTooLarge, tooLarge.describe());

        tree = "{\"tree\":[{\"path\":\"hopsync.toml\",\"type\":\"blob\",\"sha\":\"sha-toml\",\"size\":24},"
            + "{\"path\":\"configs/../../evil.json\",\"type\":\"blob\",\"sha\":\"sha-rules\",\"size\":2}]}";
        assertEquals(new SyncFailure.UnsafePath("configs/../../evil.json"),
            assertThrows(SyncException.class, () -> packs.fetch(REPO, dir)).failure());
        assertFalse(Files.exists(dir.resolve("../evil.json")));
    }

    @Test
    void fetchRefusesANameThatIsNotARepo() {
        assertEquals(new SyncFailure.NotAHopSyncRepo("player/../user"),
            assertThrows(SyncException.class, () -> packs.fetch("player/../user", dir)).failure());
    }
}
