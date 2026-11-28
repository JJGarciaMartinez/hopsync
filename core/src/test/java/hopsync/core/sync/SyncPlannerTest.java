package hopsync.core.sync;

import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ModEntry;
import hopsync.core.pack.OptionalChoice;
import hopsync.core.sync.SyncPlan.ConfigChange;
import hopsync.core.sync.SyncPlan.ModChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static hopsync.core.sync.Fixtures.config;
import static hopsync.core.sync.Fixtures.manifest;
import static hopsync.core.sync.Fixtures.gameFile;
import static hopsync.core.sync.Fixtures.mod;
import static hopsync.core.sync.Fixtures.optionalConfig;
import static hopsync.core.sync.Fixtures.recordOf;
import static hopsync.core.sync.Fixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPlannerTest {

    @TempDir
    Path root;
    Path mods;
    Path configs;
    SyncPlanner planner;

    @BeforeEach
    void setUp() throws IOException {
        mods = Files.createDirectories(root.resolve("mods"));
        configs = Files.createDirectories(root.resolve("config"));
        planner = new SyncPlanner(mods, configs, root);
    }

    private SyncPlan plan(List<ModEntry> modEntries, List<ConfigEntry> configEntries, LocalState state)
            throws IOException {
        return planner.plan(manifest(modEntries, configEntries), state);
    }

    private static LocalState state(Map<String, FileRecord> mods, Map<String, FileRecord> configs) {
        return new LocalState(null, mods, configs);
    }

    private static ModChange changeOf(SyncPlan plan, String file) {
        return plan.mods().stream().filter(a -> a.mod().file().equals(file)).findFirst().orElseThrow().change();
    }

    private static ConfigChange configChangeOf(SyncPlan plan, String path) {
        return plan.configs().stream().filter(a -> a.config().path().equals(path)).findFirst().orElseThrow().change();
    }

    @Test
    void missingModIsNew() throws IOException {
        SyncPlan plan = plan(List.of(mod("a.jar", "A")), List.of(), LocalState.empty());

        assertEquals(ModChange.NEW, changeOf(plan, "a.jar"));
        assertEquals(1, plan.downloadBytes());
        assertFalse(plan.isUpToDate());
    }

    @Test
    void matchingFileIsUpToDateAndAdoptedEvenWithoutState() throws IOException {
        write(mods, "a.jar", "A");

        SyncPlan plan = plan(List.of(mod("a.jar", "A")), List.of(), LocalState.empty());

        assertEquals(ModChange.UP_TO_DATE, changeOf(plan, "a.jar"));
        assertTrue(plan.keptMods().containsKey("a.jar"));
        assertEquals("1.0", plan.keptMods().get("a.jar").version(), "an up-to-date file has the manifest's version");
        assertTrue(plan.isUpToDate());
    }

    @Test
    void differentContentIsUpdate() throws IOException {
        write(mods, "a.jar", "old");

        SyncPlan plan = plan(List.of(mod("a.jar", "new")), List.of(), LocalState.empty());

        assertEquals(ModChange.UPDATE, changeOf(plan, "a.jar"));
        assertFalse(plan.keptMods().containsKey("a.jar"));
    }

    @Test
    void reusesRecordedHashWhenSizeAndTimeMatch() throws IOException {
        Path file = write(mods, "a.jar", "X");
        FileRecord onDisk = recordOf(file);
        // Same size and time but a recorded hash for other content: the record must be trusted, not rehashed
        FileRecord recorded = new FileRecord(Fixtures.sha512("Y"), onDisk.size(), onDisk.modifiedMillis());

        SyncPlan plan = plan(List.of(mod("a.jar", "Y")), List.of(), state(Map.of("a.jar", recorded), Map.of()));

        assertEquals(ModChange.UP_TO_DATE, changeOf(plan, "a.jar"));
    }

    @Test
    void rehashesWhenFileChangedSinceRecorded() throws IOException {
        Path file = write(mods, "a.jar", "A");
        FileRecord recorded = recordOf(file);
        Files.writeString(file, "tampered!");

        SyncPlan plan = plan(List.of(mod("a.jar", "A")), List.of(), state(Map.of("a.jar", recorded), Map.of()));

        assertEquals(ModChange.UPDATE, changeOf(plan, "a.jar"));
    }

    @Test
    void removesOnlyManagedModsThatLeftThePack() throws IOException {
        FileRecord managed = recordOf(write(mods, "old-managed.jar", "M"));
        write(mods, "personal.jar", "P");

        SyncPlan plan = plan(List.of(), List.of(), state(Map.of("old-managed.jar", managed), Map.of()));

        assertEquals(List.of("old-managed.jar"), plan.removals());
    }

    @Test
    void keepsManagedModThePlayerReplaced() throws IOException {
        Path file = write(mods, "old-managed.jar", "M");
        FileRecord managed = recordOf(file);
        Files.writeString(file, "player's own build");

        SyncPlan plan = plan(List.of(), List.of(), state(Map.of("old-managed.jar", managed), Map.of()));

        assertTrue(plan.removals().isEmpty());
    }

    @Test
    void ignoresManagedModAlreadyGone() throws IOException {
        FileRecord ghost = new FileRecord(Fixtures.sha512("G"), 1, 1);

        SyncPlan plan = plan(List.of(), List.of(), state(Map.of("gone.jar", ghost), Map.of()));

        assertTrue(plan.removals().isEmpty());
        assertTrue(plan.isUpToDate());
    }

    @Test
    void ignoresUnsafeNamesFromATamperedState() throws IOException {
        Files.writeString(root.resolve("outside.jar"), "O");
        FileRecord record = recordOf(root.resolve("outside.jar"));

        SyncPlan plan = plan(List.of(), List.of(), state(Map.of("../outside.jar", record), Map.of()));

        assertTrue(plan.removals().isEmpty());
    }

    @Test
    void missingConfigIsInstalled() throws IOException {
        SyncPlan plan = plan(List.of(), List.of(config("sub/a.toml", "v1", false)), LocalState.empty());

        assertEquals(ConfigChange.INSTALL, configChangeOf(plan, "sub/a.toml"));
    }

    @Test
    void matchingConfigIsUpToDate() throws IOException {
        write(configs, "a.toml", "v1");

        SyncPlan plan = plan(List.of(), List.of(config("a.toml", "v1", false)), LocalState.empty());

        assertEquals(ConfigChange.UP_TO_DATE, configChangeOf(plan, "a.toml"));
        assertTrue(plan.keptConfigs().containsKey("a.toml"));
    }

    @Test
    void enforcedConfigIsReplacedEvenIfEdited() throws IOException {
        write(configs, "a.toml", "player edit");

        SyncPlan plan = plan(List.of(), List.of(config("a.toml", "v2", true)), LocalState.empty());

        assertEquals(ConfigChange.REPLACE, configChangeOf(plan, "a.toml"));
    }

    @Test
    void untouchedConfigFollowsNewVersion() throws IOException {
        FileRecord installed = recordOf(write(configs, "a.toml", "v1"));

        SyncPlan plan = plan(List.of(), List.of(config("a.toml", "v2", false)),
            state(Map.of(), Map.of("a.toml", installed)));

        assertEquals(ConfigChange.REPLACE, configChangeOf(plan, "a.toml"));
    }

    @Test
    void editedConfigIsKept() throws IOException {
        Path file = write(configs, "a.toml", "v1");
        FileRecord installed = recordOf(file);
        Files.writeString(file, "player edit");

        SyncPlan plan = plan(List.of(), List.of(config("a.toml", "v2", false)),
            state(Map.of(), Map.of("a.toml", installed)));

        assertEquals(ConfigChange.KEEP_EDITED, configChangeOf(plan, "a.toml"));
        assertTrue(plan.isUpToDate());
    }

    @Test
    void preexistingPlayerConfigIsKept() throws IOException {
        write(configs, "a.toml", "player's");

        SyncPlan plan = plan(List.of(), List.of(config("a.toml", "v1", false)), LocalState.empty());

        assertEquals(ConfigChange.KEEP_EDITED, configChangeOf(plan, "a.toml"));
    }

    @Test
    void countsAndBytes() throws IOException {
        write(mods, "keep.jar", "K");
        write(mods, "upd.jar", "old");

        SyncPlan plan = plan(List.of(mod("keep.jar", "K"), mod("upd.jar", "newer"), mod("new.jar", "NN")),
            List.of(config("c.toml", "CCC", false)), LocalState.empty());

        assertEquals(1, plan.count(ModChange.UP_TO_DATE));
        assertEquals(1, plan.count(ModChange.UPDATE));
        assertEquals(1, plan.count(ModChange.NEW));
        assertEquals(5 + 2 + 3, plan.downloadBytes());
    }

    // ---------------------------------------------------------------- optional configs

    private SyncPlan planWith(List<ConfigEntry> entries, LocalState state, Map<String, OptionalChoice> choices)
            throws IOException {
        return planner.plan(manifest(List.of(), entries), state, choices);
    }

    private static String revisionOf(ConfigEntry... entries) {
        return ConfigUnits.revision(List.of(entries));
    }

    private static ConfigChange changeOfKey(SyncPlan plan, String stateKey) {
        return plan.configs().stream().filter(a -> a.config().stateKey().equals(stateKey)).findFirst().orElseThrow()
            .change();
    }

    @Test
    void optionalUnitWithoutADecisionIsOfferedAndNeverWritten() throws IOException {
        ConfigEntry a = optionalConfig("jei/a.json", "A");
        ConfigEntry b = optionalConfig("jei/b.json", "B");

        SyncPlan plan = planWith(List.of(a, b), LocalState.empty(), Map.of());

        assertEquals(List.of(ConfigChange.OFFERED, ConfigChange.OFFERED),
            plan.configs().stream().map(SyncPlan.ConfigAction::change).toList());
        assertEquals(2, plan.offered().size());
        assertTrue(plan.configDownloads().isEmpty());
        assertFalse(plan.isUpToDate(), "an offer needs the player's review");
        assertTrue(plan.pendingFiles().isEmpty());
    }

    @Test
    void optionalUnitAlreadyMatchingTheManifestHasNothingToOffer() throws IOException {
        write(configs, "jei/a.json", "A");
        SyncPlan plan = planWith(List.of(optionalConfig("jei/a.json", "A")), LocalState.empty(), Map.of());

        assertEquals(ConfigChange.UP_TO_DATE, plan.configs().get(0).change());
        assertTrue(plan.isUpToDate());
    }

    @Test
    void aChangedUnitIsOfferedAgainEvenIfAcceptedBefore() throws IOException {
        ConfigEntry changed = optionalConfig("jei/a.json", "A2");
        OptionalChoice old = new OptionalChoice(revisionOf(optionalConfig("jei/a.json", "A1")), true, true);

        SyncPlan plan = planWith(List.of(changed), LocalState.empty(), Map.of("config:jei/", old));

        assertEquals(ConfigChange.OFFERED, plan.configs().get(0).change());
    }

    @Test
    void aDeclinedUnitIsSkippedWhileItsRevisionHolds() throws IOException {
        ConfigEntry a = optionalConfig("jei/a.json", "A");
        OptionalChoice declined = new OptionalChoice(revisionOf(a), false, false);

        SyncPlan plan = planWith(List.of(a), LocalState.empty(), Map.of("config:jei/", declined));

        assertEquals(ConfigChange.SKIPPED, plan.configs().get(0).change());
        assertEquals(1, plan.skipped().size());
        assertTrue(plan.isUpToDate());
    }

    @Test
    void anAcceptedUnitIsInstalledReplacingWithBackupOrLeftAlone() throws IOException {
        ConfigEntry missing = optionalConfig("jei/missing.json", "M");
        ConfigEntry same = optionalConfig("jei/same.json", "S");
        ConfigEntry other = optionalConfig("jei/other.json", "O");
        write(configs, "jei/same.json", "S");
        write(configs, "jei/other.json", "player's");
        OptionalChoice accepted = new OptionalChoice(revisionOf(missing, same, other), true, false);

        SyncPlan plan = planWith(List.of(missing, same, other), LocalState.empty(), Map.of("config:jei/", accepted));

        assertEquals(ConfigChange.INSTALL, configChangeOf(plan, "jei/missing.json"));
        assertEquals(ConfigChange.UP_TO_DATE, configChangeOf(plan, "jei/same.json"));
        assertEquals(ConfigChange.REPLACE_WITH_BACKUP, configChangeOf(plan, "jei/other.json"));
        assertEquals(2, plan.configDownloads().size());
    }

    @Test
    void anAppliedUnitFollowsTheNormalRulesSoPlayerEditsSurvive() throws IOException {
        ConfigEntry entry = optionalConfig("jei/a.json", "A");
        Path file = write(configs, "jei/a.json", "A");
        LocalState installed = state(Map.of(), Map.of("jei/a.json", recordOf(file)));
        Files.writeString(file, "edited by the player, longer");
        OptionalChoice applied = new OptionalChoice(revisionOf(entry), true, true);

        SyncPlan plan = planWith(List.of(entry), installed, Map.of("config:jei/", applied));

        assertEquals(ConfigChange.KEEP_EDITED, plan.configs().get(0).change());
    }

    @Test
    void gameFilesLiveInTheGameFolderAndHaveTheirOwnStateKey() throws IOException {
        ConfigEntry inGame = gameFile("options.txt", "G");
        ConfigEntry inConfig = config("options.txt", "C", false);
        write(root, "options.txt", "G");
        write(configs, "options.txt", "C");
        OptionalChoice accepted = new OptionalChoice(revisionOf(inGame), true, false);

        SyncPlan plan = planWith(List.of(inGame, inConfig), LocalState.empty(), Map.of("game:options.txt", accepted));

        assertEquals(ConfigChange.UP_TO_DATE, changeOfKey(plan, "game:options.txt"));
        assertEquals(ConfigChange.UP_TO_DATE, changeOfKey(plan, "options.txt"));
        assertEquals(Set.of("game:options.txt", "options.txt"), plan.keptConfigs().keySet());
    }
}
