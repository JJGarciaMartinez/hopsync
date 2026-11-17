package hopsync.core.pack;

import hopsync.core.manifest.PackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackTomlTest {

    @Test
    void catalogModsArePinnedByVersionIdAndOwnJarsAreLeftToTheFolder() {
        PackDefinition definition = new PackDefinition(
            new PackInfo("Aventura \"con\" amigos", "1.0.0", "Línea 1\nLínea 2", "play.example.net", "1.21.1", "fabric"),
            List.of(new PackDefinition.ModrinthMod("AANobbMI", "sodium", "Lwa1Q6e4", "0.6.13"),
                new PackDefinition.OwnMod("mine.jar")),
            List.of("server/rules.json"));

        String toml = PackToml.write(definition);

        assertTrue(toml.contains("name = \"Aventura \\\"con\\\" amigos\"\n"), toml);
        assertTrue(toml.contains("description = \"Línea 1\\nLínea 2\"\n"), toml);
        assertTrue(toml.contains("server_ip = \"play.example.net\"\n"), toml);
        assertTrue(toml.contains("overwrite = [\"server/rules.json\"]\n"), toml);
        assertTrue(toml.contains("[[mods]]\nmodrinth = \"sodium\"\nversion = \"Lwa1Q6e4\"  # 0.6.13\n"), toml);
        assertFalse(toml.contains("mine.jar"), "own jars are found in local-mods/, not listed");
    }

    @Test
    void anEmptyPackHasNoModsAndNoServer() {
        String toml = PackToml.write(PackDefinition.empty(new PackInfo("x", "1.0.0", "", null, "1.21.1", "quilt")));
        assertFalse(toml.contains("[[mods]]"));
        assertFalse(toml.contains("server_ip"));
        assertTrue(toml.contains("overwrite = []\n"));
    }

    @Test
    void controlCharactersAreEscaped() {
        assertEquals("\"a\\tb\\u0001\"", PackToml.quote("a\tb\u0001"));
    }
}
