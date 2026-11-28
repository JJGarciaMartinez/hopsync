package hopsync.core.pack;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.port.PackDefinitionRepository;
import hopsync.core.port.PackSettingsRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The packs the player keeps in sync. Changes are saved right away; removing a pack only forgets it
 * and never touches its game folder. Each pack needs its own game folder. Packs the player created also
 * keep their definition, which is removed only when asked.
 */
public class PackRegistry {
    private static final System.Logger LOG = System.getLogger(PackRegistry.class.getName());

    private final PackSettingsRepository repository;
    private final PackDefinitionRepository definitions;
    private PackSettings settings;

    public PackRegistry(PackSettingsRepository repository, PackDefinitionRepository definitions) {
        this.repository = repository;
        this.definitions = definitions;
        this.settings = repository.load();
    }

    public synchronized PackSettings settings() {
        return settings;
    }

    public synchronized List<Pack> packs() {
        return settings.packs();
    }

    public synchronized boolean isEmpty() {
        return settings.packs().isEmpty();
    }

    public synchronized Optional<Pack> find(String id) {
        return settings.find(id);
    }

    public synchronized Optional<Pack> active() {
        return settings.active();
    }

    public synchronized int parallelDownloads() {
        return settings.parallelDownloads();
    }

    public synchronized boolean chainQueue() {
        return settings.chainQueue();
    }

    /**
     * Adds a pack and selects it when it is the first one.
     *
     * @throws SyncException with {@link SyncFailure.FolderUsedByPack} when another pack uses the folder
     */
    public synchronized Pack add(String manifestUrl, Path gameDir) throws IOException {
        Pack pack = new Pack(UUID.randomUUID().toString(), manifestUrl, gameDir, "");
        checkFolderIsFree(pack);
        List<Pack> packs = new ArrayList<>(settings.packs());
        packs.add(pack);
        save(settings.withPacks(packs, settings.activeId()));
        return pack;
    }

    /**
     * Adds a pack the player creates, not yet published, and keeps its definition.
     *
     * @throws SyncException with {@link SyncFailure.FolderUsedByPack} when another pack uses the folder
     */
    public synchronized Pack addLocal(PackDefinition definition, Path gameDir) throws IOException {
        Pack pack = new Pack(UUID.randomUUID().toString(), new PackSource.Local(null), gameDir,
            definition.pack().name());
        checkFolderIsFree(pack);
        definitions.save(pack.id(), definition);
        List<Pack> packs = new ArrayList<>(settings.packs());
        packs.add(pack);
        try {
            save(settings.withPacks(packs, settings.activeId()));
        } catch (IOException e) {
            // Without the pack in the settings nothing refers to the definition any more
            definitions.delete(pack.id());
            throw e;
        }
        return pack;
    }

    /** The definition of a pack the player created; empty for other packs or when it cannot be read. */
    public synchronized Optional<PackDefinition> definition(String id) {
        return settings.find(id).filter(Pack::isLocal).flatMap(pack -> definitions.load(id));
    }

    /** Saves a created pack's edited definition, and its name for the library. */
    public synchronized void saveDefinition(String id, PackDefinition definition) throws IOException {
        Pack pack = settings.find(id).filter(Pack::isLocal)
            .orElseThrow(() -> new IllegalArgumentException("Not a created pack " + id));
        definitions.save(id, definition);
        if (!pack.name().equals(definition.pack().name())) {
            update(pack.withName(definition.pack().name()));
        }
    }

    /** Records where a created pack is published. */
    public synchronized void published(String id, PackSource.Publication publication) throws IOException {
        Pack pack = settings.find(id).filter(Pack::isLocal)
            .orElseThrow(() -> new IllegalArgumentException("Not a created pack " + id));
        update(pack.withSource(new PackSource.Local(publication)));
    }

    /**
     * Replaces the pack with the same id.
     *
     * @throws SyncException with {@link SyncFailure.FolderUsedByPack} when another pack uses the new folder
     */
    public synchronized void update(Pack pack) throws IOException {
        if (settings.find(pack.id()).isEmpty()) {
            throw new IllegalArgumentException("Unknown pack " + pack.id());
        }
        checkFolderIsFree(pack);
        List<Pack> packs = settings.packs().stream()
            .map(existing -> existing.id().equals(pack.id()) ? pack : existing)
            .toList();
        save(settings.withPacks(packs, settings.activeId()));
    }

    /** Forgets the pack; its game folder and, for a created pack, its definition are left as they are. */
    public synchronized void remove(String id) throws IOException {
        remove(id, false);
    }

