package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.PackSource;
import hopsync.core.port.PackPublisher;
import hopsync.core.port.SecretStore;
import hopsync.core.publish.PublishRequest;
import hopsync.core.publish.PublishStep;
import hopsync.core.util.CancellationToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Publishes created packs on GitHub: a first publish creates a public repository from the HopSync pack template
 * in the player's account, then every publish writes {@code hopsync.toml}, the README, the configs and own jars in
 * one commit to {@code main} and waits for the repository's build to put the manifest in its {@code hopsync}
 * release. The template's demo content and its weekly update workflow are removed: the pack is edited in HopSync.
 */
public class GitHubPublisher implements PackPublisher {
    private static final System.Logger LOG = System.getLogger(GitHubPublisher.class.getName());
    static final String RELEASE_TAG = "hopsync";
    static final String MANIFEST_ASSET = "manifest.json";
    private static final Pattern UNSAFE_ASSET_CHARS = Pattern.compile("[^A-Za-z0-9._-]+");
    /** The template every pack repository starts from. */
    static final String TEMPLATE = "JJGarciaMartinez/hopsync-demo";
    static final String BRANCH = "main";
    /** Template files that are not this pack's: the demo's manifest and the workflow that bumps mods on GitHub. */
    static final Set<String> TEMPLATE_ONLY = Set.of("manifest.json", ".github/workflows/check-updates.yml");
    private static final Duration REPOSITORY_WAIT = Duration.ofSeconds(60);
    private static final Duration BUILD_START_WAIT = Duration.ofMinutes(2);
    private static final Duration BUILD_WAIT = Duration.ofMinutes(15);
    private static final Duration POLL = Duration.ofSeconds(3);

    private final GitHubApi api;
    private final SecretStore secrets;
    private final String tokenKey;
    private final GitHubAuth.Sleeper sleeper;

    /** @param tokenKey the keychain entry holding the player's GitHub token */
    public GitHubPublisher(GitHubApi api, SecretStore secrets, String tokenKey) {
        this(api, secrets, tokenKey, GitHubAuth::sleepUnlessCancelled);
    }

    GitHubPublisher(GitHubApi api, SecretStore secrets, String tokenKey, GitHubAuth.Sleeper sleeper) {
        this.api = api;
        this.secrets = secrets;
        this.tokenKey = tokenKey;
        this.sleeper = sleeper;
    }

    /** The link friends use for a pack published in this repository. */
    public static String manifestUrl(String repo) {
        return "https://github.com/" + repo + "/releases/download/hopsync/manifest.json";
    }

    @Override
    public PackSource.Publication publish(PublishRequest request, Consumer<PublishStep> progress,
                                          CancellationToken cancel) throws IOException {
        String token = secrets.read(tokenKey).orElseThrow(() -> new SyncException(new SyncFailure.GitHubSignedOut()));
        String repo;
        if (request.existing() == null) {
            progress.accept(PublishStep.CREATING_REPOSITORY);
            repo = createRepository(request, token, cancel);
        } else {
            repo = request.existing().repo();
        }
        cancel.throwIfCancelled();
        progress.accept(PublishStep.UPLOADING);
        String commit = commit(repo, request, token, cancel);
        if (commit != null) {
            progress.accept(PublishStep.BUILDING);
            awaitBuild(repo, commit, token, cancel);
        }
        pruneRelease(repo, request, token);
        return new PackSource.Publication(repo, manifestUrl(repo), request.contentHash());
    }

