package hopsync.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTextTest {

    @Test
    void dropsImagesLinksAndHtml() {
        assertEquals("Sodium is fast",
            MarkdownText.plain("![x](https://a/b.png)\n[Sodium](https://s) is <b>fast</b>", 600));
    }

    @Test
    void keepsParagraphsAndStripsMarkers() {
        assertEquals("Title\n\none\ntwo", MarkdownText.plain("# Title\n\n- one\n- two", 600));
    }

    @Test
    void dropsCodeFences() {
        assertEquals("Before\n\nAfter", MarkdownText.plain("Before\n\n```java\nint x = 1;\n```\n\nAfter", 600));
    }

    @Test
    void cutsOnASentenceEndWithEllipsis() {
        String sentence = "one two three four five six seven eight nine ten.";
        String text = (sentence + " ").repeat(14).strip();
        String result = MarkdownText.plain(text, 100);
        assertTrue(result.length() <= 101, result);
        assertTrue(result.endsWith("…"), result);
        assertEquals('.', result.charAt(result.length() - 2));
    }

    @Test
    void blankOrNullGivesEmpty() {
        assertEquals("", MarkdownText.plain(null, 10));
        assertEquals("", MarkdownText.plain("  ", 10));
    }
}
