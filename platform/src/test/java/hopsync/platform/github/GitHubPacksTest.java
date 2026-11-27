package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.publish.FetchProgress;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
        if (path.equals("/repos/" + REPO + "/git/ref/heads/main")) {
            return tree == null ? new GitHubApi.Response(404, "{}")
                : new GitHubApi.Response(200, "{\"object\":{\"sha\":\"commit-1\"}}");
        }
        if (path.equals("/repos/" + REPO + "/git/trees/commit-1?recursive=1")) {
            return new GitHubApi.Response(200, tree);
        }
        if (path.equals("/repos/" + REPO + "/git/blobs/sha-toml")) {
            String base64 = Base64.getMimeEncoder(4, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString("[pack]\nname = \"Mi pack\"\n".getBytes(StandardCharsets.UTF_8));
            return new GitHubApi.Response(200,
                "{\"encoding\":\"base64\",\"content\":\"" + base64.replace("\n", "\\n") + "\"}");
        }
        throw new AssertionError("Unexpected call " + method + " " + path);
    };

    private static final String RAW = "https://raw.githubusercontent.com/Player/mi-pack/commit-1/";

    private final Map<String, byte[]> raw = new ConcurrentHashMap<>();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger mostRunning = new AtomicInteger();
    private long downloadMillis;

    /** A raw.githubusercontent.com that serves {@code raw}, one request at a time counted. */
    private final GitHubPacks.Downloader downloader = (url, max) -> {
        mostRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
        try {
            if (downloadMillis > 0) {
                Thread.sleep(downloadMillis);
            }
            byte[] bytes = raw.get(url);
            if (bytes == null) {
                throw new AssertionError("Unexpected download " + url);
            }
            return bytes;
        } catch (InterruptedException e) {
            throw new IOException(e);
        } finally {
            running.decrementAndGet();
        }
    };

    private final GitHubPacks packs = new GitHubPacks(new GitHubApi(github), secrets, "github.token", downloader);

    private static String jarEntry(String path, int size) {
        return "{\"path\":\"" + path + "\",\"type\":\"blob\",\"sha\":\"x\",\"size\":" + size + "}";
    }

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
        raw.put(RAW + "configs/server/rules.json", "{}".getBytes(StandardCharsets.UTF_8));
        raw.put(RAW + "local-mods/mine.jar", "jar bytes".getBytes(StandardCharsets.UTF_8));
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
        RemotePack remote = packs.fetch(REPO, dir, progress -> { });

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
        SyncException e = assertThrows(SyncException.class, () -> packs.fetch("friend/their-pack", dir, progress -> { }));

        assertEquals(new SyncFailure.NotRepoOwner("friend/their-pack"), e.failure());
    }

    @Test
    void fetchRefusesARepoWithoutTomlOrBranch() {
        tree = "{\"truncated\":false,\"tree\":[{\"path\":\"README.md\",\"type\":\"blob\",\"sha\":\"x\"}]}";
        assertEquals(new SyncFailure.NotAHopSyncRepo(REPO),
            assertThrows(SyncException.class, () -> packs.fetch(REPO, dir, progress -> { })).failure());

        tree = null;
        assertEquals(new SyncFailure.NotAHopSyncRepo(REPO),
            assertThrows(SyncException.class, () -> packs.fetch(REPO, dir, progress -> { })).failure());
    }

    @Test
    void fetchRefusesAFileTooBigToHoldAndAPathOutsideTheFolder() {
        tree = "{\"tree\":[{\"path\":\"hopsync.toml\",\"type\":\"blob\",\"sha\":\"sha-toml\",\"size\":24},"
            + "{\"path\":\"local-mods/huge.jar\",\"type\":\"blob\",\"sha\":\"sha-jar\",\"size\":"
            + (GitHubPacks.MAX_FILE_BYTES + 1) + "}]}";
        SyncFailure tooLarge = assertThrows(SyncException.class, () -> packs.fetch(REPO, dir, progress -> { })).failure();
        assertTrue(tooLarge instanceof SyncFailure.ResponseTooLarge, tooLarge.describe());

        tree = "{\"tree\":[{\"path\":\"hopsync.toml\",\"type\":\"blob\",\"sha\":\"sha-toml\",\"size\":24},"
            + "{\"path\":\"configs/../../evil.json\",\"type\":\"blob\",\"sha\":\"sha-rules\",\"size\":2}]}";
        assertEquals(new SyncFailure.UnsafePath("configs/../../evil.json"),
            assertThrows(SyncException.class, () -> packs.fetch(REPO, dir, progress -> { })).failure());
        assertFalse(Files.exists(dir.resolve("../evil.json")));
    }

    @Test
    void fetchRefusesANameThatIsNotARepo() {
        assertEquals(new SyncFailure.NotAHopSyncRepo("player/../user"),
            assertThrows(SyncException.class, () -> packs.fetch("player/../user", dir, progress -> { })).failure());
    }

    @Test
    void fetchEncodesEachPathSegmentOfTheDownloadUrl() throws IOException {
        tree = "{\"tree\":[{\"path\":\"hopsync.toml\",\"type\":\"blob\",\"sha\":\"sha-toml\",\"size\":24},"
            + jarEntry("local-mods/Camera [1.21]+x y.jar", 3) + "]}";
        raw.put(RAW + "local-mods/Camera%20%5B1.21%5D%2Bx%20y.jar", "abc".getBytes(StandardCharsets.UTF_8));

        packs.fetch(REPO, dir, progress -> { });

        assertEquals("abc", Files.readString(dir.resolve("local-mods/Camera [1.21]+x y.jar")));
    }

    @Test
    void fetchReportsProgressFromZeroToTheNumberOfFiles() throws IOException {
        List<FetchProgress> seen = new CopyOnWriteArrayList<>();

        packs.fetch(REPO, dir, seen::add);

        assertEquals(new FetchProgress(0, 2), seen.getFirst());
        assertEquals(new FetchProgress(2, 2), seen.getLast());
        assertEquals(3, seen.size());
    }

    @Test
    void fetchDownloadsSeveralFilesAtOnceButNeverMoreThanTheLimit() throws IOException {
        StringBuilder entries = new StringBuilder(jarEntry("hopsync.toml", 24).replace("\"x\"", "\"sha-toml\""));
        for (int i = 0; i < 24; i++) {
            entries.append(',').append(jarEntry("local-mods/m" + i + ".jar", 1));
            raw.put(RAW + "local-mods/m" + i + ".jar", new byte[] {1});
        }
        tree = "{\"tree\":[" + entries + "]}";
        downloadMillis = 20;

        packs.fetch(REPO, dir, progress -> { });

        assertTrue(mostRunning.get() > 1, "downloads overlap");
        assertTrue(mostRunning.get() <= GitHubPacks.PARALLEL_DOWNLOADS, "at most " + mostRunning.get());
        assertEquals(24, Files.list(dir.resolve("local-mods")).count());
    }

    @Test
    void aFailedDownloadFailsTheFetchWithItsCause() {
        raw.remove(RAW + "local-mods/mine.jar");
        GitHubPacks failing = new GitHubPacks(new GitHubApi(github), secrets, "github.token", (url, max) -> {
            if (url.endsWith("mine.jar")) {
                throw new SyncException(new SyncFailure.Network("raw.githubusercontent.com", "timeout"));
            }
            return downloader.get(url, max);
        });

        SyncException e = assertThrows(SyncException.class, () -> failing.fetch(REPO, dir, progress -> { }));

        assertEquals(new SyncFailure.Network("raw.githubusercontent.com", "timeout"), e.failure());
    }

    @Test
    void aFileThatArrivesWithAnotherSizeThanTheTreeSaysFails() {
        raw.put(RAW + "local-mods/mine.jar", "abc".getBytes(StandardCharsets.UTF_8));

        SyncException e = assertThrows(SyncException.class, () -> packs.fetch(REPO, dir, progress -> { }));

        assertEquals(new SyncFailure.SizeMismatch("local-mods/mine.jar", 9, 3), e.failure());
    }
}