    /**
     * Forgets the pack; its game folder is left as it is.
     *
     * @param deleteDefinition for a created pack, also delete its definition and its own jars and configs
     */
    public synchronized void remove(String id, boolean deleteDefinition) throws IOException {
        boolean local = settings.find(id).map(Pack::isLocal).orElse(false);
        List<Pack> packs = settings.packs().stream().filter(pack -> !pack.id().equals(id)).toList();
        save(settings.withPacks(packs, settings.activeId()));
        if (local && deleteDefinition) {
            definitions.delete(id);
        }
    }

    public synchronized void select(String id) throws IOException {
        if (settings.find(id).isEmpty()) {
            throw new IllegalArgumentException("Unknown pack " + id);
        }
        save(settings.withPacks(settings.packs(), id));
    }

    /**
     * Keeps the name from the latest manifest for display. A failure to save is only logged: the name is
     * fetched again on the next check.
     */
    public synchronized void rememberName(String id, String name) {
        Optional<Pack> pack = settings.find(id);
        if (pack.isEmpty() || pack.get().name().equals(name)) {
            return;
        }
        try {
            update(pack.get().withName(name));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the name of pack " + id, e);
        }
    }

    /** The player's decisions on the pack's optional config units, by unit key. */
    public synchronized Map<String, OptionalChoice> choices(String packId) {
        return settings.choicesOf(packId);
    }

    /** Saves the decisions for these units, keeping the ones for other units of the pack. */
    public synchronized void recordChoices(String packId, Map<String, OptionalChoice> decided) throws IOException {
        Map<String, OptionalChoice> merged = new HashMap<>(settings.choicesOf(packId));
        merged.putAll(decided);
        save(settings.withChoices(packId, merged));
    }

    /** Marks the accepted units as installed, so later updates treat their files like any other config. */
    public synchronized void markApplied(String packId, Set<String> unitKeys) throws IOException {
        Map<String, OptionalChoice> merged = new HashMap<>(settings.choicesOf(packId));
        boolean changed = false;
        for (String key : unitKeys) {
            OptionalChoice choice = merged.get(key);
            if (choice != null && choice.accepted() && !choice.applied()) {
                merged.put(key, choice.asApplied());
                changed = true;
            }
        }
        if (changed) {
            save(settings.withChoices(packId, merged));
        }
    }

    private void save(PackSettings next) throws IOException {
        repository.save(next);
        settings = next;
    }

    /**
     * The pack whose folder is, contains or is inside {@code folder}, other than {@code exceptPackId}.
     *
     * @param exceptPackId the pack being edited, which may keep its own folder, or {@code null}
     */
    public synchronized Optional<Pack> packUsing(Path folder, String exceptPackId) {
        Path wanted = canonical(folder);
        for (Pack other : settings.packs()) {
            if (other.id().equals(exceptPackId)) continue;
            Path otherFolder = canonical(other.gameDir());
            if (wanted.startsWith(otherFolder) || otherFolder.startsWith(wanted)) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    /**
     * Saves the app-wide preferences; the number of parallel downloads is kept within 1–8.
     *
     * @param language        language tag of the app's texts, or blank to follow the system
     * @param checkAppUpdates ask for a newer HopSync release when the app starts
     */
    public synchronized void updatePreferences(boolean checkOnStart, boolean chainQueue, int parallelDownloads,
                                               boolean reduceMotion, String language, boolean checkAppUpdates)
            throws IOException {
        save(new PackSettings(settings.packs(), settings.activeId(), checkOnStart, chainQueue, parallelDownloads,
            reduceMotion, settings.libraryWidth(), language, checkAppUpdates, settings.choices()));
    }

    /**
     * Keeps the library width the player dragged to, kept within 200–360. A failure to save is only logged: the
     * library starts at its default width next time.
     */
    public synchronized void rememberLibraryWidth(int width) {
        if (settings.libraryWidth() == width) return;
        try {
            save(new PackSettings(settings.packs(), settings.activeId(), settings.checkOnStart(), settings.chainQueue(),
                settings.parallelDownloads(), settings.reduceMotion(), width, settings.language(),
                settings.checkAppUpdates(), settings.choices()));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the library width", e);
        }
    }

    private void checkFolderIsFree(Pack candidate) throws SyncException {
        Optional<Pack> other = packUsing(candidate.gameDir(), candidate.id());
        if (other.isPresent()) {
            Pack used = other.get();
            throw new SyncException(new SyncFailure.FolderUsedByPack(candidate.gameDir().toString(), used.label()));
        }
    }

    /**
     * The real location of a folder, following symlinks of the part that exists, so two spellings of the
     * same folder compare equal even before the folder is created.
     */
    static Path canonical(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute));
        } catch (IOException e) {
            return absolute;
        }
    }
}
