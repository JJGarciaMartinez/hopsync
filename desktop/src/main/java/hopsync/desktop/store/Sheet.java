package hopsync.desktop.store;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.failure.SyncFailure;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * A sheet open on top of the window. Everything it shows lives here, so it renders like any other view.
 */
public sealed interface Sheet {

    /**
     * The add-pack wizard: step 1 checks the link, or describes a new pack when the player starts from
     * scratch; step 2 picks the folder.
     *
     * @param firstRun shown in the whole window while there are no packs; cannot be closed
     * @param folders  folders found on disk, {@code null} while they are being looked for
     * @param chosen   the folder picked in step 2, or {@code null}
     * @param error    why saving failed, or {@code null}
     * @param creation the new pack being described, or {@code null} when adding a pack by its link
     */
    record AddPack(boolean firstRun, int step, String url, LinkCheck link, List<GameFolder> folders,
                   GameFolder chosen, SyncFailure error, Creation creation) implements Sheet {

        public static AddPack start(boolean firstRun) {
            return new AddPack(firstRun, 1, "", new LinkCheck.Unchecked(), null, null, null, null);
        }

        /** Step 1 can move on: a checked link, or a new pack with a name, a Minecraft version and a loader. */
        public boolean canContinue() {
            return creation == null ? link instanceof LinkCheck.Valid : creation.ready();
        }

        AddPack withCreation(Creation newCreation) {
            return new AddPack(firstRun, 1, url, link, folders, chosen, null, newCreation);
        }

        AddPack withUrl(String newUrl, LinkCheck newLink) {
            return new AddPack(firstRun, step, newUrl, newLink, folders, chosen, error, creation);
        }

        AddPack withLink(LinkCheck newLink) {
            return new AddPack(firstRun, step, url, newLink, folders, chosen, error, creation);
        }

        AddPack withStep(int newStep) {
            return new AddPack(firstRun, newStep, url, link, newStep == 2 ? null : folders, newStep == 2 ? null : chosen,
                null, creation);
        }

        AddPack withFolders(List<GameFolder> found) {
            // Preselect the first free folder, as the design does
            GameFolder first = found.stream().filter(GameFolder::isFree).findFirst().orElse(null);
            return new AddPack(firstRun, step, url, link, List.copyOf(found), chosen != null ? chosen : first, error,
                creation);
        }

        AddPack withChosen(GameFolder folder) {
            return new AddPack(firstRun, step, url, link, folders, folder, null, creation);
        }

        AddPack withError(SyncFailure failure) {
            return new AddPack(firstRun, step, url, link, folders, chosen, failure, creation);
        }
    }

    /**
     * Settings of the selected pack.
     *
     * @param folder the folder picked, or the pack's own
     * @param error  why saving failed, or {@code null}
     */
    record PackSettings(String packId, Section section, String url, LinkCheck link, GameFolder folder,
                        SyncFailure error) implements Sheet {

        public enum Section { LINK, FOLDER, REMOVE }

        PackSettings withSection(Section newSection) {
            return new PackSettings(packId, newSection, url, link, folder, null);
        }

        PackSettings withUrl(String newUrl, LinkCheck newLink) {
            return new PackSettings(packId, section, newUrl, newLink, folder, null);
        }

        PackSettings withLink(LinkCheck newLink) {
            return new PackSettings(packId, section, url, newLink, folder, error);
        }

        PackSettings withFolder(GameFolder newFolder) {
            return new PackSettings(packId, section, url, link, newFolder, null);
        }

        PackSettings withError(SyncFailure failure) {
            return new PackSettings(packId, section, url, link, folder, failure);
        }
    }

    /**
     * A pack the player starts from scratch, as step 1 of the wizard describes it.
     *
     * @param minecraft     the Minecraft version, or {@code null} until the versions arrive
     * @param versions      Minecraft release versions to offer, newest first; {@code null} while loading
     * @param versionsError why the versions could not be loaded, or {@code null}
     */
    record Creation(String name, String minecraft, String loader, List<String> versions, SyncFailure versionsError) {
        /** The loaders a new pack can use, as Modrinth names them; the first is preselected. */
        public static final List<String> LOADERS = List.of("fabric", "neoforge", "forge", "quilt");

        public static Creation start() {
            return new Creation("", null, LOADERS.getFirst(), null, null);
        }

        public Creation {
            name = name == null ? "" : name;
            versions = versions == null ? null : List.copyOf(versions);
        }

        public boolean ready() {
            return !name.isBlank() && minecraft != null && LOADERS.contains(loader);
        }

        Creation withDetails(String newName, String newMinecraft, String newLoader) {
            return new Creation(newName, newMinecraft, newLoader, versions, versionsError);
        }

        /** The newest version is picked until the player picks another. */
        Creation withVersions(List<String> found) {
            String picked = minecraft != null && found.contains(minecraft) ? minecraft
                : found.isEmpty() ? null : found.getFirst();
            return new Creation(name, picked, loader, found, null);
        }

        Creation withVersionsError(SyncFailure failure) {
            return new Creation(name, minecraft, loader, versions, failure);
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
     */
    record AddMods(String packId, String minecraft, String loader, String query, List<CatalogProject> results,
                   SyncFailure searchError, Set<String> inPack, Set<String> adding, int added,
                   Added lastAdded, SyncFailure error) implements Sheet {

        public AddMods {
            results = results == null ? null : List.copyOf(results);
            inPack = inPack == null ? null : Set.copyOf(inPack);
            adding = Set.copyOf(adding);
        }

        public static AddMods start(String packId, String minecraft, String loader) {
            return new AddMods(packId, minecraft, loader, "", null, null, null, Set.of(), 0, null, null);
        }

        AddMods withQuery(String newQuery) {
            return new AddMods(packId, minecraft, loader, newQuery, null, null, inPack, adding, added, lastAdded, error);
        }

        AddMods withResults(List<CatalogProject> found, SyncFailure failure) {
            return new AddMods(packId, minecraft, loader, query, found, failure, inPack, adding, added, lastAdded, error);
        }

        AddMods withInPack(Set<String> projects) {
            return new AddMods(packId, minecraft, loader, query, results, searchError, projects, adding, added,
                lastAdded, error);
        }

        AddMods withAdding(String projectId) {
            Set<String> next = new HashSet<>(adding);
            next.add(projectId);
            return new AddMods(packId, minecraft, loader, query, results, searchError, inPack, next, added, lastAdded,
                null);
        }

        AddMods withAdded(String projectId, Added result, Set<String> projects) {
            Set<String> next = new HashSet<>(adding);
            next.remove(projectId);
            return new AddMods(packId, minecraft, loader, query, results, searchError, projects, next,
                added + 1 + result.alsoAdded().size(), result, null);
        }

        AddMods withFailure(String projectId, SyncFailure failure) {
            Set<String> next = new HashSet<>(adding);
            if (projectId != null) next.remove(projectId);
            return new AddMods(packId, minecraft, loader, query, results, searchError, inPack, next, added, lastAdded,
                failure);
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

    /** Preferences for every pack, and information about the app. */
    record AppSettings(Section section, Preferences draft) implements Sheet {

        public enum Section { GENERAL, ACCOUNT, ABOUT }
    }
}
