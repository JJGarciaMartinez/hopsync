package hopsync.core.port;

import hopsync.core.pack.PackDefinition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Where the definitions of the packs the player created are kept, one folder per pack id. */
public interface PackDefinitionRepository {

    /** @return the definition, or empty when there is none or it cannot be read */
    Optional<PackDefinition> load(String packId);

    void save(String packId, PackDefinition definition) throws IOException;

    /** Deletes the definition and the pack's own files (jars and configs); a missing one is fine. */
    void delete(String packId) throws IOException;

    /** The pack's folder, holding the definition, {@code local-mods/} and {@code configs/}. */
    Path folder(String packId);
}
