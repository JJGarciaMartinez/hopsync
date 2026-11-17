package hopsync.desktop.screens.library;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.LogoIcon;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The pack library on the left: every pack with its status, a search once there are
 * {@value UiState#PACK_SEARCH_FROM} packs, "Añadir pack" and the app settings. Selecting a pack (mouse, arrows)
 * dispatches {@link UiAction.SelectPack}; typing in the search dispatches {@link UiAction.SetPackQuery}.
 */
public final class PackLibrary extends JPanel {
    private static final int WIDTH = 232;

    private final Consumer<UiAction> dispatch;
    private final DefaultListModel<PackUiState> model = new DefaultListModel<>();
    private final JList<PackUiState> list = new JList<>(model);
    private final PackCell cell = new PackCell();
    private final JLabel title = new JLabel();
    private final JTextField search = new JTextField();
    private final JPanel searchRow = new JPanel(new BorderLayout());
    private final JLabel noMatch = new JLabel();
    private boolean rendering;

    public PackLibrary(Consumer<UiAction> dispatch) {
        super(new BorderLayout());
        this.dispatch = dispatch;
        setBackground(Palette.sidebar());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Palette.line()));
        setPreferredSize(new Dimension(UIScale.scale(WIDTH), 0));
        setMinimumSize(getPreferredSize());

        add(header(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        noMatch.setForeground(Palette.textSecondary());
        noMatch.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 16));
        noMatch.setVisible(false);
        center.add(noMatch, BorderLayout.NORTH);
        center.add(packList(), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
    }

    private JPanel header() {
        JLabel logo = new JLabel("HopSync", new LogoIcon(26, LogoIcon.onDark()), JLabel.LEFT);
        logo.setFont(Fonts.display(logo.getFont(), 4));
        logo.setIconTextGap(UIScale.scale(10));
        JPanel brand = new JPanel(new BorderLayout());
        brand.setOpaque(false);
        brand.setPreferredSize(new Dimension(0, UIScale.scale(58)));
        brand.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.line()),
            BorderFactory.createEmptyBorder(0, 16, 0, 16)));
        brand.add(logo, BorderLayout.CENTER);

        title.putClientProperty(FlatClientProperties.STYLE, "font: semibold -2");
        title.setForeground(Palette.textFaint());
        JPanel label = new JPanel(new BorderLayout());
        label.setOpaque(false);
        label.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        label.add(title, BorderLayout.CENTER);

        searchField();
        searchRow.setOpaque(false);
        searchRow.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.setVisible(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(brand, BorderLayout.NORTH);
        header.add(label, BorderLayout.CENTER);
        header.add(searchRow, BorderLayout.SOUTH);
        return header;
    }

    /** Sunken search over the packs; Esc clears it. */
    private void searchField() {
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("library.search"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
            Icons.get(Icons.Glyph.SEARCH, 14, Palette.textFaint()));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getAccessibleContext().setAccessibleName(Messages.get("library.search"));
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
                if (!rendering) dispatch.accept(new UiAction.SetPackQuery(search.getText()));
            }
        });
        search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "clearSearch");
        search.getActionMap().put("clearSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search.setText("");
            }
        });
    }

    private JScrollPane packList() {
        list.setOpaque(false);
        list.setBackground(Palette.sidebar());
        list.setCellRenderer(cell);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(UIScale.scale(53));
        list.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        list.addListSelectionListener(e -> {
            PackUiState selected = list.getSelectedValue();
            if (!rendering && !e.getValueIsAdjusting() && selected != null) {
                dispatch.accept(new UiAction.SelectPack(selected.packId()));
            }
        });
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(list.locationToIndex(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        list.addMouseMotionListener(hover);
        list.addMouseListener(hover);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private JPanel footer() {
        JButton add = new DashedButton(Messages.get("library.add"), Icons.get(Icons.Glyph.PLUS));
        add.addActionListener(e -> dispatch.accept(new UiAction.AddPack()));
        JButton appSettings = new JButton(Messages.get("library.appSettings"), Icons.get(Icons.Glyph.GEAR));
        ButtonStyle.apply(appSettings, ButtonStyle.QUIET);
        appSettings.setHorizontalAlignment(JButton.LEFT);
        appSettings.addActionListener(e -> dispatch.accept(new UiAction.OpenAppSettings(Sheet.AppSettings.Section.GENERAL)));

        JPanel addRow = new JPanel(new GridLayout(1, 1));
        addRow.setOpaque(false);
        addRow.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        addRow.add(add);
        JPanel foot = new JPanel(new GridLayout(1, 1));
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.line()),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        foot.add(appSettings);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(addRow, BorderLayout.NORTH);
        footer.add(foot, BorderLayout.SOUTH);
        return footer;
    }

    public void render(UiState state) {
        rendering = true;
        try {
            title.setText(Messages.get("library.title", state.packs().size()).toUpperCase(Locale.ROOT));
            cell.setNames(LibraryStatus.names(new ArrayList<>(state.packs().values())));
            searchRow.setVisible(state.packSearchShown());
            if (!search.getText().equals(state.packQuery())) {
                search.setText(state.packQuery());
            }
            List<PackUiState> packs = state.shownPacks();
            noMatch.setVisible(packs.isEmpty() && !state.packs().isEmpty());
            // Wraps inside the library instead of ending in "…": the search text is the part that matters
            noMatch.setText("<html><body style='width:" + UIScale.scale(WIDTH - 40) + "px'>"
                + escape(Messages.get("library.noMatch", state.packQuery().strip())) + "</body></html>");
            if (!sameEntries(packs)) {
                model.clear();
                model.addAll(packs);
            }
            int selected = packs.stream().map(PackUiState::packId).toList().indexOf(state.selectedPackId());
            if (list.getSelectedIndex() != selected) {
                list.setSelectedIndex(selected);
            }
        } finally {
            rendering = false;
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean sameEntries(List<PackUiState> packs) {
        if (model.size() != packs.size()) return false;
        for (int i = 0; i < packs.size(); i++) {
            if (!model.get(i).equals(packs.get(i))) return false;
        }
        return true;
    }

    private void setHover(int index) {
        if (cell.hoverIndex() != index) {
            cell.setHoverIndex(index);
            list.repaint();
        }
    }

    /** "Añadir pack": a dashed outline that turns coral on hover, so it reads as a place to add more. */
    private static final class DashedButton extends JButton {
        private boolean hover;

        DashedButton(String text, javax.swing.Icon icon) {
            super(text, icon);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(true);
            setForeground(Palette.textSecondary());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(0, UIScale.scale(40)));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    setForeground(Palette.action());
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    setForeground(Palette.textSecondary());
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                float dash = UIScale.scale(5f);
                g2.setStroke(new BasicStroke(UIScale.scale(1.5f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10,
                    new float[] {dash, dash * 0.7f}, 0));
                g2.setColor(hover ? Palette.action() : Palette.edge());
                int arc = UIScale.scale(20);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
