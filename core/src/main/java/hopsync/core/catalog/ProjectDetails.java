package hopsync.core.catalog;

import java.util.Comparator;
import java.util.List;

/**
 * A mod project's page in the catalog.
 *
 * @param body    the project's long description, as the catalog writes it (Markdown)
 * @param gallery images to show, the featured one first
 */
public record ProjectDetails(String id, String slug, String title, String summary, String body,
                             List<Image> gallery) {

    public ProjectDetails {
        id = id == null ? "" : id;
        slug = slug == null ? "" : slug;
        title = title == null || title.isBlank() ? slug : title;
        summary = summary == null ? "" : summary;
        body = body == null ? "" : body;
        gallery = gallery == null ? List.of()
            : gallery.stream().sorted(Comparator.comparing((Image i) -> !i.featured())).toList();
    }

    /** A gallery picture: https url, optional title. */
    public record Image(String url, String title, boolean featured) {

        public Image {
            url = url == null ? "" : url;
            title = title == null ? "" : title;
        }
    }
}
