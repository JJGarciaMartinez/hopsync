package hopsync.desktop.store;

import java.text.Normalizer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the window shows. The single source of truth for rendering: views read it, never the core.
 *
 * @param packs          by pack id, in the order the player added them
 * @param selectedPackId the pack shown in the main area, or {@code null} when there are no packs
 * @param queries        search text of the mod list, per tab
 * @param packQuery      search text of the pack library; only applies once it shows ({@link #packSearchShown()})
 * @param expandedRow    file of the expanded mod row, or {@code null}
 * @param sheet          the sheet open on top of the window, or {@code null}
 * @param toast          the last short notice shown, or {@code null}
 * @param preferences    app-wide preferences, as saved
 * @param modVersions    the versions asked for with "Cambiar versión" on a row, or {@code null}
 */
public record UiState(Map<String, PackUiState> packs, String selectedPackId, Tab tab, Filter filter,
                      Map<Tab, String> queries, String packQuery, String expandedRow, Sheet sheet, Toast toast,
                      Preferences preferences, ModVersions modVersions) {

    /** The library offers a search from this many packs: with fewer, the list is short enough to scan. */
    public static final int PACK_SEARCH_FROM = 6;

    public enum Tab { PACK, OWN_MODS, CONFIGS }

    public enum Filter { CHANGES, ALL }

    /** @param id grows with every notice, so the same text shown twice still counts as new */
    public record Toast(String text, long id) {
    }

    public static final UiState INITIAL = new UiState(Map.of(), null, Tab.PACK, Filter.CHANGES, Map.of(), "",
        null, null, null, Preferences.DEFAULT, null);

    public UiState {
        packs = Collections.unmodifiableMap(new LinkedHashMap<>(packs));
        queries = Map.copyOf(queries);
        packQuery = packQuery == null ? "" : packQuery;
    }

    public boolean packSearchShown() {
        return packs.size() >= PACK_SEARCH_FROM;
    }

    /**
     * The packs the library lists: all of them, or those whose name or game folder contains the search, ignoring
     * case and accents. The search only filters while it is shown.
     */
    public List<PackUiState> shownPacks() {
        String query = fold(packQuery);
        if (!packSearchShown() || query.isBlank()) {
            return List.copyOf(packs.values());
        }
        return packs.values().stream()
            .filter(pack -> fold(pack.displayName()).contains(query) || fold(String.valueOf(pack.gameDir())).contains(query))
            .toList();
    }

    private static String fold(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT);
    }

    /** Search text of the current tab, never {@code null}. */
    public String query() {
        return queries.getOrDefault(tab, "");
    }

    public Optional<PackUiState> selected() {
        return Optional.ofNullable(selectedPackId).map(packs::get);
    }

    UiState withPacks(Map<String, PackUiState> newPacks, String newSelected) {
        return new UiState(newPacks, newSelected, tab, filter, queries, packQuery, expandedRow, sheet,
            toast, preferences, modVersions);
    }

    UiState withPack(PackUiState pack) {
        Map<String, PackUiState> next = new LinkedHashMap<>(packs);
        next.put(pack.packId(), pack);
        return withPacks(next, selectedPackId);
    }

    UiState withSelected(String packId) {
        return new UiState(packs, packId, tab, filter, queries, packQuery, null, sheet,
            toast, preferences, modVersions);
    }

    UiState withTab(Tab newTab) {
        return new UiState(packs, selectedPackId, newTab, filter, queries, packQuery, expandedRow, sheet,
            toast, preferences, modVersions);
    }

    UiState withFilter(Filter newFilter) {
        return new UiState(packs, selectedPackId, tab, newFilter, queries, packQuery, expandedRow, sheet,
            toast, preferences, modVersions);
    }

    UiState withQuery(String newQuery) {
        Map<Tab, String> next = new EnumMap<>(Tab.class);
        next.putAll(queries);
        next.put(tab, newQuery == null ? "" : newQuery);
        return new UiState(packs, selectedPackId, tab, filter, next, packQuery, expandedRow, sheet,
            toast, preferences, modVersions);
    }

    UiState withPackQuery(String newQuery) {
        return new UiState(packs, selectedPackId, tab, filter, queries, newQuery, expandedRow, sheet,
            toast, preferences, modVersions);
    }

    UiState withExpandedRow(String file) {
        return new UiState(packs, selectedPackId, tab, filter, queries, packQuery, file, sheet,
            toast, preferences, modVersions);
    }

    UiState withToast(String text) {
        return new UiState(packs, selectedPackId, tab, filter, queries, packQuery, expandedRow, sheet,
            new Toast(text, toast == null ? 1 : toast.id() + 1), preferences, modVersions);
    }

    /** True while any pack other than {@code packId} is writing files. */
    public boolean anotherPackApplying(String packId) {
        return applyingPack().map(pack -> !pack.packId().equals(packId)).orElse(false);
    }

    public Optional<PackUiState> applyingPack() {
        return packs.values().stream().filter(pack -> pack.phase() == PackPhase.APPLYING).findFirst();
    }

    UiState withPreferences(Preferences newPreferences) {
        return new UiState(packs, selectedPackId, tab, filter, queries, packQuery, expandedRow, sheet,
            toast, newPreferences, modVersions);
    }

    UiState withModVersions(ModVersions versions) {
        return new UiState(packs, selectedPackId, tab, filter, queries, packQuery, expandedRow, sheet, toast, preferences,
            versions);
    }

    UiState withSheet(Sheet newSheet) {
        if (Objects.equals(sheet, newSheet)) return this;
        return new UiState(packs, selectedPackId, tab, filter, queries, packQuery, expandedRow, newSheet,
            toast, preferences, modVersions);
    }
}
