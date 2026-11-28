package hopsync.desktop.screens.pack;

import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ConfigRoot;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.store.PackPhase;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.UiAction;

import java.util.ArrayList;
import java.util.List;

/**
 * The optional config files the creator offers in a review, each unchecked until the player ticks it. Pure, so the
 * texts and the ticks are tested without a window.
 *
 * @param offers one per offered unit, in the order the manifest lists them
 */
public record OptionalOffers(List<Offer> offers) {

    /**
     * @param key    {@code ConfigUnits.Unit#key()}, what the session records the decision under
     * @param title  the folder ({@code jei/}) or file name
     * @param detail "3 archivos · 12.4 KB", or where a game file goes
     * @param toggle what ticking or unticking dispatches
     */
    public record Offer(String key, String title, String detail, boolean picked, UiAction toggle) {
    }

    public OptionalOffers {
        offers = List.copyOf(offers);
    }

    public static OptionalOffers of(PackUiState pack) {
        if (pack.phase() != PackPhase.REVIEW || pack.plan() == null) {
            return new OptionalOffers(List.of());
        }
        List<Offer> offers = new ArrayList<>();
        pack.plan().offeredUnits().forEach((unit, files) -> {
            long bytes = files.stream().mapToLong(ConfigEntry::size).sum();
            String size = PlanSummary.formatBytes(bytes);
            String detail = unit.root() == ConfigRoot.GAME ? Messages.get("offers.detail.game", size)
                : Messages.get("offers.detail.files", files.size(), size);
            offers.add(new Offer(unit.key(), unit.id(), detail, pack.optionalPicks().contains(unit.key()),
                new UiAction.TogglePick(pack.packId(), unit.key())));
        });
        return new OptionalOffers(offers);
    }

    public boolean isEmpty() {
        return offers.isEmpty();
    }
}
