package hopsync.desktop.screens.pack;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.StatusTag;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.UiAction;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Opcionales del creador": under the pack header while the plan is reviewed, one unchecked box per optional file
 * or folder the creator offers. Hidden when there is nothing to decide.
 */
public final class OptionalOffersPanel extends JPanel {
    private final Consumer<UiAction> dispatch;
    private OptionalOffers shown = new OptionalOffers(List.of());

    public OptionalOffersPanel(Consumer<UiAction> dispatch) {
        super(new BorderLayout());
        this.dispatch = dispatch;
        setBackground(Palette.surface());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Palette.line()),
            BorderFactory.createEmptyBorder(12, 24, 14, 24)));
        setVisible(false);
    }

    public void render(PackUiState pack) {
        OptionalOffers offers = OptionalOffers.of(pack);
        if (offers.equals(shown)) return;
        shown = offers;
        removeAll();
        setVisible(!offers.isEmpty());
        if (offers.isEmpty()) {
            revalidate();
            repaint();
            return;
        }
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        JLabel heading = new JLabel(Messages.get("offers.title"));
        heading.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
        title.add(heading);
        title.add(Box.createHorizontalStrut(UIScale.scale(8)));
        title.add(new StatusTag(Messages.get("offers.tag"), StatusTag.Tone.UPDATE));
        content.add(left(title));

        JTextArea note = new Note(Messages.get("offers.note"));
        note.setEditable(false);
        note.setFocusable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setForeground(Palette.textSecondary());
        note.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        content.add(left(note));

        for (OptionalOffers.Offer offer : offers.offers()) {
            JCheckBox box = new JCheckBox(offer.title(), offer.picked());
            box.setOpaque(false);
            box.setFont(new Font(Fonts.MONO_FAMILY, Font.PLAIN, box.getFont().getSize()));
            box.getAccessibleContext().setAccessibleName(offer.title());
            box.addActionListener(e -> dispatch.accept(offer.toggle()));
            JLabel detail = new JLabel(offer.detail());
            detail.setForeground(Palette.textSecondary());
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            row.setOpaque(false);
            row.add(box);
            row.add(Box.createHorizontalStrut(UIScale.scale(10)));
            row.add(detail);
            content.add(left(row));
        }
        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static Component left(JPanel row) {
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static Component left(JTextArea area) {
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    /** Wrapped text that is as tall as its lines need at the panel's width, and never taller. */
    private final class Note extends JTextArea {
        Note(String text) {
            super(text);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = OptionalOffersPanel.this.getWidth() - OptionalOffersPanel.this.getInsets().left
                - OptionalOffersPanel.this.getInsets().right;
            setSize(width > 0 ? width : UIScale.scale(600), Short.MAX_VALUE);
            return super.getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
