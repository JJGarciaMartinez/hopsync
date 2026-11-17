package hopsync.desktop.kit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the installer icons ({@code icon.png}, {@code icon.ico}, {@code icon.icns}) from {@link LogoIcon},
 * each size drawn from the vector at its exact pixel size. Run by the {@code brandIcons} Gradle task; the files
 * are generated, never edited by hand.
 */
public final class IconExport {
    /** Windows picks 20 px at 125 % scale and 24 px at 150 %, so both are included. */
    static final int[] ICO_SIZES = {16, 20, 24, 32, 40, 48, 64, 256};
    static final int PNG_SIZE = 512;
    private static final Map<String, Integer> ICNS_TYPES = icnsTypes();

    private IconExport() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: IconExport <output dir>");
        }
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        Files.write(dir.resolve("icon.png"), png(LogoIcon.appImage(PNG_SIZE)));
        Files.write(dir.resolve("icon.ico"), ico());
        Files.write(dir.resolve("icon.icns"), icns());
    }

    /** ICO with one PNG entry per size (supported since Windows Vista). */
    static byte[] ico() {
        List<byte[]> images = new ArrayList<>();
        for (int size : ICO_SIZES) {
            images.add(png(LogoIcon.appImage(size)));
        }
        int offset = 6 + 16 * images.size();
        int total = offset + images.stream().mapToInt(image -> image.length).sum();
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0).putShort((short) 1).putShort((short) images.size());
        for (int i = 0; i < images.size(); i++) {
            int size = ICO_SIZES[i];
            // 0 means 256 in the one-byte width and height fields
            out.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            out.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            out.putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        images.forEach(out::put);
        return out.array();
    }

    /** ICNS with PNG entries for the 1x and 2x sizes macOS asks for. */
    static byte[] icns() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(body)) {
            for (Map.Entry<String, Integer> type : ICNS_TYPES.entrySet()) {
                byte[] image = png(LogoIcon.appImage(type.getValue()));
                out.writeBytes(type.getKey());
                out.writeInt(8 + image.length);
                out.write(image);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(file)) {
            out.writeBytes("icns");
            out.writeInt(8 + body.size());
            out.write(body.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file.toByteArray();
    }

    static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static Map<String, Integer> icnsTypes() {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("icp4", 16);
        types.put("icp5", 32);
        types.put("icp6", 64);
        types.put("ic07", 128);
        types.put("ic08", 256);
        types.put("ic09", 512);
        types.put("ic10", 1024);
        types.put("ic11", 32);
        types.put("ic12", 64);
        types.put("ic13", 512);
        types.put("ic14", 1024);
        return types;
    }
}
