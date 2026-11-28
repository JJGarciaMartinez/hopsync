package hopsync.desktop.screens.pack;

import hopsync.core.event.SyncEvent;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static hopsync.desktop.store.StoreFixtures.DEMO;
import static hopsync.desktop.store.StoreFixtures.loaded;
import static hopsync.desktop.store.StoreFixtures.offeredPlan;
import static hopsync.desktop.store.StoreFixtures.plan;
import static hopsync.desktop.store.StoreFixtures.reduce;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalOffersTest {
    private static final String D = DEMO.id();

    private static OptionalOffers offers(UiState state) {
        return OptionalOffers.of(state.packs().get(D));
    }

    private static UiState reviewing(Object... more) {
        UiState review = reduce(loaded(), new SyncEvent.UpdateStarted(D), new SyncEvent.PlanReady(D, offeredPlan()));
        return more.length == 0 ? review : reduce(review, more);
    }

    @Test
    void everyOfferedUnitStartsUnchecked() {
        OptionalOffers offers = offers(reviewing());

        assertEquals(List.of("config:jei/", "game:options.txt"), offers.offers().stream().map(OptionalOffers.Offer::key).toList());
        assertFalse(offers.offers().stream().anyMatch(OptionalOffers.Offer::picked));
        assertEquals(Messages.get("offers.detail.files", 2, "3.0 KB"), offers.offers().get(0).detail());
        assertEquals(Messages.get("offers.detail.game", "4.0 KB"), offers.offers().get(1).detail());
        assertEquals(new UiAction.TogglePick(D, "game:options.txt"), offers.offers().get(1).toggle());
    }

    @Test
    void tickingIsKeptUntilTheNextPlanThenForgotten() {
        UiState ticked = reviewing(new UiAction.TogglePick(D, "game:options.txt"));
        assertTrue(offers(ticked).offers().get(1).picked());
        assertFalse(offers(ticked).offers().get(0).picked());

        assertFalse(offers(reduce(ticked, new UiAction.TogglePick(D, "game:options.txt"))).offers().get(1).picked());
        assertTrue(offers(reduce(ticked, new SyncEvent.UpdateStarted(D),
            new SyncEvent.PlanReady(D, offeredPlan()))).offers().stream().noneMatch(OptionalOffers.Offer::picked));
    }

    @Test
    void nothingIsOfferedOutsideTheReviewOrWithoutOffers() {
        assertTrue(offers(loaded()).isEmpty());
        assertTrue(offers(reduce(loaded(), new SyncEvent.UpdateStarted(D), new SyncEvent.PlanReady(D, plan()))).isEmpty());
        assertTrue(offers(reduce(reviewing(), new SyncEvent.ApplyStarted(D))).isEmpty());
    }

    @Test
    void aReviewWithOnlyOffersSavesTheChoiceInsteadOfApplying() {
        UiState state = reviewing();
        HeroModel model = HeroModel.of(state.packs().get(D), state, java.time.ZoneOffset.UTC, hopsync.desktop.store.StoreFixtures.NOW);

        assertEquals(Messages.get("hero.state.reviewChoices"), model.title());
        assertEquals(Messages.get("hero.saveChoices"), model.actions().get(1).label());
        assertEquals(new UiAction.ConfirmPlan(D), model.actions().get(1).action());
        assertTrue(model.tags().isEmpty());
    }
}
