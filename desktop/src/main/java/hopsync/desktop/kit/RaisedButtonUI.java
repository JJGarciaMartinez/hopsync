package hopsync.desktop.kit;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatButtonUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;

/**
 * Every button that can be pressed looks raised: a face sitting on a darker bevel. Hover lifts the face one
 * pixel, a press drops it onto the bevel and its label moves with it; a disabled button is flat, with no
 * bevel and no hand cursor. Each {@link ButtonStyle} role has its own colors ({@code HopSync.button.<role>.*}
 * in {@code HopSyncDark.properties}). Focus shows as a 2 px turquoise ring.
 */
public class RaisedButtonUI extends FlatButtonUI {
    private static final int FACE_HEIGHT = 36;
    private static final int PRIMARY_FACE_HEIGHT = 38;
    private static final int ICON_SIZE = 30;
    private static final int SMALL_ICON_SIZE = 26;

    private final PropertyChangeListener enabledCursor = e -> {
        if ("enabled".equals(e.getPropertyName()) && e.getSource() instanceof JComponent c) {
            updateCursor(c);
        }
    };

    public static ComponentUI createUI(JComponent c) {
        return new RaisedButtonUI();
    }

    protected RaisedButtonUI() {
        // Not shared: style classes and per-button colors are kept on the UI instance
        super(false);
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        updateCursor(c);
        c.addPropertyChangeListener("enabled", enabledCursor);
    }

    @Override
    public void uninstallUI(JComponent c) {
        c.removePropertyChangeListener("enabled", enabledCursor);
        super.uninstallUI(c);
    }

