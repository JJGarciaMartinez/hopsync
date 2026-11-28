package hopsync.core.pack;

import hopsync.core.util.SafePaths;

import java.util.Objects;

/**
 * The mode of one config unit of a pack: a top-level folder of {@code configs/} written with a trailing slash
 * ({@code jei/}) or a file directly in it ({@code sodium-options.json}). Units left at {@link ConfigMode#IF_MISSING}
 * have no rule.
 */
public record ConfigRule(String unit, ConfigMode mode) {

    public ConfigRule {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mode, "mode");
        if (mode == ConfigMode.IF_MISSING) {
            throw new IllegalArgumentException("IF_MISSING is the default and has no rule: " + unit);
        }
        String name = unit.endsWith("/") ? unit.substring(0, unit.length() - 1) : unit;
        if (name.contains("/") || !SafePaths.isSafeRelativePath(name)) {
            throw new IllegalArgumentException("Not a config unit: " + unit);
        }
    }
}
