package hopsync.desktop.store;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.PackInfo;
import hopsync.core.publish.PublishStep;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * A sheet open on top of the window. Everything it shows lives here, so it renders like any other view.
 */
public sealed interface Sheet {

    /**
     * The add-pack wizard: step 1 checks the link, describes a new pack when the player starts from scratch, or
     * picks one of their published packs to recover; step 2 picks the folder.
     *
     * @param firstRun shown in the whole window while there are no packs; cannot be closed
     * @param folders  folders found on disk, {@code null} while they are being looked for
     * @param chosen   the folder picked in step 2, or {@code null}
     * @param error    why saving failed, or {@code null}
     * @param creation the new pack being described, or {@code null} when adding a pack by its link
     * @param recovery the player's published packs being picked, or {@code null}; never together with a creation
     * @param ownRepo  the player's own repository the checked link comes from, or {@code null}: saving the link then
     *                 recovers the pack for editing instead of only following it
     */
    record AddPack(boolean firstRun, int step, String url, LinkCheck link, List<GameFolder> folders,
                   GameFolder chosen, SyncFailure error, Creation creation, Recovery recovery,
                   String ownRepo) implements Sheet {

        public static AddPack start(boolean firstRun) {
            return new AddPack(firstRun, 1, "", new LinkCheck.Unchecked(), null, null, null, null, null, null);
        }

        /** Step 1 can move on: a checked link, a repository picked to recover, or a new pack that is described. */
        public boolean canContinue() {
            if (creation != null) return creation.ready();
            if (recovery != null) return recovery.chosen() != null;
            return link instanceof LinkCheck.Valid;
        }

        AddPack withCreation(Creation newCreation) {
            return new AddPack(firstRun, 1, url, link, folders, chosen, null, newCreation, null, ownRepo);
        }

        AddPack withRecovery(Recovery newRecovery) {
            return new AddPack(firstRun, 1, url, link, folders, chosen, null, null, newRecovery, ownRepo);
        }

        AddPack withUrl(String newUrl, LinkCheck newLink) {
            return new AddPack(firstRun, step, newUrl, newLink, folders, chosen, error, creation, recovery, null);
        }

        AddPack withLink(LinkCheck newLink) {
            return new AddPack(firstRun, step, url, newLink, folders, chosen, error, creation, recovery, ownRepo);
        }

        AddPack withOwnRepo(String repo) {
            return new AddPack(firstRun, step, url, link, folders, chosen, error, creation, recovery, repo);
        }

        AddPack withStep(int newStep) {
            return new AddPack(firstRun, newStep, url, link, newStep == 2 ? null : folders, newStep == 2 ? null : chosen,
                null, creation, recovery, ownRepo);
        }

        AddPack withFolders(List<GameFolder> found) {
            // Preselect the first free folder, as the design does
            GameFolder first = found.stream().filter(GameFolder::isFree).findFirst().orElse(null);
            return new AddPack(firstRun, step, url, link, List.copyOf(found), chosen != null ? chosen : first, error,
                creation, recovery, ownRepo);
        }

        AddPack withChosen(GameFolder folder) {
            return new AddPack(firstRun, step, url, link, folders, folder, null, creation, recovery, ownRepo);
        }

        AddPack withError(SyncFailure failure) {
            return new AddPack(firstRun, step, url, link, folders, chosen, failure, creation, recovery, ownRepo);
        }
    }

    /**
     * The player's published packs, offered to bring back for editing.
     *
     * @param repos  their repositories as {@code owner/name}, {@code null} while they are being looked for
     * @param chosen the repository picked, or {@code null}
     * @param error  why the list could not be loaded, or {@code null}; {@code GitHubSignedOut} asks to connect
     */
    record Recovery(List<String> repos, String chosen, SyncFailure error) {

        public static Recovery start() {
            return new Recovery(null, null, null);
        }

        /** Preselects the repository when there is only one. */
        Recovery withRepos(List<String> found) {
            return new Recovery(List.copyOf(found), found.size() == 1 ? found.getFirst() : chosen, null);
        }

        Recovery withChosen(String repo) {
            return new Recovery(repos, repo, error);
        }

        Recovery withError(SyncFailure failure) {
            return new Recovery(repos, chosen, failure);
        }
    }

