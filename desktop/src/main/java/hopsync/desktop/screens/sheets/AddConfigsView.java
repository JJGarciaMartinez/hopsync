package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.util.UIScale;
import hopsync.core.manifest.ConfigRoot;
import hopsync.core.pack.ConfigMode;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.screens.FileChoosers;
import hopsync.desktop.screens.pack.PlanSummary;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Agregar configs" for a pack the player created: the files and folders found in the pack's game folder (the
 * top-level entries of {@code config/} and the few files of the game folder HopSync allows), more picked from the
 * computer, each with how it is installed once ticked. Nothing leaves the game folder: the picked ones are copied
 * into the pack.
 */
public final class AddConfigsView extends JPanel {
    private static final int LIST_HEIGHT = 260;

    private final Consumer<UiAction> dispatch;
    private final JLabel title = SheetParts.title("");
    private final JLabel intro = new JLabel();
    private final JPanel body = SheetParts.column();
    private final JPanel bottom = new JPanel(new BorderLayout());
    private Sheet.AddConfigs shown;
    private boolean rendering;

    public AddConfigsView(Consumer<UiAction> dispatch) {
        super(new BorderLayout(0, UIScale.scale(16)));
        this.dispatch = dispatch;
        setOpaque(false);
        bottom.setOpaque(false);
        intro.setForeground(Palette.textSecondary());

        JPanel header = SheetParts.column();
        SheetParts.stack(header, 0, title);
        SheetParts.stack(header, 6, intro);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(UIScale.scale(SheetParts.CONTENT_WIDTH), UIScale.scale(LIST_HEIGHT)));
        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public void render(Sheet.AddConfigs sheet, String packName) {
        if (sheet.equals(shown)) return;
        rendering = true;
        try {
            shown = sheet;
            title.setText(Messages.get("addConfigs.title", packName));
            intro.setText(SheetParts.wrap(Messages.get("addConfigs.intro"), SheetParts.CONTENT_WIDTH));
            body.removeAll();
            if (sheet.scanning() && sheet.candidates().isEmpty()) {
                SheetParts.stack(body, 0, SheetParts.hint(Messages.get("addConfigs.scanning")));
            } else if (sheet.candidates().isEmpty()) {
                SheetParts.stack(body, 0, SheetParts.hint(Messages.get("addConfigs.none")));
            }
            for (Sheet.AddConfigs.Candidate candidate : sheet.candidates()) {
                SheetParts.stack(body, 6, row(candidate, sheet.saving()));
            }
            if (sheet.error() != null) {
                SheetParts.stack(body, 12, SheetParts.notice(Messages.get("addConfigs.failed",
                    Messages.failure(sheet.error())), Palette.error()));
            }
            bottom.removeAll();
            JButton cancel = SheetParts.button("sheet.cancel", ButtonStyle.SECONDARY, dispatch, new UiAction.CloseSheet());
            JButton pick = new JButton(Messages.get("addConfigs.pick"));
            ButtonStyle.apply(pick, ButtonStyle.SECONDARY);
            pick.setIcon(Icons.get(Icons.Glyph.FILE));
            pick.setEnabled(!sheet.saving());
            pick.addActionListener(e -> {
                List<Path> paths = FileChoosers.chooseConfigs(this);
                if (!paths.isEmpty()) dispatch.accept(new UiAction.ConfigPathsPicked(sheet.packId(), paths));
            });
            JButton add = SheetParts.button("addConfigs.add", ButtonStyle.PRIMARY, dispatch,
                new UiAction.ConfirmAddConfigs(sheet.packId()));
            add.setEnabled(sheet.canAdd());
            bottom.add(SheetParts.footer(cancel, pick, add), BorderLayout.CENTER);
            revalidate();
            repaint();
        } finally {
            rendering = false;
        }
    }

    /** Forgets the last sheet, so opening it again starts from its own state. */
    public void reset() {
        shown = null;
    }

    private JPanel row(Sheet.AddConfigs.Candidate candidate, boolean saving) {
        JCheckBox box = new JCheckBox(candidate.folder() ? candidate.name() + "/" : candidate.name(), candidate.picked());
        box.setOpaque(false);
        box.setEnabled(!saving);
        box.setFont(new Font(Fonts.MONO_FAMILY, Font.PLAIN, box.getFont().getSize()));
        box.getAccessibleContext().setAccessibleName(candidate.name());
        box.addActionListener(e -> {
            if (!rendering) dispatch.accept(new UiAction.ToggleConfigCandidate(candidate.key()));
        });
        String where = candidate.root() == ConfigRoot.GAME ? Messages.get("mods.configs.gameFolder") + " · " : "";
        JLabel detail = new JLabel(where + (candidate.folder()
            ? Messages.get("offers.detail.files", candidate.files(), PlanSummary.formatBytes(candidate.bytes()))
            : PlanSummary.formatBytes(candidate.bytes())));
        detail.setForeground(Palette.textSecondary());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UIScale.scale(10), 0));
        left.setOpaque(false);
        left.add(box);
        left.add(detail);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(left, BorderLayout.CENTER);
        if (candidate.picked()) {
            row.add(modePicker(candidate), BorderLayout.EAST);
        }
        return row;
    }

    /** A game file is always optional: only the label shows. */
    private JComponent modePicker(Sheet.AddConfigs.Candidate candidate) {
        if (candidate.root() == ConfigRoot.GAME) {
            JLabel fixed = new JLabel(Messages.get("mods.configs.mode.optional"));
            fixed.setForeground(Palette.update());
            return fixed;
        }
        JComboBox<ConfigMode> modes = new JComboBox<>(ConfigMode.values());
        modes.setSelectedItem(candidate.mode());
        modes.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                label.setText(Messages.get(switch ((ConfigMode) value) {
                    case IF_MISSING -> "mods.configs.mode.ifMissing";
                    case ALWAYS -> "mods.configs.mode.always";
                    case OPTIONAL -> "mods.configs.mode.optional";
                }));
                return label;
            }
        });
        modes.getAccessibleContext().setAccessibleName(Messages.get("addConfigs.mode", candidate.name()));
        modes.addActionListener(e -> {
            if (!rendering && modes.getSelectedItem() != candidate.mode()) {
                dispatch.accept(new UiAction.SetCandidateMode(candidate.key(), (ConfigMode) modes.getSelectedItem()));
            }
        });
        return modes;
    }
}
