package hopsync.desktop.kit;

import com.formdev.flatlaf.ui.FlatTextBorder;
import com.formdev.flatlaf.ui.FlatUIUtils;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Text field border that looks pressed into the window: an outline whose top edge is darker, a soft shadow
 * along the inner top, and FlatLaf's turquoise focus border and halo.
 */
public class SunkenBorder extends FlatTextBorder {

    public SunkenBorder() {
        borderColor = Palette.sunkenBorder();
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        super.paintBorder(c, g, x, y, width, height);
        if (!(c instanceof JComponent jc) || !c.isEnabled() || FlatUIUtils.isPermanentFocusOwner(c)) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            FlatUIUtils.setRenderingHints(g2);
            float focus = FlatUIUtils.getBorderFocusWidth(jc);
            float arc = FlatUIUtils.getBorderArc(jc);
            float left = x + focus;
            float top = y + focus;
            float w = width - focus * 2;
            g2.clip(FlatUIUtils.createComponentRectangle(left, top, w, height - focus * 2, arc));
            Relief.paintSunkenTop(g2, left, top, w, arc);
        } finally {
            g2.dispose();
        }
    }
}
