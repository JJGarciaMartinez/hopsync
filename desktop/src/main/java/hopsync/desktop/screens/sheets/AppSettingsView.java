package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.core.failure.SyncFailure;
import hopsync.core.pack.PackSettings;
import hopsync.desktop.config.AppVersion;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.SegmentedControl;
import hopsync.desktop.store.Account;
import hopsync.desktop.store.Preferences;
import hopsync.desktop.store.Sheet.AppSettings.Section;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import hopsync.platform.system.SystemInfoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Preferences for every pack (check on start, chain the queue, parallel downloads) and the language, the GitHub
 * account used to publish created packs, and what a bug report needs to know about this copy of HopSync.
 */
public final class AppSettingsView extends JPanel {
    private final Consumer<UiAction> dispatch;
    private final SegmentedControl<Section> sections;
    private final JCheckBox checkOnStart = new JCheckBox(Messages.get("sheet.app.checkOnStart"));
    private final JCheckBox chainQueue = new JCheckBox(Messages.get("sheet.app.chainQueue"));
    private final JCheckBox reduceMotion = new JCheckBox(Messages.get("sheet.app.reduceMotion"));
    private final SegmentedControl<String> language;
    private final JSpinner parallel = new JSpinner(new SpinnerNumberModel(Preferences.DEFAULT.parallelDownloads(),
        PackSettings.MIN_PARALLEL_DOWNLOADS, PackSettings.MAX_PARALLEL_DOWNLOADS, 1));
    private final JPanel general = SheetParts.column();
    private final JPanel about = SheetParts.column();
    private final JPanel account = SheetParts.column();
    private Account shownAccount;
    private final Path settingsFile;
    private UiState.AppUpdate shownUpdate;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel bottom = new JPanel(new BorderLayout());
    private boolean rendering;
    private Sheet.AppSettings shown;

