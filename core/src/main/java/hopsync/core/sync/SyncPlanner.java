package hopsync.core.sync;

import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ConfigRoot;
import hopsync.core.manifest.Manifest;
import hopsync.core.manifest.ModEntry;
import hopsync.core.pack.OptionalChoice;
import hopsync.core.sync.SyncPlan.ConfigAction;
import hopsync.core.sync.SyncPlan.ConfigChange;
import hopsync.core.sync.SyncPlan.ModAction;
import hopsync.core.sync.SyncPlan.ModChange;
import hopsync.core.util.SafePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Compares a manifest with the files on disk and what HopSync installed before, without
 * modifying anything. Files HopSync did not install are never scheduled for removal.
 */
public class SyncPlanner {
    private final Path modsDir;
    private final Path configDir;
    private final Path gameDir;

    public SyncPlanner(Path modsDir, Path configDir, Path gameDir) {
        this.modsDir = modsDir;
        this.configDir = configDir;
        this.gameDir = gameDir;
    }

    /** Plans a pack where the player has decided nothing about its optional configs yet. */
    public SyncPlan plan(Manifest manifest, LocalState state) throws IOException {
        return plan(manifest, state, Map.of());
    }

    /**
     * @param choices the player's decisions on optional config units, by {@link ConfigUnits.Unit#key()}
     */
    public SyncPlan plan(Manifest manifest, LocalState state, Map<String, OptionalChoice> choices)
            throws IOException {
        List<ModAction> modActions = new ArrayList<>();
        Map<String, FileRecord> keptMods = new HashMap<>();
        for (ModEntry mod : manifest.mods()) {
            Path path = SafePaths.resolveInside(modsDir, mod.file());
            if (!Files.isRegularFile(path)) {
                modActions.add(new ModAction(mod, ModChange.NEW));
                continue;
            }
            FileRecord current = FileHasher.describe(path, state.mods().get(mod.file()));
            if (current.sha512().equals(mod.sha512())) {
                modActions.add(new ModAction(mod, ModChange.UP_TO_DATE));
                // Same hash as the manifest, so this is the manifest's version (also fills in old records)
                keptMods.put(mod.file(), current.withVersion(mod.version()));
            } else {
                modActions.add(new ModAction(mod, ModChange.UPDATE));
            }
        }

        List<String> removals = planRemovals(manifest, state);

        List<ConfigAction> configActions = new ArrayList<>();
        Map<String, FileRecord> keptConfigs = new HashMap<>();
        Map<ConfigUnits.Unit, Boolean> unitSatisfied = new HashMap<>();
        for (ConfigEntry config : manifest.configs()) {
            Path path = SafePaths.resolveInside(baseOf(config), config.path());
            FileRecord installed = state.configs().get(config.stateKey());
            FileRecord current = Files.isRegularFile(path) ? FileHasher.describe(path, installed) : null;
            ConfigChange change = config.optional()
                ? optionalChange(config, current, installed, manifest, choices, unitSatisfied)
                : change(config, current, installed);
            configActions.add(new ConfigAction(config, change));
            if (change == ConfigChange.UP_TO_DATE) {
                keptConfigs.put(config.stateKey(), current);
            }
        }

        return new SyncPlan(manifest, modActions, removals, configActions, keptMods, keptConfigs);
    }

    private Path baseOf(ConfigEntry config) {
        return config.root() == ConfigRoot.GAME ? gameDir : configDir;
    }

    private static ConfigChange change(ConfigEntry config, FileRecord current, FileRecord installed) {
        if (current == null) {
            return ConfigChange.INSTALL;
        }
        boolean untouchedByPlayer = installed != null && installed.sha512().equals(current.sha512());
        if (current.sha512().equals(config.sha512())) {
            return ConfigChange.UP_TO_DATE;
        }
        return config.overwrite() || untouchedByPlayer ? ConfigChange.REPLACE : ConfigChange.KEEP_EDITED;
    }

    /**
     * An optional unit is offered until the player decides on its current revision; a unit whose files already match
     * the manifest has nothing to offer. An accepted unit is forced in once, then treated like any other config.
     */
    private ConfigChange optionalChange(ConfigEntry config, FileRecord current, FileRecord installed,
                                        Manifest manifest, Map<String, OptionalChoice> choices,
                                        Map<ConfigUnits.Unit, Boolean> unitSatisfied) throws IOException {
        ConfigUnits.Unit unit = ConfigUnits.unitOf(config);
        List<ConfigEntry> files = ConfigUnits.entriesOf(unit, manifest.configs());
        OptionalChoice choice = choices.get(unit.key());
        boolean decided = choice != null && choice.revision().equals(ConfigUnits.revision(files));
        if (!decided) {
            if (satisfied(unit, files, unitSatisfied)) {
                return ConfigChange.UP_TO_DATE;
            }
            return ConfigChange.OFFERED;
        }
        if (!choice.accepted()) {
            return ConfigChange.SKIPPED;
        }
        if (choice.applied()) {
            return change(config, current, installed);
        }
        if (current == null) {
            return ConfigChange.INSTALL;
        }
        return current.sha512().equals(config.sha512()) ? ConfigChange.UP_TO_DATE : ConfigChange.REPLACE_WITH_BACKUP;
    }

    private boolean satisfied(ConfigUnits.Unit unit, List<ConfigEntry> files, Map<ConfigUnits.Unit, Boolean> cache)
            throws IOException {
        Boolean known = cache.get(unit);
        if (known != null) {
            return known;
        }
        boolean all = true;
        for (ConfigEntry file : files) {
            Path path = SafePaths.resolveInside(baseOf(file), file.path());
            if (!Files.isRegularFile(path) || !FileHasher.describe(path, null).sha512().equals(file.sha512())) {
                all = false;
                break;
            }
        }
        cache.put(unit, all);
        return all;
    }

    /**
     * Mods HopSync installed that left the pack. A file the player replaced since installation
     * (hash no longer matches the record) is left alone and simply stops being managed.
     */
    private List<String> planRemovals(Manifest manifest, LocalState state) throws IOException {
        Set<String> inManifest = manifest.mods().stream().map(ModEntry::file).collect(Collectors.toSet());
        Set<String> removals = new TreeSet<>();
        for (Map.Entry<String, FileRecord> entry : state.mods().entrySet()) {
            String file = entry.getKey();
            if (inManifest.contains(file) || !SafePaths.isSafeFileName(file)) {
                continue;
            }
            Path path = SafePaths.resolveInside(modsDir, file);
            if (Files.isRegularFile(path)
                    && FileHasher.describe(path, entry.getValue()).sha512().equals(entry.getValue().sha512())) {
                removals.add(file);
            }
        }
        return List.copyOf(removals);
    }
}
