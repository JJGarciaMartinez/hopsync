package hopsync.core.util;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Turns catalog Markdown into short plain text. */
public final class MarkdownText {

    private static final Pattern FENCE = Pattern.compile("(?s)(```|~~~).*?(\\1|$)");
    // Spans are bounded so an unclosed marker scans a few KB, not the rest of the body
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]{0,1000}+]\\([^)]{0,2000}+\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]{0,1000}+)]\\([^)]{0,2000}+\\)");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML = Pattern.compile("</?[A-Za-z!][^>]{0,2000}+>");
    // Lookaheads plus one possessive class instead of a repeated group: Java recurses once per group repetition
    private static final Pattern RULE = Pattern.compile("(?m)^(?=(?:[ \\t]*+[-*_]){3})[-*_ \\t]++$");
    private static final Pattern TABLE_SEPARATOR =
        Pattern.compile("(?m)^(?=[ \\t]*+\\|?[ \\t]*+:?-)(?=[^|\\n]*+\\|)[-:| \\t]++(?:\\n|$)");
    private static final Pattern TABLE_ROW = Pattern.compile("(?m)^[ \\t]*\\|(.*)$");
    private static final Pattern STRONG = Pattern.compile("(\\*\\*|__|~~)(?=\\S)(.{1,1000}?)(?<=\\S)\\1");
    // Single * or _ only around words, so snake_case names and a lone "2 * 3" stay as they are
    private static final Pattern EMPHASIS = Pattern.compile("(?<![\\w*])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![\\w*])"
        + "|(?<![\\w_])_(?=\\S)([^_\\n]+?)(?<=\\S)_(?![\\w_])");
    private static final Pattern CODE = Pattern.compile("`+([^`\\n]+?)`+");
    private static final Pattern LINE_MARKER = Pattern.compile("(?m)^[ \\t]*(#{1,6}|>|[-*+]|\\d+\\.)[ \\t]+");
    private static final Pattern BREAKS = Pattern.compile("\\n[ \\t]*\\n+");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");

    // The output is at most maxChars long, so the regexes (some quadratic on hostile lines) only see a bounded head
    private static final int MIN_INPUT = 8_192;
    private static final int INPUT_PER_OUTPUT_CHAR = 8;

    private MarkdownText() {
    }

    /** Plain text of the Markdown, cut at a sentence end (or a word) before {@code maxChars} with an ellipsis. */
    public static String plain(String markdown, int maxChars) {
        if (markdown == null || markdown.isBlank() || maxChars <= 0) {
            return "";
        }
        String head = head(markdown, Math.max(MIN_INPUT, (int) Math.min(Integer.MAX_VALUE,
            (long) maxChars * INPUT_PER_OUTPUT_CHAR)));
        boolean capped = head.length() < markdown.length();
        String text = FENCE.matcher(head.replace("\r\n", "\n")).replaceAll("");
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
        String result = cut(out.toString(), maxChars);
        return capped && !result.endsWith("…") ? result + "…" : result;
    }

    // Cut at the last paragraph, else line, break in the second half of the limit, else at the limit
    private static String head(String markdown, int limit) {
        if (markdown.length() <= limit) {
            return markdown;
        }
        String head = markdown.substring(0, limit);
        int end = head.lastIndexOf("\n\n");
        if (end < limit / 2) {
            end = head.lastIndexOf('\n');
        }
        return end >= limit / 2 ? head.substring(0, end) : head;
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
