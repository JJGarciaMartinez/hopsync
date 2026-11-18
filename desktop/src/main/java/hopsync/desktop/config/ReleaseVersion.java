package hopsync.desktop.config;

import hopsync.platform.github.ReleaseFeed;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version tag in HopSync's scheme, {@code [a|b|rc]YY.MAJOR.FIXES} (see VERSION_GUIDE.md), ordered so that the test
 * builds of a number come before its release: {@code a26.2.0 < b26.2.0 < rc26.2.0 < 26.2.0 < a26.2.1}.
 *
 * @param stage 0 = a, 1 = b, 2 = rc, 3 = release
 */
public record ReleaseVersion(int year, int major, int fixes, int stage) implements Comparable<ReleaseVersion> {
    private static final Pattern TAG = Pattern.compile("^(a|b|rc)?(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final List<String> TEST_STAGES = List.of("a", "b", "rc");
    private static final int RELEASE = 3;
    private static final Comparator<ReleaseVersion> ORDER = Comparator.comparingInt(ReleaseVersion::year)
        .thenComparingInt(ReleaseVersion::major).thenComparingInt(ReleaseVersion::fixes)
        .thenComparingInt(ReleaseVersion::stage);

    /** Empty for anything outside the scheme, such as the old {@code beta-v26.1.4} tags or {@code unknown}. */
    public static Optional<ReleaseVersion> parse(String tag) {
        Matcher m = TAG.matcher(tag == null ? "" : tag.strip());
        if (!m.matches()) return Optional.empty();
        try {
            int stage = m.group(1) == null ? RELEASE : TEST_STAGES.indexOf(m.group(1));
            return Optional.of(new ReleaseVersion(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), stage));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean isTestBuild() {
        return stage != RELEASE;
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        return ORDER.compare(this, other);
    }

    /**
     * The newest release worth offering to a player on {@code current}: a release copy is only offered releases,
     * a test build also newer test builds. Empty when {@code current} is outside the scheme (a local build).
     */
    public static Optional<ReleaseFeed.Release> newerThan(String current, List<ReleaseFeed.Release> releases) {
        Optional<ReleaseVersion> running = parse(current);
        if (running.isEmpty()) return Optional.empty();
        boolean testBuild = running.get().isTestBuild();
        ReleaseFeed.Release newest = null;
        ReleaseVersion newestVersion = running.get();
        for (ReleaseFeed.Release release : releases) {
            Optional<ReleaseVersion> version = parse(release.tag());
            if (version.isEmpty() || (!testBuild && (release.prerelease() || version.get().isTestBuild()))) continue;
            if (version.get().compareTo(newestVersion) > 0) {
                newest = release;
                newestVersion = version.get();
            }
        }
        return Optional.ofNullable(newest);
    }
}