    /**
     * Settings of the selected pack.
     *
     * @param folder the folder picked, or the pack's own
     * @param server the server address of a pack the player created, as typed; blank for none, {@code null} while
     *               the pack's details are not known, which keeps the saved one
     * @param error  why saving failed, or {@code null}
     */
    record PackSettings(String packId, Section section, String url, LinkCheck link, GameFolder folder, String server,
                        SyncFailure error) implements Sheet {

        /** {@link #SERVER} is only offered for a pack the player created: the others take it from their link. */
        public enum Section { LINK, SERVER, FOLDER, REMOVE }

        /** Blank, or an address players could type into Minecraft. */
        public boolean serverValid() {
            return validServer(server);
        }

        PackSettings withSection(Section newSection) {
            return new PackSettings(packId, newSection, url, link, folder, server, null);
        }

        PackSettings withUrl(String newUrl, LinkCheck newLink) {
            return new PackSettings(packId, section, newUrl, newLink, folder, server, null);
        }

        PackSettings withLink(LinkCheck newLink) {
            return new PackSettings(packId, section, url, newLink, folder, server, error);
        }

        PackSettings withFolder(GameFolder newFolder) {
            return new PackSettings(packId, section, url, link, newFolder, server, null);
        }

        PackSettings withServer(String newServer) {
            return new PackSettings(packId, section, url, link, folder, newServer, null);
        }

        PackSettings withError(SyncFailure failure) {
            return new PackSettings(packId, section, url, link, folder, server, failure);
        }
    }

    /** Blank for no server, or a {@link PackInfo#isServerAddress server address} once trimmed. */
    static boolean validServer(String server) {
        return server == null || server.isBlank() || PackInfo.isServerAddress(server.strip());
    }

    /**
     * A pack the player starts from scratch, as step 1 of the wizard describes it.
     *
     * @param minecraft     the Minecraft version, or {@code null} until the versions arrive
     * @param server        the address of the server it is played on, optional; blank for none
     * @param versions      Minecraft release versions to offer, newest first; {@code null} while loading
     * @param versionsError why the versions could not be loaded, or {@code null}
     */
    record Creation(String name, String minecraft, String loader, String server, List<String> versions,
                    SyncFailure versionsError) {
        /** The loaders a new pack can use, as Modrinth names them; the first is preselected. */
        public static final List<String> LOADERS = List.of("fabric", "neoforge", "forge", "quilt");

        public static Creation start() {
            return new Creation("", null, LOADERS.getFirst(), "", null, null);
        }

        public Creation {
            name = name == null ? "" : name;
            server = server == null ? "" : server;
            versions = versions == null ? null : List.copyOf(versions);
        }

        public boolean ready() {
            return !name.isBlank() && minecraft != null && LOADERS.contains(loader) && serverValid();
        }

        public boolean serverValid() {
            return validServer(server);
        }

        Creation withDetails(String newName, String newMinecraft, String newLoader, String newServer) {
            return new Creation(newName, newMinecraft, newLoader, newServer, versions, versionsError);
        }

        /** The newest version is picked until the player picks another. */
        Creation withVersions(List<String> found) {
            String picked = minecraft != null && found.contains(minecraft) ? minecraft
                : found.isEmpty() ? null : found.getFirst();
            return new Creation(name, picked, loader, server, found, null);
        }

        Creation withVersionsError(SyncFailure failure) {
            return new Creation(name, minecraft, loader, server, versions, failure);
        }
    }

    /**
     * Finding mods for a pack the player created and adding them; each one is saved to the pack as it is added.
     *
     * @param results     mods found for {@code query}, {@code null} while searching
     * @param searchError why the search failed, or {@code null}
     * @param inPack      catalog projects the pack lists, {@code null} until known
     * @param adding      projects being added right now
     * @param added       how many mods this sheet added, dependencies included
     * @param lastAdded   what the last add did, to say which mods came with it; or {@code null}
     * @param error       why the last add failed, or {@code null}
     * @param detail      the mod open in place of the list, or {@code null} for the list
     */
    record AddMods(String packId, String minecraft, String loader, String query, List<CatalogProject> results,
                   SyncFailure searchError, Set<String> inPack, Set<String> adding, int added,
                   Added lastAdded, SyncFailure error, ModDetail detail) implements Sheet {

        public AddMods {
            results = results == null ? null : List.copyOf(results);
            inPack = inPack == null ? null : Set.copyOf(inPack);
            adding = Set.copyOf(adding);
        }

        public static AddMods start(String packId, String minecraft, String loader) {
            return new AddMods(packId, minecraft, loader, "", null, null, null, Set.of(), 0, null, null, null);
        }

        AddMods withQuery(String newQuery) {
            return new AddMods(packId, minecraft, loader, newQuery, null, null, inPack, adding, added, lastAdded, error, null);
        }

        AddMods withResults(List<CatalogProject> found, SyncFailure failure) {
            return new AddMods(packId, minecraft, loader, query, found, failure, inPack, adding, added, lastAdded, error,
                detail);
        }

        AddMods withInPack(Set<String> projects) {
            return new AddMods(packId, minecraft, loader, query, results, searchError, projects, adding, added,
                lastAdded, error, detail);
        }

        AddMods withAdding(String projectId) {
            Set<String> next = new HashSet<>(adding);
            next.add(projectId);
            return new AddMods(packId, minecraft, loader, query, results, searchError, inPack, next, added, lastAdded,
                null, detail);
        }

        AddMods withAdded(String projectId, Added result, Set<String> projects) {
            Set<String> next = new HashSet<>(adding);
            next.remove(projectId);
            // Only the added mod's own detail returns to the list; another one opened meanwhile stays open
            boolean ownDetail = detail != null && detail.project().id().equals(projectId);
            return new AddMods(packId, minecraft, loader, query, results, searchError, projects, next,
                added + 1 + result.alsoAdded().size(), result, null, ownDetail ? null : detail);
        }

        AddMods withFailure(String projectId, SyncFailure failure) {
            Set<String> next = new HashSet<>(adding);
            if (projectId != null) next.remove(projectId);
            return new AddMods(packId, minecraft, loader, query, results, searchError, inPack, next, added, lastAdded,
                failure, detail);
        }

        AddMods withDetail(ModDetail open) {
            return new AddMods(packId, minecraft, loader, query, results, searchError, inPack, adding, added,
                lastAdded, error, open);
        }

        AddMods withoutDetail() {
            return withDetail(null);
        }
    }

