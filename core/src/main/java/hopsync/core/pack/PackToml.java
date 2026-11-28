package hopsync.core.pack;

import hopsync.core.manifest.PackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a created pack's definition as the {@code hopsync.toml} of a pack repository, which the repository's
 * build reads to publish the manifest. Catalog mods are pinned by their Modrinth version id, so the build picks the
 * same file HopSync does; the version number follows as a comment for whoever reads the file.
 */
public final class PackToml {

    private PackToml() {
    }

    public static String write(PackDefinition definition) {
        PackInfo pack = definition.pack();
        StringBuilder toml = new StringBuilder();
        toml.append("# HopSync modpack definition, written by HopSync.\n");
        toml.append("# Edit this pack in HopSync and publish its changes from there: edits made here are replaced.\n\n");
        toml.append("[pack]\n");
        line(toml, "name", pack.name());
        line(toml, "version", pack.version());
        line(toml, "description", pack.description() == null ? "" : pack.description());
        line(toml, "minecraft", pack.minecraft());
        line(toml, "loader", pack.loader());
        if (pack.serverIp() != null && !pack.serverIp().isBlank()) {
            line(toml, "server_ip", pack.serverIp());
        }
        toml.append("\n[configs]\n");
        toml.append("# Units under configs/ are a top-level folder (\"jei/\") or a file directly in it. They are installed\n");
        toml.append("# only if the player does not have them yet; \"overwrite\" ones always replace the player's copy and\n");
        toml.append("# \"optional\" ones are installed only if the player chooses them.\n");
        units(toml, "overwrite", definition, ConfigMode.ALWAYS);
        units(toml, "optional", definition, ConfigMode.OPTIONAL);
        toml.append("\n# Mods hosted on Modrinth. Jars that are not on Modrinth are in local-mods/.\n");
        for (PackDefinition.Mod mod : definition.mods()) {
            if (mod instanceof PackDefinition.ModrinthMod modrinth) {
                toml.append("\n[[mods]]\n");
                line(toml, "modrinth", modrinth.slug());
                toml.append("version = ").append(quote(modrinth.versionId()));
                if (!modrinth.versionNumber().isBlank()) {
                    toml.append("  # ").append(modrinth.versionNumber().replace('\n', ' '));
                }
                toml.append('\n');
            }
        }
        return toml.toString();
    }

    /**
     * Reads what {@link #write} produces: the {@code [pack]} and {@code [configs]} tables and the {@code [[mods]]}
     * entries, with strings and one-line string arrays. Unknown tables and keys are ignored. Catalog mods come back
     * with an empty {@code projectId} (the file only holds the slug and the version id) and own jars are not listed,
     * since they are found in {@code local-mods/}.
     *
     * @throws IllegalArgumentException if the text is malformed or lacks a required key; the repository's file is
     *                                  not trusted, so the message names the line
     */
    public static PackDefinition parse(String toml) {
        Map<String, Entry> pack = new HashMap<>();
        Map<String, Entry> configs = new HashMap<>();
        List<Map<String, Entry>> modTables = new ArrayList<>();
        Map<String, Entry> table = null;
        int number = 0;
        for (String raw : toml.split("\\R", -1)) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                if (line.startsWith("[")) {
                    table = switch (line) {
                        case "[pack]" -> pack;
                        case "[configs]" -> configs;
                        case "[[mods]]" -> {
                            modTables.add(new HashMap<>());
                            yield modTables.get(modTables.size() - 1);
                        }
                        default -> new HashMap<>();
                    };
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals < 1 || table == null) {
                    throw new IllegalArgumentException("expected a key = value inside a table");
                }
                table.put(line.substring(0, equals).strip(), readEntry(line.substring(equals + 1).strip()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("hopsync.toml line " + number + ": " + e.getMessage(), e);
            }
        }
        PackInfo info = new PackInfo(required(pack, "pack", "name"), required(pack, "pack", "version"),
            pack.containsKey("description") ? required(pack, "pack", "description") : "",
            pack.containsKey("server_ip") ? required(pack, "pack", "server_ip") : null,
            required(pack, "pack", "minecraft"), required(pack, "pack", "loader"));
        List<PackDefinition.Mod> mods = new ArrayList<>();
        for (Map<String, Entry> mod : modTables) {
            mods.add(new PackDefinition.ModrinthMod("", required(mod, "mods", "modrinth"),
                required(mod, "mods", "version"), mod.get("version").comment()));
        }
        List<ConfigRule> rules = new ArrayList<>();
        addRules(rules, configs.get("overwrite"), ConfigMode.ALWAYS);
        addRules(rules, configs.get("optional"), ConfigMode.OPTIONAL);
        return new PackDefinition(info, mods, rules);
    }