    @Override
    protected void paintBackground(Graphics g, JComponent c) {
        if (!isRaised(c)) {
            super.paintBackground(g, c);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            FlatUIUtils.setRenderingHints(g2);
            Geometry box = geometry(c);
            if (!c.isEnabled()) {
                // Flat: "not now"
                Color disabled = UIManager.getColor("HopSync.button.disabled");
                g2.setColor(disabled != null ? disabled : Palette.raised());
                g2.fill(new RoundRectangle2D.Float(box.x, box.restTop(), box.width, box.faceHeight, box.arc, box.arc));
                return;
            }
            String role = role((AbstractButton) c);
            boolean hover = ((AbstractButton) c).getModel().isRollover();
            Relief.Raised colors = new Relief.Raised(
                Palette.button(role, hover ? "hoverTop" : "top"),
                Palette.button(role, hover ? "hoverBottom" : "bottom"),
                Palette.button(role, hover ? "hoverBorder" : "border"),
                Palette.button(role, "borderBottom"),
                highlight(role),
                Palette.button(role, "bevel"));
            float top = faceTop(c, box);
            // The bevel's bottom stays put: whatever the face does not cover of it shows
            Relief.paintRaised(g2, colors, box.x, top, box.width, box.faceHeight, box.arc, box.y + box.lift - top);
            if (c.isFocusOwner() && ((AbstractButton) c).isFocusPainted()) {
                float ring = UIScale.scale(2f);
                g2.setColor(Palette.mine());
                g2.setStroke(new BasicStroke(ring));
                float out = box.focus - ring / 2;
                g2.draw(new RoundRectangle2D.Float(box.x - out, top - out, box.width + out * 2,
                    box.faceHeight + out * 2, box.arc + out * 2, box.arc + out * 2));
            }
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        if (!isRaised(c)) {
            super.paint(g, c);
            return;
        }
        // The label follows the face: centered on it, lifted on hover, lowered while pressed
        Geometry box = geometry(c);
        float shift = c.isEnabled() ? faceTop(c, box) + box.faceHeight / 2 - c.getHeight() / 2f
            : box.restTop() + box.faceHeight / 2 - c.getHeight() / 2f;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(0, Math.round(shift));
            super.paint(g2, c);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension size = super.getPreferredSize(c);
        if (size == null || !isRaised(c)) {
            return size;
        }
        AbstractButton button = (AbstractButton) c;
        int focus = Math.round(FlatUIUtils.getBorderFocusWidth(c)) * 2;
        int lift = Math.round(maxBevel(role(button)));
        if (ButtonStyle.ICON.isAppliedTo(button)) {
            int side = UIScale.scale(ButtonStyle.SMALL.isAppliedTo(button) ? SMALL_ICON_SIZE : ICON_SIZE);
            return new Dimension(side + focus, side + focus + lift);
        }
        if (ButtonStyle.SMALL.isAppliedTo(button)) {
            size.height += lift;
            return size;
        }
        int face = UIScale.scale("primary".equals(role(button)) ? PRIMARY_FACE_HEIGHT : FACE_HEIGHT);
        size.height = Math.max(size.height, face + focus) + lift;
        return size;
    }

    /**
     * Toolbar buttons and buttons without a filled content area (links, dashed outlines) keep FlatLaf's flat
     * look; every other button is raised, quiet ones included.
     */
    static boolean isRaised(JComponent c) {
        return c instanceof JButton button
            && button.isContentAreaFilled()
            && !FlatClientProperties.BUTTON_TYPE_BORDERLESS.equals(c.getClientProperty(FlatClientProperties.BUTTON_TYPE))
            && !(c.getParent() instanceof JToolBar);
    }

    static String role(AbstractButton button) {
        if (ButtonStyle.DANGER.isAppliedTo(button)) {
            return "danger";
        }
        if (ButtonStyle.PRIMARY.isAppliedTo(button) || button instanceof JButton b && b.isDefaultButton()) {
            return "primary";
        }
        return ButtonStyle.QUIET.isAppliedTo(button) ? "quiet" : "secondary";
    }

    private static void updateCursor(JComponent c) {
        if (isRaised(c)) {
            c.setCursor(c.isEnabled() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
        }
    }

    private static Color highlight(String role) {
        Color own = UIManager.getColor("HopSync.button." + role + ".highlight");
        Color shared = UIManager.getColor("HopSync.button.highlight");
        return own != null ? own : shared != null ? shared : Palette.raisedHighlight();
    }

    /** Bevel at rest; hover shows one pixel more. */
    private static float restBevel(String role) {
        int width = UIManager.getInt("primary".equals(role) ? "HopSync.bevelWidth.primary" : "HopSync.bevelWidth");
        return UIScale.scale((float) (width > 0 ? width : 2));
    }

    private static float maxBevel(String role) {
        return restBevel(role) + UIScale.scale(1f);
    }

    /** Top of the face: highest on hover, one pixel lower at rest, on the bevel while pressed. */
    private static float faceTop(JComponent c, Geometry box) {
        ButtonModel model = ((AbstractButton) c).getModel();
        if (model.isArmed() && model.isPressed()) {
            return box.y + box.lift;
        }
        return model.isRollover() ? box.y : box.restTop();
    }

    private static Geometry geometry(JComponent c) {
        AbstractButton button = (AbstractButton) c;
        String role = role(button);
        float focus = FlatUIUtils.getBorderFocusWidth(c);
        float lift = maxBevel(role);
        float bevel = restBevel(role);
        float arc = ButtonStyle.ICON.isAppliedTo(button)
            ? UIScale.scale(ButtonStyle.SMALL.isAppliedTo(button) ? 14f : 16f)
            : FlatUIUtils.getBorderArc(c);
        return new Geometry(focus, focus, focus, c.getWidth() - focus * 2, c.getHeight() - focus * 2 - lift, arc, lift, bevel);
    }

    /**
     * @param lift  room kept under the face for the bevel at its tallest (hover)
     * @param bevel bevel shown at rest
     */
    private record Geometry(float focus, float x, float y, float width, float faceHeight, float arc, float lift, float bevel) {
        float restTop() {
            return y + lift - bevel;
        }
    }
}
