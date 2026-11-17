package hopsync.desktop.screens.library;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.kit.LogoIcon;
import hopsync.desktop.kit.MarkTile;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.Relief;
import hopsync.desktop.kit.StatusMark;
import hopsync.desktop.store.PackUiState;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.Map;

/**
 * One pack in the library: mark tile, name and status line. The selected pack is raised like a pressed
 * key; the pack under the mouse gets a faint background.
 */
final class PackCell extends JPanel implements ListCellRenderer<PackUiState> {
    private final JLabel tile = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel status = new JLabel();
    private boolean selected;
    private boolean hovered;
    private int hoverIndex = -1;
    private Map<String, String> names = Map.of();

    PackCell() {
        super(new BorderLayout(UIScale.scale(10), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        name.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
        status.putClientProperty(FlatClientProperties.STYLE, "font: -2");
        status.setForeground(Palette.textSecondary());
        status.setIconTextGap(UIScale.scale(6));
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(name);
        text.add(status);
        add(tile, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
    }

    /** Names from {@link LibraryStatus#names}, by pack id. */
    void setNames(Map<String, String> newNames) {
        names = newNames;
    }

    int hoverIndex() {
        return hoverIndex;
    }

    void setHoverIndex(int index) {
        hoverIndex = index;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends PackUiState> list, PackUiState pack, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        LibraryStatus line = LibraryStatus.of(pack);
        tile.setIcon(MarkTile.library(LogoIcon.onDark()));
        String shownName = names.getOrDefault(pack.packId(), pack.displayName());
        name.setText(shownName);
        name.setToolTipText(pack.gameDir().toString());
        name.setForeground(Palette.text());
        status.setText(line.text());
        status.setIcon(line.mark() == StatusMark.Kind.PROGRESS ? StatusMark.progress(line.percent()) : StatusMark.of(line.mark()));
        selected = isSelected;
        hovered = index == hoverIndex;
        getAccessibleContext().setAccessibleName(shownName + ", " + line.text());
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (selected || hovered) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = UIScale.scale(20);
                int inset = UIScale.scale(2);
                int w = getWidth() - 1;
                int h = getHeight() - inset * 2;
                if (selected) {
                    int bevel = UIScale.scale(2);
                    Relief.paintRaised(g2, new Relief.Raised(Palette.selectedTop(), Palette.selectedBottom(),
                        Palette.raisedBorder(), Palette.raisedBorder(), Palette.raisedHighlight(), Palette.raisedBevel()),
                        0, inset, w, h - bevel, arc, bevel);
                } else {
                    g2.setColor(Palette.hoverTint());
                    g2.fillRoundRect(0, inset, w, h, arc, arc);
                }
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }
}
