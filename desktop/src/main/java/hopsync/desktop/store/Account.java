package hopsync.desktop.store;

import hopsync.core.failure.SyncFailure;

/**
 * The player's GitHub account, used to publish the packs they create.
 *
 * @param known           whether Effects already read the keychain
 * @param login           the GitHub user signed in, or {@code null}
 * @param connecting      signing in: asking GitHub for a code or waiting for the player to approve
 * @param userCode        while signing in, the code to type on GitHub once it arrived; else {@code null}
 * @param verificationUri while signing in, where to type it
 * @param persistent      the token is kept in the system keychain, so the player stays signed in after closing
 * @param available       this build can sign in to GitHub
 * @param error           why the last sign-in failed, or {@code null}
 */
public record Account(boolean known, String login, boolean connecting, String userCode, String verificationUri,
                      boolean persistent, boolean available, SyncFailure error) {

    public static final Account UNKNOWN = new Account(false, null, false, null, null, true, true, null);

    public boolean signedIn() {
        return login != null;
    }

    Account loaded(String newLogin, boolean keychain, boolean canSignIn) {
        return new Account(true, newLogin, false, null, null, keychain, canSignIn, null);
    }

    Account startConnecting() {
        return new Account(known, null, true, null, null, persistent, available, null);
    }

    Account withCode(String code, String uri) {
        return new Account(known, null, true, code, uri, persistent, available, null);
    }

    Account connected(String newLogin) {
        return new Account(true, newLogin, false, null, null, persistent, available, null);
    }

    /** @param failure why it stopped, or {@code null} when the player cancelled */
    Account stopped(SyncFailure failure) {
        return new Account(known, null, false, null, null, persistent, available, failure);
    }
}
