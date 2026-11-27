package hopsync.core.util;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Turns catalog Markdown into short plain text. */
public final class MarkdownText {

    private static final Pattern FENCE = Pattern.compile("(?s)(```|~~~).*?(\\1|$)");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML = Pattern.compile("</?[A-Za-z!][^>]*>");
    private static final Pattern RULE = Pattern.compile("(?m)^[ \\t]*([-*_][ \\t]*){3,}$");
    private static final Pattern TABLE_SEPARATOR =
        Pattern.compile("(?m)^[ \\t]*\\|?([ \\t]*:?-+:?[ \\t]*\\|)+([ \\t]*:?-+:?[ \\t]*)?(\\n|$)");
    private static final Pattern TABLE_ROW = Pattern.compile("(?m)^[ \\t]*\\|(.*)$");
    private static final Pattern STRONG = Pattern.compile("(\\*\\*|__|~~)(?=\\S)(.+?)(?<=\\S)\\1");
    // Single * or _ only around words, so snake_case names and a lone "2 * 3" stay as they are
    private static final Pattern EMPHASIS = Pattern.compile("(?<![\\w*])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![\\w*])"
        + "|(?<![\\w_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\w_])");
    private static final Pattern CODE = Pattern.compile("`+([^`\\n]+?)`+");
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
        text = LINE_BREAK.matcher(text).replaceAll("\n");
        text = HTML.matcher(text).replaceAll("");
        text = RULE.matcher(text).replaceAll("");
        text = TABLE_SEPARATOR.matcher(text).replaceAll("");
        text = TABLE_ROW.matcher(text).replaceAll(row -> Matcher.quoteReplacement(cells(row.group(1))));
        text = LINE_MARKER.matcher(text).replaceAll("");
        text = STRONG.matcher(text).replaceAll("$2");
        text = EMPHASIS.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
            match.group(1) != null ? match.group(1) : match.group(2)));
        text = CODE.matcher(text).replaceAll("$1");
        text = decodeEntities(text);
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

    private static String cells(String row) {
        return Arrays.stream(row.split("\\|")).map(String::strip).filter(cell -> !cell.isEmpty())
            .collect(Collectors.joining(" · "));
    }

    // &amp; last, so "&amp;lt;" stays the text "&lt;"
    private static String decodeEntities(String text) {
        return text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&");
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
