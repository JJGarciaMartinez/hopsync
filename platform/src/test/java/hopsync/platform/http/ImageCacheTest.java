package hopsync.platform.http;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageCacheTest {
    @TempDir
    Path dir;

    @Test
    void secondLoadComesFromDisk() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        ImageCache cache = new ImageCache((url, max) -> {
            calls.incrementAndGet();
            return new byte[]{1, 2, 3};
        }, dir.resolve("images"));

        byte[] first = cache.load("https://cdn.example/a.png");
        byte[] second = cache.load("https://cdn.example/a.png");

        assertEquals(1, calls.get());
        assertArrayEquals(new byte[]{1, 2, 3}, first);
        assertArrayEquals(first, second);
    }

    @Test
    void rejectsNonHttpsUrls() {
        AtomicInteger calls = new AtomicInteger();
        ImageCache cache = new ImageCache((url, max) -> {
            calls.incrementAndGet();
            return new byte[0];
        }, dir);

        assertThrows(IOException.class, () -> cache.load("http://cdn.example/a.png"));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsTooLargeAnswers() throws IOException {
        ImageCache cache = new ImageCache((url, max) -> {
            throw new SyncException(new SyncFailure.ResponseTooLarge(url, max));
        }, dir);

        assertThrows(SyncException.class, () -> cache.load("https://cdn.example/big.png"));
        try (var files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void concurrentLoadsOfSameUrlDoNotClash() throws Exception {
        CountDownLatch both = new CountDownLatch(2);
        ImageCache cache = new ImageCache((url, max) -> {
            both.countDown();
            try {
                both.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return new byte[]{7, 8, 9};
        }, dir);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> a = pool.submit(() -> cache.load("https://cdn.example/a.png"));
            Future<byte[]> b = pool.submit(() -> cache.load("https://cdn.example/a.png"));
            assertArrayEquals(new byte[]{7, 8, 9}, a.get());
            assertArrayEquals(new byte[]{7, 8, 9}, b.get());
        } finally {
            pool.shutdown();
        }
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }
}
