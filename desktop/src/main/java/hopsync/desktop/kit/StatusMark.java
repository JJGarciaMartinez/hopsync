package hopsync.desktop.kit;

import com.formdev.flatlaf.icons.FlatAbstractIcon;
import com.formdev.flatlaf.util.ColorFunctions;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * The small mark in front of a status line. Never the only signal: it always goes next to text.
 */
public final class StatusMark extends FlatAbstractIcon {

    public enum Kind {
        /** Grey dot: unchecked, cancelled. */
        IDLE,
        /** Turquoise dot with a halo: up to date. */
        OK,
        /** Amber dot with a halo: changes waiting, applied with errors. */
        WARN,
        /** Rose dot with a halo: the update failed. */
        ERROR,
        /** Empty ring: waiting in the apply queue. */
        QUEUED,
        /** Coral dot with a halo: checking or applying; the pack header shows the animated mark instead. */
        WORKING,
        /** Turquoise ring filled up to the percent: downloading. */
        PROGRESS
    }

    /** A 9 px dot inside a 3 px halo. */
    private static final int SIZE = 15;

    private final Kind kind;
    private final int percent;

    private StatusMark(Kind kind, int percent) {
        super(SIZE, SIZE, null);
        this.kind = kind;
        this.percent = percent;
    }

    public static StatusMark of(Kind kind) {
        return new StatusMark(kind, 0);
    }

    public static StatusMark progress(int percent) {
        return new StatusMark(Kind.PROGRESS, Math.clamp(percent, 0, 100));
    }

    public Kind kind() {
        return kind;
    }

    /** The color that stands for a state, shared by the dot, the library badge and its text. */
    public static Color colorOf(Kind kind) {
        return switch (kind) {
            case IDLE -> Palette.textFaint();
            case OK -> Palette.mine();
            case WARN -> Palette.update();
            case ERROR -> Palette.error();
            case QUEUED -> Palette.textSecondary();
            case WORKING, PROGRESS -> Palette.action();
        };
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g) {
        switch (kind) {
            case IDLE -> dot(g, Palette.textFaint(), false);
            case OK -> dot(g, Palette.mine(), true);
            case WARN -> dot(g, Palette.update(), true);
            case ERROR -> dot(g, Palette.error(), true);
            case QUEUED -> {
                g.setColor(Palette.textSecondary());
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Float(4, 4, 7, 7));
            }
            case WORKING -> dot(g, Palette.action(), true);
            case PROGRESS -> {
                g.setColor(Palette.raised());
                g.fill(new Ellipse2D.Float(0, 0, SIZE, SIZE));
                g.setColor(Palette.mine());
                g.fill(new Arc2D.Float(0, 0, SIZE, SIZE, 90, -360f * percent / 100, Arc2D.PIE));
            }
        }
    }

    private static void dot(Graphics2D g, Color color, boolean halo) {
        if (halo) {
            g.setColor(ColorFunctions.fade(color, 0.18f));
            g.fill(new Ellipse2D.Float(0, 0, SIZE, SIZE));
        }
        g.setColor(color);
        g.fill(new Ellipse2D.Float(3, 3, 9, 9));
    }
}
