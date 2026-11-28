package hopsync.core.sync;

import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ConfigRoot;
import hopsync.core.sync.ConfigUnits.Unit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUnitsTest {

    private static ConfigEntry config(String path, String sha) {
        return new ConfigEntry(path, "https://x.org/a", sha, 1, false);
    }

    private static ConfigEntry game(String path) {
        return new ConfigEntry(path, "https://x.org/a", "a", 1, false, ConfigRoot.GAME, true);
    }

    @Test
    void aNestedFileBelongsToItsTopLevelFolder() {
        Unit unit = ConfigUnits.unitOf(config("jei/deep/a.json", "a"));
        assertEquals("jei/", unit.id());
        assertTrue(unit.isFolder());
    }

    @Test
    void aFileDirectlyInConfigIsItsOwnUnit() {
        Unit unit = ConfigUnits.unitOf(config("sodium-options.json", "a"));
        assertEquals("sodium-options.json", unit.id());
        assertFalse(unit.isFolder());
    }

    @Test
    void theSameNameInBothRootsIsTwoUnits() {
        Unit inConfig = ConfigUnits.unitOf(config("options.txt", "a"));
        Unit inGame = ConfigUnits.unitOf(game("options.txt"));
        assertNotEquals(inConfig, inGame);
        assertNotEquals(inConfig.key(), inGame.key());
        assertEquals("game:options.txt", inGame.key());
    }

    @Test
    void aRuleNamesTheSameUnitAsItsFiles() {
        assertEquals(ConfigUnits.unitOf(config("jei/a.json", "a")), ConfigUnits.configUnit("jei/"));
        assertEquals(ConfigUnits.unitOf(config("a.toml", "a")), ConfigUnits.configUnit("a.toml"));
    }

    @Test
    void entriesOfKeepsOnlyTheUnit() {
        List<ConfigEntry> all = List.of(config("jei/a.json", "a"), config("jei/b/c.json", "b"), config("other.toml", "c"));
        assertEquals(2, ConfigUnits.entriesOf(ConfigUnits.configUnit("jei/"), all).size());
    }

    @Test
    void revisionChangesWithContentAndIgnoresOrder() {
        ConfigEntry a = config("jei/a.json", "a");
        ConfigEntry b = config("jei/b.json", "b");
        String base = ConfigUnits.revision(List.of(a, b));
        assertEquals(base, ConfigUnits.revision(List.of(b, a)));
        assertNotEquals(base, ConfigUnits.revision(List.of(a, config("jei/b.json", "c"))));
        assertNotEquals(base, ConfigUnits.revision(List.of(a)));
    }
}