    public AppSettingsView(Consumer<UiAction> dispatch, Path settingsFile) {
        super(new BorderLayout(0, UIScale.scale(16)));
        this.dispatch = dispatch;
        this.settingsFile = settingsFile;
        setOpaque(false);
        body.setOpaque(false);
        bottom.setOpaque(false);
        sections = new SegmentedControl<>(Arrays.asList(Section.values()),
            section -> dispatch.accept(new UiAction.OpenAppSettings(section)));
        for (Section section : Section.values()) {
            sections.setLabel(section, Messages.get("sheet.app.section." + section.name()));
        }

        checkOnStart.addActionListener(e -> changed(shownLanguage()));
        chainQueue.addActionListener(e -> changed(shownLanguage()));
        reduceMotion.addActionListener(e -> changed(shownLanguage()));
        parallel.addChangeListener(e -> changed(shownLanguage()));
        // Blank follows the system
        List<String> languages = new ArrayList<>(List.of(""));
        languages.addAll(Messages.LANGUAGES);
        language = new SegmentedControl<>(languages, this::changed);
        languages.forEach(tag -> language.setLabel(tag,
            Messages.get(tag.isEmpty() ? "language.system" : "language." + tag)));
        parallel.getAccessibleContext().setAccessibleName(Messages.get("sheet.app.parallel"));
        JPanel parallelRow = new JPanel(new BorderLayout(UIScale.scale(12), 0));
        parallelRow.setOpaque(false);
        parallelRow.add(SheetParts.label(Messages.get("sheet.app.parallel")), BorderLayout.CENTER);
        parallelRow.add(parallel, BorderLayout.EAST);
        SheetParts.stack(general, 4, option(checkOnStart, "sheet.app.checkOnStartHint"));
        SheetParts.stack(general, 14, option(chainQueue, "sheet.app.chainQueueHint"));
        SheetParts.stack(general, 14, parallelRow);
        SheetParts.stack(general, 4, SheetParts.hint(Messages.get("sheet.app.parallelHint")));
        SheetParts.stack(general, 14, option(reduceMotion, "sheet.app.reduceMotionHint"));
        SheetParts.stack(general, 14, SheetParts.label(Messages.get("sheet.app.language")));
        SheetParts.stack(general, 8, language);
        SheetParts.stack(general, 4, SheetParts.hint(Messages.get("sheet.app.languageHint")));

        renderAbout(null);

        JPanel header = SheetParts.column();
        SheetParts.stack(header, 12, SheetParts.title(Messages.get("sheet.app.title")), sections);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** @param appUpdate a newer release, shown on top of "Acerca de", or {@code null} */
    public void render(Sheet.AppSettings sheet, Account gitHub, UiState.AppUpdate appUpdate) {
        if (!gitHub.equals(shownAccount)) {
            shownAccount = gitHub;
            renderAccount(gitHub);
        }
        if (!Objects.equals(appUpdate, shownUpdate)) {
            shownUpdate = appUpdate;
            renderAbout(appUpdate);
        }
        if (sheet.equals(shown)) return;
        boolean sectionChanged = shown == null || shown.section() != sheet.section();
        shown = sheet;
        rendering = true;
        try {
            sections.setSelected(sheet.section());
            checkOnStart.setSelected(sheet.draft().checkOnStart());
            chainQueue.setSelected(sheet.draft().chainQueue());
            reduceMotion.setSelected(sheet.draft().reduceMotion());
            parallel.setValue(sheet.draft().parallelDownloads());
            language.setSelected(sheet.draft().language());
        } finally {
            rendering = false;
        }
        if (sectionChanged) {
            body.removeAll();
            body.add(switch (sheet.section()) {
                case GENERAL -> general;
                case ACCOUNT -> account;
                case ABOUT -> about;
            }, BorderLayout.NORTH);
            bottom.removeAll();
            JButton cancel = SheetParts.button(sheet.section() == Section.GENERAL ? "sheet.cancel" : "sheet.close",
                ButtonStyle.SECONDARY, dispatch, new UiAction.CloseSheet());
            bottom.add(sheet.section() == Section.GENERAL
                ? SheetParts.footer(cancel, SheetParts.button("sheet.save", ButtonStyle.PRIMARY, dispatch,
                    new UiAction.SaveAppSettings()))
                : SheetParts.footer(cancel), BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    /**
     * Signed out: why connect, and "Conectar GitHub". Signing in: the code to type on GitHub and the button that
     * copies it and opens the page. Signed in: who, and "Desconectar".
     */
    private void renderAccount(Account gitHub) {
        account.removeAll();
        SheetParts.stack(account, 0, SheetParts.label(Messages.get("sheet.account.github")));
        if (!gitHub.available()) {
            SheetParts.stack(account, 8, SheetParts.notice(Messages.failure(new SyncFailure.GitHubNotConfigured()),
                Palette.update()));
        } else if (gitHub.signedIn()) {
            SheetParts.stack(account, 6, SheetParts.paragraph(Messages.get("sheet.account.signedIn", gitHub.login()),
                Palette.text()));
            JButton disconnect = SheetParts.button("sheet.account.disconnect", ButtonStyle.DANGER, dispatch,
                new UiAction.DisconnectGitHub());
            ButtonStyle.apply(disconnect, ButtonStyle.DANGER, ButtonStyle.SMALL);
            SheetParts.stack(account, 12, disconnect);
            SheetParts.stack(account, 10, SheetParts.hint(Messages.get("sheet.account.revokeHint")));
        } else if (gitHub.connecting()) {
            SheetParts.stack(account, 6, SheetParts.paragraph(Messages.get(gitHub.userCode() == null
                ? "sheet.account.asking" : "sheet.account.typeCode"), Palette.textSecondary()));
            if (gitHub.userCode() != null) {
                JLabel code = new JLabel(gitHub.userCode());
                code.setFont(new Font(Fonts.MONO_FAMILY, Font.BOLD, Math.round(getFont().getSize2D() * 2)));
                code.getAccessibleContext().setAccessibleName(Messages.get("sheet.account.code", gitHub.userCode()));
                JButton open = SheetParts.button("sheet.account.openGitHub", ButtonStyle.PRIMARY, dispatch,
                    new UiAction.OpenGitHubDevice());
                open.setIcon(Icons.get(Icons.Glyph.EXTERNAL));
                open.setHorizontalTextPosition(JButton.LEFT);
                SheetParts.stack(account, 12, code);
                SheetParts.stack(account, 12, SheetParts.footer(SheetParts.button("sheet.cancel", ButtonStyle.SECONDARY,
                    dispatch, new UiAction.CancelGitHubConnect()), open));
                SheetParts.stack(account, 10, SheetParts.hint(Messages.get("sheet.account.waiting")));
            }
        } else {
            SheetParts.stack(account, 6, SheetParts.paragraph(Messages.get("sheet.account.why"), Palette.textSecondary()));
            if (gitHub.error() != null) {
                SheetParts.stack(account, 10, SheetParts.notice(Messages.failure(gitHub.error()), Palette.error()));
            }
            JButton connect = SheetParts.button("sheet.account.connect", ButtonStyle.PRIMARY, dispatch,
                new UiAction.ConnectGitHub());
            connect.setEnabled(gitHub.known());
            SheetParts.stack(account, 12, connect);
            SheetParts.stack(account, 10, SheetParts.hint(Messages.get("sheet.account.scope")));
        }
        if (gitHub.known() && !gitHub.persistent()) {
            SheetParts.stack(account, 12, SheetParts.notice(Messages.get("sheet.account.noKeychain"), Palette.update()));
        }
        account.revalidate();
        account.repaint();
    }

    /** A newer release first, with "Descargar", then what a bug report needs and the button that copies it. */
    private void renderAbout(UiState.AppUpdate appUpdate) {
        about.removeAll();
        if (appUpdate != null) {
            SheetParts.stack(about, 0, SheetParts.notice(Messages.get("sheet.app.update", appUpdate.version()),
                Palette.action()));
            JButton download = SheetParts.button("sheet.app.download", ButtonStyle.PRIMARY, dispatch,
                new UiAction.OpenAppUpdate());
            download.setIcon(Icons.get(Icons.Glyph.EXTERNAL));
            download.setHorizontalTextPosition(JButton.LEFT);
            SheetParts.stack(about, 10, download);
        }
        JButton copy = SheetParts.button("sheet.app.copyDiagnostics", ButtonStyle.SECONDARY, dispatch,
            new UiAction.CopyDiagnostics());
        copy.setIcon(Icons.get(Icons.Glyph.COPY));
        SheetParts.stack(about, 18, facts(settingsFile), copy);
        about.revalidate();
        about.repaint();
    }

    private String shownLanguage() {
        return shown == null ? "" : shown.draft().language();
    }

    private void changed(String pickedLanguage) {
        if (rendering || shown == null) return;
        dispatch.accept(new UiAction.SetPreferences(new Preferences(checkOnStart.isSelected(),
            chainQueue.isSelected(), (Integer) parallel.getValue(), reduceMotion.isSelected(),
            shown.draft().libraryWidth(), pickedLanguage)));
    }

    private static JComponent option(JCheckBox box, String hintKey) {
        box.setOpaque(false);
        box.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
        JLabel hint = SheetParts.hint(Messages.get(hintKey));
        // Lines up with the check box text
        hint.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(26), 0, 0));
        JPanel option = SheetParts.column();
        SheetParts.stack(option, 2, box, hint);
        return option;
    }

    private static JComponent facts(Path settingsFile) {
        String[][] rows = {
            {"sheet.app.version", AppVersion.VERSION_APP},
            {"sheet.app.system", SystemInfoService.getOS() + " " + System.getProperty("os.version")},
            {"sheet.app.java", System.getProperty("java.version") + " · " + System.getProperty("java.vendor")},
            {"sheet.app.scale", String.format(Locale.ROOT, "%.2f×", UIScale.getUserScaleFactor())
                + " · sun.java2d.uiScale=" + System.getProperty("sun.java2d.uiScale", "auto")},
            {"sheet.app.settingsFile", settingsFile.toString()},
        };
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, UIScale.scale(6), UIScale.scale(16));
        for (int row = 0; row < rows.length; row++) {
            c.gridy = row;
            c.gridx = 0;
            c.weightx = 0;
            JLabel key = new JLabel(Messages.get(rows[row][0]));
            key.setForeground(Palette.textSecondary());
            grid.add(key, c);
            c.gridx = 1;
            c.weightx = 1;
            JLabel value = SheetParts.mono(rows[row][1]);
            value.setForeground(Palette.text());
            value.setToolTipText(rows[row][1]);
            grid.add(value, c);
        }
        return grid;
    }
}
