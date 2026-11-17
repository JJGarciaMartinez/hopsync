package hopsync.core.port;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The mod catalog created packs pick their mods from (Modrinth). */
public interface ModCatalog {

    /** Mods matching the words that work with the Minecraft version and loader, best match first; no words lists the most downloaded. */
    List<CatalogProject> search(String query, String minecraft, String loader, int limit) throws IOException;

    /** A project's versions that work with the Minecraft version and loader, newest first; empty if unknown. */
    List<CatalogVersion> versions(String projectIdOrSlug, String minecraft, String loader) throws IOException;

    /** The versions with these ids; unknown ids are left out. */
    List<CatalogVersion> versionsById(Collection<String> versionIds) throws IOException;

    /** The projects with these ids, keyed by id, with their author; unknown ids are left out. */
    Map<String, CatalogProject> projects(Collection<String> projectIds) throws IOException;
}
