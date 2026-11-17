package hopsync.core.publish;

import hopsync.core.pack.PackSource;

import java.nio.file.Path;
import java.util.Map;

/**
 * Everything a publish of a created pack sends.
 *
 * @param packName    the pack's name, for the repository's description and README
 * @param repoName    the repository to create on a first publish; ignored when updating
 * @param existing    where the pack is already published, or {@code null} on a first publish
 * @param packToml    the pack repository's {@code hopsync.toml}
 * @param files       the pack's own files by their path in the repository ({@code configs/…}, {@code local-mods/…})
 * @param contentHash fingerprint of what is published, kept to tell later whether the pack changed since
 */
public record PublishRequest(String packName, String repoName, PackSource.Publication existing, String packToml,
                             Map<String, Path> files, String contentHash) {
    public PublishRequest {
        files = Map.copyOf(files);
    }
}
