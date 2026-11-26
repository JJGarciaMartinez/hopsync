package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.core.catalog.CatalogProject;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.RemoteImages;
import hopsync.desktop.kit.StatusTag;
import hopsync.desktop.screens.FileChoosers;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * "Agregar mods" for a pack the player created: a search in the mod catalog limited to the pack's Minecraft
 * version and loader, one row per mod with "Agregar" (or "Agregado" once in the pack), a note on the mods that came
 * along as dependencies, "Agregar un jar" for a mod that is not in the catalog, and "Listo". The search waits for
 * the player to stop typing before it is dispatched. A result opens the mod's detail in place of the list, which
 * stays as it was for when the player goes back.
 */
public final class AddModsView extends JPanel {
    /** The results take whatever height the card leaves; this is only their smallest. */
    private static final int RESULTS_HEIGHT = 160;
    private static final int TYPING_PAUSE_MS = 300;
    private static final String LIST = "list";
    private static final String DETAIL = "detail";
    private static final String OPEN = "openDetail";
    private static final int ICON = 48;

    private final Consumer<UiAction> dispatch;
    private final RemoteImages images;
    private final JLabel subtitle = new JLabel();
    private final JTextField search = new JTextField();
    private final JPanel searchBlock = SheetParts.column();
    private final JPanel notes = SheetParts.column();
    private final Results results = new Results();
    private final CardLayout centerCards = new CardLayout();
    private final JPanel center = new JPanel(centerCards);
    private final ModDetailView detail;
    private final JLabel count = new JLabel();
    private final Timer typing;
    private Sheet.AddMods shown;
    private boolean rendering;

