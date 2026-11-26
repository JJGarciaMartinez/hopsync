package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseFeedTest {
    private final List<String> asked = new ArrayList<>();

    private ReleaseFeed feed(String repo, String answer) {
        return new ReleaseFeed(url -> {
            asked.add(url);
            return answer;
        }, repo);
    }

    @Test
    void listsPublishedReleasesNewestFirst() throws Exception {
        ReleaseFeed feed = feed("owner/hopsync", """
            [{"tag_name":"26.3.0-b.1","html_url":"https://github.com/owner/hopsync/releases/tag/26.3.0-b.1","prerelease":true,"draft":false},
             {"tag_name":"26.3.0-wip","html_url":"https://github.com/owner/hopsync/releases/tag/wip","prerelease":false,"draft":true},
             {"tag_name":"26.2.0","html_url":"https://github.com/owner/hopsync/releases/tag/26.2.0","prerelease":false,"draft":false}]
            """);

        assertEquals(List.of(
            new ReleaseFeed.Release("26.3.0-b.1", "https://github.com/owner/hopsync/releases/tag/26.3.0-b.1", true),
            new ReleaseFeed.Release("26.2.0", "https://github.com/owner/hopsync/releases/tag/26.2.0", false)),
            feed.releases());
        assertEquals(List.of(ReleaseFeed.API + "owner/hopsync/releases?per_page=" + ReleaseFeed.PAGE), asked);
    }

    @Test
    void releasesWithoutAnHttpsPageAreSkipped() throws Exception {
        ReleaseFeed feed = feed("owner/hopsync", """
            [{"tag_name":"26.2.0","html_url":"http://example.org/26.2.0","prerelease":false}]
            """);

        assertEquals(List.of(), feed.releases());
    }

    @Test
    void aBlankRepoAsksNothing() throws Exception {
        ReleaseFeed feed = feed(" ", "[]");

        assertFalse(feed.configured());
        assertEquals(List.of(), feed.releases());
        assertEquals(List.of(), asked);
    }

    @Test
    void anAnswerThatIsNotAListFails() {
        assertThrows(SyncException.class, () -> feed("owner/hopsync", "{\"message\":\"Not Found\"}").releases());
        assertThrows(SyncException.class, () -> feed("owner/hopsync", "<html>").releases());
    }
}