    /**
     * A mod's page inside the add-mods sheet.
     *
     * @param details   the catalog page, {@code null} while loading
     * @param version   the version that would be added, {@code null} when there is none
     * @param needs     names of the required mods it would bring
     * @param unavailable the name of the mod, this one or one it requires, that has no version for the pack's
     *                    Minecraft version and loader; {@code null} when it can be added
     * @param failure     why loading failed, or {@code null}
     * @param shot        index of the gallery picture shown
     */
    record ModDetail(CatalogProject project, ProjectDetails details, CatalogVersion version, List<String> needs,
                     String unavailable, SyncFailure failure, int shot) {
        public ModDetail {
            needs = List.copyOf(needs);
        }

        static ModDetail loading(CatalogProject project) {
            return new ModDetail(project, null, null, List.of(), null, null, 0);
        }

        public boolean noVersion() {
            return unavailable != null;
        }

        /** The required mod without a version, or {@code null} when it is this mod itself or none. */
        public String missingDependency() {
            return unavailable == null || unavailable.isBlank() || unavailable.equals(project.title())
                || unavailable.equals(project.id()) || unavailable.equals(project.slug()) ? null : unavailable;
        }
    }

    /**
     * What adding a mod did, for the add-mods sheet to say.
     *
     * @param title     the mod's name, or the jar's file name
     * @param alsoAdded names of the mods it needs that were added with it
     */
    record Added(String title, List<String> alsoAdded) {
        public Added {
            alsoAdded = List.copyOf(alsoAdded);
        }
    }

    /**
     * Publishing a created pack on GitHub: review (repository name on a first publish, the player's own files to
     * confirm), then the steps as they happen, then the link or what failed.
     *
     * @param update      the pack is already published; this publishes its changes
     * @param ownFiles    configs and jars that do not come from the catalog, {@code null} while they are listed
     * @param confirmed   the player confirmed they may share their own files
     * @param step        the step running while publishing, or {@code null}
     * @param link        the pack's link once published, or {@code null}
     * @param error       why publishing failed, or {@code null}
     */
    record Publish(String packId, boolean update, String repoName, List<String> ownFiles, boolean confirmed,
                   Stage stage, PublishStep step, String link, SyncFailure error) implements Sheet {

        public enum Stage { REVIEW, PUBLISHING, DONE }

        public Publish {
            ownFiles = ownFiles == null ? null : List.copyOf(ownFiles);
        }

        public static Publish start(String packId, boolean update, String repoName) {
            return new Publish(packId, update, repoName, null, false, Stage.REVIEW, null, null, null);
        }

        /** A name GitHub accepts, files listed, and their sharing confirmed when there are any. */
        public boolean canPublish() {
            return stage == Stage.REVIEW && ownFiles != null && (ownFiles.isEmpty() || confirmed)
                && (update || validRepoName(repoName));
        }

        /** Letters, digits, "-", "_" and ".", up to 100, as GitHub allows; not "." or "..". */
        public static boolean validRepoName(String name) {
            return name != null && name.matches("[A-Za-z0-9._-]{1,100}") && !name.equals(".") && !name.equals("..");
        }

        Publish with(String newName, List<String> files, boolean ok, Stage newStage, PublishStep newStep,
                     String newLink, SyncFailure failure) {
            return new Publish(packId, update, newName, files, ok, newStage, newStep, newLink, failure);
        }
    }

    /** Preferences for every pack, and information about the app. */
    record AppSettings(Section section, Preferences draft) implements Sheet {

        public enum Section { GENERAL, ACCOUNT, ABOUT }
    }
}
