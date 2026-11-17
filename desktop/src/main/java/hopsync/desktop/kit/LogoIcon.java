package hopsync.desktop.kit;

import com.formdev.flatlaf.icons.FlatAbstractIcon;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The HopSync mark, "Tu caja": an open box (the brand) holding the player's things (turquoise) while a new
 * piece drops in (coral). Drawn on a 32-unit grid from one geometry, so the library header, the window icon
 * and the installer icons in {@code packaging/} all come from the same shapes. At 24 px and below it uses
 * the small version: one larger piece inside, so no gap falls under one pixel.
 */
public final class LogoIcon extends FlatAbstractIcon {
    /** Largest size, in pixels, that uses the small version. */
    public static final int SMALL_MAX = 24;
    /** Largest app icon size, in pixels, where the mark fills more of its tile. */
    static final int TIGHT_MAX = 16;
    private static final float GRID = 32;
    private static final int[] WINDOW_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    /** Fill of each piece: the box, what the player already has, what arrives. */
    public record Colors(Color box, Color mine, Color incoming) {
    }

    /** The mark in color on the dark app background. */
    public static Colors onDark() {
        return new Colors(Palette.brand(), Palette.mine(), Palette.action());
    }

    /** The mark in color on paper: documents, the web, print. */
    public static Colors onLight() {
        return new Colors(Palette.printBox(), Palette.printMine(), Palette.printAction());
    }

    public static Colors oneInk(Color ink) {
        return new Colors(ink, ink, ink);
    }

    private static final Shape BOX = rounded(
        new float[][] {{2, 9}, {8, 9}, {8, 24}, {24, 24}, {24, 9}, {30, 9}, {30, 30}, {2, 30}},
        new float[] {1.5f, 1.5f, 3, 3, 1.5f, 1.5f, 8, 8});
    private static final Shape CIRCLE = new Ellipse2D.Float(10, 16, 6, 6);
    private static final Shape SQUARE = rounded(new float[][] {{18, 15}, {22, 15}, {22, 22}, {18, 22}}, 1.2f);
    private static final Shape TRIANGLE = rounded(new float[][] {{16, 1}, {21, 7}, {11, 7}}, 1.4f);
    private static final Shape SMALL_SQUARE = rounded(new float[][] {{11, 13}, {21, 13}, {21, 22}, {11, 22}}, 2);
    private static final Shape SMALL_TRIANGLE = rounded(new float[][] {{16, 1}, {21.5f, 8}, {10.5f, 8}}, 1.4f);

    private final Colors colors;

    public LogoIcon(int size, Colors colors) {
        super(size, size, null);
        this.colors = colors;
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g) {
        paint(g, width, colors, width <= SMALL_MAX);
    }

    /** The app icon at {@code size} pixels: the mark in color on a dark rounded tile. */
    public static BufferedImage appImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            antialias(g);
            float unit = size / GRID;
            g.setColor(Palette.background());
            g.fill(new RoundRectangle2D.Float(0, 0, size, size, 15 * unit, 15 * unit));
            g.setColor(new Color(255, 255, 255, 26));
            g.setStroke(new BasicStroke(unit));
            g.draw(new RoundRectangle2D.Float(0.5f * unit, 0.5f * unit, 31 * unit, 31 * unit, 14 * unit, 14 * unit));
            // The mark sits at 0.625 of the tile, 6 units from each edge; at 16 px that leaves gaps under one
            // pixel, so there it fills 0.8 of the tile
            float scale = size <= TIGHT_MAX ? 0.8f : 0.625f;
            float inset = GRID * (1 - scale) / 2;
            g.translate(inset * unit, inset * unit);
            paint(g, size * scale, onDark(), size <= SMALL_MAX);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** The app icon in the usual window icon sizes, for {@code Window.setIconImages}. */
    public static List<Image> windowImages() {
        return IntStream.of(WINDOW_SIZES).mapToObj(LogoIcon::appImage).map(Image.class::cast).toList();
    }

    /** The mark alone on a transparent square of {@code size} pixels. */
    public static BufferedImage image(int size, Colors colors) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            antialias(g);
            paint(g, size, colors, size <= SMALL_MAX);
        } finally {
            g.dispose();
        }
        return image;
    }

    static void paint(Graphics2D g, float size, Colors colors, boolean small) {
        paint(g, size, colors, small, 0, 1);
    }

    /**
     * @param drop  how far above its place the incoming piece is, in grid units (the loader animates it)
     * @param alpha opacity of the incoming piece
     */
    static void paint(Graphics2D g, float size, Colors colors, boolean small, float drop, float alpha) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            antialias(g2);
            g2.scale(size / GRID, size / GRID);
            g2.setColor(colors.box());
            g2.fill(BOX);
            g2.setColor(colors.mine());
            if (small) {
                g2.fill(SMALL_SQUARE);
            } else {
                g2.fill(CIRCLE);
                g2.fill(SQUARE);
            }
            if (alpha > 0) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1, alpha)));
                g2.translate(0, -drop);
                g2.setColor(colors.incoming());
                g2.fill(small ? SMALL_TRIANGLE : TRIANGLE);
            }
        } finally {
            g2.dispose();
        }
    }

    private static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Shape rounded(float[][] points, float radius) {
        float[] radii = new float[points.length];
        Arrays.fill(radii, radius);
        return rounded(points, radii);
    }

    /**
     * Polygon whose every corner is a quadratic curve through the vertex, with its radius capped at half of
     * each adjacent edge; the same rounding as the brand preview, so the shapes match it exactly.
     */
    private static Shape rounded(float[][] points, float[] radii) {
        int n = points.length;
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < n; i++) {
            float[] p = points[i];
            float[] a = points[(i - 1 + n) % n];
            float[] b = points[(i + 1) % n];
            float la = (float) Math.hypot(a[0] - p[0], a[1] - p[1]);
            float lb = (float) Math.hypot(b[0] - p[0], b[1] - p[1]);
            float k = Math.min(radii[i], Math.min(la / 2, lb / 2));
            float x1 = p[0] + (a[0] - p[0]) * k / la;
            float y1 = p[1] + (a[1] - p[1]) * k / la;
            float x2 = p[0] + (b[0] - p[0]) * k / lb;
            float y2 = p[1] + (b[1] - p[1]) * k / lb;
            if (i == 0) {
                path.moveTo(x1, y1);
            } else {
                path.lineTo(x1, y1);
            }
            path.quadTo(p[0], p[1], x2, y2);
        }
        path.closePath();
        return path;
    }
}
