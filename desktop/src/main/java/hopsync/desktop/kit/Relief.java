package hopsync.desktop.kit;

import com.formdev.flatlaf.util.UIScale;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Paints the two heights of the HopSync theme by hand. Raised: a face with a gradient, a border that is
 * darker along its bottom edge, a one-pixel light line inside the top and a dark bevel showing under it.
 * Sunken: a well whose top edge is darker and casts a soft shadow inside. Flat things paint nothing here.
 */
public final class Relief {

    /** Colors of one raised face. */
    public record Raised(Color top, Color bottom, Color border, Color borderBottom, Color highlight, Color bevel) {
    }

    private Relief() {
    }

    /**
     * @param faceTop      top of the face, already moved by hover or press
     * @param faceHeight   height of the face alone
     * @param arc          corner diameter
     * @param bevelVisible how much of the bevel shows under the face, 0 while pressed
     */
    public static void paintRaised(Graphics2D g, Raised colors, float x, float faceTop, float width,
                                   float faceHeight, float arc, float bevelVisible) {
        if (bevelVisible > 0) {
            g.setColor(colors.bevel());
            g.fill(new RoundRectangle2D.Float(x, faceTop + bevelVisible, width, faceHeight, arc, arc));
        }
        float line = UIScale.scale(1f);
        float edge = Math.max(0, (faceHeight - line * 1.5f) / faceHeight);
        g.setPaint(new LinearGradientPaint(0, faceTop, 0, faceTop + faceHeight, new float[] {0, edge, 1},
            new Color[] {colors.border(), colors.border(), colors.borderBottom()}));
        g.fill(new RoundRectangle2D.Float(x, faceTop, width, faceHeight, arc, arc));
        g.setPaint(new GradientPaint(0, faceTop + line, colors.top(), 0, faceTop + faceHeight - line, colors.bottom()));
        g.fill(new RoundRectangle2D.Float(x + line, faceTop + line, width - line * 2, faceHeight - line * 2,
            Math.max(0, arc - line * 2), Math.max(0, arc - line * 2)));
        g.setColor(colors.highlight());
        g.fill(new Rectangle2D.Float(x + arc / 2, faceTop + line, Math.max(0, width - arc), line));
    }

    /** Darker top edge and inner shadow of a sunken area whose outline is already painted. */
    public static void paintSunkenTop(Graphics2D g, float x, float y, float width, float arc) {
        float line = UIScale.scale(1f);
        g.setColor(Palette.sunkenTopEdge());
        g.fill(new Rectangle2D.Float(x + arc / 2, y, Math.max(0, width - arc), line));
        Color shadow = Palette.innerShadow();
        float depth = UIScale.scale(4f);
        g.setPaint(new GradientPaint(0, y + line, shadow, 0, y + line + depth, new Color(shadow.getRGB() & 0xFFFFFF, true)));
        g.fill(new Rectangle2D.Float(x + line, y + line, width - line * 2, depth));
    }
}
