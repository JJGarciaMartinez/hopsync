package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.kit.Palette;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** The wizard's progress: one 4 px bar per step, coral up to the current one. Flat: it only informs. */
final class StepBar extends JComponent {
    private static final int HEIGHT = 4;
    private static final int GAP = 6;
    private final int total;
    private int step = 1;

    StepBar(int total) {
        this.total = total;
    }

    void setStep(int step) {
        if (this.step != step) {
            this.step = step;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(UIScale.scale(SheetParts.CONTENT_WIDTH), UIScale.scale(HEIGHT));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, UIScale.scale(HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int gap = UIScale.scale(GAP);
            int h = getHeight();
            float width = (getWidth() - gap * (total - 1)) / (float) total;
            for (int i = 0; i < total; i++) {
                g2.setColor(i < step ? Palette.action() : Palette.edge());
                g2.fillRoundRect(Math.round(i * (width + gap)), 0, Math.round(width), h, h, h);
            }
        } finally {
            g2.dispose();
        }
    }
}
