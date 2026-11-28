package hopsync.desktop.screens.sheets;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import hopsync.core.failure.SyncFailure;
import hopsync.core.publish.PublishStep;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.kit.StatusMark;
import hopsync.desktop.store.Account;
import hopsync.desktop.store.Sheet;
import hopsync.desktop.store.UiAction;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Publishing a created pack: the review (repository name on a first publish, confirming the player may share
 * their own files), the steps while it publishes, and the link once it is out. Without a GitHub account it says
 * so and leads to Ajustes de HopSync › Cuenta.
 */
public final class PublishView extends JPanel {
    /** Own files named in the warning before it says how many more. */
    private static final int NAMED_FILES = 4;

    private final Consumer<UiAction> dispatch;
    private final JLabel title = SheetParts.title("");
    private final JLabel intro = new JLabel();
    private final JTextField repoName = new JTextField();
    private final JCheckBox confirm = new JCheckBox(Messages.get("publish.confirm"));
    private final JPanel body = SheetParts.column();
    private final JPanel bottom = new JPanel(new BorderLayout());
    private Sheet.Publish shown;
    private Account shownAccount;
    private boolean rendering;

    public PublishView(Consumer<UiAction> dispatch) {
        super(new BorderLayout(0, UIScale.scale(16)));
        this.dispatch = dispatch;
        setOpaque(false);
        bottom.setOpaque(false);
        intro.setForeground(Palette.textSecondary());
        repoName.getAccessibleContext().setAccessibleName(Messages.get("publish.repoName"));
        repoName.setFont(mono(repoName.getFont()));
        repoName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                if (!rendering) dispatch.accept(new UiAction.SetRepoName(repoName.getText()));
            }
        });
        repoName.addActionListener(e -> dispatch.accept(new UiAction.StartPublish()));
        confirm.setOpaque(false);
        confirm.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
        confirm.addActionListener(e -> {
            if (!rendering) dispatch.accept(new UiAction.ConfirmOwnFiles(confirm.isSelected()));
        });

        JPanel header = SheetParts.column();
        SheetParts.stack(header, 0, title);
        SheetParts.stack(header, 6, intro);
        // The card is taller than most of its content: the content stays at the top, the buttons at the bottom
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(body, BorderLayout.NORTH);
        add(header, BorderLayout.NORTH);
        add(top, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public void render(Sheet.Publish sheet, String packName, Account account) {
        if (sheet.equals(shown) && account.equals(shownAccount)) return;
        rendering = true;
        try {
            boolean opened = shown == null || !shown.packId().equals(sheet.packId());
            shown = sheet;
            shownAccount = account;
            if (opened || !repoName.getText().strip().equals(sheet.repoName())) {
                repoName.setText(sheet.repoName());
            }
            confirm.setSelected(sheet.confirmed());
            title.setText(Messages.get(sheet.update() ? "publish.titleChanges" : "publish.title", packName));
            intro.setText(SheetParts.wrap(Messages.get(sheet.update() ? "publish.introChanges" : "publish.intro"),
                SheetParts.CONTENT_WIDTH));
            body.removeAll();
            bottom.removeAll();
            switch (sheet.stage()) {
                case REVIEW -> review(sheet, account);
                case PUBLISHING -> publishing(sheet);
                case DONE -> done(sheet);
            }
            revalidate();
            repaint();
        } finally {
            rendering = false;
        }
    }

    /** Forgets the last sheet, so opening it again starts from its own state. */
    public void reset() {
        shown = null;
    }

    private void review(Sheet.Publish sheet, Account account) {
        JButton cancel = SheetParts.button("sheet.cancel", ButtonStyle.SECONDARY, dispatch, new UiAction.CloseSheet());
        if (!account.signedIn()) {
            SheetParts.stack(body, 0, SheetParts.notice(Messages.get("publish.signIn"), Palette.update()));
            JButton connect = SheetParts.button("publish.connect", ButtonStyle.PRIMARY, dispatch,
                new UiAction.OpenAppSettings(Sheet.AppSettings.Section.ACCOUNT));
            bottom.add(SheetParts.footer(cancel, connect), BorderLayout.CENTER);
            return;
        }
        if (!sheet.update()) {
            SheetParts.stack(body, 0, SheetParts.label(Messages.get("publish.repoName")), repoName);
            boolean valid = Sheet.Publish.validRepoName(sheet.repoName());
            SheetParts.stack(body, 6, valid
                ? SheetParts.hint(Messages.get("publish.repoHint", account.login(), sheet.repoName()))
                : SheetParts.paragraph(Messages.get("publish.repoInvalid"), Palette.error()));
            SheetParts.stack(body, 14, SheetParts.hint(Messages.get("publish.public")));
        }
        if (sheet.ownFiles() == null) {
            SheetParts.stack(body, 14, SheetParts.hint(Messages.get("publish.listing")));
        } else if (!sheet.ownFiles().isEmpty()) {
            SheetParts.stack(body, 14, SheetParts.notice(Messages.get("publish.ownFiles", sheet.ownFiles().size(),
                named(sheet.ownFiles())), Palette.update()));
            if (sheet.ownFiles().stream().anyMatch(file -> file.startsWith("game-files/"))) {
                SheetParts.stack(body, 8, SheetParts.notice(Messages.get("publish.gameFiles"), Palette.update()));
            }
            SheetParts.stack(body, 8, confirm);
        }
        if (sheet.error() != null) {
            SheetParts.stack(body, 14, SheetParts.notice(Messages.get("publish.failed", Messages.failure(sheet.error())),
                Palette.error()));
            if (sheet.error() instanceof SyncFailure.PublishBuildFailed failed) {
                JButton page = SheetParts.button("publish.openBuild", ButtonStyle.SECONDARY, dispatch,
                    new UiAction.OpenModPage(failed.url()));
                ButtonStyle.apply(page, ButtonStyle.SECONDARY, ButtonStyle.SMALL);
                page.setIcon(Icons.get(Icons.Glyph.EXTERNAL, 13, Palette.text()));
                page.setHorizontalTextPosition(JButton.LEFT);
                SheetParts.stack(body, 8, page);
            }
        }
        JButton start = SheetParts.button(sheet.update() ? "publish.startChanges" : "publish.start",
            ButtonStyle.PRIMARY, dispatch, new UiAction.StartPublish());
        start.setIcon(Icons.get(Icons.Glyph.UP));
        start.setEnabled(sheet.canPublish());
        bottom.add(SheetParts.footer(cancel, start), BorderLayout.CENTER);
    }

    /** The steps of this publish: done ones checked, the running one moving, the rest waiting. */
    private void publishing(Sheet.Publish sheet) {
        List<PublishStep> steps = new ArrayList<>(List.of(PublishStep.values()));
        if (sheet.update()) steps.remove(PublishStep.CREATING_REPOSITORY);
        int current = sheet.step() == null ? 0 : Math.max(0, steps.indexOf(sheet.step()));
        for (int i = 0; i < steps.size(); i++) {
            StatusMark.Kind kind = i < current ? StatusMark.Kind.OK : i == current ? StatusMark.Kind.WORKING
                : StatusMark.Kind.IDLE;
            JLabel line = new JLabel(Messages.get("publish.step." + steps.get(i).name()), StatusMark.of(kind),
                JLabel.LEFT);
            line.setIconTextGap(UIScale.scale(10));
            line.setForeground(i <= current ? Palette.text() : Palette.textFaint());
            if (i == current) line.putClientProperty(FlatClientProperties.STYLE, "font: semibold");
            SheetParts.stack(body, i == 0 ? 0 : 12, line);
        }
        SheetParts.stack(body, 18, SheetParts.hint(Messages.get("publish.wait")));
        bottom.add(SheetParts.footer(SheetParts.button("sheet.close", ButtonStyle.SECONDARY, dispatch,
            new UiAction.CloseSheet())), BorderLayout.CENTER);
    }

    private void done(Sheet.Publish sheet) {
        title.setText(Messages.get("publish.done"));
        intro.setText(SheetParts.wrap(Messages.get("publish.doneText"), SheetParts.CONTENT_WIDTH));
        JTextField link = new JTextField(sheet.link());
        link.setEditable(false);
        link.setFont(mono(link.getFont()));
        link.getAccessibleContext().setAccessibleName(Messages.get("publish.copy"));
        SheetParts.stack(body, 0, link);
        JButton copy = SheetParts.button("publish.copy", ButtonStyle.SECONDARY, dispatch,
            new UiAction.CopyPackLink(sheet.packId()));
        copy.setIcon(Icons.get(Icons.Glyph.COPY));
        JButton done = SheetParts.button("addMods.done", ButtonStyle.PRIMARY, dispatch, new UiAction.CloseSheet());
        bottom.add(SheetParts.footer(copy, done), BorderLayout.CENTER);
    }

    private static Font mono(Font base) {
        return new Font(Fonts.MONO_FAMILY, Font.PLAIN, Math.round(base.getSize2D() - 1));
    }

    /** The first files by name, then how many more. */
    private static String named(List<String> files) {
        List<String> names = files.stream().limit(NAMED_FILES).map(path -> path.substring(path.lastIndexOf('/') + 1))
            .toList();
        String shownNames = String.join(", ", names);
        return files.size() > NAMED_FILES ? Messages.get("publish.more", shownNames, files.size() - NAMED_FILES)
            : shownNames;
    }
}