    public AddModsView(Consumer<UiAction> dispatch, RemoteImages images) {
        super(new BorderLayout(0, UIScale.scale(12)));
        this.dispatch = dispatch;
        this.images = images;
        detail = new ModDetailView(dispatch, images, RESULTS_HEIGHT);
        setOpaque(false);
        typing = new Timer(TYPING_PAUSE_MS, e -> dispatch.accept(new UiAction.SearchMods(search.getText().strip())));
        typing.setRepeats(false);

        JButton close = new JButton(Icons.get(Icons.Glyph.X));
        close.setToolTipText(Messages.get("addMods.close"));
        close.getAccessibleContext().setAccessibleName(Messages.get("addMods.close"));
        ButtonStyle.apply(close, ButtonStyle.SECONDARY, ButtonStyle.ICON);
        close.addActionListener(e -> dispatch.accept(new UiAction.CloseSheet()));
        subtitle.setForeground(Palette.textSecondary());
        JPanel titles = SheetParts.column();
        SheetParts.stack(titles, 0, SheetParts.title(Messages.get("addMods.title")));
        SheetParts.stack(titles, 3, subtitle);
        JPanel head = new JPanel(new BorderLayout(UIScale.scale(12), 0));
        head.setOpaque(false);
        head.add(titles, BorderLayout.CENTER);
        head.add(close, BorderLayout.EAST);

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("addMods.search"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
            Icons.get(Icons.Glyph.SEARCH, 14, Palette.textFaint()));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getAccessibleContext().setAccessibleName(Messages.get("addMods.search"));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                if (!rendering) typing.restart();
            }
        });

        JScrollPane scroll = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Palette.line()));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(0, UIScale.scale(RESULTS_HEIGHT)));
        scroll.getVerticalScrollBar().setUnitIncrement(UIScale.scale(16));

        JPanel top = SheetParts.column();
        SheetParts.stack(searchBlock, 16, Box.createVerticalStrut(0), search);
        SheetParts.stack(top, 0, head, searchBlock);
        SheetParts.stack(top, 16, notes);

        JButton jar = new JButton(Messages.get("addMods.addJar"), Icons.get(Icons.Glyph.FILE));
        ButtonStyle.apply(jar, ButtonStyle.SECONDARY);
        jar.addActionListener(e -> {
            String packId = shown == null ? null : shown.packId();
            if (packId != null) {
                FileChoosers.chooseJar(this).ifPresent(path -> dispatch.accept(new UiAction.AddOwnJar(packId, path)));
            }
        });
        JButton done = SheetParts.button("addMods.done", ButtonStyle.PRIMARY, dispatch, new UiAction.CloseSheet());
        count.setForeground(Palette.textSecondary());
        count.setHorizontalAlignment(SwingConstants.RIGHT);
        count.setMinimumSize(new Dimension(0, 0));
        JPanel foot = new JPanel(new BorderLayout(UIScale.scale(12), 0));
        foot.setOpaque(false);
        foot.add(jar, BorderLayout.WEST);
        foot.add(count, BorderLayout.CENTER);
        foot.add(done, BorderLayout.EAST);

        center.setOpaque(false);
        center.add(scroll, LIST);
        center.add(detail, DETAIL);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(foot, BorderLayout.SOUTH);
    }

    public void render(Sheet.AddMods sheet, String packName) {
        if (sheet.equals(shown)) return;
        rendering = true;
        try {
            boolean opened = shown == null || !shown.packId().equals(sheet.packId());
            Sheet.AddMods before = shown;
            shown = sheet;
            subtitle.setText(Messages.get("addMods.subtitle", packName, sheet.minecraft(), Messages.loader(sheet.loader())));
            if (opened) {
                typing.stop();
                search.setText(sheet.query());
            }
            renderNotes(sheet);
            count.setText(Messages.get("addMods.count", sheet.added()));
            if (before == null || !Objects.equals(before.results(), sheet.results())
                    || !Objects.equals(before.inPack(), sheet.inPack()) || !before.adding().equals(sheet.adding())
                    || !Objects.equals(before.searchError(), sheet.searchError())) {
                results.show(sheet);
            }
            if (sheet.detail() != null && (before == null || !Objects.equals(before.detail(), sheet.detail())
                    || !Objects.equals(before.inPack(), sheet.inPack()) || !before.adding().equals(sheet.adding()))) {
                detail.render(sheet);
            }
            searchBlock.setVisible(sheet.detail() == null);
            centerCards.show(center, sheet.detail() == null ? LIST : DETAIL);
            revalidate();
            repaint();
        } finally {
            rendering = false;
        }
    }

    /** Forgets the last sheet, so opening it again starts from its own state. */
    public void reset() {
        typing.stop();
        shown = null;
    }

    private void renderNotes(Sheet.AddMods sheet) {
        notes.removeAll();
        if (sheet.error() != null) {
            SheetParts.stack(notes, 10, SheetParts.notice(
                Messages.get("addMods.addFailed", Messages.failure(sheet.error())), Palette.error()));
        } else if (sheet.lastAdded() != null) {
            String text = sheet.lastAdded().alsoAdded().isEmpty()
                ? Messages.get("addMods.justAdded", sheet.lastAdded().title())
                : Messages.get("addMods.withDependencies", sheet.lastAdded().title(),
                    Messages.list(sheet.lastAdded().alsoAdded()));
            SheetParts.stack(notes, 10, SheetParts.notice(text, Palette.mine()));
        }
    }

    /** The rows, always as wide as the sheet: long names and descriptions end in "…". */
    private final class Results extends JPanel implements Scrollable {
        Results() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        void show(Sheet.AddMods sheet) {
            removeAll();
            if (sheet.searchError() != null) {
                add(message(Messages.get("addMods.searchFailed", Messages.failure(sheet.searchError()))));
            } else if (sheet.results() == null) {
                add(message(Messages.get("addMods.searching")));
            } else if (sheet.results().isEmpty()) {
                add(message(Messages.get("addMods.none", sheet.query())));
            } else {
                for (CatalogProject project : sheet.results()) {
                    add(new Hit(project, sheet));
                }
            }
            add(Box.createVerticalGlue());
            revalidate();
            repaint();
        }

        private JComponent message(String text) {
            JLabel label = SheetParts.hint(text);
            label.setBorder(BorderFactory.createEmptyBorder(14, 4, 14, 4));
            label.setAlignmentX(LEFT_ALIGNMENT);
            return label;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return UIScale.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * One mod: its icon, name, author and downloads, one line of description, and "Agregar" or "Agregado". The
     * whole row opens the mod's detail, by mouse or with Enter or Space; its button only adds.
     */
    private final class Hit extends JPanel {
        Hit(CatalogProject project, Sheet.AddMods sheet) {
            super(new BorderLayout(UIScale.scale(12), 0));
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(Messages.get("addMods.openDetail", project.title()));
            setAlignmentX(LEFT_ALIGNMENT);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.line()),
                BorderFactory.createEmptyBorder(10, 4, 10, 4)));

            JLabel name = shrinking(project.title());
            name.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
            String downloads = Messages.get("addMods.downloads", project.downloads());
            JLabel author = shrinking(project.author().isEmpty() ? downloads
                : Messages.get("addMods.by", project.author()) + " · " + downloads);
            author.setForeground(Palette.textFaint());
            author.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            JPanel nameLine = new JPanel();
            nameLine.setLayout(new BoxLayout(nameLine, BoxLayout.X_AXIS));
            nameLine.setOpaque(false);
            nameLine.add(name);
            nameLine.add(Box.createHorizontalStrut(UIScale.scale(6)));
            nameLine.add(author);
            nameLine.add(Box.createHorizontalGlue());
            JLabel description = shrinking(project.description());
            description.setForeground(Palette.textSecondary());
            description.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            description.setToolTipText(project.description().isEmpty() ? null : project.description());
            JPanel text = new JPanel(new GridLayout(2, 1, 0, UIScale.scale(2)));
            text.setOpaque(false);
            text.add(nameLine);
            text.add(description);

            JComponent action = action(project, sheet);
            add(new Thumb(project.title(), project.iconUrl(), ICON, images), BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(action, BorderLayout.EAST);

            Runnable open = () -> dispatch.accept(new UiAction.OpenModDetail(project));
            listenForClicks(this, action, new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) open.run();
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), OPEN);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), OPEN);
            getActionMap().put(OPEN, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    open.run();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        /** Labels with a tooltip take mouse events, so every part of the row but its button listens. */
        private static void listenForClicks(Component component, Component skip, MouseAdapter listener) {
            if (component == skip) return;
            component.addMouseListener(listener);
            if (component instanceof Container container) {
                for (Component child : container.getComponents()) {
                    listenForClicks(child, skip, listener);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!isFocusOwner()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Palette.mine());
                g2.setStroke(new BasicStroke(UIScale.scale(2f)));
                int arc = UIScale.scale(12);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private JComponent action(CatalogProject project, Sheet.AddMods sheet) {
            JComponent action;
            if (sheet.inPack() != null && sheet.inPack().contains(project.id())) {
                action = new StatusTag(Messages.get("addMods.added"), StatusTag.Tone.MINE);
            } else {
                boolean adding = sheet.adding().contains(project.id());
                JButton add = new JButton(Messages.get(adding ? "addMods.adding" : "addMods.add"));
                ButtonStyle.apply(add, ButtonStyle.SECONDARY, ButtonStyle.SMALL);
                add.setEnabled(!adding && sheet.inPack() != null);
                add.getAccessibleContext().setAccessibleName(Messages.get("addMods.add") + " " + project.title());
                add.addActionListener(e -> dispatch.accept(new UiAction.AddCatalogMod(project.id())));
                action = add;
            }
            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            center.add(action);
            return center;
        }
    }

    /** A label that gets narrower, ending in "…", but never shorter than its text. */
    private static JLabel shrinking(String text) {
        return new JLabel(text) {
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
        };
    }
}
