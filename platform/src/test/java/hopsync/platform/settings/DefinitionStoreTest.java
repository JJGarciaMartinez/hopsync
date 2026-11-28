package hopsync.platform.settings;

import hopsync.core.manifest.PackInfo;
import hopsync.core.pack.ConfigMode;
import hopsync.core.pack.ConfigRule;
import hopsync.core.pack.PackDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionStoreTest {
    private static final String ID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";

    @TempDir
    Path dir;

    @Test
    void roundTripsEveryKindOfMod() throws IOException {
        DefinitionStore store = new DefinitionStore(dir.resolve("packs"));
        PackDefinition definition = new PackDefinition(
            new PackInfo("Mi pack", "1.0.0", "Con amigos", "play.example.net", "1.21.1", "fabric"),
            List.of(new PackDefinition.ModrinthMod("P7dR8mSH", "fabric-api", "Lwa1Q6e4", "0.102.0+1.21.1"),
                new PackDefinition.OwnMod("my-mod-1.0.jar")),
            List.of(new ConfigRule("server/", ConfigMode.ALWAYS), new ConfigRule("shaders/", ConfigMode.OPTIONAL)));

        store.save(ID, definition);

        assertEquals(Optional.of(definition), store.load(ID));
        assertEquals(dir.resolve("packs").resolve(ID), store.folder(ID));
    }

    @Test
    void missingUnreadableOrUnsafeMeansNoDefinition() throws IOException {
        DefinitionStore store = new DefinitionStore(dir);
        assertEquals(Optional.empty(), store.load(ID));

        Files.createDirectories(dir.resolve(ID));
        Files.writeString(dir.resolve(ID).resolve("pack.json"), "not json");
        assertEquals(Optional.empty(), store.load(ID));

        Files.writeString(dir.resolve(ID).resolve("pack.json"), "{\"format\": 1, \"pack\": {\"name\": \"x\", "
            + "\"version\": \"1\", \"minecraft\": \"1.21.1\", \"loader\": \"fabric\"}, "
            + "\"mods\": [{\"source\": \"local\", \"file\": \"../../evil.jar\"}]}");
        assertEquals(Optional.empty(), store.load(ID), "a jar name outside local-mods/ is refused");

        Files.writeString(dir.resolve(ID).resolve("pack.json"), "{\"format\": 2}");
        assertEquals(Optional.empty(), store.load(ID));
    }

    @Test
    void deleteRemovesThePackFolderOnly() throws IOException {
        DefinitionStore store = new DefinitionStore(dir);
        store.save(ID, PackDefinition.empty(new PackInfo("x", "1", "", null, "1.21.1", "fabric")));
        Files.createDirectories(store.folder(ID).resolve("local-mods"));
        Files.writeString(store.folder(ID).resolve("local-mods").resolve("a.jar"), "jar");
        Files.writeString(dir.resolve("keep.txt"), "other");

        store.delete(ID);
        store.delete(ID);

        assertFalse(Files.exists(store.folder(ID)));
        assertEquals("other", Files.readString(dir.resolve("keep.txt")));
    }

    @Test
    void packIdsCannotLeaveTheFolder() {
        DefinitionStore store = new DefinitionStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.folder("../settings"));
    }
}
