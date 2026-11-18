package hopsync.desktop.screens;

import hopsync.desktop.i18n.Messages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/** Every screen of {@link ScreensTest} again with the English texts, which have their own lengths and plurals. */
class ScreensEnglishTest extends ScreensTest {

    @BeforeAll
    static void inEnglish() {
        Messages.use("en");
    }

    @AfterAll
    static void backToSpanish() {
        Messages.use("es");
    }
}
