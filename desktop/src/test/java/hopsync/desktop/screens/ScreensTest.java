package hopsync.desktop.screens;

import com.formdev.flatlaf.util.UIScale;
import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.catalog.ProjectDetails;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.screens.sheets.AddModsView;
import java.awt.event.MouseEvent;
import hopsync.core.event.SyncEvent;
import hopsync.core.publish.PublishStep;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.Pack;
import hopsync.desktop.kit.HopSyncDark;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.RemoteImages;
import hopsync.desktop.screens.library.PackLibrary;
import hopsync.desktop.screens.mods.ModsPanel;
import hopsync.desktop.screens.pack.PackHero;
import hopsync.desktop.screens.sheets.SheetLayer;
import hopsync.desktop.store.GameFolder;
import hopsync.desktop.store.Preferences;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.InstalledMods;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import org.junit.jupiter.api.BeforeAll;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static hopsync.desktop.store.StoreFixtures.DEMO;
import static hopsync.desktop.store.StoreFixtures.MINE;
import static hopsync.desktop.store.StoreFixtures.created;
import static hopsync.desktop.store.StoreFixtures.manifest;
import static hopsync.desktop.store.StoreFixtures.OTHER;
import static hopsync.desktop.store.StoreFixtures.loaded;
import static hopsync.desktop.store.StoreFixtures.plan;
import static hopsync.desktop.store.StoreFixtures.reduce;
import static hopsync.desktop.store.StoreFixtures.upToDatePlan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds and paints every screen (no window needed) in every phase, so a broken style or a renderer that
 * throws fails the build instead of the app.
 */
class ScreensTest {
    private static final RemoteImages NO_IMAGES = new RemoteImages(url -> {
        throw new java.io.IOException("offline");
    }, Runnable::run);

    private static final String D = DEMO.id();
    private final List<UiAction> dispatched = new ArrayList<>();

    @BeforeAll
    static void installTheme() {
        HopSyncDark.setup();
    }

    /** One state per phase of DEMO, with OTHER applying in the review one. */
    private static List<UiState> everyPhase() {
        UiState base = reduce(loaded(), new UiAction.ManifestLoaded(D, manifest()),
            new UiAction.InstalledModsLoaded(D, InstalledMods.of(List.of("a.jar", "kept.jar"))));
        return List.of(
            base,
            reduce(base, new SyncEvent.UpdateStarted(D)),
            reduce(base, new SyncEvent.UpdateStarted(OTHER.id()), new SyncEvent.PlanReady(OTHER.id(), plan()),
                new SyncEvent.ApplyStarted(OTHER.id()), new SyncEvent.UpdateStarted(D), new SyncEvent.PlanReady(D, plan())),
            reduce(base, new SyncEvent.UpdateStarted(D), new SyncEvent.PlanReady(D, plan()), new SyncEvent.Queued(D)),
            reduce(base, new SyncEvent.UpdateStarted(D), new SyncEvent.PlanReady(D, plan()), new SyncEvent.ApplyStarted(D),
                new SyncEvent.ModDownloading(D, "new.jar"), new SyncEvent.Progress(D, 1, 3, 40, 100, 0)),
            reduce(base, new SyncEvent.UpdateStarted(D), new SyncEvent.UpToDate(D, upToDatePlan())),
            reduce(base, new SyncEvent.UpdateStarted(D), new SyncEvent.PlanReady(D, plan()), new SyncEvent.ApplyStarted(D),
                new SyncEvent.UpdateCompleted(D, 1, 1)),
            reduce(base, new SyncEvent.UpdateStarted(D), new SyncEvent.UpdateCancelled(D)),
            reduce(base, new SyncEvent.UpdateStarted(D),
                new SyncEvent.UpdateFailed(D, new SyncFailure.Network("github.com", "timeout"))));
    }

    private static UiState withMods() {
        return reduce(created(), new SyncEvent.UpdateStarted(MINE.id()), new SyncEvent.UpToDate(MINE.id(), upToDatePlan()));
    }

