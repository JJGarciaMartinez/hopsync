package hopsync.desktop.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class RemoteImagesTest {
    private final List<BufferedImage> delivered = new ArrayList<>();

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private void drain() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void loadDecodesAndScales() throws Exception {
        byte[] big = png(3000, 1000);
        RemoteImages images = new RemoteImages(url -> big, Runnable::run);
        images.load("u", 300, delivered::add);
        drain();
        assertEquals(1, delivered.size());
        assertEquals(300, delivered.get(0).getWidth());
        assertEquals(100, delivered.get(0).getHeight());
        assertNotNull(images.peek("u", 300));
    }

    @Test
    void decodesWebp() throws Exception {
        byte[] webp;
        try (var in = RemoteImagesTest.class.getResourceAsStream("sample.webp")) {
            webp = in.readAllBytes();
        }
        RemoteImages images = new RemoteImages(url -> webp, Runnable::run);
        images.load("u.webp", 60, delivered::add);
        drain();
        assertEquals(1, delivered.size());
        assertEquals(60, delivered.get(0).getWidth());
        assertEquals(20, delivered.get(0).getHeight());
    }

    @Test
    void secondLoadHitsMemory() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        byte[] small = png(10, 10);
        RemoteImages images = new RemoteImages(url -> {
            calls.incrementAndGet();
            return small;
        }, Runnable::run);
        images.load("u", 64, delivered::add);
        images.load("u", 64, delivered::add);
        drain();
        assertEquals(1, calls.get());
        assertEquals(2, delivered.size());
    }

    @Test
    void failureIsRememberedAndNotRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RemoteImages images = new RemoteImages(url -> {
            calls.incrementAndGet();
            throw new IOException("down");
        }, Runnable::run);
        images.load("u", 64, delivered::add);
        images.load("u", 64, delivered::add);
        drain();
        assertEquals(1, calls.get());
        assertTrue(delivered.isEmpty());
        assertNull(images.peek("u", 64));
    }

    @Test
    void notAnImageIsAFailure() throws Exception {
        RemoteImages images = new RemoteImages(url -> new byte[] {1, 2, 3}, Runnable::run);
        images.load("u", 64, delivered::add);
        drain();
        assertTrue(delivered.isEmpty());
        assertNull(images.peek("u", 64));
    }

    @Test
    void oversizedImageIsRejectedBeforeDecoding() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10_000, 10_000, BufferedImage.TYPE_BYTE_BINARY), "png", out);
        byte[] bomb = out.toByteArray();
        AtomicInteger calls = new AtomicInteger();
        RemoteImages images = new RemoteImages(url -> {
            calls.incrementAndGet();
            return bomb;
        }, Runnable::run);
        images.load("u", 64, delivered::add);
        images.load("u", 64, delivered::add);
        drain();
        assertTrue(delivered.isEmpty());
        assertNull(images.peek("u", 64));
        assertEquals(1, calls.get());
    }

    @Test
    void outOfMemoryIsAFailure() throws Exception {
        RemoteImages images = new RemoteImages(url -> {
            throw new OutOfMemoryError("test");
        }, Runnable::run);
        images.load("u", 64, delivered::add);
        drain();
        assertTrue(delivered.isEmpty());
        assertNull(images.peek("u", 64));
    }

    @Test
    void anErrorNeverLeavesTheImageLoading() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RemoteImages images = new RemoteImages(url -> {
            calls.incrementAndGet();
            throw new LinkageError("test");
        }, Runnable::run);
        assertThrows(LinkageError.class, () -> images.load("u", 64, delivered::add));
        images.load("u", 64, delivered::add);
        drain();
        assertEquals(1, calls.get());
        assertTrue(delivered.isEmpty());
    }

    @Test
    void lruDropsTheOldestBeyondItsLimit() throws Exception {
        byte[] small = png(4, 4);
        RemoteImages images = new RemoteImages(url -> small, Runnable::run);
        for (int i = 0; i < 65; i++) {
            images.load("u" + i, 64, delivered::add);
        }
        drain();
        assertNull(images.peek("u0", 64));
        assertNotNull(images.peek("u1", 64));
        assertNotNull(images.peek("u64", 64));
    }
}
