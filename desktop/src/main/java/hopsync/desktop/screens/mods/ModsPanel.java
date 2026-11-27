package hopsync.desktop.screens.mods;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.core.manifest.PackInfo;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.LogoIcon;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.SegmentedControl;
import hopsync.desktop.screens.FileChoosers;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import hopsync.desktop.store.UiState.Filter;
import hopsync.desktop.store.UiState.Tab;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * The selected pack's files: tabs (Pack, Tus mods, Configs) with their counts, the "Solo cambios / Todos"
 * filter when there is a plan, a search box (one search per tab) and the list. A pack the player created with
 * no mods yet shows "Tu caja está vacía" with the ways to fill it instead of an empty list.
 */
public final class ModsPanel extends JPanel {
    private final SegmentedControl<Tab> tabs;
    private final SegmentedControl<Filter> filter;
    private final JTextField search = new JTextField();
    private final ModTable table;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel emptyText = new JLabel();
    private String packId;
    private boolean rendering;

    public ModsPanel(Consumer<UiAction> dispatch) {
        super(new BorderLayout());
        setBackground(Palette.background());
        tabs = new SegmentedControl<>(List.of(Tab.values()), tab -> dispatch.accept(new UiAction.SelectTab(tab)));
        filter = new SegmentedControl<>(List.of(Filter.values()), value -> dispatch.accept(new UiAction.SetFilter(value)));
        filter.setLabel(Filter.CHANGES, Messages.get("mods.filter.changes"));
        filter.setLabel(Filter.ALL, Messages.get("mods.filter.all"));

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.get("mods.search"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get(Icons.Glyph.SEARCH, 14, Palette.textFaint()));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getAccessibleContext().setAccessibleName(Messages.get("mods.search"));
        search.setPreferredSize(new Dimension(UIScale.scale(220), search.getPreferredSize().height));
        search.setMaximumSize(search.getPreferredSize());
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
                if (!rendering) dispatch.accept(new UiAction.SetQuery(search.getText()));
            }
        });

        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.line()),
            BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        bar.add(tabs);
        bar.add(Box.createHorizontalGlue());
        bar.add(filter);
        bar.add(Box.createHorizontalStrut(UIScale.scale(10)));
        bar.add(search);

        table = new ModTable(dispatch);
        center.setOpaque(false);
        center.add(table, LIST);
        center.add(emptyBox(dispatch), EMPTY);
        add(bar, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private static final String LIST = "list";
    private static final String EMPTY = "empty";

    /** The box without its pieces, the coral one still on its way: nothing is yours yet. */
    private JPanel emptyBox(Consumer<UiAction> dispatch) {
        JLabel mark = new JLabel(new LogoIcon(64,
            new LogoIcon.Colors(Palette.edge(), Palette.background(), Palette.action())));
        JLabel title = new JLabel(Messages.get("mods.empty.title"));
        title.setFont(Fonts.display(title.getFont(), 7));
        emptyText.setForeground(Palette.textSecondary());
        emptyText.setHorizontalAlignment(JLabel.CENTER);
        JButton search = new JButton(Messages.get("mods.empty.search"), Icons.get(Icons.Glyph.SEARCH));
        ButtonStyle.apply(search, ButtonStyle.PRIMARY);
        search.addActionListener(e -> {
            if (packId != null) dispatch.accept(new UiAction.OpenAddMods(packId));
        });
        JButton jar = new JButton(Messages.get("addMods.addJar"), Icons.get(Icons.Glyph.FILE));
        ButtonStyle.apply(jar, ButtonStyle.SECONDARY);
        jar.addActionListener(e -> {
            String id = packId;
            if (id == null) return;
            List<Path> jars = FileChoosers.chooseJars(this);
            if (!jars.isEmpty()) dispatch.accept(new UiAction.AddOwnJars(id, jars));
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, UIScale.scale(8), 0));
        buttons.setOpaque(false);
        buttons.add(search);
        buttons.add(jar);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        for (JComponent part : new JComponent[] {mark, title, emptyText, buttons}) {
            part.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        column.add(mark);
        column.add(Box.createVerticalStrut(UIScale.scale(14)));
        column.add(title);
        column.add(Box.createVerticalStrut(UIScale.scale(6)));
        column.add(emptyText);
        column.add(Box.createVerticalStrut(UIScale.scale(18)));
        column.add(buttons);
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);
        box.add(column);
        return box;
    }

    public void render(PackUiState pack, UiState state) {
        rendering = true;
        try {
            ModRowModel model = ModRowModel.of(pack, state);
            tabs.setLabel(Tab.PACK, label("mods.tab.pack", model.packCount()));
            tabs.setLabel(Tab.OWN_MODS, label("mods.tab.own", model.ownCount()));
            tabs.setLabel(Tab.CONFIGS, label("mods.tab.configs", model.configCount()));
            tabs.setSelected(state.tab());
            filter.setVisible(model.showsFilter());
            filter.setSelected(state.filter());
            if (!search.getText().equals(state.query())) {
                search.setText(state.query());
            }
            table.setRows(model.rows(), state.expandedRow());
            table.setVersions(state.modVersions());
            packId = pack.packId();
            boolean empty = pack.created() && pack.manifest() != null && pack.modCount() == 0
                && state.tab() == Tab.PACK;
            if (empty) {
                PackInfo info = pack.packInfo();
                emptyText.setText("<html><div style='text-align:center;width:" + UIScale.scale(360) + "px'>"
                    + ModRowRenderer.escape(Messages.get("mods.empty.text", info.minecraft(),
                    Messages.loader(info.loader()))) + "</div></html>");
            }
            cards.show(center, empty ? EMPTY : LIST);
        } finally {
            rendering = false;
        }
    }

    /** "Pack 77" with the count fainter, as in the design. */
    private static String label(String key, int count) {
        return "<html>" + ModRowRenderer.escape(Messages.get(key)) + " <span style='color:"
            + String.format("#%06X", Palette.textFaint().getRGB() & 0xFFFFFF) + "'>" + count + "</span></html>";
    }
}
