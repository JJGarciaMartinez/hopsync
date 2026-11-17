package hopsync.desktop.kit;

import com.formdev.flatlaf.util.UIScale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderIconTest {

    @AfterEach
    void restoreMotion() {
        LoaderIcon.setReducedMotion(false);
    }

    @Test
    void easingMatchesTheBrandCurve() {
        assertEquals(0, LoaderIcon.ease(0), 1e-4);
        assertEquals(1, LoaderIcon.ease(1), 1e-4);
        // An ease-in: slow start, fast end
        assertTrue(LoaderIcon.ease(0.5f) < 0.5f);
        assertTrue(LoaderIcon.ease(0.9f) > 0.8f);
    }

    @Test
    void thePieceFallsThenRestsAndFades() {
        assertEquals(LoaderIcon.FALL, LoaderIcon.drop(0), 1e-4);
        assertEquals(0, LoaderIcon.drop(0.6f), 1e-4);
        assertEquals(0, LoaderIcon.drop(0.9f), 1e-4);
        assertEquals(0, LoaderIcon.alpha(0), 1e-4);
        assertEquals(1, LoaderIcon.alpha(0.5f), 1e-4);
        assertEquals(0, LoaderIcon.alpha(0.99999f), 1e-3);
    }

    @Test
    void reducedMotionPaintsTheStillMarkUnderItsHeadroom() {
        LoaderIcon.setReducedMotion(true);
        LoaderIcon loader = new LoaderIcon(48, LogoIcon.onDark());
        // 7 of 32 units of room above the mark for the falling piece
        assertEquals(UIScale.scale(48 + 11), loader.getIconHeight());
        BufferedImage still = paint(new LogoIcon(48, LogoIcon.onDark()), 0);
        assertArrayEquals(pixels(still), pixels(paint(loader, -UIScale.scale(11))));
    }

    private static BufferedImage paint(javax.swing.Icon icon, int y) {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        icon.paintIcon(new JLabel(), g, 0, y);
        g.dispose();
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        int[] rgb = image.getRGB(0, 0, 48, 48, null, 0, 48);
        return Arrays.copyOf(rgb, rgb.length);
    }
}
