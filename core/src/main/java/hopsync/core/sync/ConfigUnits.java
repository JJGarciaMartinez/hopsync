package hopsync.core.sync;

import hopsync.core.manifest.ConfigEntry;
import hopsync.core.manifest.ConfigRoot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Rules for the config units a pack offers: what may live in the game folder and how files group. */
public final class ConfigUnits {

    /** The only files allowed in the game folder (outside {@code config/}). */
    public static final Set<String> GAME_FILES = Set.of("options.txt", "optionsshaders.txt");

    private ConfigUnits() {
    }

    /**
     * What a mode applies to: a top-level folder of {@code config/} (id ends with {@code /}), a file directly in
     * {@code config/}, or a whitelisted game file.
     */
    public record Unit(ConfigRoot root, String id) {
        /** A stable key for storing choices; the same name in two roots stays distinct. */
        public String key() {
            return root.json() + ":" + id;
        }

        public boolean isFolder() {
            return id.endsWith("/");
        }
    }

    public static Unit unitOf(ConfigEntry entry) {
        int slash = entry.path().indexOf('/');
        if (entry.root() == ConfigRoot.CONFIG && slash > 0) {
            return new Unit(entry.root(), entry.path().substring(0, slash + 1));
        }
        return new Unit(entry.root(), entry.path());
    }

    /** The unit a rule such as {@code jei/} or {@code a.toml}, written relative to {@code configs/}, names. */
    public static Unit configUnit(String rule) {
        int slash = rule.indexOf('/');
        return new Unit(ConfigRoot.CONFIG, slash > 0 ? rule.substring(0, slash + 1) : rule);
    }

    public static List<ConfigEntry> entriesOf(Unit unit, List<ConfigEntry> entries) {
        return entries.stream().filter(entry -> unitOf(entry).equals(unit)).toList();
    }

    /** Changes whenever any file of the unit is added, removed or changed, whatever the order. */
    public static String revision(List<ConfigEntry> unitEntries) {
        StringBuilder content = new StringBuilder();
        unitEntries.stream().sorted(Comparator.comparing(ConfigEntry::path))
            .forEach(entry -> content.append(entry.path()).append('\t').append(entry.sha512()).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