    private static void addRules(List<ConfigRule> rules, Entry entry, ConfigMode mode) {
        if (entry != null) {
            entry.items().forEach(unit -> rules.add(new ConfigRule(unit, mode)));
        }
    }

    /** A value and the comment after it; {@code text} is {@code null} for an array and {@code items} is empty for a string. */
    private record Entry(String text, List<String> items, String comment) {
    }

    private static String required(Map<String, Entry> table, String section, String key) {
        Entry entry = table.get(key);
        if (entry == null || entry.text() == null) {
            throw new IllegalArgumentException("hopsync.toml is missing the string [" + section + "] " + key);
        }
        return entry.text();
    }

    private static Entry readEntry(String value) {
        List<String> items = new ArrayList<>();
        String text = null;
        int end;
        if (value.startsWith("[")) {
            end = 1;
            while (true) {
                end = skipSpaces(value, end);
                if (end < value.length() && value.charAt(end) == ']') {
                    end++;
                    break;
                }
                StringBuilder item = new StringBuilder();
                end = skipSpaces(value, readString(value, end, item));
                items.add(item.toString());
                if (end < value.length() && value.charAt(end) == ',') {
                    end++;
                } else if (end >= value.length() || value.charAt(end) != ']') {
                    throw new IllegalArgumentException("expected ',' or ']' in the array");
                }
            }
        } else {
            StringBuilder out = new StringBuilder();
            end = readString(value, 0, out);
            text = out.toString();
        }
        String rest = value.substring(end).strip();
        if (!rest.isEmpty() && !rest.startsWith("#")) {
            throw new IllegalArgumentException("unexpected text after the value: " + rest);
        }
        return new Entry(text, items, rest.isEmpty() ? "" : rest.substring(1).strip());
    }

    private static int skipSpaces(String value, int from) {
        int i = from;
        while (i < value.length() && value.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** Reads the basic string that starts at {@code start} into {@code out}; returns the index after its closing quote. */
    private static int readString(String value, int start, StringBuilder out) {
        if (start >= value.length() || value.charAt(start) != '"') {
            throw new IllegalArgumentException("expected a quoted string");
        }
        int i = start + 1;
        while (i < value.length()) {
            char c = value.charAt(i++);
            if (c == '"') {
                return i;
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i >= value.length()) {
                break;
            }
            char escaped = value.charAt(i++);
            switch (escaped) {
                case '"', '\\' -> out.append(escaped);
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 > value.length()) {
                        throw new IllegalArgumentException("incomplete \\u escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(value.substring(i, i + 4), 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("invalid \\u escape", e);
                    }
                    i += 4;
                }
                default -> throw new IllegalArgumentException("unknown escape \\" + escaped);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private static void units(StringBuilder toml, String key, PackDefinition definition, ConfigMode mode) {
        toml.append(key).append(" = [");
        boolean first = true;
        for (ConfigRule rule : definition.configRules()) {
            if (rule.mode() == mode) {
                toml.append(first ? "" : ", ").append(quote(rule.unit()));
                first = false;
            }
        }
        toml.append("]\n");
    }

    private static void line(StringBuilder toml, String key, String value) {
        toml.append(key).append(" = ").append(quote(value)).append('\n');
    }

    /** A TOML basic string: quotes, backslashes and control characters escaped. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        out.append(String.format("\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
