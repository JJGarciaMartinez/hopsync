package hopsync.desktop.store;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.event.SyncEvent;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.Manifest;
import hopsync.core.publish.PublishStep;
import hopsync.core.pack.Pack;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Everything that can change the UI state: events from the core, data loaded in the background and what
 * the player does. Views only dispatch actions; {@code Effects} turns the ones with side effects into
 * calls to the core.
 */
public sealed interface UiAction {

    // From the core and background loads

    /** An event of any pack's session; the reducer switches over {@link SyncEvent} exhaustively. */
    record SyncEventReceived(SyncEvent event, Instant at) implements UiAction {}

    /** The packs in the registry and the app preferences, after startup or after the settings changed. */
    record PacksLoaded(List<Pack> packs, String activeId, Preferences preferences) implements UiAction {
        public PacksLoaded(List<Pack> packs, String activeId) {
            this(packs, activeId, Preferences.DEFAULT);
        }
    }

    record InstalledModsLoaded(String packId, InstalledMods mods) implements UiAction {}

    /** The pack's manifest, from the cache at startup and then from the network. */
    record ManifestLoaded(String packId, Manifest manifest) implements UiAction {}

    record ManifestFailed(String packId, SyncFailure failure) implements UiAction {}

    // What the player does with a pack

    record CheckPack(String packId) implements UiAction {}

    /** Checks every pack that is not already busy. */
    record CheckAll() implements UiAction {}

    record ConfirmPlan(String packId) implements UiAction {}

    record DismissPlan(String packId) implements UiAction {}

    /**
     * Retries failed downloads or continues a cancelled update: the plan is computed again and applied
     * without a new review when it only touches {@code files}.
     */
    record ContinueUpdate(String packId, Set<String> files) implements UiAction {
        public ContinueUpdate {
            files = Set.copyOf(files);
        }
    }

    /** Stops the check or apply, or takes the pack out of the apply queue. */
    record CancelUpdate(String packId) implements UiAction {}

    record ReloadInstalled(String packId) implements UiAction {}

    record OpenFolder(String packId) implements UiAction {}

    record CopyServerIp(String packId) implements UiAction {}

    record CopyPackLink(String packId) implements UiAction {}

    /** Opens a mod's page (Modrinth or the pack's repository) in the browser. */
    record OpenModPage(String url) implements UiAction {}

    /** "Cambiar versión" on a created pack's row: Effects loads the mod's versions for the menu. */
    record LoadModVersions(String packId, String slug, String currentVersion) implements UiAction {}

    record ModVersionsLoaded(String packId, String slug, List<ModVersions.Choice> choices) implements UiAction {}

    record ModVersionsFailed(String packId, String slug, SyncFailure failure) implements UiAction {}

    /** The version menu closed without a choice. */
    record CloseModVersions() implements UiAction {}

    /** Pins a mod of a created pack to another version; the pack is checked so it goes through review. */
    record ChangeModVersion(String packId, String slug, String versionId) implements UiAction {}

    /**
     * Takes a mod out of a created pack; the pack is checked so the removal goes through review.
     *
     * @param slugOrFile the catalog mod's slug, or the file name of one of the player's jars
     */
    record RemoveModFromPack(String packId, String slugOrFile, String name, boolean ownJar) implements UiAction {}

    /** Forgets the pack after the player confirmed; its files stay where they are. */
    record RemovePack(String packId) implements UiAction {}

    // Sheets

    /** "Agregar mods" of a pack the player created; Effects loads what it has and the most downloaded mods. */
    record OpenAddMods(String packId) implements UiAction {}

    /** Search text of the add-mods sheet, dispatched once the player stops typing. */
    record SearchMods(String query) implements UiAction {}

    record ModsFound(String query, List<CatalogProject> results) implements UiAction {}

    record ModSearchFailed(String query, SyncFailure failure) implements UiAction {}

    /** The catalog projects the pack already lists. */
    record PackModsLoaded(String packId, Set<String> projects) implements UiAction {}

    record AddCatalogMod(String projectId) implements UiAction {}

    /** A jar the player picked, copied into the pack as one of its own mods. */
    record AddOwnJar(String packId, Path jar) implements UiAction {}

    /** @param projectId the catalog project added, or {@code null} for an own jar */
    record ModAdded(String projectId, Sheet.Added result, Set<String> projects) implements UiAction {}

    /** @param projectId the catalog project that could not be added, or {@code null} for an own jar */
    record ModAddFailed(String projectId, SyncFailure failure) implements UiAction {}

    /** Settings of the selected pack (or the add-pack wizard when there are no packs). */
    record OpenSettings() implements UiAction {}

    record AddPack() implements UiAction {}

    /** The selected pack's settings, on the section that removes it. */
    record AskRemovePack() implements UiAction {}

    record OpenAppSettings(Sheet.AppSettings.Section section) implements UiAction {}

    /** Esc or "Cancelar"; the first-run wizard cannot be closed. */
    record CloseSheet() implements UiAction {}

