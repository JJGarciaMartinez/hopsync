package hopsync.core.util;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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

    @Test
    void realisticBodyBecomesPlainText() {
        String body = """
            # **Sodium**

            __Sodium__ is a *free* and _open-source_ rendering engine.<br>It needs `Fabric API`.

            ---

            | Feature | Status |
            |---------|:------:|
            | Chunks  | ~~slow~~ fast |

            Tom &amp; Jerry&nbsp;say &quot;hi&quot; &lt;3 &#39;ok&#39; &gt;_&lt;
            """;
        assertEquals("Sodium\n\nSodium is a free and open-source rendering engine.\nIt needs Fabric API.\n\n"
            + "Feature · Status\nChunks · slow fast\n\nTom & Jerry say \"hi\" <3 'ok' >_<",
            MarkdownText.plain(body, 600));
    }

    @Test
    void comparisonsAreNotTags() {
        assertEquals("if a < b > c then", MarkdownText.plain("if a < b > c then", 600));
    }

    @Test
    void snakeCaseAndLoneAsterisksSurvive() {
        assertEquals("set max_render_distance to 2 * 3", MarkdownText.plain("set max_render_distance to 2 * 3", 600));
    }

    @Test
    void entitiesAreDecodedOnce() {
        assertEquals("&lt; & \" '", MarkdownText.plain("&amp;lt; &amp; &quot; &#39;", 600));
    }

    @Test
    void tagsDoNotSpanParagraphsOfProse() {
        assertEquals("a < b\n\nc > d", MarkdownText.plain("a < b\n\nc > d", 600));
    }

    @Test
    void zeroOrNegativeLimitGivesEmpty() {
        assertEquals("", MarkdownText.plain("text", 0));
        assertEquals("", MarkdownText.plain("text", -5));
    }

    @Test
    void longBodyIsCappedAtALineAndMarkedAsCut() {
        String body = "Intro.\n\n" + "<b></b>\n".repeat(2_000) + "\nTail";
        assertEquals("Intro.…", MarkdownText.plain(body, 600));
    }

    @ParameterizedTest(name = "hostile body {index}")
    @MethodSource("hostileBodies")
    void hostileBodiesFinishQuickly(String body) {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            String result = MarkdownText.plain(body, 600);
            assertTrue(result.length() <= 601, () -> String.valueOf(result.length()));
        });
    }

    static Stream<String> hostileBodies() {
        return Stream.of("-".repeat(100_000), "*".repeat(100_000), "_".repeat(100_000), "|-".repeat(50_000),
            "**a ".repeat(25_000), "__a ".repeat(25_000), "~~a ".repeat(25_000), "<a".repeat(50_000),
            "[".repeat(100_000), "![".repeat(50_000), "|-*_<a[![`~**__&amp;\n".repeat(250_000));
    }
}
