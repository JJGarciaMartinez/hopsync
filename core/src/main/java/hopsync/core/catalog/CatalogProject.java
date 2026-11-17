package hopsync.core.catalog;

import java.util.Objects;

/**
 * A mod project in the mod catalog (Modrinth).
 *
 * @param id          project id
 * @param slug        short name in the project's page address
 * @param title       name to show
 * @param description one-line summary
 * @param author      owner's user name, empty when unknown
 * @param iconUrl     https icon, or {@code null}
 * @param downloads   total downloads, for sorting and display
 */
public record CatalogProject(String id, String slug, String title, String description, String author,
                             String iconUrl, long downloads) {

    public CatalogProject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slug, "slug");
        title = title == null || title.isBlank() ? slug : title;
        description = description == null ? "" : description;
        author = author == null ? "" : author;
    }
}
