package hopsync.desktop.kit;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Remote images for the UI: fetched and decoded off the EDT, downscaled, kept in a small memory LRU. */
public final class RemoteImages {
    private static final System.Logger LOG = System.getLogger(RemoteImages.class.getName());
    private static final int MAX_ENTRIES = 64;

    @FunctionalInterface
    public interface Source {
        byte[] get(String url) throws IOException;
    }

    private record Key(String url, int edgePx) { }

    private final Source source;
    private final Executor background;
    private final Map<Key, BufferedImage> memory = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, BufferedImage> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private final Set<String> failed = new HashSet<>();
    private final Map<Key, List<Consumer<BufferedImage>>> loading = new HashMap<>();

    public RemoteImages(Source source, Executor background) {
        this.source = source;
        this.background = background;
    }

    /** The image if it is already in memory; {@code null} when absent or when loading it failed. */
    public synchronized BufferedImage peek(String url, int edgePx) {
        return memory.get(new Key(url, edgePx));
    }

    /** Calls {@code onLoaded} on the EDT with the image; never calls it when the image cannot be loaded. */
    public void load(String url, int edgePx, Consumer<BufferedImage> onLoaded) {
        Key key = new Key(url, edgePx);
        BufferedImage cached;
        synchronized (this) {
            if (failed.contains(url)) {
                return;
            }
            cached = memory.get(key);
            if (cached == null) {
                List<Consumer<BufferedImage>> waiting = loading.get(key);
                if (waiting != null) {
                    waiting.add(onLoaded);
                    return;
                }
                waiting = new ArrayList<>();
                waiting.add(onLoaded);
                loading.put(key, waiting);
            }
        }
        if (cached != null) {
            BufferedImage image = cached;
            SwingUtilities.invokeLater(() -> onLoaded.accept(image));
            return;
        }
        background.execute(() -> finish(key));
    }

    private void finish(Key key) {
        BufferedImage image = null;
        try {
            image = fetch(key);
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not load image " + key.url() + ": " + e);
        }
        List<Consumer<BufferedImage>> waiting;
        synchronized (this) {
            waiting = loading.remove(key);
            if (image == null) {
                failed.add(key.url());
            } else {
                memory.put(key, image);
            }
        }
        if (image != null && waiting != null) {
            BufferedImage done = image;
            SwingUtilities.invokeLater(() -> waiting.forEach(callback -> callback.accept(done)));
        }
    }

    private BufferedImage fetch(Key key) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(source.get(key.url())));
        if (decoded == null) {
            throw new IOException("not an image");
        }
        return scaled(decoded, key.edgePx());
    }

    private static BufferedImage scaled(BufferedImage image, int edgePx) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= edgePx) {
            return image;
        }
        double ratio = (double) edgePx / longest;
        int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
