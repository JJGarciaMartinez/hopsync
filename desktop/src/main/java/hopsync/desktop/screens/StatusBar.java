package hopsync.desktop.screens;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.desktop.config.AppVersion;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.i18n.RelativeTime;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.store.PackUiState;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Flat bar at the bottom: the selected pack's folder, when it was last checked and the app version, next to a link
 * to a newer release when there is one.
 */
final class StatusBar extends JPanel {
    private final ZoneId zone;
    private Instant lastChecked;
    private final Timer ageing = new Timer(RelativeTime.REFRESH_MS, e -> showChecked());
    private final JLabel folder = label(Icons.Glyph.FOLDER);
    private final JLabel checked = label(Icons.Glyph.CLOCK);
    private final JButton update = new JButton();
    private UiState.AppUpdate shownUpdate;

    StatusBar(ZoneId zone, Consumer<UiAction> dispatch) {
        this.zone = zone;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBackground(Palette.statusBar());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.line()),
            BorderFactory.createEmptyBorder(0, 16, 0, 16)));
        setPreferredSize(new Dimension(0, UIScale.scale(28)));
        JLabel version = label(null);
        version.setText(AppVersion.VERSION_APP);
        // A real button styled as a link: Tab reaches it and Enter or Space follows it
        update.putClientProperty(FlatClientProperties.STYLE, "font: -2 semibold; margin: 0,0,0,0; borderWidth: 0;"
            + " focusWidth: 0; innerFocusWidth: 1; arc: 6");
        update.setContentAreaFilled(false);
        update.setBorderPainted(false);
        update.setForeground(Palette.action());
        update.setIcon(Icons.get(Icons.Glyph.UP, 12, Palette.action()));
        update.setIconTextGap(UIScale.scale(4));
        update.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        update.addActionListener(e -> dispatch.accept(new UiAction.OpenAppUpdate()));
        update.setVisible(false);
        add(folder);
        add(Box.createHorizontalStrut(UIScale.scale(16)));
        add(checked);
        add(Box.createHorizontalGlue());
        add(update);
        add(Box.createHorizontalStrut(UIScale.scale(12)));
        add(version);
    }

    void render(PackUiState pack, UiState.AppUpdate appUpdate) {
        if (!Objects.equals(appUpdate, shownUpdate)) {
            shownUpdate = appUpdate;
            update.setVisible(appUpdate != null);
            update.setText(appUpdate == null ? null : Messages.get("statusBar.update", appUpdate.version()));
            update.setToolTipText(appUpdate == null ? null : Messages.get("statusBar.updateHint"));
        }
        if (pack == null) {
            folder.setText(Messages.get("statusBar.noPack"));
            folder.setToolTipText(null);
            lastChecked = null;
            showChecked();
            return;
        }
        folder.setText(pack.gameDir().toString());
        folder.setToolTipText(pack.gameDir().toString());
        lastChecked = pack.lastChecked();
        showChecked();
    }

    private void showChecked() {
        checked.setText(lastChecked == null ? Messages.get("statusBar.notChecked")
            : Messages.get("statusBar.checkedAt", RelativeTime.ago(lastChecked, Instant.now(), zone)));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ageing.start();
    }

    @Override
    public void removeNotify() {
        ageing.stop();
        super.removeNotify();
    }

    private static JLabel label(Icons.Glyph glyph) {
        JLabel label = new JLabel(glyph == null ? null : Icons.get(glyph, 12, Palette.textFaint()));
        label.setForeground(Palette.textFaint());
        label.putClientProperty(FlatClientProperties.STYLE, "font: -2");
        label.setIconTextGap(UIScale.scale(5));
        return label;
    }
}
