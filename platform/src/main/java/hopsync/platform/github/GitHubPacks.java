package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.PackSource;
import hopsync.core.port.PackOwnership;
import hopsync.core.port.PackRepository;
import hopsync.core.port.SecretStore;
import hopsync.core.publish.RemotePack;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    /** GitHub answers a blob as JSON with the content in base64, so a big file is held in memory several times. */
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    /** shortcut: only the 100 most recently pushed repositories are looked at; page through them if players have more. */
    static final int REPOS_LOOKED_AT = 100;

    private final GitHubApi api;
    private final SecretStore secrets;
    private final String tokenKey;

    /** @param tokenKey the keychain entry holding the player's GitHub token */
    public GitHubPacks(GitHubApi api, SecretStore secrets, String tokenKey) {
        this.api = api;
        this.secrets = secrets;
        this.tokenKey = tokenKey;
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
    public RemotePack fetch(String repo, Path into) throws IOException {
        String token = signedInToken().orElseThrow(() -> new SyncException(new SyncFailure.GitHubSignedOut()));
        if (!REPO.matcher(repo).matches()) {
            throw new SyncException(new SyncFailure.NotAHopSyncRepo(repo));
        }
        if (!ownerOf(repo).equalsIgnoreCase(login(token))) {
            throw new SyncException(new SyncFailure.NotRepoOwner(repo));
        }
        GitHubApi.Answer answer = api.call("GET", "/repos/" + repo + "/git/trees/" + GitHubPublisher.BRANCH
            + "?recursive=1", null, token);
        if (answer.status() == 404) {
            throw new SyncException(new SyncFailure.NotAHopSyncRepo(repo));
        }
        Map<?, ?> tree = answer.object();
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
                files.add(entry);
            }
        }
        if (tomlEntry == null) {
            throw new SyncException(new SyncFailure.NotAHopSyncRepo(repo));
        }
        String toml = new String(blob(repo, tomlEntry, token), StandardCharsets.UTF_8);
        for (Map<?, ?> entry : files) {
            String path = String.valueOf(entry.get("path"));
            if (!SafePaths.isSafeRelativePath(path)) {
                throw new SyncException(new SyncFailure.UnsafePath(path));
            }
            Path target = SafePaths.resolveInside(into, path);
            Files.createDirectories(target.getParent());
            Files.write(target, blob(repo, entry, token));
        }
        return new RemotePack(toml, new PackSource.Publication(repo, GitHubPublisher.manifestUrl(repo), ""));
    }

    private byte[] blob(String repo, Map<?, ?> entry, String token) throws IOException {
        String path = String.valueOf(entry.get("path"));
        if (entry.get("size") instanceof Number size && size.longValue() > MAX_FILE_BYTES) {
            throw new SyncException(new SyncFailure.ResponseTooLarge(
                "https://github.com/" + repo + "/blob/" + GitHubPublisher.BRANCH + "/" + path, MAX_FILE_BYTES));
        }
        String content = api.call("GET", "/repos/" + repo + "/git/blobs/" + entry.get("sha"), null, token)
            .text("content");
        try {
            return Base64.getMimeDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new SyncException(new SyncFailure.Unexpected(repo + "/" + path + " is not valid base64"), e);
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