    private String createRepository(PublishRequest request, String token, CancellationToken cancel) throws IOException {
        String login = api.call("GET", "/user", null, token).text("login");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("owner", login);
        body.put("name", request.repoName());
        body.put("description", "Modpack de Minecraft «" + request.packName() + "», publicado con HopSync");
        body.put("private", false);
        body.put("include_all_branches", false);
        GitHubApi.Answer created = api.call("POST", "/repos/" + TEMPLATE + "/generate", body, token);
        if (created.status() == 422) {
            throw new SyncException(new SyncFailure.RepoNameTaken(request.repoName()));
        }
        String repo = created.text("full_name");
        // GitHub copies the template in the background; its branch shows up a few seconds later
        long until = System.nanoTime() + REPOSITORY_WAIT.toNanos();
        while (!api.call("GET", "/repos/" + repo + "/git/ref/heads/" + BRANCH, null, token).ok()) {
            if (System.nanoTime() > until) {
                throw new SyncException(new SyncFailure.Unexpected("the new repository " + repo + " has no branch yet"));
            }
            sleeper.sleep(POLL, cancel);
        }
        return repo;
    }

    /**
     * Writes the pack in one commit on top of {@code main}: the toml, the README, every file of the request, and
     * the removal of configs, jars and template files the pack no longer has.
     *
     * @return the new commit, or {@code null} when the repository already holds exactly this pack
     */
    private String commit(String repo, PublishRequest request, String token, CancellationToken cancel)
        throws IOException {
        String head = ((Map<?, ?>) api.call("GET", "/repos/" + repo + "/git/ref/heads/" + BRANCH, null, token)
            .object().get("object")).get("sha").toString();
        String baseTree = ((Map<?, ?>) api.call("GET", "/repos/" + repo + "/git/commits/" + head, null, token)
            .object().get("tree")).get("sha").toString();
        Map<?, ?> tree = api.call("GET", "/repos/" + repo + "/git/trees/" + baseTree + "?recursive=1", null, token)
            .object();

        List<Object> entries = new ArrayList<>();
        entries.add(inline("hopsync.toml", request.packToml()));
        entries.add(inline("README.md", readme(request.packName(), repo)));
        for (Map.Entry<String, Path> file : request.files().entrySet()) {
            cancel.throwIfCancelled();
            entries.add(blob(repo, file.getKey(), file.getValue(), token));
        }
        for (Object item : (List<?>) tree.get("tree")) {
            Map<?, ?> existing = (Map<?, ?>) item;
            String path = String.valueOf(existing.get("path"));
            boolean packFile = path.startsWith("configs/") || path.startsWith("game-files/") || path.startsWith("local-mods/");
            if ("blob".equals(existing.get("type"))
                    && (TEMPLATE_ONLY.contains(path) || packFile && !request.files().containsKey(path))) {
                Map<String, Object> removal = new HashMap<>();
                removal.put("path", path);
                removal.put("mode", "100644");
                removal.put("type", "blob");
                removal.put("sha", null);
                entries.add(removal);
            }
        }
        Map<String, Object> treeBody = new LinkedHashMap<>();
        treeBody.put("base_tree", baseTree);
        treeBody.put("tree", entries);
        String newTree = api.call("POST", "/repos/" + repo + "/git/trees", treeBody, token).text("sha");
        if (newTree.equals(baseTree)) {
            return null;
        }
        Map<String, Object> commitBody = new LinkedHashMap<>();
        commitBody.put("message", (request.existing() == null ? "Publish " : "Update ") + request.packName()
            + " from HopSync");
        commitBody.put("tree", newTree);
        commitBody.put("parents", List.of(head));
        String commit = api.call("POST", "/repos/" + repo + "/git/commits", commitBody, token).text("sha");
        api.call("PATCH", "/repos/" + repo + "/git/refs/heads/" + BRANCH, Map.of("sha", commit), token).object();
        return commit;
    }

