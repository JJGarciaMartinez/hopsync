package hopsync.core.failure;

/**
 * Why a sync step failed, as data. The UI turns each case into a message and an action for the player;
 * {@link #describe()} is the English technical description used in logs.
 */
public sealed interface SyncFailure {

    /** No pack has been chosen yet. */
    record NotConfigured() implements SyncFailure {
        public String describe() { return "no pack configured"; }
    }

    /** A manifest link that is not an https URL. */
    record InvalidUrl(String url) implements SyncFailure {
        public String describe() { return "not an https URL: " + url; }
    }

    /** The server could not be reached: no connection, DNS failure, timeout. */
    record Network(String host, String detail) implements SyncFailure {
        public String describe() { return "cannot reach " + host + ": " + detail; }
    }

    /** The server answered with an HTTP error. */
    record HttpStatus(int status, String url) implements SyncFailure {
        public String describe() { return "HTTP " + status + " for " + url; }
    }

    /** A response larger than the app accepts for that kind of request. */
    record ResponseTooLarge(String url, long limitBytes) implements SyncFailure {
        public String describe() { return "response from " + url + " exceeds " + limitBytes + " bytes"; }
    }

    /** The manifest is not valid JSON or breaks the manifest format. */
    record ManifestInvalid(String detail) implements SyncFailure {
        public String describe() { return "invalid manifest: " + detail; }
    }

    /** A mod a created pack lists is no longer in the catalog, or its own jar is gone. */
    record ModUnavailable(String mod) implements SyncFailure {
        public String describe() { return "mod no longer available: " + mod; }
    }

    /** What HopSync keeps of a created pack is missing or cannot be read. */
    record DefinitionMissing(String packId) implements SyncFailure {
        public String describe() { return "pack definition missing or unreadable: " + packId; }
    }

    /** This build has no GitHub app to sign in with. */
    record GitHubNotConfigured() implements SyncFailure {
        public String describe() { return "no GitHub OAuth client id configured"; }
    }

    /** The player did not enter the GitHub code in time. */
    record GitHubCodeExpired() implements SyncFailure {
        public String describe() { return "the GitHub device code expired"; }
    }

    /** The player declined HopSync's access on GitHub. */
    record GitHubDenied() implements SyncFailure {
        public String describe() { return "GitHub access was denied"; }
    }

    /** The GitHub token is gone or no longer valid. */
    record GitHubSignedOut() implements SyncFailure {
        public String describe() { return "not signed in to GitHub, or the token was revoked"; }
    }

    /** The player already has a repository with the name chosen for a new pack. */
    record RepoNameTaken(String name) implements SyncFailure {
        public String describe() { return "a repository named " + name + " already exists"; }
    }

    /** The pack repository's build did not publish the manifest; the page shows why. */
    record PublishBuildFailed(String url) implements SyncFailure {
        public String describe() { return "the pack repository's build failed: " + url; }
    }

    /** The manifest uses a format version this build does not understand. */
    record UnsupportedFormat(long found, int supported) implements SyncFailure {
        public String describe() { return "manifest format " + found + " is not supported (expected " + supported + ")"; }
    }

    /** A downloaded file does not match the hash published in the manifest. */
    record HashMismatch(String file) implements SyncFailure {
        public String describe() { return "SHA-512 mismatch for " + file; }
    }

    /** A downloaded file does not have the size published in the manifest. */
    record SizeMismatch(String file, long expected, long actual) implements SyncFailure {
        public String describe() { return "size mismatch for " + file + ": " + actual + " bytes, expected " + expected; }
    }

    /** A manifest path that would escape the game folder. */
    record UnsafePath(String path) implements SyncFailure {
        public String describe() { return "path not allowed: " + path; }
    }

    /** A folder HopSync needs does not exist and cannot be created. */
    record FolderUnavailable(String path) implements SyncFailure {
        public String describe() { return "cannot create folder " + path; }
    }

    /** Another HopSync process is already updating this game folder. */
    record FolderInUse(String path) implements SyncFailure {
        public String describe() { return "another HopSync process is updating " + path; }
    }

    /** The game folder is, contains or is inside the folder of another pack. */
    record FolderUsedByPack(String path, String packName) implements SyncFailure {
        public String describe() { return path + " overlaps the folder of pack " + packName; }
    }

    /** The disk ran out of space while writing. */
    record DiskFull(String path) implements SyncFailure {
        public String describe() { return "no space left while writing " + path; }
    }

    /** The file is open in another program (usually the game on Windows). */
    record FileLocked(String path) implements SyncFailure {
        public String describe() { return "file in use by another program: " + path; }
    }

    /** The operating system refused access to a file or folder. */
    record PermissionDenied(String path) implements SyncFailure {
        public String describe() { return "permission denied: " + path; }
    }

    /** Anything not covered above; the detail is for the log only. */
    record Unexpected(String detail) implements SyncFailure {
        public String describe() { return "unexpected error: " + detail; }
    }

    String describe();
}
