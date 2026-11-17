package hopsync.desktop.kit;

import com.formdev.flatlaf.icons.FlatAbstractIcon;

import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics2D;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The HopSync mark as the loading indicator: the coral piece drops into the box again and again, every
 * {@value #CYCLE_MS} ms. Shown only while the player waits for something long (checking a pack, downloading).
 * One shared timer repaints the components that painted it in the last moments and stops when none did; with
 * reduced motion the mark stands still.
 */
public final class LoaderIcon extends FlatAbstractIcon {
    static final int CYCLE_MS = 1500;
    /** Grid units the piece falls from. */
    static final float FALL = 7;
    private static final int FRAME_MS = 16;
    private static final long STALE_NS = 250_000_000L;
    private static final Map<Component, Long> PAINTED = new WeakHashMap<>();
    private static final Timer TIMER = new Timer(FRAME_MS, e -> repaintPainted());
    private static boolean reducedMotion;

    private final LogoIcon.Colors colors;

    private final int markSize;

    /** The icon is taller than the mark: the room above it is where the piece falls from, so it is never clipped. */
    public LoaderIcon(int size, LogoIcon.Colors colors) {
        super(size, size + headroom(size), null);
        this.markSize = size;
        this.colors = colors;
    }

    private static int headroom(int size) {
        return (int) Math.ceil(FALL * size / 32f);
    }

    /** Stops the animation (and keeps it stopped) when the player asks for less motion. EDT only. */
    public static void setReducedMotion(boolean reduced) {
        reducedMotion = reduced;
        if (reduced) {
            TIMER.stop();
            PAINTED.clear();
        }
    }

    public static boolean reducedMotion() {
        return reducedMotion;
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g) {
        boolean small = markSize <= LogoIcon.SMALL_MAX;
        g.translate(0, headroom(markSize));
        if (reducedMotion || c == null) {
            LogoIcon.paint(g, markSize, colors, small);
            return;
        }
        PAINTED.put(c, System.nanoTime());
        if (!TIMER.isRunning()) {
            TIMER.start();
        }
        float t = (System.nanoTime() / 1_000_000L % CYCLE_MS) / (float) CYCLE_MS;
        LogoIcon.paint(g, markSize, colors, small, drop(t), alpha(t));
    }

    /** Height above its place at {@code t} of the cycle: it falls during the first 60 %, then rests. */
    static float drop(float t) {
        return t < 0.6f ? FALL * (1 - ease(t / 0.6f)) : 0;
    }

    /** Fades in during the first 25 %, stays, fades out over the last 15 %. */
    static float alpha(float t) {
        if (t < 0.25f) return ease(t / 0.25f);
        if (t < 0.85f) return 1;
        return 1 - ease((t - 0.85f) / 0.15f);
    }

    /** cubic-bezier(.55, 0, .75, 1): the same easing as the brand preview. */
    static float ease(float x) {
        double lo = 0;
        double hi = 1;
        double u = x;
        for (int i = 0; i < 24; i++) {
            u = (lo + hi) / 2;
            if (bezier(u, 0.55, 0.75) < x) {
                lo = u;
            } else {
                hi = u;
            }
        }
        return (float) bezier(u, 0, 1);
    }

    private static double bezier(double u, double p1, double p2) {
        double v = 1 - u;
        return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
    }

    private static void repaintPainted() {
        long now = System.nanoTime();
        Iterator<Map.Entry<Component, Long>> entries = PAINTED.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Component, Long> entry = entries.next();
            if (!entry.getKey().isShowing() || now - entry.getValue() > STALE_NS) {
                entries.remove();
            } else {
                entry.getKey().repaint();
            }
        }
        if (PAINTED.isEmpty()) {
            TIMER.stop();
        }
    }
}
