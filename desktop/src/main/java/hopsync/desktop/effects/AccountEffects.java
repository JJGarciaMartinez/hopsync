package hopsync.desktop.effects;

import hopsync.core.failure.SyncFailures;
import hopsync.core.port.SecretStore;
import hopsync.core.util.CancellationToken;
import hopsync.desktop.i18n.Messages;
import hopsync.desktop.store.Account;
import hopsync.desktop.store.Store;
import hopsync.desktop.store.UiAction;
import hopsync.desktop.store.UiState;
import hopsync.platform.github.GitHubAuth;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

/**
 * The player's GitHub account: signing in with the device flow, keeping the token in the system keychain, and
 * forgetting it. The token never reaches the UI state or the logs.
 */
final class AccountEffects {
    static final String TOKEN_KEY = GitHubAuth.TOKEN_KEY;
    static final String LOGIN_KEY = "github.login";
    private static final System.Logger LOG = System.getLogger(AccountEffects.class.getName());

    private final Store store;
    private final SecretStore secrets;
    private final GitHubAuth auth;
    private final Executor background;
    private CancellationToken signingIn = CancellationToken.none();

    AccountEffects(Store store, SecretStore secrets, GitHubAuth auth, Executor background) {
        this.store = store;
        this.secrets = secrets;
        this.auth = auth;
        this.background = background;
    }

    /** Reads who is signed in from the keychain. */
    void load() {
        background.execute(() -> store.dispatch(new UiAction.AccountLoaded(signedInLogin(), secrets.persistent(),
            auth.configured())));
    }

    void run(UiAction action, UiState state) {
        switch (action) {
            case UiAction.ConnectGitHub a when state.account().connecting() && state.account().userCode() == null ->
                connect();
            case UiAction.CancelGitHubConnect a -> signingIn.cancel();
            case UiAction.OpenGitHubDevice a -> openDevicePage(state.account());
            case UiAction.DisconnectGitHub a -> disconnect();
            default -> { }
        }
    }

    private String signedInLogin() {
        try {
            return secrets.read(TOKEN_KEY).isPresent() ? secrets.read(LOGIN_KEY).orElse(null) : null;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read the GitHub account from the keychain", e);
            return null;
        }
    }

    private void connect() {
        signingIn.cancel();
        CancellationToken cancel = new CancellationToken();
        signingIn = cancel;
        background.execute(() -> {
            try {
                GitHubAuth.DeviceCode code = auth.start();
                store.dispatch(new UiAction.GitHubCodeShown(code.userCode(), code.verificationUri()));
                String token = auth.awaitToken(code, cancel);
                String login = auth.login(token);
                secrets.write(TOKEN_KEY, token);
                secrets.write(LOGIN_KEY, login);
                store.dispatch(new UiAction.GitHubConnected(login));
                store.dispatch(new UiAction.ShowToast(Messages.get("toast.githubConnected", login)));
            } catch (CancellationException e) {
                // The player cancelled; the state already says so
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "Could not sign in to GitHub", e);
                store.dispatch(new UiAction.GitHubConnectFailed(SyncFailures.from(e, "")));
            }
        });
    }

    /** Copies the code so the player can paste it, then opens GitHub's page to type it. */
    private void openDevicePage(Account account) {
        if (account.userCode() == null || account.verificationUri() == null
                || !account.verificationUri().startsWith("https://")) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(account.userCode()), null);
        store.dispatch(new UiAction.ShowToast(Messages.get("toast.githubCodeCopied")));
        background.execute(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("opening links is not supported here");
                }
                Desktop.getDesktop().browse(URI.create(account.verificationUri()));
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not open " + account.verificationUri(), e);
                store.dispatch(new UiAction.ShowToast(Messages.get("toast.linkFailed")));
            }
        });
    }

    private void disconnect() {
        signingIn.cancel();
        background.execute(() -> {
            try {
                secrets.delete(TOKEN_KEY);
                secrets.delete(LOGIN_KEY);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not delete the GitHub token from the keychain", e);
                store.dispatch(new UiAction.ShowToast(Messages.failure(SyncFailures.from(e, ""))));
            }
            store.dispatch(new UiAction.AccountLoaded(signedInLogin(), secrets.persistent(), auth.configured()));
        });
    }
}
