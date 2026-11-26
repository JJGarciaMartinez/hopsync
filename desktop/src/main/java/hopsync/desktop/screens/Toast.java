package hopsync.desktop.screens;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.kit.LogoIcon;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.store.UiState;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Short notice above the status bar ("IP copiada"): dark glass, a raised pill over a blurred copy of what is
 * under it. Shows each new {@link UiState.Toast} once and hides itself after {@value #VISIBLE_MS} ms.
 */
final class Toast extends JLabel {
    private static final int VISIBLE_MS = 2600;
    private static final int ARC = 20;
    private static final float GLASS_ALPHA = 0.70f;
    private static final float EDGE_ALPHA = 0.12f;
    /** What is under the pill is taken this far around it, so the blur does not darken toward its edges. */
    private static final int BLUR_MARGIN = 12;
    /** Halved three times, to 1/8, then stretched back with bilinear filtering: close to a gaussian blur. */
    private static final int BLUR_HALVINGS = 3;

    private final JLayeredPane layer;
    private final Timer hide = new Timer(VISIBLE_MS, e -> setVisible(false));
    private long shownId;

    Toast(JLayeredPane layer) {
        this.layer = layer;
        hide.setRepeats(false);
        setOpaque(false);
        setVisible(false);
        putClientProperty(FlatClientProperties.STYLE, "font: semibold");
        setForeground(Palette.text());
        setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        setIconTextGap(UIScale.scale(9));
        layer.add(this, JLayeredPane.POPUP_LAYER);
    }

    void render(UiState.Toast toast) {
        if (toast == null || toast.id() == shownId) return;
        shownId = toast.id();
        setText(toast.text());
        setIcon(new LogoIcon(16, LogoIcon.onDark()));
        Dimension size = getPreferredSize();
        setBounds((layer.getWidth() - size.width) / 2, layer.getHeight() - size.height - UIScale.scale(44),
            size.width, size.height);
        setVisible(true);
        hide.restart();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = UIScale.scale(ARC);
            Shape pill = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
            Graphics2D glass = (Graphics2D) g2.create();
            glass.clip(pill);
            paintBlurredBackdrop(glass);
            glass.dispose();
            g2.setColor(ColorFunctions.fade(Palette.raised(), GLASS_ALPHA));
            g2.fill(pill);
            g2.setColor(ColorFunctions.fade(Palette.text(), EDGE_ALPHA));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /**
     * Paints what the window shows under the pill, blurred. Swing has no backdrop filter, so the layers under
     * this one are painted again into an image at the screen's scale, clipped to the pill and a margin.
     */
    private void paintBlurredBackdrop(Graphics2D g) {
        double scale = g.getTransform().getScaleX();
        int margin = UIScale.scale(BLUR_MARGIN);
        int width = getWidth() + margin * 2;
        int height = getHeight() + margin * 2;
        BufferedImage under = new BufferedImage((int) Math.ceil(width * scale), (int) Math.ceil(height * scale),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D ug = under.createGraphics();
        try {
            ug.scale(scale, scale);
            ug.translate(margin - getX(), margin - getY());
            // The pane lists its children front to back: the bottom ones are painted first
            Component[] parts = layer.getComponents();
            for (int i = parts.length - 1; i >= 0; i--) {
                Component part = parts[i];
                if (part == this || !part.isVisible() || layer.getLayer(part) >= JLayeredPane.POPUP_LAYER) continue;
                Graphics2D pg = (Graphics2D) ug.create(part.getX(), part.getY(), part.getWidth(), part.getHeight());
                try {
                    part.paint(pg);
                } finally {
                    pg.dispose();
                }
            }
        } finally {
            ug.dispose();
        }
        BufferedImage blurred = under;
        for (int i = 0; i < BLUR_HALVINGS; i++) {
            blurred = resized(blurred, Math.max(1, blurred.getWidth() / 2), Math.max(1, blurred.getHeight() / 2));
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(blurred, -margin, -margin, width, height, null);
    }

    private static BufferedImage resized(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
