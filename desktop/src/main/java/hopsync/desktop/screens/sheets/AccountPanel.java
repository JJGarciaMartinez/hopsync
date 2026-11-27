package hopsync.desktop.screens.sheets;

import hopsync.core.failure.SyncFailure;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.kit.ButtonStyle;
import hopsync.desktop.kit.Fonts;
import hopsync.desktop.kit.Icons;
import hopsync.desktop.kit.Palette;
import hopsync.desktop.store.Account;
import hopsync.desktop.store.UiAction;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.util.function.Consumer;

/**
 * The player's GitHub account: why to connect and the button, the code to type while signing in, or who is signed
 * in. Used by the app settings and by the wizard's "Recuperar un pack mío".
 */
final class AccountPanel extends JPanel {
    private final Consumer<UiAction> dispatch;
    private final String whyKey;
    private Account shown;

    /** @param whyKey the message that says why to connect, which depends on where the panel is */
    AccountPanel(Consumer<UiAction> dispatch, String whyKey) {
        this.dispatch = dispatch;
        this.whyKey = whyKey;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /**
     * Signed out: why connect, and "Conectar GitHub". Signing in: the code to type on GitHub and the button that
     * copies it and opens the page. Signed in: who, and "Desconectar".
     */
    void render(Account gitHub) {
        if (gitHub.equals(shown)) return;
        shown = gitHub;
        removeAll();
        SheetParts.stack(this, 0, SheetParts.label(Messages.get("sheet.account.github")));
        if (!gitHub.available()) {
            SheetParts.stack(this, 8, SheetParts.notice(Messages.failure(new SyncFailure.GitHubNotConfigured()),
                Palette.update()));
        } else if (gitHub.signedIn()) {
            SheetParts.stack(this, 6, SheetParts.paragraph(Messages.get("sheet.account.signedIn", gitHub.login()),
                Palette.text()));
            JButton disconnect = SheetParts.button("sheet.account.disconnect", ButtonStyle.DANGER, dispatch,
                new UiAction.DisconnectGitHub());
            ButtonStyle.apply(disconnect, ButtonStyle.DANGER, ButtonStyle.SMALL);
            SheetParts.stack(this, 12, disconnect);
            SheetParts.stack(this, 10, SheetParts.hint(Messages.get("sheet.account.revokeHint")));
        } else if (gitHub.connecting()) {
            SheetParts.stack(this, 6, SheetParts.paragraph(Messages.get(gitHub.userCode() == null
                ? "sheet.account.asking" : "sheet.account.typeCode"), Palette.textSecondary()));
            if (gitHub.userCode() != null) {
                JLabel code = new JLabel(gitHub.userCode());
                code.setFont(new Font(Fonts.MONO_FAMILY, Font.BOLD, Math.round(getFont().getSize2D() * 2)));
                code.getAccessibleContext().setAccessibleName(Messages.get("sheet.account.code", gitHub.userCode()));
                JButton open = SheetParts.button("sheet.account.openGitHub", ButtonStyle.PRIMARY, dispatch,
                    new UiAction.OpenGitHubDevice());
                open.setIcon(Icons.get(Icons.Glyph.EXTERNAL));
                open.setHorizontalTextPosition(JButton.LEFT);
                SheetParts.stack(this, 12, code);
                SheetParts.stack(this, 12, SheetParts.footer(SheetParts.button("sheet.cancel", ButtonStyle.SECONDARY,
                    dispatch, new UiAction.CancelGitHubConnect()), open));
                SheetParts.stack(this, 10, SheetParts.hint(Messages.get("sheet.account.waiting")));
            }
        } else {
            SheetParts.stack(this, 6, SheetParts.paragraph(Messages.get(whyKey), Palette.textSecondary()));
            if (gitHub.error() != null) {
                SheetParts.stack(this, 10, SheetParts.notice(Messages.failure(gitHub.error()), Palette.error()));
            }
            JButton connect = SheetParts.button("sheet.account.connect", ButtonStyle.PRIMARY, dispatch,
                new UiAction.ConnectGitHub());
            connect.setEnabled(gitHub.known());
            SheetParts.stack(this, 12, connect);
            SheetParts.stack(this, 10, SheetParts.hint(Messages.get("sheet.account.scope")));
        }
        if (gitHub.known() && !gitHub.persistent()) {
            SheetParts.stack(this, 12, SheetParts.notice(Messages.get("sheet.account.noKeychain"), Palette.update()));
        }
        revalidate();
        repaint();
    }
}
