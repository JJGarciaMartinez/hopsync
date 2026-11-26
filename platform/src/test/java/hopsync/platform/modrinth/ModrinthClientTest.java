package hopsync.platform.modrinth;

import hopsync.core.catalog.CatalogProject;
import hopsync.core.catalog.CatalogVersion;
import hopsync.core.catalog.ProjectDetails;
import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.manifest.Manifest;
import hopsync.core.manifest.ManifestParser;
import hopsync.core.manifest.ModEntry;
import hopsync.core.manifest.ModSource;
import hopsync.core.pack.Pack;
import hopsync.core.pack.PackDefinition;
import hopsync.core.pack.PackSource;
import hopsync.core.usecase.DefinitionResolver;
import hopsync.platform.settings.DefinitionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthClientTest {
    private static final Pattern MODRINTH_URL = Pattern.compile("/data/([^/]+)/versions/([^/]+)/");

    @TempDir
    Path dir;

    /** Answers from Modrinth recorded for the hopsync-demo pack, served by endpoint. */
    private static String recorded(String url) throws IOException {
        String endpoint = url.substring(ModrinthClient.API.length(), url.indexOf('?'));
        return resource(endpoint.substring(1) + ".json");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ModrinthClientTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void demoPackResolvesToThePublishedManifest() throws IOException {
        Manifest published = ManifestParser.parse(resource("demo-manifest.json"));
        List<ModEntry> fromModrinth = published.mods().stream().filter(mod -> mod.source() == ModSource.MODRINTH).toList();
        List<PackDefinition.Mod> mods = new ArrayList<>();
        for (ModEntry mod : fromModrinth) {
            Matcher ids = MODRINTH_URL.matcher(mod.url());
            assertTrue(ids.find(), mod.url());
            mods.add(new PackDefinition.ModrinthMod(ids.group(1), mod.id(), ids.group(2), mod.version()));
        }
        // Reversed, to show the order comes from the files and not from the definition
        Collections.reverse(mods);
        DefinitionStore store = new DefinitionStore(dir);
        Pack pack = new Pack("demo", new PackSource.Local(null), dir.resolve("game"), "");
        store.save(pack.id(), new PackDefinition(published.pack(), mods, List.of()));

        Manifest resolved = new DefinitionResolver(new ModrinthClient(ModrinthClientTest::recorded), store).resolve(pack);

        assertEquals(published.pack(), resolved.pack());
        assertEquals(fromModrinth.size(), resolved.mods().size());
        Map<String, ModEntry> byFile = resolved.mods().stream().collect(Collectors.toMap(ModEntry::file, Function.identity()));
        for (ModEntry expected : fromModrinth) {
            assertEquals(expected, byFile.get(expected.file()), expected.file());
        }
        List<String> files = resolved.mods().stream().map(ModEntry::file).toList();
        assertEquals(files.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), files);
    }

    @Test
    void searchAsksForModsOfThePacksVersionAndLoader() throws IOException {
        List<String> asked = new ArrayList<>();
        ModrinthClient client = new ModrinthClient(url -> {
            asked.add(URLDecoder.decode(url, StandardCharsets.UTF_8));
            return "{\"hits\":[{\"project_id\":\"AANobbMI\",\"slug\":\"sodium\",\"title\":\"Sodium\","
                + "\"description\":\"Fast\",\"author\":\"jellysquid3\",\"icon_url\":null,\"downloads\":9000}]}";
        });

        List<CatalogProject> hits = client.search(" sodium ", "1.21.1", "fabric", 20);

        assertEquals(List.of(new CatalogProject("AANobbMI", "sodium", "Sodium", "Fast", "jellysquid3", null, 9000)), hits);
        String url = asked.getFirst();
        assertTrue(url.startsWith(ModrinthClient.API + "/search?query=sodium&"), url);
        assertTrue(url.contains("[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:1.21.1\"]]"), url);
        assertFalse(url.contains("index=downloads"), "words sort by relevance");

        client.search("", "1.21.1", "fabric", 20);
        assertTrue(asked.getLast().contains("index=downloads"), "no words lists the most downloaded");
    }

    @Test
    void gameVersionsAreReleasesOnlyInModrinthsOrder() throws IOException {
        ModrinthClient client = new ModrinthClient(url -> {
            assertEquals(ModrinthClient.API + "/tag/game_version", url);
            return "[{\"version\":\"1.21.2\",\"version_type\":\"release\"},"
                + "{\"version\":\"24w46a\",\"version_type\":\"snapshot\"},"
                + "{\"version\":\"1.21.1\",\"version_type\":\"release\"}]";
        });
        assertEquals(List.of("1.21.2", "1.21.1"), client.gameVersions());
    }

    @Test
    void anUnknownProjectHasNoVersions() throws IOException {
        ModrinthClient client = new ModrinthClient(url -> {
            throw new SyncException(new SyncFailure.HttpStatus(404, url));
        });
        assertEquals(List.of(), client.versions("no-such-mod", "1.21.1", "fabric"));
    }

    @Test
    void versionsCarryTheirFilesAndRequiredDependencies() throws IOException {
        List<CatalogVersion> versions = new ModrinthClient(ModrinthClientTest::recorded).versionsById(List.of("any"));
        CatalogVersion withDependency = versions.stream().filter(v -> !v.required().isEmpty()).findFirst().orElseThrow();

        assertTrue(withDependency.primaryFile().isPresent());
        assertTrue(withDependency.required().stream().allMatch(CatalogVersion.Dependency::required));
    }

    @Test
    void manyIdsAreAskedForInBatches() throws IOException {
        List<String> asked = new ArrayList<>();
        ModrinthClient client = new ModrinthClient(url -> {
            asked.add(url);
            return "[]";
        });

        client.versionsById(IntStream.range(0, ModrinthClient.BATCH + 1).mapToObj(i -> "v" + i).toList());

        assertEquals(2, asked.size());
    }

    @Test
    void detailsReadsGalleryAndBody() throws IOException {
        ModrinthClient client = new ModrinthClient(url -> {
            assertEquals(ModrinthClient.API + "/project/sodium", url);
            return """
                {"id":"AANobbMI","slug":"sodium","title":"Sodium","description":"Fast","body":"# Hi",
                 "gallery":[{"url":"https://cdn/a.png","title":"A","featured":false},
                            {"url":"https://cdn/b.png","title":"B","featured":true},
                            {"title":"no url"}]}""";
        });
        ProjectDetails details = client.details("sodium").orElseThrow();

        assertEquals("Fast", details.summary());
        assertEquals("# Hi", details.body());
        assertEquals(2, details.gallery().size());
        assertTrue(details.gallery().getFirst().featured());
    }

    @Test
    void detailsTolerateMissingGalleryAndBody() throws IOException {
        ProjectDetails details = new ModrinthClient(url -> "{\"id\":\"x\",\"slug\":\"x\",\"title\":\"X\"}")
            .details("x").orElseThrow();

        assertEquals(List.of(), details.gallery());
        assertEquals("", details.body());
    }

    @Test
    void detailsOfUnknownProjectIsEmpty() throws IOException {
        ModrinthClient client = new ModrinthClient(url -> {
            throw new SyncException(new SyncFailure.HttpStatus(404, url));
        });
        assertEquals(Optional.empty(), client.details("no-such-mod"));
    }
}
