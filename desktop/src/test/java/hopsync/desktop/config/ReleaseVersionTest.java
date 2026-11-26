package hopsync.desktop.config;

import hopsync.platform.github.ReleaseFeed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {

    private static ReleaseVersion version(String tag) {
        return ReleaseVersion.parse(tag).orElseThrow();
    }

    private static ReleaseFeed.Release release(String tag) {
        return new ReleaseFeed.Release(tag, "https://github.com/owner/hopsync/releases/tag/" + tag,
            ReleaseVersion.parse(tag).map(ReleaseVersion::isTestBuild).orElse(false));
    }

    @Test
    void testBuildsComeBeforeTheirRelease() {
        List<String> ordered = List.of("26.2.0-a.1", "26.2.0-b.1", "26.2.0-b.2", "26.2.0-b.10",
            "26.2.0-rc.1", "26.2.0", "26.2.1-a.1", "26.2.1", "26.2.10", "26.10.0", "27.1.0");
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(version(ordered.get(i - 1)).compareTo(version(ordered.get(i))) < 0,
                ordered.get(i - 1) + " < " + ordered.get(i));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"b26.2.0", "rc26.2.0", "26.2.0-b", "26.2.0-beta.1", "beta-v26.1.4", "unknown", "26.2",
        "v26.2.0", ""})
    void tagsOutsideTheSchemeDoNotParse(String tag) {
        assertEquals(Optional.empty(), ReleaseVersion.parse(tag));
    }

    @Test
    void aReleaseCopyIsOnlyOfferedReleases() {
        List<ReleaseFeed.Release> releases = List.of(release("26.3.0-b.1"), release("26.2.1"), release("26.2.0"));

        assertEquals(Optional.of(release("26.2.1")), ReleaseVersion.newerThan("26.2.0", releases));
        assertEquals(Optional.empty(), ReleaseVersion.newerThan("26.2.1", releases));
    }

    @Test
    void aTestBuildIsAlsoOfferedNewerTestBuilds() {
        List<ReleaseFeed.Release> releases = List.of(release("26.3.0-b.1"), release("26.2.1"), release("26.2.0-b.1"));

        assertEquals(Optional.of(release("26.3.0-b.1")), ReleaseVersion.newerThan("26.2.0-b.1", releases));
        assertEquals(Optional.of(release("26.2.0")),
            ReleaseVersion.newerThan("26.2.0-rc.1", List.of(release("26.2.0"), release("26.2.0-rc.1"))));
        assertEquals(Optional.of(release("26.2.0-b.2")),
            ReleaseVersion.newerThan("26.2.0-b.1", List.of(release("26.2.0-b.2"), release("26.2.0-b.1"))));
    }

    @Test
    void aReleaseMarkedAsPrereleaseOnGitHubIsNotOfferedToReleaseCopies() {
        ReleaseFeed.Release marked = new ReleaseFeed.Release("26.2.1", "https://example.org/26.2.1", true);

        assertEquals(Optional.empty(), ReleaseVersion.newerThan("26.2.0", List.of(marked)));
    }

    @Test
    void aLocalBuildIsOfferedNothing() {
        assertEquals(Optional.empty(), ReleaseVersion.newerThan("unknown", List.of(release("26.2.1"))));
    }
}
