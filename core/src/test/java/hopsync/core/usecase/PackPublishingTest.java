package hopsync.core.usecase;

import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackRegistry;
import hopsync.core.pack.PackSettings;
import hopsync.core.pack.PackSource;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackPublisher;
import hopsync.core.publish.PublishRequest;
import hopsync.core.publish.PublishStep;
import hopsync.core.port.PackSettingsRepository;
import hopsync.core.util.CancellationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackPublishingTest {
    private static final PackInfo INFO = new PackInfo("Mi pack", "1.0.0", "", null, "1.21.1", "fabric");
    private static final String URL = "https://github.com/player/mi-pack/releases/download/hopsync/manifest.json";

    @TempDir
    Path dir;

    private final List<PublishRequest> sent = new ArrayList<>();
    private PackRegistry registry;
    private PackPublishing publishing;
    private Pack pack;

    @BeforeEach
    void createPack() throws IOException {
        Map<String, PackDefinition> saved = new HashMap<>();
        PackDefinitionRepository definitions = new PackDefinitionRepository() {
            @Override
            public Optional<PackDefinition> load(String packId) {
                return Optional.ofNullable(saved.get(packId));
            }

            @Override
            public void save(String packId, PackDefinition definition) {
                saved.put(packId, definition);
            }

            @Override
            public void delete(String packId) {
                saved.remove(packId);
            }

            @Override
            public Path folder(String packId) {
                return dir.resolve("packs").resolve(packId);
            }
        };
        PackSettingsRepository settings = new PackSettingsRepository() {
            private PackSettings current = PackSettings.EMPTY;

            @Override
            public PackSettings load() {
                return current;
            }

            @Override
            public void save(PackSettings next) {
                current = next;
            }
        };
        PackPublisher publisher = (request, progress, cancel) -> {
            sent.add(request);
            progress.accept(PublishStep.UPLOADING);
            return new PackSource.Publication("player/mi-pack", URL, request.contentHash());
        };
        registry = new PackRegistry(settings, definitions);
        publishing = new PackPublishing(registry, definitions, publisher);
        pack = registry.addLocal(new PackDefinition(INFO, List.of(new PackDefinition.OwnMod("mine.jar")),
            List.of()), dir.resolve("game"));
        Path folder = definitions.folder(pack.id());
        Files.createDirectories(folder.resolve("local-mods"));
        Files.writeString(folder.resolve("local-mods/mine.jar"), "jar");
        Files.createDirectories(folder.resolve("configs/server"));
        Files.writeString(folder.resolve("configs/server/rules.json"), "{}");
    }

    private Pack current() {
        return registry.find(pack.id()).orElseThrow();
    }

    @Test
    void aFirstPublishSendsTheTomlAndFilesAndRemembersWhere() throws IOException {
        List<PublishStep> steps = new ArrayList<>();

        PackSource.Publication publication = publishing.publish(pack, "mi-pack", steps::add, CancellationToken.none());

        PublishRequest request = sent.getFirst();
        assertEquals("mi-pack", request.repoName());
        assertNull(request.existing());
        assertEquals(Set.of("configs/server/rules.json", "local-mods/mine.jar"), request.files().keySet());
        assertTrue(request.packToml().contains("name = \"Mi pack\""));
        assertEquals(List.of(PublishStep.UPLOADING), steps);
        assertEquals(Optional.of(URL), current().link());
        assertEquals(publication, ((PackSource.Local) current().source()).publication());
        assertFalse(publishing.changedSincePublished(current()));
    }

    @Test
    void changingAFileOrTheDefinitionLeavesChangesToPublish() throws IOException {
        assertFalse(publishing.changedSincePublished(pack), "never published: nothing to compare");
        publishing.publish(pack, "mi-pack", step -> { }, CancellationToken.none());

        Files.writeString(dir.resolve("packs").resolve(pack.id()).resolve("configs/server/rules.json"), "{\"pvp\":false}");
        assertTrue(publishing.changedSincePublished(current()));

        PackSource.Publication first = ((PackSource.Local) current().source()).publication();
        publishing.publish(current(), null, step -> { }, CancellationToken.none());
        assertEquals(first, sent.getLast().existing(), "a second publish updates the same repository");
        assertFalse(publishing.changedSincePublished(current()));

        registry.saveDefinition(pack.id(), registry.definition(pack.id()).orElseThrow().withMods(List.of()));
        assertTrue(publishing.changedSincePublished(current()));
    }
}
