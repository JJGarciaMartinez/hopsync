package hopsync.core.util;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Turns catalog Markdown into short plain text. */
public final class MarkdownText {

    private static final Pattern FENCE = Pattern.compile("(?s)(```|~~~).*?(\\1|$)");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern HTML = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern LINE_MARKER = Pattern.compile("(?m)^[ \\t]*(#{1,6}|>|[-*+]|\\d+\\.)[ \\t]+");
    private static final Pattern BREAKS = Pattern.compile("\\n[ \\t]*\\n+");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");

    private MarkdownText() {
    }

    /** Plain text of the Markdown, cut at a sentence end (or a word) before {@code maxChars} with an ellipsis. */
    public static String plain(String markdown, int maxChars) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String text = FENCE.matcher(markdown.replace("\r\n", "\n")).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = HTML.matcher(text).replaceAll("");
        text = LINE_MARKER.matcher(text).replaceAll("");
        String[] paragraphs = BREAKS.split(text.strip());
        StringBuilder out = new StringBuilder();
        for (String paragraph : paragraphs) {
            String line = paragraph.lines().map(l -> SPACES.matcher(l).replaceAll(" ").strip())
                .filter(l -> !l.isEmpty()).collect(Collectors.joining("\n"));
            if (!line.isEmpty()) {
                out.append(out.length() == 0 ? "" : "\n\n").append(line);
            }
        }
        return cut(out.toString(), maxChars);
    }

    private static String cut(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        String head = text.substring(0, maxChars);
        int sentence = Math.max(head.lastIndexOf(". "), head.lastIndexOf(".\n"));
        if (sentence > 0) {
            return head.substring(0, sentence + 1) + "…";
        }
        int word = head.lastIndexOf(' ');
        return (word > 0 ? head.substring(0, word) : head).stripTrailing() + "…";
    }
}
