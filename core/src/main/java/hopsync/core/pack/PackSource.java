package hopsync.core.pack;

import java.util.Objects;
import java.util.Optional;

/** Where a pack's content comes from: a manifest someone publishes, or a definition the player keeps. */
public sealed interface PackSource {

    /** The link friends use to follow the pack, if there is one. */
    Optional<String> link();

    /** A pack published by someone else: its https manifest link. */
    record Link(String url) implements PackSource {
        public Link {
            Objects.requireNonNull(url, "url");
        }

        @Override
        public Optional<String> link() {
            return Optional.of(url);
        }
    }

    /**
     * A pack the player created; its {@link PackDefinition} is kept by HopSync under the pack's id.
     *
     * @param publication where it is published, or {@code null} while it is only on this computer
     */
    record Local(Publication publication) implements PackSource {
        @Override
        public Optional<String> link() {
            return Optional.ofNullable(publication).map(Publication::url);
        }
    }

    /**
     * A created pack once published.
     *
     * @param repo GitHub repository as {@code owner/name}
     * @param url  https link of the manifest the repository publishes
     */
    record Publication(String repo, String url) {
        public Publication {
            Objects.requireNonNull(repo, "repo");
            Objects.requireNonNull(url, "url");
        }
    }
}