    private static Map<String, Object> inline(String path, String content) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("mode", "100644");
        entry.put("type", "blob");
        entry.put("content", content);
        return entry;
    }

    /** Uploads a file as a blob first: jars are binary, and the tree's inline content is text only. */
    private Map<String, Object> blob(String repo, String path, Path file, String token) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        body.put("encoding", "base64");
        String sha = api.call("POST", "/repos/" + repo + "/git/blobs", body, token).text("sha");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("mode", "100644");
        entry.put("type", "blob");
        entry.put("sha", sha);
        return entry;
    }

    /** Waits for the build the commit started; a failed or missing build fails with its page. */
    private void awaitBuild(String repo, String commit, String token, CancellationToken cancel) throws IOException {
        long started = System.nanoTime();
        while (true) {
            sleeper.sleep(POLL, cancel);
            Map<?, ?> runs = api.call("GET", "/repos/" + repo + "/actions/runs?head_sha=" + commit + "&event=push",
                null, token).object();
            List<?> found = runs.get("workflow_runs") instanceof List<?> list ? list : List.of();
            Map<?, ?> run = found.stream().map(item -> (Map<?, ?>) item)
                .filter(item -> String.valueOf(item.get("path")).endsWith("build.yml"))
                .findFirst().orElse(null);
            long waited = System.nanoTime() - started;
            if (run == null) {
                if (waited > BUILD_START_WAIT.toNanos()) {
                    throw new SyncException(new SyncFailure.PublishBuildFailed("https://github.com/" + repo + "/actions"));
                }
                continue;
            }
            if ("completed".equals(run.get("status"))) {
                if ("success".equals(run.get("conclusion"))) return;
                throw new SyncException(new SyncFailure.PublishBuildFailed(String.valueOf(run.get("html_url"))));
            }
            if (waited > BUILD_WAIT.toNanos()) {
                throw new SyncException(new SyncFailure.PublishBuildFailed(String.valueOf(run.get("html_url"))));
            }
        }
    }

    /**
     * Deletes the release assets no file of the pack maps to, such as the jars of the template's demo that its first
     * build uploaded before they were removed: the build only ever adds assets. Best effort, and it does nothing
     * unless every file of the pack already has its asset, so a build that has not finished never costs a file.
     */
    private void pruneRelease(String repo, PublishRequest request, String token) {
        try {
            GitHubApi.Answer release = api.call("GET", "/repos/" + repo + "/releases/tags/" + RELEASE_TAG, null, token);
            if (!release.ok() || !(release.object().get("assets") instanceof List<?> assets)) return;

            Set<String> wanted = new HashSet<>();
            wanted.add(MANIFEST_ASSET);
            for (Map.Entry<String, Path> file : request.files().entrySet()) {
                wanted.add(assetName(file.getKey(), file.getValue()));
            }
            Set<String> present = new HashSet<>();
            for (Object item : assets) present.add(String.valueOf(((Map<?, ?>) item).get("name")));
            if (!present.containsAll(wanted)) return;

            for (Object item : assets) {
                Map<?, ?> asset = (Map<?, ?>) item;
                if (wanted.contains(String.valueOf(asset.get("name")))) continue;
                api.call("DELETE", "/repos/" + repo + "/releases/assets/" + asset.get("id"), null, token);
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not clean the release of " + repo, e);
        }
    }

    /**
     * The name {@code scripts/hopsync.py} gives a file's release asset: the first 10 hex digits of its SHA-512, then
     * the jar's name, the config's path under {@code configs/} or a game file's whole path, flattened.
     */
    static String assetName(String packPath, Path file) throws IOException {
        String relative = packPath.startsWith("configs/") ? packPath.substring("configs/".length())
            : packPath.startsWith("game-files/") ? packPath : packPath.substring(packPath.lastIndexOf('/') + 1);
        try {
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(file)));
            return sha.substring(0, 10) + "-" + UNSAFE_ASSET_CHARS.matcher(relative.replace("/", "__")).replaceAll("_");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The pack repository's README: how to follow the pack, and the notice about its content. */
    static String readme(String packName, String repo) throws IOException {
        try (InputStream in = GitHubPublisher.class.getResourceAsStream("pack-readme.md")) {
            if (in == null) throw new IOException("pack-readme.md is missing from the app");
            // A checkout on Windows may turn the template's line ends into CRLF; the README keeps LF everywhere
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replace("{name}", packName)
                .replace("{link}", manifestUrl(repo));
        }
    }
}
