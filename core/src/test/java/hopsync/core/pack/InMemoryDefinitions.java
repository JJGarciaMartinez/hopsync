package hopsync.core.pack;

import hopsync.core.port.PackDefinitionRepository;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Definitions kept in memory, for tests that create packs. */
public final class InMemoryDefinitions implements PackDefinitionRepository {
    final Map<String, PackDefinition> saved = new HashMap<>();

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
        return Path.of("packs", packId);
    }
}
