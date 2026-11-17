package hopsync.desktop.kit;

import hopsync.core.util.Json;

import javax.imageio.ImageIO;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the brand sheet's assets into {@code brand/}: {@code tokens.json}, the mark, app icon, logotype and
 * pattern as SVG, the mark and app icon as PNG, and the examples of misuse. Everything is drawn by the same code
 * and read from the same theme as the app, so the sheet cannot drift from it. Run by the {@code brandAssets}
 * Gradle task; the output is deterministic, so running it twice changes nothing.
 */
public final class BrandExport {
    static final int[] PNG_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    private static final Color BLACK = new Color(0x111111);
    private static final Color WHITE = Color.WHITE;

    private BrandExport() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: BrandExport <brand dir>");
        }
        HopSyncDark.setup();
        Path dir = Path.of(args[0]);
        Path svg = Files.createDirectories(dir.resolve("svg"));
        Path png = Files.createDirectories(dir.resolve("png"));
        Files.writeString(dir.resolve("tokens.json"), pretty(tokens(), "") + "\n");

        Map<String, LogoIcon.Colors> variants = variants();
        for (Map.Entry<String, LogoIcon.Colors> variant : variants.entrySet()) {
            Files.writeString(svg.resolve("mark-" + variant.getKey() + ".svg"), markSvg(false, variant.getValue()));
            Files.writeString(svg.resolve("mark-small-" + variant.getKey() + ".svg"), markSvg(true, variant.getValue()));
            for (int size : PNG_SIZES) {
                writePng(LogoIcon.image(size, variant.getValue()), png.resolve("mark-" + variant.getKey() + "-" + size + ".png"));
            }
        }
        Files.writeString(svg.resolve("app-icon.svg"), appIconSvg());
        for (int size : PNG_SIZES) {
            writePng(LogoIcon.appImage(size), png.resolve("app-icon-" + size + ".png"));
        }
        Files.writeString(svg.resolve("logo-horizontal-dark.svg"), horizontal(LogoIcon.onDark(), Palette.text()));
        Files.writeString(svg.resolve("logo-horizontal-light.svg"), horizontal(LogoIcon.onLight(), Palette.printBox()));
        Files.writeString(svg.resolve("logo-horizontal-black.svg"), horizontal(LogoIcon.oneInk(BLACK), BLACK));
        Files.writeString(svg.resolve("logo-horizontal-white.svg"), horizontal(LogoIcon.oneInk(WHITE), WHITE));
        Files.writeString(svg.resolve("logo-stacked-dark.svg"), stacked(LogoIcon.onDark(), Palette.text()));
        Files.writeString(svg.resolve("logo-stacked-light.svg"), stacked(LogoIcon.onLight(), Palette.printBox()));
        Files.writeString(dir.resolve("pattern.svg"), pattern());
        misuse(svg, png);
    }

    /** Color on dark, color on light, one ink black, one ink white (negative). */
    static Map<String, LogoIcon.Colors> variants() {
        Map<String, LogoIcon.Colors> variants = new LinkedHashMap<>();
        variants.put("dark", LogoIcon.onDark());
        variants.put("light", LogoIcon.onLight());
        variants.put("black", LogoIcon.oneInk(BLACK));
        variants.put("white", LogoIcon.oneInk(WHITE));
        return variants;
    }

    // ---- SVG ----

    static String markSvg(boolean small, LogoIcon.Colors colors) {
        return svg(32, 32, paths(small, colors, new AffineTransform()));
    }

    static String appIconSvg() {
        AffineTransform mark = AffineTransform.getTranslateInstance(6, 6);
        mark.scale(0.625, 0.625);
        return svg(32, 32, "<rect width=\"32\" height=\"32\" rx=\"7.5\" fill=\"" + hex(Palette.background()) + "\"/>"
            + "<rect x=\"0.5\" y=\"0.5\" width=\"31\" height=\"31\" rx=\"7\" fill=\"none\" stroke=\"#FFFFFF\" stroke-opacity=\"0.1\"/>"
            + paths(false, LogoIcon.onDark(), mark));
    }

    /** Mark 48 high, then "HopSync" in Rubik SemiBold 34, 12 apart, centered on the mark. */
    static String horizontal(LogoIcon.Colors colors, Color text) {
        Shape word = wordmark(34);
        Rectangle2D bounds = word.getBounds2D();
        double x = 48 + 12 - bounds.getX();
        double y = 24 - (bounds.getY() + bounds.getHeight() / 2);
        AffineTransform mark = AffineTransform.getScaleInstance(1.5, 1.5);
        double width = 60 + bounds.getWidth();
        return svg(width, 48, paths(false, colors, mark)
            + path(word, AffineTransform.getTranslateInstance(x, y), text, null));
    }

    /** Mark 64 high centered over "HopSync" in Rubik SemiBold 24, 10 apart. */
    static String stacked(LogoIcon.Colors colors, Color text) {
        Shape word = wordmark(24);
        Rectangle2D bounds = word.getBounds2D();
        double width = Math.max(64, bounds.getWidth());
        AffineTransform mark = AffineTransform.getTranslateInstance((width - 64) / 2, 0);
        mark.scale(2, 2);
        double x = (width - bounds.getWidth()) / 2 - bounds.getX();
        double y = 64 + 10 - bounds.getY();
        return svg(width, 64 + 10 + bounds.getHeight(), paths(false, colors, mark)
            + path(word, AffineTransform.getTranslateInstance(x, y), text, null));
    }

    /** A 72 px tile: the mark in the brand color at half opacity, and in color, on the dark background. */
    static String pattern() {
        AffineTransform first = AffineTransform.getTranslateInstance(8, 8);
        first.scale(0.62, 0.62);
        AffineTransform second = AffineTransform.getTranslateInstance(44, 44);
        second.scale(0.62, 0.62);
        String tile = "<g opacity=\"0.5\">" + paths(false, LogoIcon.oneInk(Palette.brand()), first) + "</g>"
            + "<g opacity=\"0.55\">" + paths(false, LogoIcon.onDark(), second) + "</g>";
        return svg(432, 288, "<defs><pattern id=\"hopsync-mark\" width=\"72\" height=\"72\" patternUnits=\"userSpaceOnUse\">"
            + tile + "</pattern></defs>"
            + "<rect width=\"432\" height=\"288\" fill=\"" + hex(Palette.background()) + "\"/>"
            + "<rect width=\"432\" height=\"288\" fill=\"url(#hopsync-mark)\"/>");
    }

    /** What not to do, each drawn so the manual can show it next to the rule it breaks. */
    static void misuse(Path svg, Path png) throws IOException {
        String ground = "<rect width=\"48\" height=\"48\" rx=\"8\" fill=\"" + hex(Palette.background()) + "\"/>";
        AffineTransform centered = AffineTransform.getTranslateInstance(8, 8);
        Files.writeString(svg.resolve("misuse-rotated.svg"), svg(48, 48, ground
            + "<g transform=\"rotate(18 24 24)\">" + paths(false, LogoIcon.onDark(), centered) + "</g>"));
        Files.writeString(svg.resolve("misuse-stretched.svg"), svg(48, 48, ground
            + "<g transform=\"translate(2 14) scale(1.4 0.62)\">" + paths(false, LogoIcon.onDark(), new AffineTransform()) + "</g>"));
        Files.writeString(svg.resolve("misuse-recolored.svg"), svg(48, 48, ground + paths(false,
            new LogoIcon.Colors(Palette.update(), Palette.action(), Palette.mine()), centered)));
        Files.writeString(svg.resolve("misuse-shadow.svg"), svg(48, 48, ground
            + "<defs><filter id=\"s\"><feDropShadow dx=\"2\" dy=\"3\" stdDeviation=\"1.5\" flood-opacity=\"0.8\"/></filter>"
            + "<linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"#FFFFFF\"/>"
            + "<stop offset=\"1\" stop-color=\"" + hex(Palette.mine()) + "\"/></linearGradient></defs>"
            + "<g filter=\"url(#s)\">" + paths(false, new LogoIcon.Colors(Color.WHITE, Palette.mine(), Palette.action()), centered)
                .replace("fill=\"#FFFFFF\"", "fill=\"url(#g)\"") + "</g>"));
        Files.writeString(svg.resolve("misuse-low-contrast.svg"), svg(48, 48, ground
            + paths(false, LogoIcon.oneInk(Palette.raised()), centered)));
        Files.writeString(svg.resolve("misuse-blocks.svg"), svg(48, 48, ground
            + "<rect x=\"10\" y=\"10\" width=\"28\" height=\"28\" fill=\"#6E4B33\"/>"
            + "<rect x=\"10\" y=\"10\" width=\"28\" height=\"10\" fill=\"#5DBB63\"/>"
            + "<rect x=\"14\" y=\"20\" width=\"4\" height=\"4\" fill=\"#4A9A50\"/>"
            + "<rect x=\"26\" y=\"20\" width=\"4\" height=\"4\" fill=\"#4A9A50\"/>"));
        // The master version squeezed into 16 px, next to the small version made for that size; both blown up
        writePng(zoom(master16(), 6), png.resolve("misuse-master-at-16.png"));
        writePng(zoom(LogoIcon.image(16, LogoIcon.onDark()), 6), png.resolve("small-at-16.png"));
    }

    private static BufferedImage master16() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            LogoIcon.paint(g, 16, LogoIcon.onDark(), false);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage zoom(BufferedImage source, int factor) {
        BufferedImage image = new BufferedImage(source.getWidth() * factor, source.getHeight() * factor,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Palette.background());
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** "HopSync" as outlines in Rubik SemiBold with the brand's -0.025 em tracking, baseline at 0. */
    static Shape wordmark(float size) {
        Font font = new Font(Fonts.DISPLAY_FAMILY, Font.PLAIN, 1).deriveFont(size);
        GlyphVector glyphs = font.createGlyphVector(new FontRenderContext(null, true, true), "HopSync");
        for (int i = 0; i < glyphs.getNumGlyphs(); i++) {
            Point2D at = glyphs.getGlyphPosition(i);
            glyphs.setGlyphPosition(i, new Point2D.Double(at.getX() - i * 0.025 * size, at.getY()));
        }
        return glyphs.getOutline();
    }

    private static String paths(boolean small, LogoIcon.Colors colors, AffineTransform at) {
        StringBuilder out = new StringBuilder();
        for (LogoIcon.Piece piece : LogoIcon.pieces(small)) {
            Color fill = switch (piece.role()) {
                case BOX -> colors.box();
                case MINE -> colors.mine();
                case INCOMING -> colors.incoming();
            };
            out.append(path(piece.shape(), at, fill, piece.role().name().toLowerCase(Locale.ROOT)));
        }
        return out.toString();
    }

    private static String path(Shape shape, AffineTransform at, Color fill, String role) {
        StringBuilder d = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = shape.getPathIterator(at); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> d.append('M').append(num(c[0])).append(' ').append(num(c[1]));
                case PathIterator.SEG_LINETO -> d.append('L').append(num(c[0])).append(' ').append(num(c[1]));
                case PathIterator.SEG_QUADTO -> d.append('Q').append(num(c[0])).append(' ').append(num(c[1]))
                    .append(' ').append(num(c[2])).append(' ').append(num(c[3]));
                case PathIterator.SEG_CUBICTO -> d.append('C').append(num(c[0])).append(' ').append(num(c[1]))
                    .append(' ').append(num(c[2])).append(' ').append(num(c[3]))
                    .append(' ').append(num(c[4])).append(' ').append(num(c[5]));
                case PathIterator.SEG_CLOSE -> d.append('Z');
                default -> throw new IllegalStateException("unknown path segment");
            }
        }
        return "<path" + (role == null ? "" : " data-part=\"" + role + "\"") + " fill-rule=\"nonzero\" fill=\""
            + hex(fill) + "\" d=\"" + d + "\"/>";
    }

    private static String svg(double width, double height, String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + num(width) + "\" height=\"" + num(height)
            + "\" viewBox=\"0 0 " + num(width) + " " + num(height) + "\">" + body + "</svg>\n";
    }

    private static String num(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return "-0".equals(text) ? "0" : text;
    }

    static String hex(Color color) {
        String rgb = String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
        return color.getAlpha() == 255 ? rgb : rgb + String.format(Locale.ROOT, "%02X", color.getAlpha());
    }

    private static void writePng(BufferedImage image, Path file) throws IOException {
        ImageIO.write(image, "png", file.toFile());
    }

    // ---- tokens ----

    static Map<String, Object> tokens() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "HopSync");
        root.put("source", "desktop/src/main/resources/hopsync/desktop/kit/HopSyncDark.properties (written by ./gradlew brandAssets)");

        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("interface", colorList(new String[][] {
            {"HopSync.background", "Tinta: fondo de ventana"},
            {"HopSync.well", "Hundido: campos, carriles, barras de progreso"},
            {"HopSync.surface", "Cabecera del pack, tarjetas, sheets"},
            {"HopSync.raised", "Base de lo elevado que no es botón"},
            {"HopSync.line", "Separadores"},
            {"HopSync.edge", "Bordes de controles"},
            {"HopSync.sidebar", "Biblioteca de packs"},
            {"HopSync.statusBar", "Barra de estado"},
            {"HopSync.rowHover", "Fila bajo el ratón o abierta"},
            {"HopSync.rowDetail", "Detalle de la fila abierta"},
            {"HopSync.text", "Texto principal"},
            {"HopSync.textSecondary", "Texto secundario"},
            {"HopSync.textFaint", "Texto tenue y desactivado"},
            {"HopSync.brand", "La caja: logo, títulos de marca, aviso flotante"},
            {"HopSync.mine", "Lo tuyo: progreso, Todo listo, foco, enlace válido"},
            {"HopSync.action", "Lo que llega: acción principal y mods nuevos"},
            {"HopSync.update", "Mods que cambian de versión"},
            {"HopSync.error", "Quitar y errores"}}));
        colors.put("print", colorList(new String[][] {
            {"HopSync.print.paper", "Papel"},
            {"HopSync.print.box", "La caja sobre papel"},
            {"HopSync.print.mine", "Lo tuyo sobre papel"},
            {"HopSync.print.action", "Lo que llega sobre papel"}}));
        Map<String, Object> states = new LinkedHashMap<>();
        for (StatusMark.Kind kind : StatusMark.Kind.values()) {
            states.put(kind.name().toLowerCase(Locale.ROOT), hex(StatusMark.colorOf(kind)));
        }
        colors.put("state", states);
        root.put("colors", colors);

        Map<String, Object> raised = new LinkedHashMap<>();
        for (String role : List.of("primary", "secondary", "quiet", "danger")) {
            Map<String, Object> parts = new LinkedHashMap<>();
            for (String part : List.of("top", "bottom", "hoverTop", "hoverBottom", "border", "hoverBorder", "borderBottom", "bevel")) {
                parts.put(part, hex(Palette.button(role, part)));
            }
            raised.put(role, parts);
        }
        Map<String, Object> relief = new LinkedHashMap<>();
        relief.put("buttons", raised);
        relief.put("bevel", ordered("rest", UIManager.getInt("HopSync.bevelWidth"), "restPrimary",
            UIManager.getInt("HopSync.bevelWidth.primary"), "hoverExtra", 1));
        relief.put("selected", List.of(hex(Palette.selectedTop()), hex(Palette.selectedBottom())));
        relief.put("segment", List.of(hex(Palette.segmentTop()), hex(Palette.segmentBottom())));
        relief.put("sunken", ordered("border", hex(Palette.sunkenBorder()), "topEdge", hex(Palette.sunkenTopEdge()),
            "innerShadow", hex(Palette.innerShadow())));
        root.put("relief", relief);

        root.put("fonts", List.of(
            font("display", "Rubik", "600 / 700", "Logotipo, nombre del pack, títulos"),
            font("ui", "Figtree", "400 / 500 / 600", "Interfaz"),
            font("mono", "JetBrains Mono", "400 / 500", "Versiones, rutas, archivos")));
        root.put("typeScale", List.of(
            scale("welcomeTitle", "Rubik", 25, 600), scale("packName", "Rubik", 23, 600),
            scale("state", "Figtree", 14, 600), scale("body", "Figtree", 13, 500),
            scale("secondary", "Figtree", 12.5, 400), scale("version", "JetBrains Mono", 11.5, 400)));
        root.put("radius", radii());
        root.put("size", sizes());
        Map<String, Object> motion = new LinkedHashMap<>();
        motion.put("loaderCycleMs", LoaderIcon.CYCLE_MS);
        motion.put("loaderEasing", List.of(0.55, 0, 0.75, 1));
        motion.put("loaderFallUnits", (double) LoaderIcon.FALL);
        motion.put("toastMs", 2600);
        root.put("motion", motion);
        Map<String, Object> mark = new LinkedHashMap<>();
        mark.put("gridUnits", 32);
        mark.put("smallVersionUpToPx", LogoIcon.SMALL_MAX);
        mark.put("clearSpaceUnits", 4);
        mark.put("minimumScreenPx", 16);
        mark.put("minimumPrintMm", 5);
        root.put("mark", mark);
        return root;
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** JSON with two-space indentation, for a tokens file people read; scalars go through {@link Json#write}. */
    static String pretty(Object value, String indent) {
        String inner = indent + "  ";
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            StringBuilder out = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(inner).append(Json.write(entry.getKey())).append(": ").append(pretty(entry.getValue(), inner))
                    .append(++i < map.size() ? ",\n" : "\n");
            }
            return out.append(indent).append('}').toString();
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            boolean scalars = list.stream().noneMatch(item -> item instanceof Map || item instanceof List);
            if (scalars) {
                return Json.write(list).replace(",", ", ");
            }
            StringBuilder out = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                out.append(inner).append(pretty(list.get(i), inner)).append(i + 1 < list.size() ? ",\n" : "\n");
            }
            return out.append(indent).append(']').toString();
        }
        return Json.write(value);
    }

    private static List<Object> colorList(String[][] entries) {
        List<Object> list = new ArrayList<>();
        for (String[] entry : entries) {
            Color color = UIManager.getColor(entry[0]);
            Map<String, Object> token = new LinkedHashMap<>();
            token.put("key", entry[0]);
            token.put("value", color == null ? null : hex(color));
            token.put("role", entry[1]);
            list.add(token);
        }
        return list;
    }

    private static Map<String, Object> font(String role, String family, String weights, String use) {
        Map<String, Object> font = new LinkedHashMap<>();
        font.put("role", role);
        font.put("family", family);
        font.put("weights", weights);
        font.put("use", use);
        return font;
    }

    private static Map<String, Object> scale(String name, String family, double size, int weight) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", name);
        step.put("family", family);
        step.put("sizePx", size);
        step.put("weight", weight);
        return step;
    }

    private static Map<String, Object> radii() {
        Map<String, Object> radius = new LinkedHashMap<>();
        radius.put("button", 10);
        radius.put("iconButton", 8);
        radius.put("field", 10);
        radius.put("segmentTrack", 10);
        radius.put("segment", 7);
        radius.put("pack", 10);
        radius.put("card", 18);
        radius.put("appIconOf32", 7.5);
        return radius;
    }

    private static Map<String, Object> sizes() {
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("window", "1040 × 660 (mínimo 820 × 540)");
        size.put("library", 232);
        size.put("libraryHeader", 58);
        size.put("packHeaderMin", 138);
        size.put("row", 36);
        size.put("statusBar", 28);
        size.put("button", 36);
        size.put("primaryButton", 38);
        size.put("iconButton", 30);
        size.put("smallIconButton", 26);
        size.put("statusDot", 9);
        return size;
    }
}
