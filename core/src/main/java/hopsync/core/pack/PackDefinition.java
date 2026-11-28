package hopsync.core.pack;

import hopsync.core.manifest.PackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A pack the player created, as {@code hopsync.toml} describes it in a pack repository: the pack's details,
 * its mods and the configs it enforces. HopSync resolves it into a manifest to apply it, and writes it to the
 * repository to publish it.
 *
 * @param pack             name, version, description, server, Minecraft version and loader
 * @param mods             in the order they were added
 * @param configRules      the config units, relative to the pack's {@code configs/}, that are not left at the
 *                         install-if-missing default: those that replace the player's copy and those that are optional
 */
public record PackDefinition(PackInfo pack, List<Mod> mods, List<ConfigRule> configRules) {

    public PackDefinition {
        Objects.requireNonNull(pack, "pack");
        mods = List.copyOf(mods);
        configRules = List.copyOf(configRules);
        Set<String> units = new HashSet<>();
        for (ConfigRule rule : configRules) {
            if (!units.add(rule.unit())) {
                throw new IllegalArgumentException("Two modes for the config unit " + rule.unit());
            }
        }
    }

    /** A new pack with no mods yet. */
    public static PackDefinition empty(PackInfo pack) {
        return new PackDefinition(pack, List.of(), List.of());
    }

    public PackDefinition withPack(PackInfo newPack) {
        return new PackDefinition(newPack, mods, configRules);
    }

    public PackDefinition withConfigRules(List<ConfigRule> newRules) {
        return new PackDefinition(pack, mods, newRules);
    }

    public PackDefinition withMods(List<Mod> newMods) {
        return new PackDefinition(pack, newMods, configRules);
    }

    /** One mod of the pack. */
    public sealed interface Mod {
    }

    /**
     * A mod on Modrinth, pinned to one version so HopSync and the repository's build pick the same file.
     *
     * @param projectId     Modrinth project id
     * @param slug          project slug, as {@code hopsync.toml} names it
     * @param versionId     Modrinth version id
     * @param versionNumber the version as its author writes it, for display
     */
    public record ModrinthMod(String projectId, String slug, String versionId, String versionNumber) implements Mod {
        public ModrinthMod {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(versionId, "versionId");
            versionNumber = versionNumber == null ? "" : versionNumber;
        }
    }

    /**
     * A jar the player added that is not on Modrinth, kept in the pack's {@code local-mods/}.
     *
     * @param file the jar's file name
     */
    public record OwnMod(String file) implements Mod {
        public OwnMod {
            Objects.requireNonNull(file, "file");
        }
    }
}
