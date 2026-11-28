package hopsync.desktop.store;

import hopsync.core.event.SyncEvent;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.ConfigRoot;
import hopsync.core.pack.ConfigMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static hopsync.desktop.store.StoreFixtures.DEMO;
import static hopsync.desktop.store.StoreFixtures.MINE;
import static hopsync.desktop.store.StoreFixtures.created;
import static hopsync.desktop.store.StoreFixtures.loaded;
import static hopsync.desktop.store.StoreFixtures.plan;
import static hopsync.desktop.store.StoreFixtures.reduce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddConfigsTest {
    private static final String M = MINE.id();

    private static Sheet.AddConfigs.Candidate folder(String name) {
        return new Sheet.AddConfigs.Candidate(Path.of("/games/mine/config", name), ConfigRoot.CONFIG, name, true, 3, 100,
            false, ConfigMode.IF_MISSING);
    }

    private static Sheet.AddConfigs.Candidate options() {
        return new Sheet.AddConfigs.Candidate(Path.of("/games/mine/options.txt"), ConfigRoot.GAME, "options.txt", false, 1,
            50, false, ConfigMode.OPTIONAL);
    }

    private static UiState open(Object... more) {
        UiState state = reduce(created(), new UiAction.OpenAddConfigs(M));
        return more.length == 0 ? state : reduce(state, more);
    }

    private static Sheet.AddConfigs sheet(UiState state) {
        return assertInstanceOf(Sheet.AddConfigs.class, state.sheet());
    }

    @Test
    void onlyACreatedPackThatIsNotWritingOpensTheSheet() {
        assertTrue(sheet(open()).scanning());
        assertNull(reduce(loaded(), new UiAction.OpenAddConfigs(DEMO.id())).sheet(), "a followed pack is not edited");
        assertNull(reduce(created(), new SyncEvent.UpdateStarted(M), new SyncEvent.PlanReady(M, plan()),
            new SyncEvent.ApplyStarted(M), new UiAction.OpenAddConfigs(M)).sheet());
    }

    @Test
    void foundCandidatesStartUntickedAndLaterOnesKeepTheChoices() {
        UiState state = open(new UiAction.ConfigCandidatesFound(M, List.of(folder("jei"), options())),
            new UiAction.ToggleConfigCandidate("config:jei"));
        UiState more = reduce(state, new UiAction.ConfigCandidatesFound(M, List.of(folder("jei"), folder("create"))));

        assertFalse(sheet(more).scanning());
        assertEquals(List.of("config:jei", "game:options.txt", "config:create"),
            sheet(more).candidates().stream().map(Sheet.AddConfigs.Candidate::key).toList());
        assertEquals(List.of("config:jei"), sheet(more).picked().stream().map(Sheet.AddConfigs.Candidate::key).toList());
    }

    @Test
    void changingTheModeTicksItAndAGameFileStaysOptional() {
        UiState state = open(new UiAction.ConfigCandidatesFound(M, List.of(folder("jei"), options())),
            new UiAction.SetCandidateMode("config:jei", ConfigMode.ALWAYS),
            new UiAction.SetCandidateMode("game:options.txt", ConfigMode.ALWAYS));

        Sheet.AddConfigs.Candidate jei = sheet(state).candidates().get(0);
        assertTrue(jei.picked());
        assertEquals(ConfigMode.ALWAYS, jei.mode());
        assertEquals(ConfigMode.OPTIONAL, sheet(state).candidates().get(1).mode());
        assertFalse(sheet(state).candidates().get(1).picked(), "a refused mode change does not tick it");
    }

    @Test
    void addingNeedsSomethingPickedThenSavesAndClosesWhenDone() {
        UiState found = open(new UiAction.ConfigCandidatesFound(M, List.of(folder("jei"))));
        assertFalse(sheet(reduce(found, new UiAction.ConfirmAddConfigs(M))).saving());

        UiState saving = reduce(found, new UiAction.ToggleConfigCandidate("config:jei"), new UiAction.ConfirmAddConfigs(M));
        assertTrue(sheet(saving).saving());
        assertFalse(sheet(saving).canAdd());
        assertNull(reduce(saving, new UiAction.ConfigsAdded(M, List.of("jei/"), List.of())).sheet());
    }

    @Test
    void aFailureLeavesTheSheetOpenToTryAgain() {
        UiState failed = open(new UiAction.ConfigCandidatesFound(M, List.of(folder("jei"))),
            new UiAction.ToggleConfigCandidate("config:jei"), new UiAction.ConfirmAddConfigs(M),
            new UiAction.ConfigsAddFailed(M, new SyncFailure.UnsafePath("x")));

        assertEquals(new SyncFailure.UnsafePath("x"), sheet(failed).error());
        assertFalse(sheet(failed).saving());
        assertTrue(sheet(failed).canAdd());
    }

    @Test
    void answersForAnotherPackAreIgnored() {
        UiState state = open(new UiAction.ConfigCandidatesFound(DEMO.id(), List.of(folder("jei"))));

        assertTrue(sheet(state).candidates().isEmpty());
        assertTrue(sheet(state).scanning());
    }
}
