package hopsync.core.port;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Which published packs belong to the player: the ones in repositories of their own GitHub account. */
public interface PackOwnership {

    /**
     * The player's repositories that hold a HopSync pack, as {@code owner/name}.
     *
     * @throws hopsync.core.failure.SyncException with {@code GitHubSignedOut} when the player is not signed in
     */
    List<String> ownedRepos() throws IOException;

    /**
     * The repository a pack link comes from, if the link is the one a HopSync publish produces and the repository is
     * the signed-in player's. Empty for any other link and when the player is not signed in.
     */
    Optional<String> ownedRepoOf(String manifestUrl) throws IOException;
}
