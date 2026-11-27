package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.util.UIScale;
import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.util.MarkdownText;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.RemoteImages;
import hopsync.desktop.kit.StatusTag;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/**
 * A mod's page in place of the results: back to the results, its icon, name and "Agregar al pack", its gallery,
 * description, what adding it would bring, and its Modrinth page. Everything scrolls together.
 */
final class ModDetailView extends JPanel {
    private static final int ICON = 84;
    private static final int THUMB_WIDTH = 86;
    private static final int THUMB_HEIGHT = THUMB_WIDTH * 9 / 16;
    private static final int SHOT_HEIGHT = SheetParts.CONTENT_WIDTH * 9 / 16;
    private static final int BODY_CHARS = 600;

    private final Consumer<UiAction> dispatch;
    private final RemoteImages images;
    private final Column content = new Column();
    private final JScrollPane scroll;
    private String shownProject;

    ModDetailView(Consumer<UiAction> dispatch, RemoteImages images, int minHeight) {
        super(new BorderLayout());
        this.dispatch = dispatch;
        this.images = images;
        setOpaque(false);
        scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Palette.line()));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(0, UIScale.scale(minHeight)));
        scroll.getVerticalScrollBar().setUnitIncrement(UIScale.scale(16));
        add(scroll, BorderLayout.CENTER);
    }

    void render(Sheet.AddMods sheet) {
        Sheet.ModDetail detail = sheet.detail();
        CatalogProject project = detail.project();
        content.removeAll();
        SheetParts.stack(content, 0, SheetParts.iconLink(Messages.get("addMods.back"),
            Icons.Glyph.CHEVRON_LEFT, false, () -> dispatch.accept(new UiAction.BackToResults())));
        SheetParts.stack(content, 12, header(sheet, detail));
        ProjectDetails details = detail.details();
        if (detail.failure() != null) {
            SheetParts.stack(content, 16, SheetParts.notice(
                Messages.get("addMods.detailFailed", Messages.failure(detail.failure())), Palette.error()));
        } else if (details == null) {
            SheetParts.stack(content, 16, SheetParts.hint(Messages.get("addMods.loadingDetail")));
        } else {
            if (!details.gallery().isEmpty()) {
                SheetParts.stack(content, 16, gallery(details.gallery(), detail.shot()));
            }
            SheetParts.stack(content, 16, description(project, details));
            if (!inPack(sheet, project) && (detail.version() != null || detail.noVersion())) {
                SheetParts.stack(content, 16, facts(sheet, detail));
            }
        }
        SheetParts.stack(content, 12, SheetParts.iconLink(Messages.get("addMods.openInModrinth"),
            Icons.Glyph.EXTERNAL, true, () -> dispatch.accept(new UiAction.OpenModPage("https://modrinth.com/mod/" + project.slug()))));
        content.revalidate();
        content.repaint();
        if (!project.id().equals(shownProject)) {
            shownProject = project.id();
            scroll.getViewport().setViewPosition(new Point(0, 0));
        }
    }

    private static boolean inPack(Sheet.AddMods sheet, CatalogProject project) {
        return sheet.inPack() != null && sheet.inPack().contains(project.id());
    }

    private JComponent header(Sheet.AddMods sheet, Sheet.ModDetail detail) {
        CatalogProject project = detail.project();
        JLabel name = SheetParts.title(project.title(), 4);
        String downloads = Messages.get("addMods.downloads", project.downloads());
        JLabel meta = new JLabel(project.author().isEmpty() ? downloads
            : Messages.get("addMods.by", project.author()) + " · " + downloads);
        meta.setForeground(Palette.textSecondary());
        meta.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        JPanel titles = SheetParts.column();
        SheetParts.stack(titles, 0, name);
        SheetParts.stack(titles, 4, meta);
        // Centered on the icon, aligned left
        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(titles, BorderLayout.CENTER);
        middle.setBorder(BorderFactory.createEmptyBorder(
            Math.max(0, (UIScale.scale(ICON) - titles.getPreferredSize().height) / 2), 0, 0, 0));

        JComponent action;
        if (inPack(sheet, project)) {
            action = new StatusTag(Messages.get("addMods.added"), StatusTag.Tone.MINE);
        } else {
            boolean adding = sheet.adding().contains(project.id());
            JButton add = new JButton(Messages.get(adding ? "addMods.adding" : "addMods.addToPack"));
            ButtonStyle.apply(add, ButtonStyle.PRIMARY);
            add.setEnabled(!adding && sheet.inPack() != null && detail.details() != null && detail.failure() == null
                && !detail.noVersion());
            add.addActionListener(e -> dispatch.accept(new UiAction.AddCatalogMod(project.id())));
            action = add;
        }
        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(action);

        JPanel head = new JPanel(new BorderLayout(UIScale.scale(16), 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        head.setOpaque(false);
        head.add(new Thumb(project.title(), project.iconUrl(), ICON, images), BorderLayout.WEST);
        head.add(middle, BorderLayout.CENTER);
        head.add(right, BorderLayout.EAST);
        return head;
    }

    private JComponent gallery(List<ProjectDetails.Image> gallery, int shot) {
        int selected = Math.min(Math.max(shot, 0), gallery.size() - 1);
        ProjectDetails.Image featured = gallery.get(selected);
        JPanel column = SheetParts.column();
        Picture large = new Picture(featured.url(), SheetParts.CONTENT_WIDTH, SHOT_HEIGHT, images);
        large.setToolTipText(featured.title().isEmpty() ? null : featured.title());
        SheetParts.stack(column, 0, large);
        if (gallery.size() > 1) {
            JPanel thumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            thumbs.setOpaque(false);
            for (int i = 0; i < gallery.size(); i++) {
                if (i > 0) thumbs.add(Box.createHorizontalStrut(UIScale.scale(8)));
                thumbs.add(thumbButton(gallery.get(i), i, i == selected));
            }
            SheetParts.stack(column, 8, thumbs);
        }
        return column;
    }

    private JButton thumbButton(ProjectDetails.Image image, int index, boolean selected) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.add(new Picture(image.url(), THUMB_WIDTH, THUMB_HEIGHT, images), BorderLayout.CENTER);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(selected
            ? new FlatLineBorder(new Insets(2, 2, 2, 2), Palette.action(), 2, 12)
            : BorderFactory.createEmptyBorder(2, 2, 2, 2));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName(image.title().isEmpty()
            ? String.valueOf(index + 1) : image.title());
        button.addActionListener(e -> dispatch.accept(new UiAction.SelectShot(index)));
        return button;
    }

    private static JComponent description(CatalogProject project, ProjectDetails details) {
        String summary = details.summary().isBlank() ? project.description() : details.summary();
        String body = MarkdownText.plain(details.body(), BODY_CHARS);
        JTextArea text = new JTextArea(body.isBlank() ? summary : summary + "\n\n" + body);
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setBorder(null);
        text.setForeground(Palette.text());
        text.setFont(new JLabel().getFont());
        text.setSize(UIScale.scale(SheetParts.CONTENT_WIDTH), Short.MAX_VALUE);
        return text;
    }

    private static JComponent facts(Sheet.AddMods sheet, Sheet.ModDetail detail) {
        String loader = Messages.loader(sheet.loader());
        String version = detail.version() != null
            ? Messages.get("addMods.willAddValue", detail.version().versionNumber(), sheet.minecraft(), loader)
            : detail.missingDependency() == null ? Messages.get("addMods.noVersion", sheet.minecraft(), loader)
            : Messages.get("addMods.dependencyNoVersion", detail.project().title(), detail.missingDependency(),
                sheet.minecraft(), loader);
        String needs = detail.needs().isEmpty() ? Messages.get("addMods.noDependencies")
            : Messages.get("addMods.alsoAdds", Messages.list(detail.needs()));
        JPanel row = new JPanel(new GridLayout(1, 2, UIScale.scale(10), 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.add(fact(Messages.get("addMods.willAdd"), version));
        row.add(fact(Messages.get("addMods.dependencies"), needs));
        return row;
    }

    private static JComponent fact(String caption, String value) {
        JLabel small = new JLabel(caption.toUpperCase(Messages.locale()));
        small.setForeground(Palette.textFaint());
        small.putClientProperty(FlatClientProperties.STYLE, "font: -2");
        JLabel text = SheetParts.paragraph(value, Palette.text(), SheetParts.CONTENT_WIDTH / 2 - 40);
        text.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        JPanel card = SheetParts.column();
        SheetParts.stack(card, 0, small);
        SheetParts.stack(card, 2, text);
        card.setOpaque(true);
        card.setBackground(Palette.well());
        card.putClientProperty(FlatClientProperties.STYLE, "arc: 20");
        card.setBorder(BorderFactory.createCompoundBorder(
            new FlatLineBorder(new Insets(0, 0, 0, 0), Palette.line(), 1, 20),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        return card;
    }

    /** A gallery picture, cropped to fill its rounded box; a raised box until it loads, or if it never does. */
    private static final class Picture extends JComponent {
        private final int width;
        private final int height;
        private BufferedImage image;

        Picture(String url, int width, int height, RemoteImages images) {
            this.width = width;
            this.height = height;
            if (!url.isBlank()) {
                int edge = UIScale.scale(Math.max(width, height));
                image = images.peek(url, edge);
                if (image == null) {
                    images.load(url, edge, loaded -> {
                        image = loaded;
                        repaint();
                    });
                }
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(width), UIScale.scale(height));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, UIScale.scale(height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int arc = UIScale.scale(16);
                RoundRectangle2D box = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(Palette.raised());
                g2.fill(box);
                if (image != null) {
                    g2.clip(box);
                    double scale = Math.max((double) getWidth() / image.getWidth(),
                        (double) getHeight() / image.getHeight());
                    int w = (int) Math.ceil(image.getWidth() * scale);
                    int h = (int) Math.ceil(image.getHeight() * scale);
                    g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Always as wide as the sheet, so the description wraps and nothing scrolls sideways. */
    private static final class Column extends JPanel implements Scrollable {
        Column() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 2, 14, 4));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return UIScale.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
