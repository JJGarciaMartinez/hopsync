package hopsync.platform.http;

import hopsync.core.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Remote images kept on disk, one file per URL, so each is fetched once. */
public final class ImageCache {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    @FunctionalInterface
    public interface Fetcher {
        byte[] get(String url, long maxBytes) throws IOException;
    }

    private final Fetcher fetcher;
    private final Path dir;

    public ImageCache(Fetcher fetcher, Path dir) {
        this.fetcher = fetcher;
        this.dir = dir;
    }

    public byte[] load(String url) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("Image URL must be https: " + url);
        }
        Path file = dir.resolve(sha256Hex(url));
        if (Files.isRegularFile(file)) {
            return Files.readAllBytes(file);
        }
        byte[] bytes = fetcher.get(url, MAX_BYTES);
        Files.createDirectories(dir);
        // Unique temp name: concurrent loads of one URL must not share a part file
        Path part = Files.createTempFile(dir, "img", ".part");
        try {
            Files.write(part, bytes);
            AtomicFiles.move(part, file);
        } finally {
            Files.deleteIfExists(part);
        }
        return bytes;
    }

    private static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