    /** A sheet saved; the packs are reloaded by Effects. */
    record SheetSaved() implements UiAction {}

    record SheetFailed(SyncFailure failure) implements UiAction {}

    record SetLinkUrl(String url) implements UiAction {}

    /** Validates the link typed in the open sheet and, if it can be a pack, fetches its manifest. */
    record CheckLink() implements UiAction {}

    record LinkChecked(String url, Manifest manifest) implements UiAction {}

    record LinkFailed(String url, SyncFailure failure) implements UiAction {}

    record WizardNext() implements UiAction {}

    /** "Empieza desde cero": the wizard describes a new pack instead of checking a link; Effects loads the versions. */
    record StartFromScratch() implements UiAction {}

    /** Back from a new pack to adding one by its link. */
    record UseLink() implements UiAction {}

    /** Name, Minecraft version, loader and server of the new pack, as typed and picked. */
    record EditNewPack(String name, String minecraft, String loader, String server) implements UiAction {}

    record GameVersionsLoaded(List<String> versions) implements UiAction {}

    record GameVersionsFailed(SyncFailure failure) implements UiAction {}

    record WizardBack() implements UiAction {}

    record FoldersFound(List<GameFolder> folders) implements UiAction {}

    /** A folder picked in the wizard or the pack settings; Effects then checks who uses it. */
    record ChooseFolder(Path path) implements UiAction {}

    record FolderChecked(GameFolder folder) implements UiAction {}

    record FinishAddPack() implements UiAction {}

    record SelectPackSection(Sheet.PackSettings.Section section) implements UiAction {}

    /** The server address typed in the settings of a pack the player created. */
    record SetServerAddress(String address) implements UiAction {}

    record SavePackSettings() implements UiAction {}

    record SetPreferences(Preferences preferences) implements UiAction {}

    record SaveAppSettings() implements UiAction {}

    /**
     * What the keychain holds for GitHub, at start and after disconnecting.
     *
     * @param login      the user signed in, or {@code null}
     * @param persistent the system keychain keeps the token between runs
     * @param available  this build can sign in to GitHub
     */
    record AccountLoaded(String login, boolean persistent, boolean available) implements UiAction {}

    /** "Conectar GitHub": Effects asks GitHub for a code. */
    record ConnectGitHub() implements UiAction {}

    record GitHubCodeShown(String userCode, String verificationUri) implements UiAction {}

    /** Copies the code and opens GitHub's page to type it. */
    record OpenGitHubDevice() implements UiAction {}

    record GitHubConnected(String login) implements UiAction {}

    record GitHubConnectFailed(SyncFailure failure) implements UiAction {}

    /** Stops waiting for the player to approve on GitHub. */
    record CancelGitHubConnect() implements UiAction {}

    /** Forgets the token on this computer. */
    record DisconnectGitHub() implements UiAction {}

    /** "Publicar" or "Publicar cambios" on a created pack: opens the publish sheet. */
    record OpenPublish(String packId) implements UiAction {}

    /** The configs and jars of the player that a publish would share. */
    record PublishFilesLoaded(String packId, List<String> ownFiles) implements UiAction {}

    record SetRepoName(String name) implements UiAction {}

    record ConfirmOwnFiles(boolean confirmed) implements UiAction {}

    record StartPublish() implements UiAction {}

    record PublishProgressed(String packId, PublishStep step) implements UiAction {}

    record Published(String packId, String link) implements UiAction {}

    record PublishFailed(String packId, SyncFailure failure) implements UiAction {}

    /** Whether a published created pack changed since its last publish. */
    record PublishStateChecked(String packId, boolean unpublished) implements UiAction {}

    /** A HopSync release newer than this copy, found once at start. */
    record AppUpdateFound(String version, String url) implements UiAction {}

    /** Opens the page of the newer release. */
    record OpenAppUpdate() implements UiAction {}

    /** Copies version, system and paths for a bug report. */
    record CopyDiagnostics() implements UiAction {}

    /** Opens the folder with the log files, to attach them to a bug report. */
    record OpenLogsFolder() implements UiAction {}

    /** Closes HopSync: stops whatever is running (safe: the state always matches the disk) and exits. */
    record Quit() implements UiAction {}

    /** A short notice at the bottom of the window. */
    record ShowToast(String text) implements UiAction {}

    // Navigation, only UI state

    record SelectPack(String packId) implements UiAction {}

    record SelectTab(UiState.Tab tab) implements UiAction {}

    record SetFilter(UiState.Filter filter) implements UiAction {}

    record SetQuery(String query) implements UiAction {}

    /** Search text of the pack library. */
    record SetPackQuery(String query) implements UiAction {}

    /** The library dragged to a new width, in unscaled pixels; saved for the next start. */
    record ResizeLibrary(int width) implements UiAction {}

    /** Expands the row of the file, or collapses it if it was expanded. */
    record ToggleRow(String file) implements UiAction {}
}
