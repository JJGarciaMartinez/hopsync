package hopsync.desktop.kit;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoIconTest {
    private static final LogoIcon.Colors COLORS = LogoIcon.onDark();

    @Test
    void sixteenPixelsShowEveryPiece() {
        BufferedImage image = LogoIcon.image(16, COLORS);
        assertTrue(contains(image, COLORS.box()), "box");
        assertTrue(contains(image, COLORS.mine()), "the player's piece");
        assertTrue(contains(image, COLORS.incoming()), "the incoming piece");
    }

    @Test
    void sixteenPixelsKeepTheGapInsideTheBox() {
        // Unit (9.5, 18): between the box wall (x 8) and the small inner piece (x 11)
        BufferedImage image = LogoIcon.image(16, COLORS);
        assertEquals(0, alpha(image, 4, 9));
    }

    @Test
    void twentyFourPixelsUseTheSmallVersion() {
        BufferedImage image = LogoIcon.image(24, COLORS);
        assertArrayEquals(pixels(paint(24, true)), pixels(image));
        assertFalse(Arrays.equals(pixels(paint(24, false)), pixels(image)));
    }

    @Test
    void largerSizesUseTheMasterVersion() {
        assertArrayEquals(pixels(paint(25, false)), pixels(LogoIcon.image(25, COLORS)));
    }

    @Test
    void appIconIsADarkTile() {
        BufferedImage image = LogoIcon.appImage(64);
        assertEquals(Palette.background().getRGB(), image.getRGB(32, 6));
        assertEquals(0, alpha(image, 0, 0), "rounded corner");
    }

    @Test
    void sixteenPixelAppIconGivesTheMarkMoreRoom() {
        // The box spans 10 of 16 columns, a larger share of its tile than at 20 px (0.625)
        int sixteen = lightColumns(LogoIcon.appImage(16));
        int twenty = lightColumns(LogoIcon.appImage(20));
        assertTrue(sixteen >= 10);
        assertTrue(sixteen / 16f > twenty / 20f, "only 16 px fills more of the tile");
    }

    @Test
    void icoHoldsEverySize() {
        ByteBuffer ico = ByteBuffer.wrap(IconExport.ico()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, ico.getShort(2));
        assertEquals(IconExport.ICO_SIZES.length, ico.getShort(4));
        assertEquals(16, ico.get(6));
        assertEquals(20, ico.get(6 + 16));
        assertEquals(0, ico.get(6 + 16 * (IconExport.ICO_SIZES.length - 1)), "256 is written as 0");
    }

    @Test
    void icnsHeaderMatchesItsLength() {
        byte[] icns = IconExport.icns();
        assertEquals("icns", new String(icns, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(icns.length, ByteBuffer.wrap(icns).getInt(4));
    }

    private static BufferedImage paint(int size, boolean small) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        LogoIcon.paint(g, size, COLORS, small);
        g.dispose();
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /** Columns holding a light pixel of the box. */
    private static int lightColumns(BufferedImage image) {
        int columns = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (new Color(image.getRGB(x, y)).getRed() > 150 && new Color(image.getRGB(x, y)).getBlue() > 150) {
                    columns++;
                    break;
                }
            }
        }
        return columns;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static boolean contains(BufferedImage image, Color color) {
        return Arrays.stream(pixels(image)).anyMatch(rgb -> rgb == color.getRGB());
    }
}
