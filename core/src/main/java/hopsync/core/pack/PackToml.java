package hopsync.core.pack;

import hopsync.core.manifest.PackInfo;

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
        toml.append("# Files under configs/ are installed only if the player does not have them yet;\n");
        toml.append("# the ones listed here always replace the player's copy.\n");
        toml.append("overwrite = [");
        for (int i = 0; i < definition.overwriteConfigs().size(); i++) {
            toml.append(i == 0 ? "" : ", ").append(quote(definition.overwriteConfigs().get(i)));
        }
        toml.append("]\n");
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
