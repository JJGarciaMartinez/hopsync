package hopsync.desktop.i18n;

import hopsync.core.failure.SyncFailure;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    static final List<SyncFailure> ONE_OF_EACH = List.of(
        new SyncFailure.NotConfigured(),
        new SyncFailure.InvalidUrl("http://x.org/m.json"),
        new SyncFailure.Network("github.com", "timeout"),
        new SyncFailure.HttpStatus(404, "https://x.org/m.json"),
        new SyncFailure.ResponseTooLarge("https://x.org/m.json", 5),
        new SyncFailure.ManifestInvalid("mods[0].sha512 has an invalid format"),
        new SyncFailure.ModUnavailable("sodium"),
        new SyncFailure.DefinitionMissing("3f2a9c1e"),
        new SyncFailure.GitHubNotConfigured(),
        new SyncFailure.GitHubCodeExpired(),
        new SyncFailure.GitHubDenied(),
        new SyncFailure.GitHubSignedOut(),
        new SyncFailure.RepoNameTaken("mi-pack"),
        new SyncFailure.NotRepoOwner("other/pack"),
        new SyncFailure.NotAHopSyncRepo("player/notes"),
        new SyncFailure.PublishBuildFailed("https://github.com/p/r/actions/runs/1"),
        new SyncFailure.UnsupportedFormat(3, 2),
        new SyncFailure.HashMismatch("create.jar"),
        new SyncFailure.SizeMismatch("create.jar", 10, 9),
        new SyncFailure.UnsafePath("../evil.jar"),
        new SyncFailure.FolderUnavailable("/game/mods"),
        new SyncFailure.FolderInUse("/game"),
        new SyncFailure.FolderUsedByPack("/game", "Demo"),
        new SyncFailure.DiskFull("create.jar"),
        new SyncFailure.FileLocked("create.jar"),
        new SyncFailure.PermissionDenied("/game/mods"),
        new SyncFailure.Unexpected("bug"));

    @Test
    void everyFailureHasAMessage() {
        Set<Class<?>> covered = ONE_OF_EACH.stream().map(Object::getClass).collect(Collectors.toSet());
        assertEquals(Set.copyOf(Arrays.asList(SyncFailure.class.getPermittedSubclasses())), covered,
            "add new SyncFailure types to ONE_OF_EACH and messages.properties");

        for (SyncFailure failure : ONE_OF_EACH) {
            String message = Messages.failure(failure);
            assertFalse(message.isBlank(), failure.toString());
            assertFalse(message.contains("{"), "unfilled placeholder in " + message);
            assertNotEquals(failure.describe(), message, "English log text shown to the player");
        }
    }

    @Test
    void failuresNameWhatThePlayerNeeds() {
        assertTrue(Messages.failure(new SyncFailure.Network("github.com", "timeout")).contains("github.com"));
        assertTrue(Messages.failure(new SyncFailure.FileLocked("create.jar")).contains("Minecraft"));
        assertTrue(Messages.failure(new SyncFailure.HttpStatus(503, "u")).contains("503"));
    }

    @Test
    void httpStatusesGetTheRightAdvice() {
        String notFound = Messages.failure(new SyncFailure.HttpStatus(404, "u"));
        String server = Messages.failure(new SyncFailure.HttpStatus(502, "u"));
        assertTrue(notFound.contains("Ajustes"), notFound);
        assertTrue(server.contains("más tarde"), server);
    }

    @Test
    void pluralsAndAccents() {
        assertEquals("Aplicado con 1 error", Messages.get("hero.state.doneWithErrors", 1));
        assertEquals("Aplicado con 3 errores", Messages.get("hero.state.doneWithErrors", 3));
        assertEquals("Ya instalados · 70 sin cambios · Ver todos", Messages.get("mods.upToDateMore", 70));
        assertEquals("1 nuevo", Messages.get("hero.chip.new", 1));
        assertEquals("3 actualizan", Messages.get("hero.chip.update", 3));
        assertEquals("1 se quita", Messages.get("hero.chip.remove", 1));
        assertEquals("7 cambios aplicados", Messages.get("toast.applied", 7));
        assertEquals("Comparando con el pack…", Messages.get("hero.state.checking"));
    }

    private static Properties bundle(String file) throws IOException {
        Properties texts = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(file)) {
            texts.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return texts;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\{(\\d)").matcher(text);
        while (matcher.find()) found.add(matcher.group(1));
        return found;
    }

    @Test
    void everyTextExistsInBothLanguagesWithTheSamePlaceholders() throws IOException {
        Properties english = bundle("messages.properties");
        Properties spanish = bundle("messages_es.properties");
        assertEquals(english.stringPropertyNames(), spanish.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            assertEquals(placeholders(english.getProperty(key)), placeholders(spanish.getProperty(key)), key);
            // MessageFormat drops a plain apostrophe; the texts use the typographic one
            assertFalse(english.getProperty(key).contains("'"), key);
            assertFalse(spanish.getProperty(key).contains("'"), key);
        }
    }

    @Test
    void englishIsPickedOrFollowsANonSpanishSystem() {
        Locale system = Locale.getDefault();
        try {
            Messages.use("en");
            assertEquals("Applied with 3 errors", Messages.get("hero.state.doneWithErrors", 3));
            assertEquals("1 change applied", Messages.get("toast.applied", 1));
            assertTrue(Messages.failure(new SyncFailure.HttpStatus(404, "u")).contains("Settings"));

            Locale.setDefault(Locale.FRANCE);
            Messages.use("");
            assertEquals(Locale.ENGLISH, Messages.locale());
            Locale.setDefault(Locale.forLanguageTag("es-AR"));
            Messages.use("");
            assertEquals("Agregar pack", Messages.get("library.add"));
        } finally {
            Locale.setDefault(system);
            Messages.use("es");
        }
    }
}
