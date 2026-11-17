package hopsync.desktop.kit;

import com.formdev.flatlaf.icons.FlatAbstractIcon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * The HopSync mark centered on a rounded tile: it stands for a pack in the pack header, the library and the
 * add-pack wizard. Replaces the Minecraft block tiles; every pack shows the same mark, its state is told by
 * the status line next to it.
 */
public final class MarkTile extends FlatAbstractIcon {
    private final float arc;
    private final Color background;
    private final Color border;
    private final int markSize;
    private final LogoIcon.Colors colors;

    private MarkTile(int size, float arc, Color background, Color border, int markSize, LogoIcon.Colors colors) {
        super(size, size, null);
        this.arc = arc;
        this.background = background;
        this.border = border;
        this.markSize = markSize;
        this.colors = colors;
    }

    /** Pack header: 64 px dark tile, radius 14, the mark at 44 px. */
    public static MarkTile header() {
        return new MarkTile(64, 14, Palette.background(), Palette.line(), 44, LogoIcon.onDark());
    }

    /** Library row: 36 px surface tile, radius 9, the small mark at 22 px. */
    public static MarkTile library(LogoIcon.Colors colors) {
        return new MarkTile(36, 9, Palette.surface(), Palette.line(), 22, colors);
    }

    /** Valid link card in the add-pack wizard: 42 px dark tile, radius 10, the mark at 28 px. */
    public static MarkTile link() {
        return new MarkTile(42, 10, Palette.background(), null, 28, LogoIcon.onDark());
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(background);
        g.fill(new RoundRectangle2D.Float(0, 0, width, height, arc * 2, arc * 2));
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(1));
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, arc * 2 - 1, arc * 2 - 1));
        }
        float inset = (width - markSize) / 2f;
        g.translate(inset, inset);
        LogoIcon.paint(g, markSize, colors, markSize <= LogoIcon.SMALL_MAX);
    }
}
