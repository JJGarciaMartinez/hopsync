package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.RemoteImages;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * A mod's icon on a rounded square: the first letter of its name on a raised tile until the catalog icon is
 * loaded, and for good when it cannot be.
 */
final class Thumb extends JComponent {
    private final String letter;
    private final int size;
    private BufferedImage image;

    Thumb(String title, String iconUrl, int size, RemoteImages images) {
        this.size = size;
        letter = title.isBlank() ? "?" : title.strip().substring(0, 1).toUpperCase(Locale.ROOT);
        setFont(Fonts.display(new JLabel().getFont(), Math.round(size / 3f) - 13));
        if (iconUrl != null && !iconUrl.isBlank()) {
            int edge = UIScale.scale(size);
            image = images.peek(iconUrl, edge);
            if (image == null) {
                images.load(iconUrl, edge, loaded -> {
                    image = loaded;
                    repaint();
                });
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(size), UIScale.scale(size));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int edge = UIScale.scale(size);
            int arc = UIScale.scale(size * 3 / 7);
            int y = (getHeight() - edge) / 2;
            RoundRectangle2D tile = new RoundRectangle2D.Float(0, y, edge, edge, arc, arc);
            g2.setColor(Palette.raised());
            g2.fill(tile);
            if (image != null) {
                g2.clip(tile);
                g2.drawImage(image, 0, y, edge, edge, null);
                return;
            }
            g2.setColor(Palette.textSecondary());
            g2.setFont(getFont());
            var metrics = g2.getFontMetrics();
            g2.drawString(letter, (edge - metrics.stringWidth(letter)) / 2,
                y + (edge - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
