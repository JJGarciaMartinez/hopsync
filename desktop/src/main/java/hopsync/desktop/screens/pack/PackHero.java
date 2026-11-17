package hopsync.desktop.screens.pack;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.util.UIScale;
import hopsync.core.manifest.PackInfo;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.i18n.RelativeTime;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.LoaderIcon;
import hopsync.desktop.kit.LogoIcon;
import hopsync.desktop.kit.MarkTile;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.StatusMark;
import hopsync.desktop.kit.StatusTag;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Header of the selected pack: mark tile, name and version, Minecraft · loader · mods, the server address
 * (click to copy), the status line and the review tags under it. On the right, the folder and ⋯ buttons at the
 * top and the actions that make sense in the pack's phase at the bottom, apart from the tags. Long names and
 * details end in "…" instead of widening the window.
 */
public final class PackHero extends JPanel {
    private static final int MIN_HEIGHT = 148;
    private static final int ACTIONS_MIN_WIDTH = 250;
    private static final int PROGRESS_WIDTH = 300;

    private final Consumer<UiAction> dispatch;
    private final ZoneId zone;
    private final PackMenu menu;
    private final JLabel tile = new JLabel();
    private final JLabel name = narrowing();
    private final JLabel version = new JLabel();
    private final JLabel meta = narrowing();
    private final StatusTag created = new StatusTag(Messages.get("pack.created"), StatusTag.Tone.MINE);
    private final Component createdGap = Box.createHorizontalStrut(UIScale.scale(8));
    private final JButton serverIp = new JButton(Icons.get(Icons.Glyph.COPY, 12, null));
    private final JLabel stateMark = new JLabel();
    private final LoaderIcon loader = new LoaderIcon(18, LogoIcon.onDark());
    private final JLabel stateTitle = new JLabel();
    private final JLabel stateDetail = new JLabel();
    // Wraps to a second line when the header is narrow; the gaps are struts so the first tag lines up on the left
    private final JPanel tags = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, UIScale.scale(6))) {
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    };
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIScale.scale(10), 0));
    // Fixed width: the file name changes many times while applying and must not resize the bar
    private final JPanel progress = new JPanel(new BorderLayout(0, 4)) {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(PROGRESS_WIDTH), super.getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    };
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel files = new JLabel();
    private final JLabel speed = new JLabel();
    private final JLabel currentFile = new JLabel();
    private String packId;
    private HeroModel shown;
    private PackUiState lastPack;
    private UiState lastState;
    /** "comprobado hace 5 min" ages while nothing else changes, so the header re-reads it now and then. */
    private final Timer ageing = new Timer(RelativeTime.REFRESH_MS, e -> {
        if (lastPack != null && isShowing()) render(lastPack, lastState);
    });

    public PackHero(Consumer<UiAction> dispatch, ZoneId zone) {
        super(new BorderLayout(UIScale.scale(20), 0));
        this.dispatch = dispatch;
        this.zone = zone;
        this.menu = new PackMenu(dispatch);
        setBackground(Palette.surface());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.line()),
            BorderFactory.createEmptyBorder(18, 24, 20, 16)));
        buttons.setOpaque(false);
        tags.setOpaque(false);

        add(tile, BorderLayout.WEST);
        add(info(), BorderLayout.CENTER);
        add(actions(), BorderLayout.EAST);
    }

    private JPanel info() {
        name.setFont(Fonts.display(name.getFont(), 10));
        version.setFont(new Font(Fonts.MONO_FAMILY, Font.PLAIN, Math.round(getFont().getSize2D() - 1)));
        version.setForeground(Palette.textSecondary());
        version.setBorder(new FlatLineBorder(new Insets(1, 6, 1, 6), Palette.line(), 1, 10));
        // The name gives way first and ends in "…"; the version stays whole beside it
        JPanel nameRow = line();
        nameRow.add(name);
        nameRow.add(Box.createHorizontalStrut(UIScale.scale(10)));
        nameRow.add(version);
        nameRow.add(Box.createHorizontalGlue());

        meta.setForeground(Palette.textSecondary());
        serverIp.setFont(new Font(Fonts.MONO_FAMILY, Font.PLAIN, Math.round(getFont().getSize2D() - 1.5f)));
        serverIp.setHorizontalTextPosition(JButton.LEFT);
        serverIp.setToolTipText(Messages.get("hero.copyIp"));
        ButtonStyle.apply(serverIp, ButtonStyle.SECONDARY, ButtonStyle.SMALL);
        serverIp.addActionListener(e -> dispatchFor(UiAction.CopyServerIp::new));
        // Both give way when narrow, the address down to a short stub that still shows it is there
        serverIp.setMinimumSize(new Dimension(UIScale.scale(90), serverIp.getPreferredSize().height));
        JPanel metaRow = line();
        metaRow.add(created);
        metaRow.add(createdGap);
        metaRow.add(meta);
        metaRow.add(Box.createHorizontalStrut(UIScale.scale(8)));
        metaRow.add(serverIp);
        metaRow.add(Box.createHorizontalGlue());

        stateTitle.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
        stateDetail.setForeground(Palette.textSecondary());
        // One line, as in the design: the detail takes what is left and ends in "…" (full text in the tooltip)
        JPanel stateTitleRow = row(FlowLayout.LEFT);
        stateTitleRow.add(stateMark);
        stateTitleRow.add(stateTitle);
        JPanel stateRow = new JPanel(new BorderLayout(UIScale.scale(6), 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        stateRow.setOpaque(false);
        stateDetail.setMinimumSize(new Dimension(0, 0));
        stateRow.add(stateTitleRow, BorderLayout.WEST);
        stateRow.add(stateDetail, BorderLayout.CENTER);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Box.createVerticalGlue());
        // The review tags sit under the state, away from the buttons on the right
        JPanel[] lines = {nameRow, metaRow, stateRow, tags};
        int[] gapsAfter = {6, 12, 10, 0};
        for (int i = 0; i < lines.length; i++) {
            lines[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(lines[i]);
            if (gapsAfter[i] > 0) info.add(Box.createVerticalStrut(UIScale.scale(gapsAfter[i])));
        }
        info.add(Box.createVerticalGlue());
        // Never wider than the space left: the long lines shorten instead
        info.setMinimumSize(new Dimension(0, 0));
        return info;
    }

    private JPanel actions() {
        JButton folder = iconButton(Icons.Glyph.FOLDER, "hero.openFolder");
        folder.addActionListener(e -> dispatchFor(UiAction.OpenFolder::new));
        JButton more = iconButton(Icons.Glyph.MORE, "hero.more");
        // Right-aligned under the button, so it stays inside the window
        more.addActionListener(e -> menu.show(more, more.getWidth() - menu.getPreferredSize().width, more.getHeight()));
        JPanel tools = row(FlowLayout.RIGHT);
        tools.add(folder);
        tools.add(more);

        bar.setPreferredSize(new Dimension(UIScale.scale(PROGRESS_WIDTH), UIScale.scale(8)));
        files.setForeground(Palette.textSecondary());
        files.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        speed.setForeground(Palette.textSecondary());
        speed.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        currentFile.setForeground(Palette.text());
        currentFile.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        progress.setOpaque(false);
        progress.add(bar, BorderLayout.NORTH);
        JPanel counts = new JPanel(new BorderLayout());
        counts.setOpaque(false);
        counts.add(files, BorderLayout.CENTER);
        counts.add(speed, BorderLayout.EAST);
        progress.add(counts, BorderLayout.CENTER);
        progress.add(currentFile, BorderLayout.SOUTH);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (JPanel part : new JPanel[] {progress, buttons}) {
            part.setAlignmentX(Component.RIGHT_ALIGNMENT);
            stack.add(part);
        }

        // Tools at the top, the phase's actions at the bottom, with at least 16 px between them
        JPanel actions = new JPanel(new BorderLayout(0, UIScale.scale(16))) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension(Math.max(size.width, UIScale.scale(ACTIONS_MIN_WIDTH)), size.height);
            }
        };
        actions.setOpaque(false);
        actions.add(tools, BorderLayout.NORTH);
        actions.add(stack, BorderLayout.SOUTH);
        return actions;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ageing.start();
    }

    @Override
    public void removeNotify() {
        ageing.stop();
        super.removeNotify();
    }

    public void render(PackUiState pack, UiState state) {
        lastPack = pack;
        lastState = state;
        packId = pack.packId();
        PackInfo info = pack.packInfo();
        tile.setIcon(MarkTile.header());
        name.setText(pack.displayName());
        name.setToolTipText(pack.displayName());
        version.setText(info == null ? "" : info.version());
        version.setVisible(info != null);
        String metaText = HeroModel.meta(pack);
        meta.setText(metaText == null ? " " : metaText);
        meta.setToolTipText(metaText);
        created.setVisible(pack.created());
        createdGap.setVisible(pack.created());
        boolean hasIp = info != null && info.serverIp() != null && !info.serverIp().isBlank();
        serverIp.setVisible(hasIp);
        serverIp.setText(hasIp ? info.serverIp() : "");
        menu.setPack(pack);

        HeroModel model = HeroModel.of(pack, state, zone, Instant.now());
        if (!sameLayout(shown, model)) {
            rebuild(model);
        }
        HeroModel.Applying applying = model.applying();
        if (applying != null) {
            bar.setValue(applying.percent());
            bar.getAccessibleContext().setAccessibleDescription(Messages.get("hero.progress", applying.percent()));
            files.setText(applying.files());
            speed.setText(applying.speed());
            // A long name is cut with "…" inside the fixed width; the tooltip keeps it whole
            currentFile.setText(applying.current().isEmpty() ? " " : applying.current());
            currentFile.setToolTipText(applying.current().isEmpty() ? null : applying.current());
        }
        shown = model;
        // The texts above change the height the header needs (the server button only exists with an IP)
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, UIScale.scale(MIN_HEIGHT));
        return size;
    }

    /** Everything but the progress numbers, which change many times per second while applying. */
    private static boolean sameLayout(HeroModel before, HeroModel model) {
        return before != null && before.mark() == model.mark() && before.title().equals(model.title())
            && Objects.equals(before.detail(), model.detail()) && before.tags().equals(model.tags())
            && before.actions().equals(model.actions()) && (before.applying() == null) == (model.applying() == null);
    }

    private void rebuild(HeroModel model) {
        // Checking and downloading are the long waits: there the mark itself moves
        stateMark.setIcon(model.mark() == StatusMark.Kind.WORKING ? loader : StatusMark.of(model.mark()));
        stateTitle.setText(model.title());
        stateDetail.setText(model.detail() == null ? "" : "· " + model.detail());
        stateDetail.setToolTipText(model.detail());

        tags.removeAll();
        for (HeroModel.Tag tag : model.tags()) {
            tags.add(new StatusTag(tag.text(), tag.tone()));
            tags.add(Box.createHorizontalStrut(UIScale.scale(6)));
        }
        tags.setVisible(!model.tags().isEmpty());

        progress.setVisible(model.applying() != null);

        buttons.removeAll();
        for (HeroModel.Action action : model.actions()) {
            JButton button = new JButton(action.label(), action.icon() == null ? null : Icons.get(action.icon()));
            ButtonStyle.apply(button, action.style());
            button.setEnabled(action.action() != null);
            if (action.action() != null) {
                button.addActionListener(e -> dispatch.accept(action.action()));
            }
            buttons.add(button);
        }
        revalidate();
        repaint();
    }

    private void dispatchFor(Function<String, UiAction> action) {
        if (packId != null) {
            dispatch.accept(action.apply(packId));
        }
    }

    private static JButton iconButton(Icons.Glyph glyph, String key) {
        JButton button = new JButton(Icons.get(glyph));
        button.setToolTipText(Messages.get(key));
        button.getAccessibleContext().setAccessibleName(Messages.get(key));
        ButtonStyle.apply(button, ButtonStyle.SECONDARY, ButtonStyle.ICON);
        return button;
    }

    /** A label that gets narrower, ending in "…", but never shorter than its text. */
    private static JLabel narrowing() {
        return new JLabel() {
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
        };
    }

    /** A left-to-right line whose labels shorten with "…" when the header gets narrow. */
    private static JPanel line() {
        // Gives way in width only: its height stays what its tallest part needs
        JPanel line = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
        };
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setOpaque(false);
        return line;
    }

    private static JPanel row(int align) {
        JPanel row = new JPanel(new FlowLayout(align, UIScale.scale(6), 0));
        row.setOpaque(false);
        return row;
    }
}
