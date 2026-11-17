package hopsync.core.usecase;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSource;
import hopsync.core.pack.PackToml;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackPublisher;
import hopsync.core.publish.PublishRequest;
import hopsync.core.publish.PublishStep;
import hopsync.core.sync.FileHasher;
import hopsync.core.util.CancellationToken;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Publishes a pack the player created: its {@code hopsync.toml}, configs and own jars go to the publisher, and the
 * pack remembers where it went and what it sent, so the app can tell when it has changes to publish.
 */
public final class PackPublishing {
    private final PackRegistry registry;
    private final PackDefinitionRepository definitions;
    private final PackPublisher publisher;

    public PackPublishing(PackRegistry registry, PackDefinitionRepository definitions, PackPublisher publisher) {
        this.registry = registry;
        this.definitions = definitions;
        this.publisher = publisher;
    }

    /** What a publish of the pack would send, without sending it. */
    public PublishRequest request(Pack pack, String repoName) throws IOException {
        PackDefinition definition = registry.definition(pack.id())
            .orElseThrow(() -> new SyncException(new SyncFailure.DefinitionMissing(pack.id())));
        Map<String, Path> files = files(definition, definitions.folder(pack.id()));
        String toml = PackToml.write(definition);
        PackSource.Publication existing = pack.source() instanceof PackSource.Local local ? local.publication() : null;
        return new PublishRequest(definition.pack().name(), repoName, existing, toml, files,
            fingerprint(toml, files));
    }

    /** Whether a published pack changed since its last publish; {@code false} for one never published. */
    public boolean changedSincePublished(Pack pack) throws IOException {
        if (!(pack.source() instanceof PackSource.Local(PackSource.Publication publication)) || publication == null) {
            return false;
        }
        return !request(pack, null).contentHash().equals(publication.contentHash());
    }

    /** Publishes the pack and records where; the next check of the pack still resolves it from its definition. */
    public PackSource.Publication publish(Pack pack, String repoName, Consumer<PublishStep> progress,
                                          CancellationToken cancel) throws IOException {
        PackSource.Publication publication = publisher.publish(request(pack, repoName), progress, cancel);
        registry.published(pack.id(), publication);
        return publication;
    }

    /** The configs and the player's own jars, by their path in a pack repository. */
    private static Map<String, Path> files(PackDefinition definition, Path folder) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        Path configs = folder.resolve(DefinitionResolver.CONFIGS);
        if (Files.isDirectory(configs)) {
            try (Stream<Path> found = Files.walk(configs)) {
                for (Path file : found.filter(Files::isRegularFile).toList()) {
                    String relative = configs.relativize(file).toString().replace('\\', '/');
                    if (SafePaths.isSafeRelativePath(relative)) files.put("configs/" + relative, file);
                }
            }
        }
        for (PackDefinition.Mod mod : definition.mods()) {
            if (mod instanceof PackDefinition.OwnMod own) {
                Path jar = SafePaths.resolveInside(folder.resolve(DefinitionResolver.OWN_MODS), own.file());
                if (!Files.isRegularFile(jar)) throw new SyncException(new SyncFailure.ModUnavailable(own.file()));
                files.put("local-mods/" + own.file(), jar);
            }
        }
        return files;
    }

    /** Changes whenever the toml or any file to publish changes. */
    static String fingerprint(String toml, Map<String, Path> files) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
        digest.update(toml.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, Path> file : new TreeMap<>(files).entrySet()) {
            digest.update(("\n" + file.getKey() + "\t" + FileHasher.sha512(file.getValue())).getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