    private static List<UiState> everyPhaseAndCreated() {
        List<UiState> states = new ArrayList<>(everyPhase());
        states.add(created());
        return states;
    }

    @Test
    void heroAndLibraryRenderEveryPhase() {
        PackHero hero = new PackHero(dispatched::add, ZoneOffset.UTC);
        PackLibrary library = new PackLibrary(dispatched::add);
        StatusBar statusBar = new StatusBar(ZoneOffset.UTC, dispatched::add);
        UiState.AppUpdate update = new UiState.AppUpdate("26.2.1", "https://github.com/o/h/releases/tag/26.2.1");
        ModsPanel mods = new ModsPanel(dispatched::add);
        for (UiState phase : everyPhaseAndCreated()) {
            for (UiState state : List.of(phase, reduce(phase, new UiAction.SetFilter(UiState.Filter.ALL),
                    new UiAction.ToggleRow("kept.jar")), reduce(phase, new UiAction.SelectTab(UiState.Tab.OWN_MODS)),
                    reduce(phase, new UiAction.SelectTab(UiState.Tab.CONFIGS)))) {
                PackUiState pack = state.selected().orElseThrow();
                hero.render(pack, state);
                library.render(state);
                statusBar.render(pack, update);
                mods.render(pack, state);
                paint(hero, 746, 134);
                paint(library, 214, 560);
                paint(statusBar, 960, 28);
                paint(mods, 746, 420);
            }
        }
        assertTrue(dispatched.isEmpty(), "rendering never dispatches: " + dispatched);
    }

    @Test
    void progressBarKeepsItsWidthWhateverFileIsDownloading() throws Exception {
        UiState applying = reduce(loaded(), new UiAction.ManifestLoaded(D, manifest()), new SyncEvent.UpdateStarted(D),
            new SyncEvent.PlanReady(D, plan()), new SyncEvent.ApplyStarted(D), new SyncEvent.Progress(D, 1, 3, 40, 100, 0));
        PackHero hero = new PackHero(dispatched::add, ZoneOffset.UTC);

        int shortName = barWidth(hero, reduce(applying, new SyncEvent.ModDownloading(D, "a.jar")));
        int longName = barWidth(hero, reduce(applying, new SyncEvent.ModDownloading(D,
            "create-connected-and-all-its-addons-mc1.21.1-neoforge-1.1.10-super-long-file-name.jar")));

        assertTrue(shortName > 0);
        assertEquals(shortName, longName);
    }

