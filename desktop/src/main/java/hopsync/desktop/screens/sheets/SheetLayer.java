package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.kit.RemoteImages;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Draws {@link UiState#sheet()} inside the window: a dimmed layer over everything with the sheet's card in
 * the middle. Every card has the same size, a share of the window's ({@link #cardSize}), so switching a sheet's
 * section never resizes it; what does not fit scrolls inside the card. The layer takes every click, Esc closes the
 * sheet, and Tab stays inside it. The first-run wizard is not drawn here: it fills the window instead (see
 * {@link #firstRunWizard()}).
 */
public final class SheetLayer extends JPanel {
    private static final Color SCRIM = new Color(0, 0, 0, 150);

    private final JLayeredPane owner;
    private final AddPackView wizard;
    private final AddPackView firstRunWizard;
    private final PackSettingsView packSettings;
    private final AppSettingsView appSettings;
    private final AddModsView addMods;
    private final JPanel addModsCard;
    private final AddConfigsView addConfigs;
    private final JPanel addConfigsCard;
    private final PublishView publish;
    private final JPanel publishCard;
    private final JPanel wizardCard;
    private final JPanel packSettingsCard;
    private final JPanel appSettingsCard;
    private JComponent shownCard;

    public SheetLayer(JLayeredPane owner, Consumer<UiAction> dispatch, String demoUrl, Path settingsFile,
        RemoteImages images) {
        super(null);
        this.owner = owner;
        wizard = new AddPackView(dispatch, demoUrl);
        firstRunWizard = new AddPackView(dispatch, demoUrl);
        packSettings = new PackSettingsView(dispatch);
        appSettings = new AppSettingsView(dispatch, settingsFile);
        wizardCard = SheetParts.floatingCard(wizard);
        packSettingsCard = SheetParts.floatingCard(packSettings);
        appSettingsCard = SheetParts.floatingCard(appSettings);
        addMods = new AddModsView(dispatch, images);
        addModsCard = SheetParts.floatingCard(addMods);
        addConfigs = new AddConfigsView(dispatch);
        addConfigsCard = SheetParts.floatingCard(addConfigs);
        publish = new PublishView(dispatch);
        publishCard = SheetParts.floatingCard(publish);
        setOpaque(false);
        setVisible(false);
        setFocusCycleRoot(true);
        // Nothing under the layer reacts while a sheet is open
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "closeSheet");
        getActionMap().put("closeSheet", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isVisible()) dispatch.accept(new UiAction.Escape());
            }
        });

        owner.add(this, JLayeredPane.MODAL_LAYER);
        owner.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setBounds(0, 0, owner.getWidth(), owner.getHeight());
            }
        });
    }

    /** The wizard shown in the whole window while there are no packs; call it once. */
    public JComponent firstRunWizard() {
        return SheetParts.card(firstRunWizard);
    }

    public void render(UiState state) {
        Sheet sheet = state.sheet();
        JComponent card = switch (sheet) {
            case Sheet.AddPack add when add.firstRun() -> {
                firstRunWizard.render(add, state.account());
                yield null;
            }
            case Sheet.AddPack add -> {
                wizard.render(add, state.account());
                yield wizardCard;
            }
            case Sheet.PackSettings settings -> {
                PackUiState pack = state.packs().get(settings.packId());
                packSettings.render(settings, pack);
                yield pack == null ? null : packSettingsCard;
            }
            case Sheet.AppSettings settings -> {
                appSettings.render(settings, state.account(), state.appUpdate());
                yield appSettingsCard;
            }
            case Sheet.Publish publishing -> {
                PackUiState pack = state.packs().get(publishing.packId());
                if (pack == null) yield null;
                publish.render(publishing, pack.displayName(), state.account());
                yield publishCard;
            }
            case Sheet.AddMods mods -> {
                PackUiState pack = state.packs().get(mods.packId());
                if (pack == null) yield null;
                addMods.render(mods, pack.displayName());
                yield addModsCard;
            }
            case Sheet.AddConfigs configs -> {
                PackUiState pack = state.packs().get(configs.packId());
                if (pack == null) yield null;
                addConfigs.render(configs, pack.displayName());
                yield addConfigsCard;
            }
            case null -> null;
        };
        if (card == shownCard) return;
        if (shownCard == addModsCard) addMods.reset();
        if (shownCard == addConfigsCard) addConfigs.reset();
        if (shownCard == publishCard) publish.reset();
        shownCard = card;
        removeAll();
        if (card != null) {
            add(card);
            setBounds(0, 0, owner.getWidth(), owner.getHeight());
        }
        setVisible(card != null);
        revalidate();
        repaint();
        if (card != null) {
            SwingUtilities.invokeLater(() -> {
                Component first = getFocusTraversalPolicy().getDefaultComponent(this);
                if (first != null) first.requestFocusInWindow();
            });
        }
    }

    /**
     * A card's size in a window of the given size: 62 % of its width within 600–720 px and 82 % of its height
     * within 460–600 px, never larger than the window less a margin.
     */
    static Dimension cardSize(int windowWidth, int windowHeight) {
        int margin = UIScale.scale(24);
        int width = Math.clamp(Math.round(windowWidth * 0.62f), UIScale.scale(600), UIScale.scale(720));
        int height = Math.clamp(Math.round(windowHeight * 0.82f), UIScale.scale(460), UIScale.scale(600));
        return new Dimension(Math.max(0, Math.min(width, windowWidth - margin * 2)),
            Math.max(0, Math.min(height, windowHeight - margin * 2)));
    }

    @Override
    public void doLayout() {
        if (getComponentCount() == 0) return;
        Dimension size = cardSize(getWidth(), getHeight());
        getComponent(0).setBounds((getWidth() - size.width) / 2, (getHeight() - size.height) / 2, size.width,
            size.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(SCRIM);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }
}
