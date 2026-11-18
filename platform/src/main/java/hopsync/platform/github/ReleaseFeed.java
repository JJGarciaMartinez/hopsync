package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.util.Json;
import hopsync.core.util.JsonParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The releases of HopSync's own repository, to tell the player a newer version is out. Read-only and without an
 * account; requests go through the app's HTTP client, so they carry its user agent and retries.
 */
public class ReleaseFeed {
    static final String API = "https://api.github.com/repos/";
    /** Newest first; enough to find the latest stable release behind a few test builds. */
    static final int PAGE = 20;

    /** Fetches a text document; the app's {@code DownloadService::getUrlString}. */
    @FunctionalInterface
    public interface Fetcher {
        String get(String url) throws IOException;
    }

    /**
     * @param tag        the version tag, e.g. {@code 26.2.1} or {@code b26.2.1}
     * @param url        the release page
     * @param prerelease marked as a test build on GitHub
     */
    public record Release(String tag, String url, boolean prerelease) {
    }

    private final Fetcher fetcher;
    private final String repo;

    /** @param repo {@code owner/name}; blank turns the feed off */
    public ReleaseFeed(Fetcher fetcher, String repo) {
        this.fetcher = fetcher;
        this.repo = repo == null ? "" : repo.strip();
    }

    public boolean configured() {
        return repo.matches("[\\w.-]+/[\\w.-]+");
    }

    /** The published releases, newest first; drafts are left out. */
    public List<Release> releases() throws IOException {
        if (!configured()) return List.of();
        String url = API + repo + "/releases?per_page=" + PAGE;
        Object json;
        try {
            json = Json.parse(fetcher.get(url));
        } catch (JsonParseException e) {
            throw new SyncException(new SyncFailure.Unexpected(url + " answered something that is not JSON"), e);
        }
        if (!(json instanceof List<?> items)) {
            throw new SyncException(new SyncFailure.Unexpected(url + " did not answer a list of releases"));
        }
        List<Release> releases = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> release && !Boolean.TRUE.equals(release.get("draft"))
                    && release.get("tag_name") instanceof String tag && release.get("html_url") instanceof String page
                    && page.startsWith("https://")) {
                releases.add(new Release(tag, page, Boolean.TRUE.equals(release.get("prerelease"))));
            }
        }
        return releases;
    }
}
