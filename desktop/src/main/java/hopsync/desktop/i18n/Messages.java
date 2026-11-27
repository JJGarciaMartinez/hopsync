package hopsync.desktop.i18n;

import hopsync.core.failure.SyncFailure;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Every user-facing text, in Latin American Spanish ({@code messages_es.properties}) or English
 * ({@code messages.properties}, also used for any other system language). Core and platform never hold
 * user-facing text: they report {@link SyncFailure}s, which are turned into messages here.
 */
public final class Messages {
    /** Language tags the player can pick, as settings.json keeps them. */
    public static final List<String> LANGUAGES = List.of("es", "en");
    private static final String BUNDLE_NAME = "hopsync.desktop.i18n.messages";
    // The system's language would otherwise stand in for a missing bundle, before the English base
    private static final ResourceBundle.Control NO_FALLBACK =
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static volatile Locale locale;
    private static volatile ResourceBundle bundle;

    static {
        use("");
    }

    private Messages() {
    }

    /**
     * Picks the language of every text from now on. Called once at startup, before any window is built: views
     * read their texts when they are created.
     *
     * @param preferred one of {@link #LANGUAGES}, or blank (or unknown) to follow the system: Spanish for any
     *                  Spanish locale, English otherwise
     */
    public static void use(String preferred) {
        String language = LANGUAGES.contains(preferred) ? preferred
            : Locale.getDefault().getLanguage().equals("es") ? "es" : "en";
        Locale picked = language.equals("es") ? Locale.forLanguageTag("es-MX") : Locale.ENGLISH;
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, picked, NO_FALLBACK);
        locale = picked;
    }

    /** The locale of the texts, for dates and numbers written inside them. */
    public static Locale locale() {
        return locale;
    }

    /** Names joined as a sentence says them: "A", "A y B", "A, B y C". */
    public static String list(List<String> names) {
        if (names.size() <= 1) return names.isEmpty() ? "" : names.getFirst();
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + get("list.and") + " " + names.getLast();
    }

    /** A mod loader as players write it ("NeoForge"); one HopSync does not know is shown as the pack names it. */
    public static String loader(String id) {
        String key = "loader." + (id == null ? "" : id.toLowerCase(Locale.ROOT));
        return bundle.containsKey(key) ? get(key) : String.valueOf(id);
    }

    public static String get(String key, Object... args) {
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }

    /**
     * What went wrong and what the player can do about it.
     */
    public static String failure(SyncFailure failure) {
        return switch (failure) {
            case SyncFailure.NotConfigured f -> get("failure.notConfigured");
            case SyncFailure.InvalidUrl f -> get("failure.invalidUrl", f.url());
            case SyncFailure.Network f -> get("failure.network", f.host());
            case SyncFailure.HttpStatus f -> get(httpStatusKey(f.status()), String.valueOf(f.status()));
            case SyncFailure.ResponseTooLarge f -> get("failure.responseTooLarge");
            case SyncFailure.ManifestInvalid f -> get("failure.manifestInvalid", f.detail());
            case SyncFailure.ModUnavailable f -> get("failure.modUnavailable", f.mod());
            case SyncFailure.DefinitionMissing f -> get("failure.definitionMissing");
            case SyncFailure.GitHubNotConfigured f -> get("failure.githubNotConfigured");
            case SyncFailure.GitHubCodeExpired f -> get("failure.githubCodeExpired");
            case SyncFailure.GitHubDenied f -> get("failure.githubDenied");
            case SyncFailure.GitHubSignedOut f -> get("failure.githubSignedOut");
            case SyncFailure.RepoNameTaken f -> get("failure.repoNameTaken", f.name());
            case SyncFailure.NotRepoOwner f -> get("failure.notRepoOwner", f.repo());
            case SyncFailure.NotAHopSyncRepo f -> get("failure.notAHopSyncRepo", f.repo());
            case SyncFailure.PublishBuildFailed f -> get("failure.publishBuildFailed");
            case SyncFailure.UnsupportedFormat f -> get("failure.unsupportedFormat", String.valueOf(f.found()), String.valueOf(f.supported()));
            case SyncFailure.HashMismatch f -> get("failure.hashMismatch", f.file());
            case SyncFailure.SizeMismatch f -> get("failure.sizeMismatch", f.file());
            case SyncFailure.UnsafePath f -> get("failure.unsafePath", f.path());
            case SyncFailure.FolderUnavailable f -> get("failure.folderUnavailable", f.path());
            case SyncFailure.FolderInUse f -> get("failure.folderInUse");
            case SyncFailure.FolderUsedByPack f -> get("failure.folderUsedByPack", f.packName());
            case SyncFailure.DiskFull f -> get("failure.diskFull", f.path());
            case SyncFailure.FileLocked f -> get("failure.fileLocked", f.path());
            case SyncFailure.PermissionDenied f -> get("failure.permissionDenied", f.path());
            case SyncFailure.Unexpected f -> get("failure.unexpected");
        };
    }

    private static String httpStatusKey(int status) {
        if (status == 404 || status == 410) return "failure.httpStatus.notFound";
        if (status == 401 || status == 403) return "failure.httpStatus.forbidden";
        if (status >= 500 || status == 429) return "failure.httpStatus.server";
        return "failure.httpStatus.other";
    }
}
