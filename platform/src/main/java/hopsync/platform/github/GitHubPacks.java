package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.PackSource;
import hopsync.core.port.PackOwnership;
import hopsync.core.port.PackRepository;
import hopsync.core.port.SecretStore;
import hopsync.core.publish.FetchProgress;
import hopsync.core.publish.RemotePack;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads back the packs the player published from their own GitHub repositories. A pack is the player's when the
 * repository belongs to the signed-in account (compared by the owner in its name, never by write access, so a
 * collaborator on someone else's pack does not get it) and holds a {@code hopsync.toml}.
 */
public class GitHubPacks implements PackRepository, PackOwnership {
    /** A repository name as {@code owner/name}, safe to put in an API path. */
    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9-]+/[A-Za-z0-9._-]+");
    private static final Pattern LINK = Pattern.compile(
        "https://github\\.com/([A-Za-z0-9-]+/[A-Za-z0-9._-]+)/releases/download/" + GitHubPublisher.RELEASE_TAG
            + "/" + Pattern.quote(GitHubPublisher.MANIFEST_ASSET));
    /** A file of the pack is held in memory while it downloads, up to this size. */
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    /** Packs can hold hundreds of own jars, so they download in parallel. */
    static final int PARALLEL_DOWNLOADS = 4;
    private static final String RAW = "https://raw.githubusercontent.com/";
    /** shortcut: only the 100 most recently pushed repositories are looked at; page through them if players have more. */
    static final int REPOS_LOOKED_AT = 100;

    /** Downloads a public file of at most {@code maxBytes}, with retries; the app's download service. */
    @FunctionalInterface
    public interface Downloader {
        byte[] get(String url, long maxBytes) throws IOException;
    }

    private final GitHubApi api;
    private final SecretStore secrets;
    private final String tokenKey;
    private final Downloader downloader;

    /**
     * @param tokenKey   the keychain entry holding the player's GitHub token
     * @param downloader fetches the pack's files from {@code raw.githubusercontent.com}: pack repositories are
     *                   public, and a plain download streams, unlike a blob wrapped in JSON as base64
     */
    public GitHubPacks(GitHubApi api, SecretStore secrets, String tokenKey, Downloader downloader) {
        this.api = api;
        this.secrets = secrets;
        this.tokenKey = tokenKey;
        this.downloader = downloader;
    }

    @Override
    public List<String> ownedRepos() throws IOException {
        String token = signedInToken().orElseThrow(() -> new SyncException(new SyncFailure.GitHubSignedOut()));
        List<?> repos = api.call("GET", "/user/repos?affiliation=owner&sort=pushed&per_page=" + REPOS_LOOKED_AT,
            null, token).array();
        List<String> found = new ArrayList<>();
        for (Object item : repos) {
            Map<?, ?> repo = (Map<?, ?>) item;
            if (Boolean.TRUE.equals(repo.get("fork")) || Boolean.TRUE.equals(repo.get("archived"))
                    || !(repo.get("full_name") instanceof String name) || !REPO.matcher(name).matches()) {
                continue;
            }
            GitHubApi.Answer toml = api.call("GET", "/repos/" + name + "/contents/hopsync.toml", null, token);
            if (toml.status() == 404) {
                continue;
            }
            toml.object();
            found.add(name);
        }
        return found;
    }

    @Override
    public Optional<String> ownedRepoOf(String manifestUrl) throws IOException {
        Matcher link = LINK.matcher(manifestUrl);
        Optional<String> token = signedInToken();
        if (!link.matches() || token.isEmpty()) {
            return Optional.empty();
        }
        String repo = link.group(1);
        return ownerOf(repo).equalsIgnoreCase(login(token.get())) ? Optional.of(repo) : Optional.empty();
    }

    @Override
    public RemotePack fetch(String repo, Path into, Consumer<FetchProgress> progress) throws IOException {
        String token = signedInToken().orElseThrow(() -> new SyncException(new SyncFailure.GitHubSignedOut()));
        if (!REPO.matcher(repo).matches()) {
            throw new SyncException(new SyncFailure.NotAHopSyncRepo(repo));
        }
        if (!ownerOf(repo).equalsIgnoreCase(login(token))) {
            throw new SyncException(new SyncFailure.NotRepoOwner(repo));
        }
        // Everything is read at one commit, so a publish in the middle cannot mix two versions of the pack
        GitHubApi.Answer ref = api.call("GET", "/repos/" + repo + "/git/ref/heads/" + GitHubPublisher.BRANCH, null, token);
        if (ref.status() == 404) {
            throw new SyncException(new SyncFailure.NotAHopSyncRepo(repo));
        }
        String commit = ((Map<?, ?>) ref.object().get("object")).get("sha").toString();
        Map<?, ?> tree = api.call("GET", "/repos/" + repo + "/git/trees/" + commit + "?recursive=1", null, token).object();
        if (Boolean.TRUE.equals(tree.get("truncated"))) {
            throw new SyncException(new SyncFailure.Unexpected("the file list of " + repo + " is too long to read"));
        }

        Map<?, ?> tomlEntry = null;
        List<Map<?, ?>> files = new ArrayList<>();
        for (Object item : (List<?>) tree.get("tree")) {
            Map<?, ?> entry = (Map<?, ?>) item;
            if (!"blob".equals(entry.get("type"))) {
                continue;
            }
            String path = String.valueOf(entry.get("path"));
            if (path.equals("hopsync.toml")) {
                tomlEntry = entry;
            } else if (path.startsWith("configs/")
                    || path.startsWith("local-mods/") && path.indexOf('/', "local-mods/".length()) < 0) {
                if (!SafePaths.isSafeRelativePath(path)) {
                    throw new SyncException(new SyncFailure.UnsafePath(path));
                }
                if (entry.get("size") instanceof Number size && size.longValue() > MAX_FILE_BYTES) {
                    throw new SyncException(new SyncFailure.ResponseTooLarge(rawUrl(repo, commit, path), MAX_FILE_BYTES));
                }
                files.add(entry);
            }
        }
        if (tomlEntry == null) {
            throw new SyncException(new SyncFailure.NotAHopSyncRepo(repo));
        }
        String toml = new String(blob(repo, tomlEntry, token), StandardCharsets.UTF_8);
        download(repo, commit, files, into, progress);
        return new RemotePack(toml, new PackSource.Publication(repo, GitHubPublisher.manifestUrl(repo), ""));
    }

    /** Downloads the files {@link #PARALLEL_DOWNLOADS} at a time; the first failure stops the rest. */
    private void download(String repo, String commit, List<Map<?, ?>> files, Path into,
                          Consumer<FetchProgress> progress) throws IOException {
        int total = files.size();
        progress.accept(new FetchProgress(0, total));
        Object lock = new Object();
        int[] done = {0};
        Semaphore slots = new Semaphore(PARALLEL_DOWNLOADS);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> pending = new ArrayList<>();
            for (Map<?, ?> entry : files) {
                pending.add(executor.submit(() -> {
                    slots.acquire();
                    try {
                        save(repo, commit, entry, into);
                    } finally {
                        slots.release();
                    }
                    synchronized (lock) {
                        progress.accept(new FetchProgress(++done[0], total));
                    }
                    return null;
                }));
            }
            try {
                for (Future<?> file : pending) {
                    file.get();
                }
            } catch (ExecutionException e) {
                executor.shutdownNow();
                if (e.getCause() instanceof IOException io) throw io;
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new IOException("Could not download the pack's files", e.getCause());
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading the pack's files", e);
            }
        }
    }

    private void save(String repo, String commit, Map<?, ?> entry, Path into) throws IOException {
        String path = String.valueOf(entry.get("path"));
        byte[] data = downloader.get(rawUrl(repo, commit, path), MAX_FILE_BYTES);
        if (entry.get("size") instanceof Number size && size.longValue() != data.length) {
            throw new SyncException(new SyncFailure.SizeMismatch(path, size.longValue(), data.length));
        }
        Path target = SafePaths.resolveInside(into, path);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
    }

    /** The file at that commit, each path segment percent-encoded: jar names hold spaces, brackets and plus signs. */
    static String rawUrl(String repo, String commit, String path) {
        StringBuilder url = new StringBuilder(RAW).append(repo).append('/').append(commit);
        for (String segment : path.split("/")) {
            url.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return url.toString();
    }

    /** The repository's {@code hopsync.toml}, which is small: GitHub's blob answer is JSON with the file in base64. */
    private byte[] blob(String repo, Map<?, ?> entry, String token) throws IOException {
        String content = api.call("GET", "/repos/" + repo + "/git/blobs/" + entry.get("sha"), null, token)
            .text("content");
        try {
            return Base64.getMimeDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new SyncException(new SyncFailure.Unexpected(repo + "/hopsync.toml is not valid base64"), e);
        }
    }

    private Optional<String> signedInToken() throws IOException {
        return secrets.read(tokenKey);
    }

    private String login(String token) throws IOException {
        return api.call("GET", "/user", null, token).text("login");
    }

    private static String ownerOf(String repo) {
        return repo.substring(0, repo.indexOf('/'));
    }
}
