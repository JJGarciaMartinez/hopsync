package hopsync.core.pack;

import hopsync.core.manifest.PackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackTomlTest {

    @Test
    void catalogModsArePinnedByVersionIdAndOwnJarsAreLeftToTheFolder() {
        PackDefinition definition = new PackDefinition(
            new PackInfo("Aventura \"con\" amigos", "1.0.0", "Línea 1\nLínea 2", "play.example.net", "1.21.1", "fabric"),
            List.of(new PackDefinition.ModrinthMod("AANobbMI", "sodium", "Lwa1Q6e4", "0.6.13"),
                new PackDefinition.OwnMod("mine.jar")),
            List.of(new ConfigRule("server/", ConfigMode.ALWAYS)));

        String toml = PackToml.write(definition);

        assertTrue(toml.contains("name = \"Aventura \\\"con\\\" amigos\"\n"), toml);
        assertTrue(toml.contains("description = \"Línea 1\\nLínea 2\"\n"), toml);
        assertTrue(toml.contains("server_ip = \"play.example.net\"\n"), toml);
        assertTrue(toml.contains("overwrite = [\"server/\"]\n"), toml);
        assertTrue(toml.contains("[[mods]]\nmodrinth = \"sodium\"\nversion = \"Lwa1Q6e4\"  # 0.6.13\n"), toml);
        assertFalse(toml.contains("mine.jar"), "own jars are found in local-mods/, not listed");
    }

    @Test
    void anEmptyPackHasNoModsAndNoServer() {
        String toml = PackToml.write(PackDefinition.empty(new PackInfo("x", "1.0.0", "", null, "1.21.1", "quilt")));
        assertFalse(toml.contains("[[mods]]"));
        assertFalse(toml.contains("server_ip"));
        assertTrue(toml.contains("overwrite = []\n"));
        assertTrue(toml.contains("optional = []\n"));
    }

    @Test
    void parseReadsBackWhatWriteProduces() {
        PackDefinition definition = new PackDefinition(
            new PackInfo("Aventura \"con\" amigos\\", "1.0.0", "Línea 1\nLínea 2\t\u0001", "play.example.net:25566",
                "1.21.1", "fabric"),
            List.of(new PackDefinition.ModrinthMod("", "sodium", "Lwa1Q6e4", "0.6.13"),
                new PackDefinition.ModrinthMod("", "lithium", "abc12345", "")),
            List.of(new ConfigRule("server/", ConfigMode.ALWAYS), new ConfigRule("a, \"b\".toml", ConfigMode.ALWAYS),
                new ConfigRule("shaders/", ConfigMode.OPTIONAL), new ConfigRule("extra.json", ConfigMode.OPTIONAL)));

        assertEquals(definition, PackToml.parse(PackToml.write(definition)));
    }

    @Test
    void parseKeepsNoServerAndNoMods() {
        PackDefinition definition = PackDefinition.empty(new PackInfo("x", "1.0.0", "", null, "1.21.1", "quilt"));
        assertEquals(definition, PackToml.parse(PackToml.write(definition)));
    }

    @Test
    void parseIgnoresOwnJarsUnknownTablesAndKeys() {
        PackDefinition parsed = PackToml.parse("""
            # note

            [pack]
            name = "x"
            version = "1"
            minecraft = "1.21.1"
            loader = "fabric"
            future = "ignored"

            [future]
            key = "value"

            [configs]
            overwrite = [ "a.toml" , "b.toml" ]  # trailing
            """);
        assertEquals("", parsed.pack().description());
        assertEquals(List.of(new ConfigRule("a.toml", ConfigMode.ALWAYS), new ConfigRule("b.toml", ConfigMode.ALWAYS)),
            parsed.configRules());
        assertTrue(parsed.mods().isEmpty());
    }

    @Test
    void parseRejectsMalformedFiles() {
        String head = "[pack]\nname = \"x\"\nversion = \"1\"\nminecraft = \"1.21.1\"\nloader = \"fabric\"\n";
        for (String bad : List.of(
            "[pack]\nversion = \"1\"\nminecraft = \"1\"\nloader = \"f\"\n",
            head + "description = \"open\n",
            head + "description = \"a\" junk\n",
            head + "description = \"\\q\"\n",
            head + "description = \"\\u12\"\n",
            head + "[configs]\noverwrite = [\"a\" \"b\"]\n",
            head + "[[mods]]\nmodrinth = \"sodium\"\n",
            "name = \"x\"\n")) {
            assertThrows(IllegalArgumentException.class, () -> PackToml.parse(bad), bad);
        }
    }

    @Test
    void parseErrorNamesTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> PackToml.parse("[pack]\nname = \"x\nversion = \"1\"\n"));
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void controlCharactersAreEscaped() {
        assertEquals("\"a\\tb\\u0001\"", PackToml.quote("a\tb\u0001"));
    }

    @Test
    void rejectsUnitsThatAreNotATopLevelFolderOrFile() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigRule("a/b.json", ConfigMode.ALWAYS));
        assertThrows(IllegalArgumentException.class, () -> new ConfigRule("../x", ConfigMode.OPTIONAL));
        assertThrows(IllegalArgumentException.class, () -> new ConfigRule("a.toml", ConfigMode.IF_MISSING));
    }

    @Test
    void aUnitCannotHaveTwoModes() {
        assertThrows(IllegalArgumentException.class, () -> PackToml.parse("""
            [pack]
            name = "P"
            version = "1"
            minecraft = "1.21.1"
            loader = "fabric"

            [configs]
            overwrite = ["jei/"]
            optional = ["jei/"]
            """));
    }
}