    @Test
    void aNarrowHeaderMovesTheButtonsUnderTheNameInsteadOfHidingIt() throws Exception {
        // A created pack in review has the most buttons: add mods, publish, not now, apply
        UiState review = reduce(created(), new SyncEvent.UpdateStarted(MINE.id()), new SyncEvent.PlanReady(MINE.id(), plan()));
        PackHero hero = new PackHero(dispatched::add, ZoneOffset.UTC);

        for (int width : new int[] {600, 800, 1100}) {
            SwingUtilities.invokeAndWait(() -> {
                hero.render(review.selected().orElseThrow(), review);
                hero.setSize(UIScale.scale(width), UIScale.scale(134));
                invalidateTree(hero);
                layoutTree(hero);
                // The row of buttons makes the header taller, as the window would then give it
                hero.setSize(hero.getWidth(), hero.getPreferredSize().height);
                invalidateTree(hero);
                layoutTree(hero);
                // Right the first time: a window lays it out once more only if something else changes
                assertEquals(hero.getHeight(), hero.getPreferredSize().height, width + " px: the height kept changing");

                JLabel name = findLabel(hero, review.selected().orElseThrow().displayName());
                assertTrue(name.getWidth() >= name.getPreferredSize().width,
                    width + " px: the name is cut to " + name.getWidth() + " px");
                Rectangle header = new Rectangle(0, 0, hero.getWidth(), hero.getHeight());
                for (JButton button : buttons(hero)) {
                    Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), hero);
                    assertTrue(header.contains(bounds), width + " px: " + button.getText() + " at " + bounds);
                }
            });
        }
    }

    private static JLabel findLabel(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) return label;
            if (child instanceof Container inner) {
                JLabel found = findLabel(inner, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && button.isVisible() && button.getText() != null
                    && !button.getText().isEmpty()) {
                found.add(button);
            } else if (child instanceof Container inner && child.isVisible()) {
                found.addAll(buttons(inner));
            }
        }
        return found;
    }

    /**
     * Lays the hero out as a shown window would. Without a window Swing never marks components valid, so
     * {@code revalidate()} stops early and layouts keep their cached sizes; invalidating the tree by hand
     * clears them, as the window does.
     */
    private static int barWidth(PackHero hero, UiState state) throws Exception {
        int[] width = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            hero.render(state.selected().orElseThrow(), state);
            // The width the hero has in a 960 px window
            hero.setSize(UIScale.scale(746), UIScale.scale(134));
            invalidateTree(hero);
            layoutTree(hero);
            width[0] = find(hero, JProgressBar.class).getWidth();
        });
        return width[0];
    }

    private static void invalidateTree(Container container) {
        container.invalidate();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) invalidateTree(inner);
        }
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) layoutTree(inner);
        }
    }

    private static <T extends Component> T find(Container container, Class<T> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void emptyStateRenders() {
        StatusBar statusBar = new StatusBar(ZoneOffset.UTC, dispatched::add);
        statusBar.render(null, null);
        paint(statusBar, 960, 28);
        PackLibrary library = new PackLibrary(dispatched::add);
        library.render(UiState.INITIAL);
        paint(library, 214, 560);
    }

    @Test
    void longPackNamesNeverScrollTheLibrarySideways() throws Exception {
        Pack longName = new Pack("long", "https://example.org/long.json", Path.of("/games/long").toAbsolutePath(),
            "Un pack con un nombre larguísimo que no cabe en la biblioteca ni de lejos");
        UiState state = reduce(UiState.INITIAL, new UiAction.PacksLoaded(List.of(longName), "long"),
            new UiAction.ResizeLibrary(Preferences.MIN_LIBRARY_WIDTH));
        PackLibrary library = new PackLibrary(dispatched::add);
        SwingUtilities.invokeAndWait(() -> {
            library.render(state);
            library.setSize(library.getPreferredSize().width, UIScale.scale(560));
            invalidateTree(library);
            layoutTree(library);
            layoutTree(library);
        });
        JScrollPane scroll = find(library, JScrollPane.class);
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
        assertEquals(scroll.getViewport().getWidth(), scroll.getViewport().getView().getWidth());
    }

    @Test
    void librarySearchRendersMatchesAndNoMatch() {
        List<Pack> six = new ArrayList<>();
        for (int i = 0; i < UiState.PACK_SEARCH_FROM; i++) {
            six.add(new Pack("p" + i, "https://example.org/p" + i + ".json", Path.of("/games/p" + i).toAbsolutePath(),
                "Pack " + i));
        }
        UiState many = reduce(UiState.INITIAL, new UiAction.PacksLoaded(six, "p0"));
        PackLibrary library = new PackLibrary(dispatched::add);
        for (UiState state : List.of(many, reduce(many, new UiAction.SetPackQuery("pack 3")),
                reduce(many, new UiAction.SetPackQuery("zzz")))) {
            library.render(state);
            paint(library, 232, 560);
        }
        assertTrue(dispatched.isEmpty(), "rendering never dispatches, not even the search text: " + dispatched);
    }

    /** Every sheet in every state it can be in, as the store would leave it. */
    private static List<UiState> everySheet() {
        String url = "https://example.org/new.json";
        UiState empty = reduce(UiState.INITIAL, new UiAction.PacksLoaded(List.of(), null));
        UiState add = reduce(loaded(), new UiAction.AddPack(), new UiAction.SetLinkUrl(url));
        UiState valid = reduce(add, new UiAction.CheckLink(), new UiAction.LinkChecked(url, manifest()));
        GameFolder taken = new GameFolder(GameFolder.Kind.PRISM, "Demo", DEMO.gameDir(), 40, "Demo");
        GameFolder free = new GameFolder(GameFolder.Kind.OFFICIAL, null, Path.of("/games/.minecraft").toAbsolutePath(),
            0, null);
        UiState folders = reduce(valid, new UiAction.WizardNext());
        UiState packSettings = reduce(loaded(), new UiAction.OpenSettings());
        UiState scratch = reduce(empty, new UiAction.StartFromScratch());
        UiState described = reduce(scratch, new UiAction.GameVersionsLoaded(List.of("1.21.2", "1.21.1")),
            new UiAction.EditNewPack("Aventura con amigos", "1.21.1", "neoforge", ""));
        return List.of(
            empty,
            scratch,
            reduce(scratch, new UiAction.GameVersionsFailed(new SyncFailure.Network("api.modrinth.com", "timeout"))),
            described,
            reduce(described, new UiAction.WizardNext(), new UiAction.FoldersFound(List.of(free))),
            reduce(add, new UiAction.StartFromScratch()),
            reduce(empty, new UiAction.SetLinkUrl("http://example.org"), new UiAction.CheckLink()),
            add,
            reduce(add, new UiAction.CheckLink()),
            reduce(add, new UiAction.CheckLink(), new UiAction.LinkFailed(url, new SyncFailure.HttpStatus(404, url))),
            reduce(add, new UiAction.SetLinkUrl(DEMO.link().orElseThrow()), new UiAction.CheckLink(),
                new UiAction.LinkChecked(DEMO.link().orElseThrow(), manifest())),
            valid,
            folders,
            reduce(folders, new UiAction.FoldersFound(List.of())),
            reduce(folders, new UiAction.FoldersFound(List.of(taken, free))),
            reduce(folders, new UiAction.FoldersFound(List.of(taken)), new UiAction.ChooseFolder(taken.path())),
            reduce(folders, new UiAction.FoldersFound(List.of(free)), new UiAction.SheetFailed(
                new SyncFailure.PermissionDenied("/games/.minecraft"))),
            packSettings,
            reduce(packSettings, new UiAction.SetLinkUrl(url), new UiAction.CheckLink(),
                new UiAction.LinkChecked(url, manifest())),
            reduce(packSettings, new UiAction.SelectPackSection(Sheet.PackSettings.Section.FOLDER),
                new UiAction.ChooseFolder(OTHER.gameDir()),
                new UiAction.FolderChecked(new GameFolder(GameFolder.Kind.OTHER, null, OTHER.gameDir(), 2, "Other"))),
            reduce(packSettings, new UiAction.SelectPackSection(Sheet.PackSettings.Section.REMOVE)),
            reduce(created(), new UiAction.OpenSettings(),
                new UiAction.SelectPackSection(Sheet.PackSettings.Section.SERVER)),
            reduce(created(), new UiAction.OpenSettings(),
                new UiAction.SelectPackSection(Sheet.PackSettings.Section.SERVER),
                new UiAction.SetServerAddress("https://play.example.net")),
            reduce(described, new UiAction.EditNewPack("Aventura con amigos", "1.21.1", "neoforge", "play example")),
            reduce(loaded(), new UiAction.OpenAppSettings(Sheet.AppSettings.Section.GENERAL),
                new UiAction.SetPreferences(new Preferences(false, true, 6, true))),
            reduce(loaded(), new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ABOUT)),
            reduce(loaded(), new UiAction.AppUpdateFound("26.2.1", "https://github.com/o/h/releases/tag/26.2.1"),
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ABOUT)),
            reduce(loaded(), new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT)),
            reduce(loaded(), new UiAction.AccountLoaded(null, false, true),
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT),
                new UiAction.ConnectGitHub(), new UiAction.GitHubConnectFailed(new SyncFailure.GitHubCodeExpired())),
            reduce(loaded(), new UiAction.AccountLoaded(null, true, true),
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT), new UiAction.ConnectGitHub()),
            reduce(loaded(), new UiAction.AccountLoaded(null, true, true),
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT), new UiAction.ConnectGitHub(),
                new UiAction.GitHubCodeShown("WDJB-MJHT", "https://github.com/login/device")),
            reduce(loaded(), new UiAction.AccountLoaded("player", true, true),
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT)),
            reduce(loaded(), new UiAction.AccountLoaded(null, true, false),
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT)),
            reduce(withMods(), new UiAction.OpenPublish(MINE.id())),
            reduce(withMods(), new UiAction.AccountLoaded("player", true, true), new UiAction.OpenPublish(MINE.id())),
            reduce(withMods(), new UiAction.AccountLoaded("player", true, true), new UiAction.OpenPublish(MINE.id()),
                new UiAction.PublishFilesLoaded(MINE.id(), List.of("local-mods/a.jar", "local-mods/b.jar", "configs/c.json",
                    "configs/d.json", "configs/e.json")), new UiAction.SetRepoName("no vale!"),
                new UiAction.PublishFailed(MINE.id(), new SyncFailure.PublishBuildFailed("https://github.com/p/r/actions/runs/1"))),
            reduce(withMods(), new UiAction.AccountLoaded("player", true, true), new UiAction.OpenPublish(MINE.id()),
                new UiAction.PublishFilesLoaded(MINE.id(), List.of()), new UiAction.StartPublish(),
                new UiAction.PublishProgressed(MINE.id(), PublishStep.UPLOADING)),
            reduce(withMods(), new UiAction.AccountLoaded("player", true, true), new UiAction.OpenPublish(MINE.id()),
                new UiAction.PublishFilesLoaded(MINE.id(), List.of()), new UiAction.StartPublish(),
                new UiAction.Published(MINE.id(), "https://github.com/player/aventura/releases/download/hopsync/manifest.json")),
            reduce(created(), new UiAction.OpenAddMods(MINE.id())),
            reduce(created(), new UiAction.OpenAddMods(MINE.id()), new UiAction.PackModsLoaded(MINE.id(), Set.of("a")),
                new UiAction.ModsFound("", List.of(
                    new CatalogProject("a", "a", "Sodium", "Fast", "jelly", null, 1),
                    new CatalogProject("b", "b", "Un mod con un nombre larguísimo que no cabe en la hoja", "", "", null, 1))),
                new UiAction.AddCatalogMod("b"), new UiAction.ModAdded("b", new Sheet.Added("B", List.of("C", "D")), Set.of("a", "b"))),
            reduce(created(), new UiAction.OpenAddMods(MINE.id()), new UiAction.SearchMods("zzz"),
                new UiAction.ModsFound("zzz", List.of()), new UiAction.ModAddFailed(null, new SyncFailure.UnsafePath("x.txt"))),
            reduce(created(), new UiAction.OpenAddMods(MINE.id()),
                new UiAction.ModSearchFailed("", new SyncFailure.Network("api.modrinth.com", "timeout"))),
            reduce(addModsList(), new UiAction.OpenModDetail(IRIS)),
            reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
                new UiAction.ModDetailLoaded(IRIS.id(), details(3, "## Iris\n\nShaders **fast**."), VERSION,
                    List.of("Fabric API"), false), new UiAction.SelectShot(2)),
            reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
                new UiAction.ModDetailLoaded(IRIS.id(), details(0, ""), VERSION, List.of(), false)),
            reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
                new UiAction.ModDetailLoaded(IRIS.id(), details(1, "Body"), null, List.of(), true)),
            reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
                new UiAction.ModDetailFailed(IRIS.id(), new SyncFailure.Network("api.modrinth.com", "timeout"))),
            reduce(addModsList(), new UiAction.OpenModDetail(SODIUM),
                new UiAction.ModDetailLoaded(SODIUM.id(), details(2, "Body"), null, List.of(), false)),
            reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
                new UiAction.ModDetailLoaded(IRIS.id(), details(1, "Body"), VERSION, List.of(), false),
                new UiAction.AddCatalogMod(IRIS.id())));
    }

    private static final CatalogProject SODIUM =
        new CatalogProject("a", "sodium", "Sodium", "Fast", "jelly", "https://cdn.example.org/a.png", 1_234_567);
    private static final CatalogProject IRIS = new CatalogProject("b", "iris", "Iris", "Shaders", "coderbot", null, 99);
    private static final CatalogVersion VERSION = new CatalogVersion("v1", IRIS.id(), "1.8.0", List.of("1.21.1"),
        List.of("fabric"), true, List.of(), List.of());

    private static ProjectDetails details(int shots, String body) {
        List<ProjectDetails.Image> gallery = new ArrayList<>();
        for (int i = 0; i < shots; i++) {
            gallery.add(new ProjectDetails.Image("https://cdn.example.org/shot" + i + ".png", "Shot " + i, i == 1));
        }
        return new ProjectDetails(IRIS.id(), IRIS.slug(), IRIS.title(), "Shaders for everyone.", body, gallery);
    }

    /** Add mods for MINE with Sodium already in the pack and Iris not. */
    private static UiState addModsList() {
        return reduce(created(), new UiAction.OpenAddMods(MINE.id()), new UiAction.PackModsLoaded(MINE.id(), Set.of("a")),
            new UiAction.ModsFound("", List.of(SODIUM, IRIS)));
    }

    private AddModsView addModsView(UiState state) {
        AddModsView view = new AddModsView(dispatched::add, NO_IMAGES);
        view.render((Sheet.AddMods) state.sheet(), "Aventura");
        paint(view, 700, 520);
        return view;
    }

    @Test
    void clickingAResultOpensItsDetailButItsAddButtonOnlyAdds() {
        AddModsView view = addModsView(addModsList());
        JComponent row = findNamed(view, Messages.get("addMods.openDetail", IRIS.title()));
        assertTrue(row != null, "the row is named for screen readers");
        assertTrue(row.isFocusable(), "the row takes keyboard focus");

        click(row);
        assertEquals(List.of(new UiAction.OpenModDetail(IRIS)), dispatched);
        dispatched.clear();

        JButton add = (JButton) findNamed(view, Messages.get("addMods.add") + " " + IRIS.title());
        click(add);
        add.doClick();
        assertEquals(List.of(new UiAction.AddCatalogMod(IRIS.id())), dispatched);
    }

    @Test
    void detailGoesBackAndAddsToThePack() {
        AddModsView view = addModsView(reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
            new UiAction.ModDetailLoaded(IRIS.id(), details(3, "Body"), VERSION, List.of(), false)));
        assertTrue(dispatched.isEmpty(), "rendering never dispatches: " + dispatched);

        visibleButton(view, Messages.get("addMods.back")).doClick();
        assertEquals(List.of(new UiAction.BackToResults()), dispatched);
        dispatched.clear();
        JButton add = visibleButton(view, Messages.get("addMods.addToPack"));
        assertTrue(add.isEnabled());
        add.doClick();
        assertEquals(List.of(new UiAction.AddCatalogMod(IRIS.id())), dispatched);
    }

    @Test
    void detailAddButtonIsAbsentInThePackAndDisabledWithoutAVersion() {
        AddModsView inPack = addModsView(reduce(addModsList(), new UiAction.OpenModDetail(SODIUM),
            new UiAction.ModDetailLoaded(SODIUM.id(), details(0, ""), null, List.of(), false)));
        assertEquals(null, visibleButton(inPack, Messages.get("addMods.addToPack")));
        assertTrue(findNamed(inPack, Messages.get("addMods.added")) != null, "shows Agregado");

        AddModsView noVersion = addModsView(reduce(addModsList(), new UiAction.OpenModDetail(IRIS),
            new UiAction.ModDetailLoaded(IRIS.id(), details(0, ""), null, List.of(), true)));
        assertFalse(visibleButton(noVersion, Messages.get("addMods.addToPack")).isEnabled());
    }

    private static void click(Component component) {
        for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
            component.dispatchEvent(new MouseEvent(component, id, 0, 0, 4, 4, 1, false, MouseEvent.BUTTON1));
        }
    }

    private static JComponent findNamed(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JComponent c && c.getAccessibleContext() != null
                    && name.equals(c.getAccessibleContext().getAccessibleName())) return c;
            if (child instanceof Container inner) {
                JComponent found = findNamed(inner, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton visibleButton(Container container, String text) {
        return buttons(container).stream().filter(b -> text.equals(b.getText())).findFirst().orElse(null);
    }

    @Test
    void sheetsRenderEveryState() {
        JLayeredPane window = new JLayeredPane();
        window.setSize(960, 620);
        SheetLayer sheets = new SheetLayer(window, dispatched::add, "https://example.org/demo.json",
            Path.of("settings.json"), NO_IMAGES);
        JComponent firstRun = sheets.firstRunWizard();
        for (UiState state : everySheet()) {
            sheets.render(state);
            paint(sheets, 960, 620);
            paint(firstRun, 960, 620);
        }
        sheets.render(UiState.INITIAL);
        assertFalse(sheets.isVisible());
        assertTrue(dispatched.isEmpty(), "rendering never dispatches: " + dispatched);
    }

    @Test
    void typingALinkDispatchesItButRenderingItBackDoesNot() {
        JLayeredPane window = new JLayeredPane();
        window.setSize(960, 620);
        SheetLayer sheets = new SheetLayer(window, dispatched::add, "", Path.of("settings.json"), NO_IMAGES);
        UiState add = reduce(loaded(), new UiAction.AddPack());
        sheets.render(add);
        JTextField field = find(sheets, JTextField.class);

        field.setText("https://example.org/a.json");
        assertEquals(new UiAction.SetLinkUrl("https://example.org/a.json"), dispatched.getLast());
        dispatched.clear();
        sheets.render(reduce(add, new UiAction.SetLinkUrl("https://example.org/b.json")));
        assertEquals("https://example.org/b.json", field.getText());
        assertTrue(dispatched.isEmpty(), "rendering never dispatches: " + dispatched);
    }

    @Test
    void toastIsDarkGlassOverABlurOfWhatIsUnderIt() {
        JLayeredPane layer = new JLayeredPane();
        layer.setSize(400, 200);
        JPanel content = new JPanel();
        content.setBackground(Palette.brand());
        content.setBounds(0, 0, 400, 200);
        layer.add(content, JLayeredPane.DEFAULT_LAYER);
        Toast toast = new Toast(layer);
        toast.render(reduce(loaded(), new UiAction.ShowToast("IP copiada")).toast());

        BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        layer.paint(g);
        g.dispose();

        assertEquals(Palette.text(), toast.getForeground());
        // Inside the pill, past the text: the raised glass, lightened by the light panel showing through
        int red = new Color(image.getRGB(toast.getX() + toast.getWidth() - 8, toast.getY() + toast.getHeight() / 2))
            .getRed();
        assertTrue(red > Palette.raised().getRed() + 20 && red < Palette.brand().getRed() - 40, "glass red " + red);
    }

    @Test
    void toastShowsEachNoticeOnce() {
        JLayeredPane layer = new JLayeredPane();
        layer.setSize(960, 620);
        Toast toast = new Toast(layer);
        UiState state = reduce(loaded(), new UiAction.ShowToast("IP copiada"));

        toast.render(state.toast());
        assertTrue(toast.isVisible());
        assertEquals("IP copiada", toast.getText());
        toast.setVisible(false);
        toast.render(state.toast());
        assertTrue(!toast.isVisible(), "the same notice is not shown again");
    }

    private static void paint(JComponent component, int width, int height) {
        component.setSize(width, height);
        component.doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        component.paint(g);
        g.dispose();
    }
}
